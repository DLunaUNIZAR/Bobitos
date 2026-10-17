package com.dlunaunizar.bobitos.feature.recipes

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.EDITOR_SAVE_TIMEOUT_MILLIS
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.FailureKind
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.PantryItem
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.core.model.pantryId
import com.dlunaunizar.bobitos.data.recipeimport.ImportFailure
import com.dlunaunizar.bobitos.data.recipeimport.ImportedRecipe
import com.dlunaunizar.bobitos.data.recipeimport.RecipeImportException
import com.dlunaunizar.bobitos.data.recipeimport.RecipeImporter
import com.dlunaunizar.bobitos.data.repository.PantryFailure
import com.dlunaunizar.bobitos.data.repository.PantryRepositoryException
import com.dlunaunizar.bobitos.data.repository.RecipeFailure
import com.dlunaunizar.bobitos.data.repository.RecipeRepository
import com.dlunaunizar.bobitos.data.repository.RecipeRepositoryException
import com.dlunaunizar.bobitos.data.repository.ShoppingFailure
import com.dlunaunizar.bobitos.data.repository.ShoppingRepositoryException
import com.dlunaunizar.bobitos.feature.common.FakePantryRepository
import com.dlunaunizar.bobitos.feature.common.IngredientReviewChoice
import com.dlunaunizar.bobitos.feature.common.RecordingShoppingRepository
import com.dlunaunizar.bobitos.feature.common.ReviewRowKind
import com.dlunaunizar.bobitos.feature.common.ingredientShoppingSource
import com.dlunaunizar.bobitos.feature.common.initialState
import com.dlunaunizar.bobitos.feature.common.toChoice
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientBrandRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientPrefsRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RecipesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeRecipeRepository()
    private val importer = FakeRecipeImporter()
    private val shoppingRepository = RecordingShoppingRepository()
    private val ingredientRepository = FakeIngredientRepository()
    private val brandRepository = FakeIngredientBrandRepository()
    private val prefsRepository = FakeIngredientPrefsRepository()
    private val pantryRepository = FakePantryRepository()
    private val viewModel = RecipesViewModel(
        repository,
        importer,
        shoppingRepository,
        ingredientShoppingSource(ingredientRepository, brandRepository, prefsRepository),
        pantryRepository,
    )

    @Test
    fun `observes global and personal recipes`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.globalState.value = listOf(recipe("g1", RecipeVisibility.GLOBAL, "Paella"))
        repository.mineState.value = listOf(recipe("m1", RecipeVisibility.PRIVATE, "Tortilla"))

        viewModel.observe()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("Paella"), (state.global as UiState.Content).value.map(Recipe::title))
        assertEquals(listOf("Tortilla"), (state.mine as UiState.Content).value.map(Recipe::title))
    }

    @Test
    fun `setQuery updates the state`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.setQuery("lente")

        assertEquals("lente", viewModel.uiState.value.query)
    }

    @Test
    fun `creating a recipe trims the title and reports success`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.createRecipe(RecipeVisibility.PRIVATE, "  Tortilla  ", "  ", null)
        advanceUntilIdle()

        assertEquals(1, repository.createCount)
        assertEquals(RecipeVisibility.PRIVATE, repository.lastVisibility)
        assertEquals("Tortilla", repository.lastTitle)
        assertEquals(null, repository.lastSourceRecipeId)
        assertEquals(RecipeUiMessage.RecipeSaved, viewModel.uiState.value.notice)
    }

    @Test
    fun aLoadFailureNeverExposesTheExceptionText() = runTest(mainDispatcherRule.testDispatcher) {
        repository.loadFailure = RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions.")

        viewModel.observe()
        advanceUntilIdle()

        assertEquals(UiState.Error(FailureKind.UNKNOWN), viewModel.uiState.value.global)
    }

    @Test
    fun `a repository limit failure shows its message`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.nextFailure = RecipeRepositoryException(RecipeFailure.ServingsOutOfRange)

        viewModel.createRecipe(RecipeVisibility.PRIVATE, "Lentejas", null, null)
        advanceUntilIdle()

        assertEquals(RecipeUiMessage.ServingsInvalid, viewModel.uiState.value.error)
    }

    @Test
    fun `creating a recipe forwards its ingredients`() = runTest(mainDispatcherRule.testDispatcher) {
        val ingredients = listOf(Ingredient("Huevo", "2", "uds"), Ingredient("Sal"))
        viewModel.createRecipe(RecipeVisibility.PRIVATE, "Tortilla", null, null, ingredients)
        advanceUntilIdle()

        assertEquals(ingredients, repository.lastIngredients)
    }

    @Test
    fun `an invalid recipe never reaches the repository`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.createRecipe(RecipeVisibility.PRIVATE, "   ", null, null)

        assertEquals(0, repository.createCount)
        assertEquals(RecipeUiMessage.TitleRequired, viewModel.uiState.value.error)
    }

    @Test
    fun `an admin can publish a global recipe`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.admin = true
        viewModel.observe()
        advanceUntilIdle()

        viewModel.createRecipe(RecipeVisibility.GLOBAL, "Paella", null, null)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.isAdmin)
        assertEquals(RecipeVisibility.GLOBAL, repository.lastVisibility)
    }

    @Test
    fun `a non-admin global recipe is downgraded to private`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe()
        advanceUntilIdle()

        viewModel.createRecipe(RecipeVisibility.GLOBAL, "Paella", null, null)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isAdmin)
        assertEquals(RecipeVisibility.PRIVATE, repository.lastVisibility)
    }

    @Test
    fun `fork copies the source recipe including its ingredients and marks its origin`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val ingredients = listOf(Ingredient("Arroz", "300", "g"), Ingredient("Azafrán"))
            viewModel.fork(recipe("origin", RecipeVisibility.GLOBAL, "Paella común", ingredients))
            advanceUntilIdle()

            assertEquals(1, repository.createCount)
            assertEquals(RecipeVisibility.PRIVATE, repository.lastVisibility)
            assertEquals("Paella común", repository.lastTitle)
            assertEquals("origin", repository.lastSourceRecipeId)
            assertEquals(ingredients, repository.lastIngredients)
            assertEquals(RecipeUiMessage.RecipeForked, viewModel.uiState.value.notice)
        }

    @Test
    fun `a successful import leaves a draft to review`() = runTest(mainDispatcherRule.testDispatcher) {
        importer.result = ImportedRecipe(
            title = "Tortilla",
            description = "Batir y cuajar",
            category = "Cena",
            ingredients = listOf(Ingredient("Huevo", "2", null)),
            sourceUrl = "https://example.com/tortilla",
        )

        viewModel.importFromUrl("https://example.com/tortilla")
        advanceUntilIdle()

        val draft = viewModel.uiState.value.importDraft
        assertEquals("Tortilla", draft?.title)
        assertEquals("https://example.com/tortilla", draft?.sourceUrl)
        assertEquals(false, viewModel.uiState.value.isImporting)
    }

    @Test
    fun `consuming the draft clears it`() = runTest(mainDispatcherRule.testDispatcher) {
        importer.result = ImportedRecipe("Tortilla", null, null, emptyList(), "https://example.com/t")
        viewModel.importFromUrl("https://example.com/t")
        advanceUntilIdle()

        viewModel.consumeImportDraft()

        assertEquals(null, viewModel.uiState.value.importDraft)
    }

    @Test
    fun `a failed import surfaces an error and no draft`() = runTest(mainDispatcherRule.testDispatcher) {
        importer.failure = ImportFailure.NoRecipeFound

        viewModel.importFromUrl("https://example.com/not-a-recipe")
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.importDraft)
        assertEquals(RecipeUiMessage.ImportNoRecipe, viewModel.uiState.value.error)
        assertEquals(false, viewModel.uiState.value.isImporting)
    }

    @Test
    fun `the imported source url is forwarded on save`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.createRecipe(
            RecipeVisibility.PRIVATE,
            "Tortilla",
            null,
            null,
            emptyList(),
            sourceUrl = "https://example.com/tortilla",
        )
        advanceUntilIdle()

        assertEquals("https://example.com/tortilla", repository.lastSourceUrl)
    }

    @Test
    fun `adding a recipe to shopping opens a review of its ingredients`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe()
        advanceUntilIdle()
        val recipe = recipe("r1", RecipeVisibility.PRIVATE, "Tortilla", listOf(Ingredient("Huevo", "4", "uds")))

        viewModel.addToShopping("home", recipe)
        advanceUntilIdle()

        assertEquals(listOf("Huevo"), viewModel.uiState.value.ingredientReview?.map { it.name })
    }

    @Test
    fun `confirming the review uses the chosen brand and store`() = runTest(mainDispatcherRule.testDispatcher) {
        ingredientRepository.catalogState.value = listOf(CatalogIngredient("huevo", "Huevo"))
        brandRepository.brandsState.value = listOf(
            IngredientBrand("b1", "huevo", "Camperos", favorite = true, stores = listOf(Supermarket.EROSKI)),
        )
        prefsRepository.state.value = mapOf("huevo" to IngredientPref(listOf(Supermarket.DIA)))
        viewModel.observe()
        advanceUntilIdle()
        viewModel.addToShopping(
            "home",
            recipe("r1", RecipeVisibility.PRIVATE, "Tortilla", listOf(Ingredient("Huevo", "4", "uds"))),
        )
        advanceUntilIdle()
        assertEquals("huevo", viewModel.uiState.value.ingredientReview?.single()?.context?.ingredient?.id)

        viewModel.confirmShoppingReview("home", listOf(IngredientReviewChoice("4", Supermarket.EROSKI, "Camperos")))
        advanceUntilIdle()

        assertEquals(listOf("Huevo"), shoppingRepository.addedNames)
        assertEquals(listOf(Supermarket.EROSKI), shoppingRepository.addedSupermarkets)
        assertEquals(listOf<String?>("Camperos"), shoppingRepository.addedBrands)
        assertEquals(RecipeUiMessage.AddedToShopping, viewModel.uiState.value.notice)
        assertEquals(null, viewModel.uiState.value.ingredientReview)
    }

    @Test
    fun `confirming with every row omitted writes nothing and says it was all in the list`() =
        runTest(mainDispatcherRule.testDispatcher) {
            shoppingRepository.itemsState.value = listOf(pendingItem("s1", "Huevos"))
            viewModel.observe()
            advanceUntilIdle()
            viewModel.addToShopping(
                "home",
                recipe("r1", RecipeVisibility.PRIVATE, "Tortilla", listOf(Ingredient("Huevo", "4", "uds"))),
            )
            advanceUntilIdle()

            viewModel.confirmShoppingReview("home", listOf(IngredientReviewChoice(null, null, null, skip = true)))
            advanceUntilIdle()

            assertTrue(shoppingRepository.addedNames.isEmpty())
            assertTrue(shoppingRepository.updatedIds.isEmpty())
            assertEquals(RecipeUiMessage.NothingToAddToShopping, viewModel.uiState.value.notice)
            assertEquals(null, viewModel.uiState.value.ingredientReview)
        }

    @Test
    fun `while the shopping list is being read a second request is ignored and isSaving is on`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.observe()
            advanceUntilIdle()
            val gate = CompletableDeferred<Unit>()
            shoppingRepository.currentItemsGate = gate
            val recipe = recipe("r1", RecipeVisibility.PRIVATE, "Tortilla", listOf(Ingredient("Huevo", "4", "uds")))

            viewModel.addToShopping("home", recipe)
            viewModel.addToShopping("home", recipe)
            runCurrent()

            assertEquals(1, shoppingRepository.currentItemsCalls)
            assertTrue(viewModel.uiState.value.isSaving)
            gate.complete(Unit)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isSaving)
            assertEquals(listOf("Huevo"), viewModel.uiState.value.ingredientReview?.map { it.name })
        }

    @Test
    fun `if the shopping list cannot be read isSaving goes off and the error is shown`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.observe()
            advanceUntilIdle()
            shoppingRepository.itemsFailure = ShoppingRepositoryException(ShoppingFailure.Network)

            viewModel.addToShopping(
                "home",
                recipe("r1", RecipeVisibility.PRIVATE, "Tortilla", listOf(Ingredient("Huevo", "4", "uds"))),
            )
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isSaving)
            assertEquals(null, viewModel.uiState.value.ingredientReview)
            assertEquals(RecipeUiMessage.NetworkError, viewModel.uiState.value.error)
        }

    @Test
    fun `adding a recipe without ingredients shows a message`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe()
        advanceUntilIdle()

        viewModel.addToShopping("home", recipe("r1", RecipeVisibility.PRIVATE, "Agua", null))
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.ingredientReview)
        assertEquals(RecipeUiMessage.NoIngredients, viewModel.uiState.value.error)
    }

    @Test
    fun `editor save is SAVED only after the repository answers`() = runTest(mainDispatcherRule.testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.addGate = gate

        viewModel.createRecipe(RecipeVisibility.PRIVATE, "Tortilla", null, null)
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.editorSave)
        assertTrue(viewModel.uiState.value.isSaving)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.editorSave)
        assertFalse(viewModel.uiState.value.isSaving)
        viewModel.consumeEditorSave()
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)
    }

    @Test
    fun `editor save failure or timeout leaves FAILED`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.nextFailure = RecipeRepositoryException(RecipeFailure.Network)
        viewModel.createRecipe(RecipeVisibility.PRIVATE, "Tortilla", null, null)
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
        assertEquals(RecipeUiMessage.NetworkError, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)

        repository.nextFailure = null
        viewModel.consumeEditorSave()
        repository.hang = true
        viewModel.updateRecipe("r1", "Tortilla", null, null)
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.editorSave)
        advanceTimeBy(EDITOR_SAVE_TIMEOUT_MILLIS + 1)
        runCurrent()

        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
        assertEquals(RecipeUiMessage.SaveTimeout, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `restoreRecipe does not touch the editor status`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.restoreRecipe(recipe("r1", RecipeVisibility.PRIVATE, "Tortilla"))
        advanceUntilIdle()

        assertEquals("Tortilla", repository.restored?.title)
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)
        assertEquals(RecipeUiMessage.RecipeSaved, viewModel.uiState.value.notice)
    }

    @Test
    fun `saving a new draft creates the recipe with clean steps, servings and minutes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val draft = RecipeDraft.of(null, null).copy(
                title = " Lentejas ",
                ingredients = listOf(IngredientRow("Lentejas", "300", "g"), IngredientRow(" ")),
                steps = listOf("  Remoja. ", "", "Cuece."),
                servings = " 4 ",
                totalMinutes = "60",
            )

            viewModel.saveDraft(null, draft)
            advanceUntilIdle()

            assertEquals(1, repository.createCount)
            assertEquals(RecipeVisibility.PRIVATE, repository.lastVisibility)
            assertEquals("Lentejas", repository.lastTitle)
            assertEquals(listOf(Ingredient("Lentejas", "300", "g")), repository.lastIngredients)
            assertEquals(listOf("Remoja.", "Cuece."), repository.lastSteps)
            assertEquals(4, repository.lastServings)
            assertEquals(60, repository.lastTotalMinutes)
            assertEquals(RecipeUiMessage.RecipeSaved, viewModel.uiState.value.notice)
        }

    @Test
    fun `an admin saving a global draft publishes it`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.admin = true
        viewModel.observe()
        advanceUntilIdle()

        viewModel.saveDraft(null, RecipeDraft.of(null, null).copy(title = "Lentejas", global = true))
        advanceUntilIdle()

        assertEquals(RecipeVisibility.GLOBAL, repository.lastVisibility)
    }

    @Test
    fun `saving the draft of an existing recipe updates it and empty numbers clear the values`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.saveDraft("r1", RecipeDraft.of(null, null).copy(title = "Lentejas", steps = listOf("Cuece.")))
            advanceUntilIdle()

            assertEquals(0, repository.createCount)
            assertEquals(1, repository.updateCount)
            assertEquals("r1", repository.lastUpdatedId)
            assertEquals(listOf("Cuece."), repository.lastSteps)
            assertEquals(null, repository.lastServings)
            assertEquals(null, repository.lastTotalMinutes)
        }

    @Test
    fun `a draft with invalid servings never reaches the repository`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.saveDraft(null, RecipeDraft.of(null, null).copy(title = "Lentejas", servings = "dos"))
        advanceUntilIdle()

        assertEquals(0, repository.createCount)
        assertEquals(RecipeUiMessage.ServingsInvalid, viewModel.uiState.value.error)
        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
    }

    @Test
    fun `my recipes keep only the private ones while the common ones arrive through global`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.mineState.value = listOf(
                recipe("lentejas", RecipeVisibility.GLOBAL, "Lentejas"),
                recipe("p1", RecipeVisibility.PRIVATE, "Mis lentejas"),
            )
            repository.globalState.value = listOf(recipe("lentejas", RecipeVisibility.GLOBAL, "Lentejas"))

            viewModel.observe()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf("p1"), (state.mine as UiState.Content).value.map(Recipe::id))
            assertEquals(listOf("lentejas"), (state.global as UiState.Content).value.map(Recipe::id))
        }

    @Test
    fun `fork copies steps, servings and minutes`() = runTest(mainDispatcherRule.testDispatcher) {
        val common = recipe("lentejas", RecipeVisibility.GLOBAL, "Lentejas")
            .copy(steps = listOf("Remoja.", "Cuece."), servings = 4, totalMinutes = 60)

        viewModel.fork(common)
        advanceUntilIdle()

        assertEquals("lentejas", repository.lastSourceRecipeId)
        assertEquals(listOf("Remoja.", "Cuece."), repository.lastSteps)
        assertEquals(4, repository.lastServings)
        assertEquals(60, repository.lastTotalMinutes)
    }

    @Test
    fun `undoing a delete recreates the same recipe under its own id`() = runTest(mainDispatcherRule.testDispatcher) {
        val deleted = recipe(
            "lentejas-estofadas",
            RecipeVisibility.PRIVATE,
            "Lentejas",
            listOf(Ingredient("Lentejas", "300", "g")),
        ).copy(
            steps = listOf("Remoja.", "Cuece."),
            servings = 4,
            totalMinutes = 60,
            sourceRecipeId = "lentejas-comunes",
            sourceUrl = "https://example.com/lentejas",
            createdByName = "Catálogo Bobitos",
        )

        viewModel.restoreRecipe(deleted)
        advanceUntilIdle()

        assertEquals(deleted, repository.restored)
        assertEquals(0, repository.createCount)
    }

    @Test
    fun `the undo is offered only once the delete is confirmed`() = runTest(mainDispatcherRule.testDispatcher) {
        val tortilla = recipe("r1", RecipeVisibility.PRIVATE, "Tortilla")
        val gate = CompletableDeferred<Unit>()
        repository.deleteGate = gate

        viewModel.deleteRecipe(tortilla)
        runCurrent()
        assertNull(viewModel.uiState.value.deletedRecipe)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("r1"), repository.deletedIds)
        assertEquals(tortilla, viewModel.uiState.value.deletedRecipe)

        viewModel.consumeDeletedRecipe()
        assertNull(viewModel.uiState.value.deletedRecipe)
    }

    @Test
    fun `a failed delete offers no undo and shows the error`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.nextFailure = RecipeRepositoryException(RecipeFailure.Network)

        viewModel.deleteRecipe(recipe("r1", RecipeVisibility.PRIVATE, "Tortilla"))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.deletedRecipe)
        assertEquals(RecipeUiMessage.NetworkError, viewModel.uiState.value.error)
    }

    @Test
    fun `undoing the delete of a common recreates it common, and without admin rights the rules refuse it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val common = recipe("torrijas", RecipeVisibility.GLOBAL, "Torrijas")

            viewModel.restoreRecipe(common)
            advanceUntilIdle()
            assertEquals(common, repository.restored)

            repository.nextFailure = RecipeRepositoryException(RecipeFailure.PermissionDenied)
            viewModel.restoreRecipe(common)
            advanceUntilIdle()
            assertEquals(RecipeUiMessage.PermissionDenied, viewModel.uiState.value.error)
        }

    @Test
    fun `a shopping list changed while reviewing is reported`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe()
        advanceUntilIdle()
        viewModel.addToShopping(
            "home",
            recipe("r1", RecipeVisibility.PRIVATE, "Tortilla", listOf(Ingredient("Huevo", "4", "uds"))),
        )
        advanceUntilIdle()
        shoppingRepository.applyFailure = ShoppingRepositoryException(ShoppingFailure.ItemChanged)
        val rows = viewModel.uiState.value.ingredientReview!!

        viewModel.confirmShoppingReview("home", rows.map { it.initialState().toChoice(it) })
        advanceUntilIdle()

        assertEquals(RecipeUiMessage.ShoppingChanged, viewModel.uiState.value.error)
    }

    @Test
    fun `the review reads the list from the server, not from a stale cached snapshot`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val huevo = pendingItem("s1", "Huevo")
            shoppingRepository.itemsState.value = listOf(huevo)
            shoppingRepository.serverItems = listOf(huevo.copy(purchased = true))
            viewModel.observe()
            advanceUntilIdle()

            viewModel.addToShopping(
                "home",
                recipe("r1", RecipeVisibility.PRIVATE, "Tortilla", listOf(Ingredient("Huevo", "4", "uds"))),
            )
            advanceUntilIdle()

            assertEquals(ReviewRowKind.PURCHASED, viewModel.uiState.value.ingredientReview!!.single().kind)
        }

    @Test
    fun `a shopping item deleted while reviewing is reported as a changed list`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.observe()
            advanceUntilIdle()
            viewModel.addToShopping(
                "home",
                recipe("r1", RecipeVisibility.PRIVATE, "Tortilla", listOf(Ingredient("Huevo", "4", "uds"))),
            )
            advanceUntilIdle()
            shoppingRepository.applyFailure = ShoppingRepositoryException(ShoppingFailure.ItemNotFound)
            val rows = viewModel.uiState.value.ingredientReview!!

            viewModel.confirmShoppingReview("home", rows.map { it.initialState().toChoice(it) })
            advanceUntilIdle()

            assertEquals(RecipeUiMessage.ShoppingChanged, viewModel.uiState.value.error)
        }

    @Test
    fun `if the shopping list cannot be read the review does not open and the error is shown`() =
        runTest(mainDispatcherRule.testDispatcher) {
            shoppingRepository.itemsFailure = ShoppingRepositoryException(ShoppingFailure.Network)
            viewModel.observe()
            advanceUntilIdle()

            viewModel.addToShopping(
                "home",
                recipe("r1", RecipeVisibility.PRIVATE, "Tortilla", listOf(Ingredient("Huevo", "4", "uds"))),
            )
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.ingredientReview)
            assertEquals(RecipeUiMessage.NetworkError, viewModel.uiState.value.error)
        }

    @Test
    fun `the review marks what is in the pantry, read from the server`() = runTest(mainDispatcherRule.testDispatcher) {
        pantryRepository.serverIds = setOf(pantryId("Arroz"))
        viewModel.observe()
        advanceUntilIdle()
        val recipe = recipe(
            "r1",
            RecipeVisibility.PRIVATE,
            "Arroz con huevo",
            listOf(Ingredient("Arroz", "300", "g"), Ingredient("Huevo", "2", "uds")),
        )

        viewModel.addToShopping("home", recipe)
        advanceUntilIdle()

        assertEquals(
            listOf(ReviewRowKind.IN_PANTRY, ReviewRowKind.NEW),
            viewModel.uiState.value.ingredientReview?.map { it.kind },
        )
    }

    @Test
    fun `if the pantry cannot be read the review opens without it`() = runTest(mainDispatcherRule.testDispatcher) {
        pantryRepository.itemsFailure = PantryRepositoryException(PantryFailure.Network)
        viewModel.observe()
        advanceUntilIdle()
        val recipe = recipe("r1", RecipeVisibility.PRIVATE, "Arroz", listOf(Ingredient("Arroz", "300", "g")))

        viewModel.addToShopping("home", recipe)
        advanceUntilIdle()

        assertEquals(listOf(ReviewRowKind.NEW), viewModel.uiState.value.ingredientReview?.map { it.kind })
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `the pantry of the active space feeds the pantry chip`() = runTest(mainDispatcherRule.testDispatcher) {
        pantryRepository.itemsState.value = listOf(PantryItem("arroc", "Arroz", null, null))

        viewModel.observePantry("home")
        advanceUntilIdle()

        assertEquals("home", pantryRepository.observedSpaceId)
        assertEquals(setOf("arroc"), viewModel.uiState.value.pantryIds)
    }

    @Test
    fun `without a space or a readable pantry there is no pantry chip`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observePantry("home")
        advanceUntilIdle()
        viewModel.observePantry(null)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.pantryIds)

        pantryRepository.itemsFailure = PantryRepositoryException(PantryFailure.PermissionDenied)
        viewModel.observePantry("home")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pantryIds)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `observing the same space again after stopping never drops the pantry ids`() =
        runTest(mainDispatcherRule.testDispatcher) {
            pantryRepository.itemsState.value = listOf(PantryItem(pantryId("Arroz"), "Arroz", null, null))
            val seen = mutableListOf<Set<String>?>()
            backgroundScope.launch(mainDispatcherRule.testDispatcher) {
                viewModel.uiState.collect { seen += it.pantryIds }
            }
            viewModel.observePantry("home")
            runCurrent()
            assertEquals(setOf(pantryId("Arroz")), viewModel.uiState.value.pantryIds)
            seen.clear()

            viewModel.stopObserving()
            viewModel.observePantry("home")
            runCurrent()

            // Con el dispatcher sin cola la instantánea llega al momento: lo que importa es que no pase por null.
            assertFalse(seen.contains(null))
            assertEquals(setOf(pantryId("Arroz")), viewModel.uiState.value.pantryIds)
        }

    @Test
    fun `observing another space switches the pantry chip off`() = runTest(mainDispatcherRule.testDispatcher) {
        pantryRepository.itemsState.value = listOf(PantryItem(pantryId("Arroz"), "Arroz", null, null))
        viewModel.observePantry("home")
        runCurrent()
        viewModel.setPantryOnly(true)

        // La despensa del otro espacio no se puede leer: sin chip, y el filtro no queda activo y oculto.
        pantryRepository.itemsFailure = PantryRepositoryException(PantryFailure.PermissionDenied)
        viewModel.observePantry("work")

        assertFalse(viewModel.uiState.value.pantryOnly)
        assertNull(viewModel.uiState.value.pantryIds)
    }

    @Test
    fun `the pantry filter requested right after observing the first space stays on`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.observePantry("home")
            viewModel.setPantryOnly(true)

            assertTrue(viewModel.uiState.value.pantryOnly)
        }

    @Test
    fun `the pantry chip switches on and off`() {
        viewModel.setPantryOnly(true)
        assertTrue(viewModel.uiState.value.pantryOnly)

        viewModel.setPantryOnly(false)
        assertFalse(viewModel.uiState.value.pantryOnly)
    }

    @Test
    fun `the review reads the list and the pantry at the same time`() = runTest(mainDispatcherRule.testDispatcher) {
        pantryRepository.serverIds = setOf(pantryId("Arroz"))
        viewModel.observe()
        advanceUntilIdle()
        shoppingRepository.currentItemsGate = CompletableDeferred()
        val recipe = recipe("r1", RecipeVisibility.PRIVATE, "Arroz", listOf(Ingredient("Arroz", "300", "g")))

        viewModel.addToShopping("home", recipe)
        runCurrent()

        assertEquals(1, shoppingRepository.currentItemsCalls)
        assertEquals(1, pantryRepository.currentIdsCalls)
        shoppingRepository.currentItemsGate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(ReviewRowKind.IN_PANTRY), viewModel.uiState.value.ingredientReview?.map { it.kind })
    }

    // Ya pasa hoy (la lista se lee primero); protege la versión en paralelo de esperar a la despensa.
    @Test
    fun `if the list cannot be read the review fails without waiting for the pantry`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.observe()
            advanceUntilIdle()
            shoppingRepository.itemsFailure = ShoppingRepositoryException(ShoppingFailure.Network)
            pantryRepository.currentIdsGate = CompletableDeferred()
            val recipe = recipe("r1", RecipeVisibility.PRIVATE, "Arroz", listOf(Ingredient("Arroz", "300", "g")))

            viewModel.addToShopping("home", recipe)
            runCurrent()

            assertEquals(RecipeUiMessage.NetworkError, viewModel.uiState.value.error)
            assertNull(viewModel.uiState.value.ingredientReview)
            assertFalse(viewModel.uiState.value.isSaving)
        }
}

private class FakeRecipeRepository : RecipeRepository {
    var addGate: CompletableDeferred<Unit>? = null
    var nextFailure: Throwable? = null

    // Nunca responde; como los repositorios reales, convierte la cancelación en otra excepción.
    var hang = false

    val globalState = MutableStateFlow<List<Recipe>>(emptyList())
    val mineState = MutableStateFlow<List<Recipe>>(emptyList())
    var createCount = 0
    var lastVisibility: RecipeVisibility? = null
    var lastTitle: String? = null
    var lastSourceRecipeId: String? = null
    var lastSourceUrl: String? = null
    var lastIngredients: List<Ingredient> = emptyList()
    var lastSteps: List<String> = emptyList()
    var lastServings: Int? = null
    var lastTotalMinutes: Int? = null
    var updateCount = 0
    var lastUpdatedId: String? = null
    var restored: Recipe? = null
    var admin = false
    var loadFailure: Throwable? = null

    override fun globalRecipes(): Flow<List<Recipe>> = loadFailure?.let { error -> flow { throw error } } ?: globalState
    override fun myRecipes(): Flow<List<Recipe>> = mineState
    override fun isCurrentUserRecipeAdmin(): Boolean = admin
    override suspend fun createRecipe(
        visibility: RecipeVisibility,
        title: String,
        description: String?,
        category: String?,
        sourceRecipeId: String?,
        ingredients: List<Ingredient>,
        sourceUrl: String?,
        steps: List<String>,
        servings: Int?,
        totalMinutes: Int?,
    ) {
        nextFailure?.let { throw it }
        if (hang) {
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                throw RecipeRepositoryException(RecipeFailure.Unknown)
            }
        }
        addGate?.await()
        createCount++
        lastVisibility = visibility
        lastTitle = title
        lastSourceRecipeId = sourceRecipeId
        lastSourceUrl = sourceUrl
        lastIngredients = ingredients
        lastSteps = steps
        lastServings = servings
        lastTotalMinutes = totalMinutes
    }

    override suspend fun updateRecipe(
        recipeId: String,
        title: String,
        description: String?,
        category: String?,
        ingredients: List<Ingredient>,
        steps: List<String>,
        servings: Int?,
        totalMinutes: Int?,
    ) {
        nextFailure?.let { throw it }
        if (hang) {
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                throw RecipeRepositoryException(RecipeFailure.Unknown)
            }
        }
        addGate?.await()
        updateCount++
        lastUpdatedId = recipeId
        lastTitle = title
        lastIngredients = ingredients
        lastSteps = steps
        lastServings = servings
        lastTotalMinutes = totalMinutes
    }

    var deleteGate: CompletableDeferred<Unit>? = null
    val deletedIds = mutableListOf<String>()

    override suspend fun deleteRecipe(recipeId: String) {
        deleteGate?.await()
        nextFailure?.let { throw it }
        deletedIds += recipeId
    }

    override suspend fun restoreRecipe(recipe: Recipe) {
        nextFailure?.let { throw it }
        restored = recipe
    }
}

private class FakeRecipeImporter : RecipeImporter {
    var result: ImportedRecipe? = null
    var failure: ImportFailure? = null

    override suspend fun import(url: String): ImportedRecipe {
        failure?.let { throw RecipeImportException(it) }
        return result ?: error("No import result configured")
    }
}

private fun recipe(id: String, visibility: RecipeVisibility, title: String, ingredients: List<Ingredient>? = null) =
    Recipe(
        id = id,
        ownerUid = "u",
        visibility = visibility,
        title = title,
        description = null,
        category = null,
        ingredients = ingredients,
        createdBy = "u",
        createdByName = "U",
        createdAt = Instant.EPOCH,
        updatedBy = "u",
        updatedAt = Instant.EPOCH,
    )

private fun pendingItem(id: String, name: String) = ShoppingItem(
    id = id,
    name = name,
    quantity = null,
    notes = null,
    purchased = false,
    createdBy = "owner",
    createdByName = "David",
    createdAt = Instant.EPOCH,
    updatedBy = "owner",
    updatedAt = Instant.EPOCH,
    purchasedBy = null,
    purchasedByName = null,
    purchasedAt = null,
)
