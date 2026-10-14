# Despensa en recetas y compra (plan 2) — Plan de implementación

> **Para agentes:** se ejecuta con `delegating-plan-execution`. Es un **plan grande** (5 tareas): pasos 1 a 6, **con `simplify`**. Cada tarea la hace un subagente `sonnet`, en serie y en orden. La revisión final la hace `opus` (si falla, `fable`). **Ninguna tarea toca `firestore.rules` ni `migrations/`.** Los pasos usan casillas (`- [ ]`).

**Objetivo:** la Despensa se usa donde se decide qué comprar y qué cocinar: la revisión de «Añadir a la compra» omite lo que hay, el alta a mano en la Compra lo avisa, cocinar pregunta «¿Se ha acabado algo?», y el Recetario y el selector de receta del planificador filtran «Con lo que hay».

**Arquitectura:**
- **Lecturas:** al abrir la revisión y al cocinar, la despensa se lee del servidor (`PantryRepository.currentIds`, como ya se hace con la lista de la compra). Donde hace falta en vivo se escucha (`PantryRepository.items`): el Recetario (chip), Comidas (chip del selector) y la Compra (solo con el editor abierto, como el catálogo).
- **Lógica pura** en ficheros propios, con tests JVM: `IngredientReview.kt` (fila «Hay en la despensa»), `CookedReview.kt` (diálogo «Has cocinado …») y `RecipePantry.kt` (lo que falta y su línea).
- **«A la compra» compartido:** `restockFromPantry` (lee la lista del servidor y escribe lo que haga falta) lo usan la Despensa y el diálogo de cocinar.
- **«¿Qué cocino?»:** la Despensa abre el Recetario y deja una marca en el `savedStateHandle` de su entrada, como ya hace la ficha de ingrediente con la Compra (`SHOPPING_PREFILL_KEY`).

**Stack:** Kotlin + Jetpack Compose (Material 3) + Hilt + Firestore; tests JVM (JUnit 4 + `kotlinx-coroutines-test`, dobles a mano).

**Spec:** `docs/superpowers/specs/2026-10-13-despensa-design.md`, secciones **3c** y **4**. Este plan es el **plan 2** de la sección 7. El plan 1 (`docs/superpowers/plans/2026-10-13-despensa.md`) ya está en `main` (19b1871).

## Qué entra

| Spec | Tarea |
|---|---|
| §4a Fila «Hay en la despensa» (`IN_PANTRY`) en la revisión de Recetario y Comidas, y el aviso «No hace falta añadir nada» | 1 |
| §4b Pista «Hay en la despensa» en el alta a mano de la Compra | 2 |
| §3c Diálogo «Has cocinado …» (tachar, «¿Se ha acabado algo?», «Apuntar a la compra…») y «A la compra» compartido | 3 |
| §4c `missingFromPantry`, su línea, chip «Con lo que hay» del Recetario y «¿Qué cocino?» en la Despensa | 4 |
| §4d Chip «Con lo que hay» del selector de receta del planificador | 5 |
| Documentación funcional (`FUNCTIONAL_SPEC.md`, `USER_FLOWS.md`) | 5 |
| Menor interno nombrado por el usuario: el test de «A la compra» sin catálogo a tiempo usa `runCurrent()` (`docs/superpowers/backlog.md`, «2026-10-14 — `agent/despensa`») | 3 |

## Qué no entra

- **Cualquier despliegue** (reglas, beta) y cualquier push: aplazados por el usuario. Este plan no cambia las reglas, pero las del plan 1 siguen sin desplegar (ver «Despliegue» al final).
- Los demás menores de la sección «2026-10-14 — `agent/despensa`» del backlog.
- Lo que la spec deja fuera de alcance (cantidades, básicos que cuentan como presentes, etc.).

## Rulings de planificación

1. **Fila `IN_PANTRY`.** Precedencia al construir las filas: pendiente en la lista → en la despensa → comprado → nueva. Una fila en la despensa sin artículo en la lista lleva el `context` de una nueva (marca y tienda por defecto ya preparadas). Por defecto, «Omitir». Con «Añadir» se comporta como una nueva (cantidad, marca y tienda) o, si hay un comprado, como «Volver a apuntar». Las opciones del selector son «Omitir / Añadir» o, con comprado, «Omitir / Volver a apuntar». La marca y la tienda solo salen con «Añadir», y una marca libre larga en una fila omitida no bloquea «Confirmar».
2. **La despensa al abrir la revisión** se lee del servidor con `currentIdsOrNull` después de leer la lista; si falla, la revisión sale sin filas `IN_PANTRY` (sin error). Igual en el Recetario y en Comidas.
3. **Pista del alta.** La Compra escucha la despensa **solo con el editor abierto**, dentro del mismo trabajo que el catálogo (`startIngredientAssist` / `stopIngredientAssist`). Solo al crear. Texto: «Ya en la lista · 2 kg · Hay en la despensa», o cada parte por separado. Si la despensa no se puede leer, no hay pista.
4. **Diálogo de cocinar.** `MealsUiState.cooked: CookedReview?` sustituye a `cookedCrossOff`. Las casillas viven en Compose (`remember`, como la revisión). «Listo» hace, en este orden: tachar lo marcado (con `pantryEntryFor`, así que entra en la despensa si es un ingrediente reconocido) → quitar de la despensa lo que se ha acabado → si la casilla está marcada, apuntarlo con `restockFromPantry`. El catálogo es el que ya escucha Comidas; si aún no ha llegado, se espera 5 s como máximo (`firstOrEmpty`). Un solo aviso al terminar: «Compra y despensa al día». Si no hay nada marcado, no escribe ni avisa. La casilla «Apuntar a la compra lo que se ha acabado» está deshabilitada mientras no haya nada marcado en «¿Se ha acabado algo?». Se borran los textos del diálogo antiguo y `MealUiMessage.IngredientsCrossedOff`.
5. **«A la compra» compartido.** `restockFromPantry(spaceId, names, shoppingRepository, data)` lee la lista del servidor, calcula con `pantryRestock` y escribe en una sola escritura. Un artículo borrado entretanto se convierte en `ItemChanged`, con `applySeenWrites`, que pasa a usar también `applyIngredientReview`. La Despensa (`PantryViewModel.restock`) pasa a usarlo, sin cambiar su comportamiento.
6. **Lo que falta.** Se compara por `pantryId` del nombre recortado, en el orden de la receta y sin repetir. Los nombres sin letras ni números (`pantryId` vacío) ni están ni faltan. «Receta con ingredientes» = alguno con `pantryId` no vacío. Orden con el chip: cuántos faltan, después título (en minúsculas) y después id. Línea: hasta tres nombres, todos («Faltan 3: huevos, leche y harina»); con más, los dos primeros y «y N más». Los nombres van con la inicial en minúscula, salvo las siglas («AOVE», dos mayúsculas al principio).
7. **Chip del Recetario.** `RecipesUiState.pantryOnly` (en el ViewModel, sobrevive a una rotación). `RecipesUiState.pantryIds` es `null` sin espacio, mientras se lee o si no se puede leer: entonces el chip no sale y no filtra. Se combina con el buscador, la categoría y las dos secciones. Si con el chip no queda ninguna receta en una sección, sale «recipes_no_results».
8. **«¿Qué cocino?»** navega al Recetario y pone `RECIPES_PANTRY_FILTER_KEY = true` en el `savedStateHandle` de su entrada. `RecipesScreen` gana `pantryFilterRequested` y `onPantryFilterConsumed`, activa el chip una vez y lo consume. Su firma está en `config/detekt/baseline.xml` (LongMethod): se actualiza **esa misma entrada** en el mismo commit; no se añade ninguna entrada nueva.
9. **Chip del selector del planificador.** Su estado vive en el diálogo (`rememberSaveable`). Comidas escucha la despensa mientras observa el espacio (`MealsUiState.pantryIds`; `null` si no se puede leer → sin chip).
10. **Tests de tiempo límite** (catálogo que no llega): `advanceTimeBy(4_999)` + `runCurrent()` → aún no ha escrito; `advanceTimeBy(2)` + `runCurrent()` → ya ha escrito. **Nunca `advanceUntilIdle()`** después de `advanceTimeBy` en estos tests: adelantaría el reloj hasta el final y el test pasaría con cualquier límite. La Tarea 3 corrige con este patrón el test existente de la Despensa.

## Restricciones globales

- **Rutas:**
  - `APP` = `app/src/main/java/com/dlunaunizar/bobitos/`
  - `UT` = `app/src/test/java/com/dlunaunizar/bobitos/`
  - `RES` = `app/src/main/res/values/strings.xml`
- **Comandos de verificación**, en llamadas separadas y sin `sh -c`:
  - **GRADLE:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug :app:ktlintCheck :app:detekt :app:testDebugUnitTest`
  - **JVM (una clase):** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest --tests "<clase completa>"`
  - **NODE:** `npm run test:scripts`
  - **EMU (reglas):** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && export PATH="$JAVA_HOME/bin:$PATH" && npm run test:emulators` (solo el coordinador, al final: este plan no cambia reglas).
  - Si ktlint marca formato: `./gradlew :app:ktlintFormat` (con el mismo `JAVA_HOME`) y revisar el diff antes del commit.
- **Prohibido:**
  - cualquier script contra `bobitos-dev` (`--project dev` o `bobitos-dev`);
  - desplegar reglas, importar al proyecto real, distribuir betas o hacer push;
  - descargar nada;
  - tocar `firestore.rules`, `migrations/` o `tests/firebase-emulators.test.mjs`.
- **Commits con ficheros con nombre:** nunca `git add -A` ni `git add .`.
- **Textos y comentarios en español**, con el estilo de los ficheros vecinos. Textos de la app en `RES`.
- **Tests Kotlin:** JVM puros (JUnit 4 + `kotlinx-coroutines-test`), dobles a mano (`FakePantryRepository`, `RecordingShoppingRepository`, los de `IngredientFakes.kt`); sin mockk ni Robolectric. **Orden TDD obligatorio:** test primero, verlo en rojo (un fallo de compilación por lo que aún no existe también vale; copiar el fallo en el informe), después el código. Las pantallas Compose no tienen test JVM: cada tarea dice qué parte se prueba y cómo.
- **detekt:**
  - Límites: `ReturnCount` máx. 4, `LongMethod` máx. 80 líneas, `TooManyFunctions` 25 (por clase y por fichero), `CyclomaticComplexMethod` 15. `MagicNumber` está desactivado.
  - Extraer código antes que tocar `config/detekt/baseline.xml`. La única edición permitida del baseline es la de la Tarea 4 (firma de `RecipesScreen`).
  - **No cambies estas firmas, que están en el baseline:** `ShoppingScreen(...)`, `MealsScreen(...)`, `BobitosApp(...)`, `BobitosNavHost(...)` y `runSpaceOperation(...)`. `RecipesScreen(...)` solo en la Tarea 4.
  - No añadas `catch (Throwable)` nuevos.
- **ktlint:** sin imports sin usar; línea ≤120.
- **Tests existentes:** solo se cambian los constructores de los ViewModels en los tests (parámetro nuevo `PantryRepository`) y los tests que la tarea nombra.

## Review Focus

1. **Ingredientes repetidos o en plural distinto en una receta** («Tomates» y «tomate», «Ajo» dos veces): una sola fila en «¿Se ha acabado algo?», un solo nombre y una sola cuenta en «Faltan N». → `CookedReviewTest` (Tarea 3) y `RecipePantryTest` (Tarea 4).
2. **La despensa no se puede leer** (sin conexión, permiso denegado): la revisión se abre sin filas «Hay en la despensa», el diálogo de cocinar sale sin «¿Se ha acabado algo?» (o no sale), y el Recetario, el selector y la Compra se quedan sin chip ni pista; nunca un error a pantalla completa. → `RecipesViewModelTest` y `MealsViewModelTest` (Tareas 1, 3, 4 y 5), `ShoppingViewModelTest` (Tarea 2).
3. **Algo pendiente en la lista que también está en la despensa:** en la revisión manda «Ya en la lista» y, al cocinar, sale solo en «Tachar de la compra». → `IngredientReviewTest` (Tarea 1) y `CookedReviewTest` (Tarea 3).
4. **Confirmar «Has cocinado …» con el catálogo lento o sin conexión:** no escribe nada antes de 5 s y después tacha sin entrada en la despensa; sin nada marcado, no escribe nada. → `MealsViewModelTest` (Tarea 3, con `runCurrent()`).
5. **Ingredientes sin letras ni números** («—», «¡!», en blanco): no cuentan como presentes ni como que faltan, no salen en el diálogo y una receta que solo tiene esos no sale con el chip. → `PantryItemTest` (Tarea 1), `CookedReviewTest` (Tarea 3) y `RecipePantryTest` (Tarea 4).

---

### Task 1: Fila «Hay en la despensa» en la revisión de «Añadir a la compra»

**Files:**
- Modify: `APP/core/model/PantryItem.kt`
- Modify: `APP/feature/common/IngredientReview.kt`
- Modify: `APP/feature/common/PantryShopping.kt`
- Modify: `APP/feature/recipes/RecipesViewModel.kt`
- Modify: `APP/feature/meals/MealsViewModel.kt`
- Modify: `RES`
- Test: `UT/core/model/PantryItemTest.kt`, `UT/feature/common/IngredientReviewTest.kt`, `UT/feature/recipes/RecipesViewModelTest.kt`, `UT/feature/meals/MealsViewModelTest.kt`
- Create (test): `UT/feature/common/IngredientReviewStringsTest.kt`

**Interfaces:**
- Consumes (del plan 1): `pantryId(name: String): String` (`core/model/PantryItem.kt`), `PantryRepository.currentIds(spaceId): Set<String>`, `FakePantryRepository` (`UT/feature/common/`, con `serverIds` e `itemsFailure`).
- Produces:
  - `fun isInPantry(name: String, pantryIds: Set<String>): Boolean` (`core/model/PantryItem.kt`).
  - `ReviewRowKind.IN_PANTRY`; `IngredientReviewRow.inPantry: Boolean = false`; `internal fun IngredientReviewRow.includeLabel(): Int`.
  - `fun buildIngredientReviewRows(ingredients, currentItems, data = IngredientShoppingData(), pantryIds: Set<String> = emptySet())`.
  - `internal suspend fun PantryRepository.currentIdsOrNull(spaceId: String): Set<String>?` (`feature/common/PantryShopping.kt`).
  - Constructores: `RecipesViewModel(repository, importer, shoppingRepository, ingredientShoppingSource, pantryRepository: PantryRepository)` y `MealsViewModel(repository, spaces, recipeRepository, shoppingRepository, ingredientShoppingSource, pantryRepository: PantryRepository)` (el parámetro nuevo, al final).

- [ ] **Step 1: Tests de `isInPantry`.** Añade a `UT/core/model/PantryItemTest.kt` (con los imports `assertTrue` y `assertFalse`):

```kotlin
    @Test
    fun `isInPantry matches by pantryId and never matches a name without letters or numbers`() {
        val ids = setOf("tomat", "aceit-oliva", "")

        assertTrue(isInPantry("Tomates", ids))
        assertTrue(isInPantry("  aceite de oliva ", ids))
        assertFalse(isInPantry("Leche", ids))
        assertFalse(isInPantry("¡!", ids))
        assertFalse(isInPantry("   ", ids))
    }
```

- [ ] **Step 2: Tests de la revisión.** Añade a `UT/feature/common/IngredientReviewTest.kt` (import `com.dlunaunizar.bobitos.core.model.pantryId`):

```kotlin
    @Test
    fun `a row in the pantry is omitted by default and added like a new one when chosen`() {
        val row = buildIngredientReviewRows(
            listOf(Ingredient("Arroz", "300", "g")),
            emptyList(),
            data,
            setOf(pantryId("Arroz")),
        ).single()
        val state = row.initialState()

        assertEquals(ReviewRowKind.IN_PANTRY, row.kind)
        assertNotNull(row.context)
        assertFalse(state.include)
        assertTrue(listOf(state.toChoice(row)).writesNothing())
        val added = state.withInclude(true).toChoice(row)
        assertEquals(IngredientReviewChoice("300 g", Supermarket.DIA, "Brillante"), added)
        assertEquals(
            listOf(ShoppingWrite.Add("Arroz", "300 g", null, Supermarket.DIA, "Brillante")),
            ingredientReviewWrites(listOf(row), listOf(added)),
        )
    }

    @Test
    fun `pending wins over the pantry and the pantry over purchased`() {
        val rows = buildIngredientReviewRows(
            listOf(Ingredient("Sal"), Ingredient("Leche", "1", "l"), Ingredient("Arroz")),
            listOf(item("s1", "sal"), item("s2", "leche", purchased = true)),
            data,
            setOf(pantryId("Sal"), pantryId("Leche")),
        )

        assertEquals(listOf(ReviewRowKind.PENDING, ReviewRowKind.IN_PANTRY, ReviewRowKind.NEW), rows.map { it.kind })
        val milk = rows[1]
        assertEquals(R.string.meals_review_relist, milk.includeLabel())
        assertEquals(
            listOf(ShoppingWrite.Change(milk.existing!!, "1 l", null, null, null, relist = true)),
            ingredientReviewWrites(listOf(milk), listOf(milk.initialState().withInclude(true).toChoice(milk))),
        )
    }

    @Test
    fun `pantry rows hint the pantry and offer Añadir`() {
        fun rowFor(ingredient: Ingredient) =
            buildIngredientReviewRows(listOf(ingredient), emptyList(), data, setOf(pantryId("Sal"))).single()

        assertEquals(
            ReviewHint(R.string.meals_review_in_pantry_recipe, listOf("1 kg")),
            rowFor(Ingredient("Sal", "1", "kg")).hint(),
        )
        assertEquals(ReviewHint(R.string.meals_review_in_pantry), rowFor(Ingredient("Sal")).hint())
        assertEquals(R.string.meals_review_add, rowFor(Ingredient("Sal")).includeLabel())
        assertEquals(
            R.string.meals_review_sum,
            buildIngredientReviewRows(listOf(Ingredient("Sal")), listOf(item("s1", "Sal")), data).single()
                .includeLabel(),
        )
    }

    @Test
    fun `an omitted pantry row with a long free brand does not block confirming`() {
        val row = IngredientReviewRow(
            name = "Arroz",
            recipeQuantity = "300 g",
            existing = null,
            context = IngredientShoppingContext(arroz, emptyList(), emptyList()),
            inPantry = true,
        )
        val omitted = row.initialState().withFreeBrand("x".repeat(61))

        assertFalse(omitted.brandTooLong(row))
        assertTrue(canConfirmReview(listOf(row), listOf(omitted)))
        assertTrue(omitted.withInclude(true).brandTooLong(row))
    }
```

- [ ] **Step 3: Test del aviso nuevo.** Crea `UT/feature/common/IngredientReviewStringsTest.kt`:

```kotlin
package com.dlunaunizar.bobitos.feature.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class IngredientReviewStringsTest {
    private val xml = File("src/main/res/values/strings.xml").readText()

    private fun text(name: String) = Regex("""<string name="$name">(.*?)</string>""").find(xml)!!.groupValues[1]

    @Test
    fun `with every row omitted the notice says nothing needs adding`() {
        assertEquals("No hace falta añadir nada", text("meals_notice_nothing_to_add"))
        assertEquals("No hace falta añadir nada", text("recipes_notice_nothing_to_add"))
    }
}
```

- [ ] **Step 4: Tests de los ViewModels.** En `UT/feature/recipes/RecipesViewModelTest.kt`, añade `private val pantryRepository = FakePantryRepository()` y pásalo como último argumento de `RecipesViewModel(...)`. Añade (imports `pantryId`, `PantryFailure`, `PantryRepositoryException`, `FakePantryRepository`):

```kotlin
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
```

En `UT/feature/meals/MealsViewModelTest.kt`, añade `private val pantryRepository = FakePantryRepository()` y pásalo como último argumento de `MealsViewModel(...)`. Añade (imports `pantryId`, `PantryFailure`, `PantryRepositoryException`, `FakePantryRepository`, `org.junit.Assert.assertNull`):

```kotlin
    @Test
    fun `the meal review marks what is in the pantry, read from the server`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value =
                listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"), Ingredient("Tomate", "2", "ud"))))
            pantryRepository.serverIds = setOf(pantryId("Arroz"))
            viewModel.observe("home")
            advanceUntilIdle()

            val paella = meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1")
            viewModel.addIngredientsToShopping(paella)
            advanceUntilIdle()

            assertEquals(
                listOf(ReviewRowKind.IN_PANTRY, ReviewRowKind.NEW),
                viewModel.uiState.value.ingredientReview?.map { it.kind },
            )
        }

    @Test
    fun `if the pantry cannot be read the meal review opens without it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            pantryRepository.itemsFailure = PantryRepositoryException(PantryFailure.Network)
            viewModel.observe("home")
            advanceUntilIdle()

            viewModel.addIngredientsToShopping(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
            advanceUntilIdle()

            assertEquals(listOf(ReviewRowKind.NEW), viewModel.uiState.value.ingredientReview?.map { it.kind })
            assertNull(viewModel.uiState.value.error)
        }
```

- [ ] **Step 5: Verlo en rojo.** JVM de `com.dlunaunizar.bobitos.core.model.PantryItemTest`, `com.dlunaunizar.bobitos.feature.common.IngredientReviewTest`, `com.dlunaunizar.bobitos.feature.common.IngredientReviewStringsTest`, `com.dlunaunizar.bobitos.feature.recipes.RecipesViewModelTest` y `com.dlunaunizar.bobitos.feature.meals.MealsViewModelTest`. Esperado: fallo de compilación (`isInPantry`, `IN_PANTRY`, `inPantry`, `includeLabel`, los recursos nuevos y los constructores no existen). Copia el fallo en el informe.

- [ ] **Step 6: `isInPantry`.** Al final de `APP/core/model/PantryItem.kt`:

```kotlin
/** Si [name] está en la despensa ([pantryIds], ids de [pantryId]); un nombre sin letras ni números nunca lo está. */
fun isInPantry(name: String, pantryIds: Set<String>): Boolean = pantryId(name).let { it.isNotEmpty() && it in pantryIds }
```

- [ ] **Step 7: La fila `IN_PANTRY`** en `APP/feature/common/IngredientReview.kt`:
  1. En `ReviewRowKind`, entre `PENDING` y `PURCHASED`:

```kotlin
    /**
     * No está pendiente y hay en la despensa: «Omitir» (por defecto) o «Añadir», como una nueva o, si hay un comprado,
     * volviéndolo a apuntar.
     */
    IN_PANTRY,
```

  2. En `IngredientReviewRow`, el campo nuevo (después de `context`) y la precedencia:

```kotlin
    /** Hay en la despensa del espacio. */
    val inPantry: Boolean = false,
) {
    // Precedencia: pendiente en la lista → en la despensa → comprado → nueva.
    val kind: ReviewRowKind
        get() = when {
            existing != null && !existing.purchased -> ReviewRowKind.PENDING
            inPantry -> ReviewRowKind.IN_PANTRY
            existing != null -> ReviewRowKind.PURCHASED
            else -> ReviewRowKind.NEW
        }
}
```

  3. En `initialState()`, la rama nueva (la marca y la tienda por defecto quedan preparadas por si se elige «Añadir»):

```kotlin
    ReviewRowKind.IN_PANTRY -> ReviewRowState(
        quantity = recipeQuantity.orEmpty(),
        selection = context?.defaultSelection() ?: BrandStoreSelection.None,
        freeBrand = "",
        include = false,
    )
```

  y actualiza el comentario de encima: «En la despensa: «Omitir» marcado, con la cantidad de la receta y la marca y la tienda por defecto».

  4. `brandTooLong` solo cuenta si la fila se escribe:

```kotlin
/** La marca libre de una fila que se va a añadir supera el máximo: error en el campo y Confirmar deshabilitado. */
internal fun ReviewRowState.brandTooLong(row: IngredientReviewRow): Boolean =
    include && row.context?.brands?.isEmpty() == true && freeBrandTooLong(freeBrand)
```

  5. `toChoice`: `skip = !include` en las tres ramas (en una nueva, `include` siempre es `true`):

```kotlin
    return when {
        existing != null -> IngredientReviewChoice(trimmed, existing.supermarket, existing.brand, skip = !include)
        context == null -> IngredientReviewChoice(trimmed, null, null, skip = !include)
        else -> IngredientReviewChoice(
            quantity = trimmed,
            supermarket = selection.store,
            brand = context.brandToSave(selection, freeBrand),
            skip = !include,
        )
    }
```

  6. `buildIngredientReviewRows` gana `pantryIds` (último parámetro, por defecto vacío) y rellena `inPantry = isInPantry(first.name, pantryIds)`. `context` sigue igual (`if (existing == null) data.contextFor(first.name) else null`). Añade al KDoc: «y con la despensa ([pantryIds], leída del servidor): pendiente → en la despensa → comprado → nueva».
  7. La etiqueta de la opción que no omite, junto a `hint()`:

```kotlin
/** La opción que no omite: «Sumar» en un pendiente; «Volver a apuntar» si hay un comprado; si no, «Añadir». */
@StringRes
internal fun IngredientReviewRow.includeLabel(): Int = when {
    kind == ReviewRowKind.PENDING -> R.string.meals_review_sum
    existing != null -> R.string.meals_review_relist
    else -> R.string.meals_review_add
}
```

  8. En `hint()`, la rama nueva:

```kotlin
    ReviewRowKind.IN_PANTRY -> recipeQuantity?.let { ReviewHint(R.string.meals_review_in_pantry_recipe, listOf(it)) }
        ?: ReviewHint(R.string.meals_review_in_pantry)
```

  9. En `ReviewRowContent`: `SkipOrIncludePicker(..., includeLabel = row.includeLabel(), ...)` (sustituye el `if` de la etiqueta) y el `BrandStorePicker` solo con `if (context != null && state.include)`.

- [ ] **Step 8: Textos.** En `RES`, junto a los `meals_review_*`:

```xml
    <string name="meals_review_add">Añadir</string>
    <string name="meals_review_in_pantry">Hay en la despensa</string>
    <string name="meals_review_in_pantry_recipe">Hay en la despensa · receta: %1$s</string>
```

y cambia el texto de `meals_notice_nothing_to_add` y de `recipes_notice_nothing_to_add` a `No hace falta añadir nada`.

- [ ] **Step 9: Lectura de la despensa.** En `APP/feature/common/PantryShopping.kt` (imports `kotlinx.coroutines.CancellationException` y `PantryRepository`):

```kotlin
/** Los ids de la despensa de [spaceId] leídos del servidor, o `null` si no se puede leer (se sigue sin ella). */
internal suspend fun PantryRepository.currentIdsOrNull(spaceId: String): Set<String>? =
    runCatching { currentIds(spaceId) }
        .onFailure { if (it is CancellationException) throw it }
        .getOrNull()
```

- [ ] **Step 10: ViewModels.**
  - `RecipesViewModel`: `private val pantryRepository: PantryRepository` como último parámetro del constructor. En `addToShopping`, después de leer `current`:

```kotlin
                // La despensa del servidor; si no se puede leer, la revisión sale sin ella.
                val pantryIds = pantryRepository.currentIdsOrNull(spaceId).orEmpty()
                val rows = buildIngredientReviewRows(ingredients, current, shoppingData, pantryIds)
```

  - `MealsViewModel`: `private val pantryRepository: PantryRepository` como último parámetro del constructor y el mismo cambio en `prepareIngredientReview`.

- [ ] **Step 11: Verlo en verde.** Las cinco clases del Step 5 y después GRADLE.

- [ ] **Step 12: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/core/model/PantryItem.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/common/IngredientReview.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/common/PantryShopping.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipesViewModel.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModel.kt \
  app/src/main/res/values/strings.xml \
  app/src/test/java/com/dlunaunizar/bobitos/core/model/PantryItemTest.kt \
  app/src/test/java/com/dlunaunizar/bobitos/feature/common/IngredientReviewTest.kt \
  app/src/test/java/com/dlunaunizar/bobitos/feature/common/IngredientReviewStringsTest.kt \
  app/src/test/java/com/dlunaunizar/bobitos/feature/recipes/RecipesViewModelTest.kt \
  app/src/test/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModelTest.kt
git commit -m "feat(despensa): la revisión de «Añadir a la compra» omite por defecto lo que hay en la despensa"
```

---

### Task 2: Pista «Hay en la despensa» en el alta a mano de la Compra

**Files:**
- Modify: `APP/feature/shopping/ShoppingViewModel.kt`
- Modify: `APP/feature/shopping/ShoppingUiState.kt`
- Modify: `APP/feature/shopping/ShoppingScreen.kt`
- Modify: `RES`
- Test: `UT/feature/shopping/ShoppingViewModelTest.kt`, `UT/feature/shopping/ShoppingViewModelPantryTest.kt` (solo el constructor)

**Interfaces:**
- Consumes: `isInPantry(name, pantryIds)` (Tarea 1); `PantryRepository.items(spaceId): Flow<List<PantryItem>>`; `FakePantryRepository` (`itemsState`, `itemsFailure`, `observedSpaceId`).
- Produces: `ShoppingUiState.pantryIds: Set<String> = emptySet()`; constructor `ShoppingViewModel(repository, ingredientRepository, personalIngredients, ingredientSource, pantryRepository: PantryRepository)`.

- [ ] **Step 1: Tests.** En `UT/feature/shopping/ShoppingViewModelTest.kt`, añade `private val pantryRepository = FakePantryRepository()` y pásalo como último argumento de `ShoppingViewModel(...)`; en `ShoppingViewModelPantryTest.kt`, pasa `FakePantryRepository()` como último argumento. Añade a `ShoppingViewModelTest` (imports `PantryItem`, `PantryFailure`, `PantryRepositoryException`, `FakePantryRepository`):

```kotlin
    @Test
    fun `the editor assist listens to the pantry of the space only while it is open`() =
        runTest(mainDispatcherRule.testDispatcher) {
            pantryRepository.itemsState.value = listOf(PantryItem("tomat", "Tomate", null, null))
            viewModel.observe("home")
            advanceUntilIdle()
            assertEquals(emptySet<String>(), viewModel.uiState.value.pantryIds)
            assertNull(pantryRepository.observedSpaceId)

            viewModel.startIngredientAssist()
            advanceUntilIdle()
            assertEquals("home", pantryRepository.observedSpaceId)
            assertEquals(setOf("tomat"), viewModel.uiState.value.pantryIds)

            viewModel.stopIngredientAssist()
            pantryRepository.itemsState.value = emptyList()
            advanceUntilIdle()
            assertEquals(setOf("tomat"), viewModel.uiState.value.pantryIds)
        }

    @Test
    fun `a pantry that cannot be read gives no hint and no error`() = runTest(mainDispatcherRule.testDispatcher) {
        pantryRepository.itemsFailure = PantryRepositoryException(PantryFailure.PermissionDenied)
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.startIngredientAssist()
        advanceUntilIdle()

        assertEquals(emptySet<String>(), viewModel.uiState.value.pantryIds)
        assertNull(viewModel.uiState.value.error)
    }
```

- [ ] **Step 2: Verlo en rojo.** JVM de `com.dlunaunizar.bobitos.feature.shopping.ShoppingViewModelTest` y `com.dlunaunizar.bobitos.feature.shopping.ShoppingViewModelPantryTest`. Esperado: fallo de compilación (constructor y `pantryIds`).

- [ ] **Step 3: Estado.** En `ShoppingUiState`, después de `ingredientData`:

```kotlin
    // Ids de la despensa del espacio, escuchados mientras el editor está abierto: la pista «Hay en la despensa» del alta.
    val pantryIds: Set<String> = emptySet(),
```

- [ ] **Step 4: ViewModel.** `private val pantryRepository: PantryRepository` como último parámetro del constructor. `startIngredientAssist` escucha también la despensa, como hijo del mismo trabajo (así `stopIngredientAssist` lo suelta sin funciones nuevas; la clase está cerca del límite de 25):

```kotlin
    /** Ingredientes, marcas, preferencias y la despensa: solo mientras el editor de ítem está abierto. */
    fun startIngredientAssist() {
        if (catalogJob?.isActive == true) return
        // Sin datos viejos de un ViewModel restaurado: los valores por defecto parten de lo recién leído.
        mutableUiState.update { it.copy(ingredientData = IngredientShoppingData(), pantryIds = emptySet()) }
        val spaceId = observedSpaceId
        catalogJob = viewModelScope.launch {
            // La despensa, para la pista del alta; si no se puede leer, no hay pista.
            if (spaceId != null) {
                launch {
                    pantryRepository.items(spaceId)
                        .catch { emit(emptyList()) }
                        .collect { items ->
                            mutableUiState.update { it.copy(pantryIds = items.mapTo(HashSet(), PantryItem::id)) }
                        }
                }
            }
            ingredientSource.data().collect { data -> mutableUiState.update { it.copy(ingredientData = data) } }
        }
    }
```

y el KDoc de `stopIngredientAssist`: «Suelta los listeners del catálogo y de la despensa al cerrar el editor (conserva lo ya cargado)».

- [ ] **Step 5: Pantalla.** En `APP/feature/shopping/ShoppingScreen.kt` (sin cambiar la firma de `ShoppingScreen`):
  1. `ShoppingItemEditor` gana el parámetro `pantryIds: Set<String>` (después de `resolveDuplicate`); la llamada pasa `pantryIds = state.pantryIds`.
  2. Dentro de `ShoppingItemEditor`, junto a `duplicate`:

```kotlin
    // Solo al crear: avisa si ya hay un producto con ese nombre en la lista y si hay en la despensa (no bloquea nada).
    val duplicate = if (item == null) resolveDuplicate(name) else null
    val inPantry = item == null && isInPantry(name, pantryIds)
```

  3. El `supportingText` del nombre pasa a `supportingText = { NameHint(nameErrorMessage, duplicate, inPantry) }`, con este composable nuevo (así `ShoppingItemEditor`, que está cerca de las 80 líneas, no crece):

```kotlin
// Bajo el nombre: el error; si no hay, «Ya en la lista» (con su cantidad) y «Hay en la despensa».
@Composable
private fun NameHint(error: ShoppingUiMessage?, duplicate: ShoppingItem?, inPantry: Boolean) {
    if (error != null) {
        Text(stringResource(error.stringResourceId))
        return
    }
    val parts = listOfNotNull(
        duplicate?.let { listed ->
            stringResource(R.string.shopping_already_in_list) + (listed.quantity?.let { " · $it" } ?: "")
        },
        if (inPantry) stringResource(R.string.shopping_in_pantry) else null,
    )
    if (parts.isNotEmpty()) Text(parts.joinToString(" · "))
}
```

- [ ] **Step 6: Texto.** En `RES`, junto a `shopping_already_in_list`: `<string name="shopping_in_pantry">Hay en la despensa</string>`.

- [ ] **Step 7: Verlo en verde.** Las dos clases del Step 2 y GRADLE. Si detekt marca `LongMethod` en `ShoppingItemEditor`, extrae más (por ejemplo, el bloque de marca y tienda a un composable privado); no toques el baseline.

- [ ] **Step 8: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingViewModel.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingUiState.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingScreen.kt \
  app/src/main/res/values/strings.xml \
  app/src/test/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingViewModelTest.kt \
  app/src/test/java/com/dlunaunizar/bobitos/feature/shopping/ShoppingViewModelPantryTest.kt
git commit -m "feat(despensa): el alta a mano en la Compra avisa de lo que hay en la despensa"
```

---

### Task 3: Diálogo «Has cocinado …» y «A la compra» compartido

**Files:**
- Create: `APP/feature/meals/CookedReview.kt`
- Create: `APP/feature/meals/CookedDialog.kt`
- Modify: `APP/feature/common/IngredientReview.kt` (`applySeenWrites`)
- Modify: `APP/feature/common/PantryShopping.kt` (`restockFromPantry`)
- Modify: `APP/feature/pantry/PantryViewModel.kt`
- Modify: `APP/feature/meals/MealsViewModel.kt`, `APP/feature/meals/MealsUiState.kt`, `APP/feature/meals/MealUiMessage.kt`, `APP/feature/meals/MealsScreen.kt`
- Modify: `RES`
- Create (test): `UT/feature/meals/CookedReviewTest.kt`
- Test: `UT/feature/common/PantryShoppingTest.kt`, `UT/feature/meals/MealsViewModelTest.kt`, `UT/feature/pantry/PantryViewModelTest.kt`

**Interfaces:**
- Consumes: `isInPantry`, `currentIdsOrNull` y el constructor de `MealsViewModel` con `pantryRepository` (Tarea 1); `pantryId`, `shoppingMatchKey`, `pantryEntryFor(name, data): PantryEntry?`, `pantryRestock(names, items, data): PantryRestock`, `IngredientShoppingSource.firstOrEmpty()` y `PantryRepository.remove(spaceId, ids)` (plan 1).
- Produces:
  - `data class CookedPantryRow(val id: String, val name: String)`, `data class CookedReview(val mealName: String, val crossOff: List<ShoppingItem>, val finished: List<CookedPantryRow>)`, `data class CookedChoice(val crossOffIds: Set<String>, val finishedIds: Set<String>, val restock: Boolean)`.
  - `internal fun cookedReview(mealName, ingredients, items: List<ShoppingItem>?, pantryIds: Set<String>?): CookedReview?`, `internal fun CookedReview.defaultChoice()`, `internal fun CookedChoice.toggleCrossOff(id)`, `internal fun CookedChoice.toggleFinished(id)`, `internal fun CookedReview.writesFor(choice): CookedWrites`.
  - `internal suspend fun ShoppingRepository.applySeenWrites(spaceId, writes)`; `internal suspend fun restockFromPantry(spaceId, names, shoppingRepository, data): PantryRestock`.
  - `MealsUiState.cooked: CookedReview? = null` (sustituye a `cookedCrossOff`); `MealsViewModel.confirmCooked(choice: CookedChoice)` y `MealsViewModel.dismissCooked()` (sustituyen a `crossOffCookedIngredients` y `dismissCookedCrossOff`); `MealUiMessage.CookedReviewApplied` (sustituye a `IngredientsCrossedOff`).

- [ ] **Step 1: Tests de la lógica pura.** Crea `UT/feature/meals/CookedReviewTest.kt`:

```kotlin
package com.dlunaunizar.bobitos.feature.meals

import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.pantryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CookedReviewTest {
    @Test
    fun `pending items of the recipe go to cross off and what is in the pantry to finished`() {
        val review = cookedReview(
            "Lentejas",
            listOf(Ingredient("Lentejas", "300", "g"), Ingredient("Chorizo"), Ingredient("Sal")),
            listOf(item("s1", "lentejas"), item("s2", "Leche"), item("s3", "Chorizo", purchased = true)),
            setOf(pantryId("Sal"), pantryId("Chorizo"), pantryId("Leche")),
        )!!

        assertEquals("Lentejas", review.mealName)
        assertEquals(listOf("s1"), review.crossOff.map(ShoppingItem::id))
        assertEquals(
            listOf(CookedPantryRow(pantryId("Chorizo"), "Chorizo"), CookedPantryRow(pantryId("Sal"), "Sal")),
            review.finished,
        )
    }

    @Test
    fun `an ingredient pending in the list only shows in cross off`() {
        val review = cookedReview(
            "Ensalada",
            listOf(Ingredient("Tomates")),
            listOf(item("s1", "tomate")),
            setOf(pantryId("Tomate")),
        )!!

        assertEquals(listOf("s1"), review.crossOff.map(ShoppingItem::id))
        assertTrue(review.finished.isEmpty())
    }

    @Test
    fun `repeated ingredients show once and names without letters or numbers never`() {
        val review = cookedReview(
            "Sopa de ajo",
            listOf(
                Ingredient("Ajo"),
                Ingredient("ajos"),
                Ingredient("Tomates"),
                Ingredient("tomate"),
                Ingredient("—"),
                Ingredient("  "),
            ),
            emptyList(),
            setOf(pantryId("Ajo"), pantryId("Tomate"), ""),
        )!!

        assertEquals(listOf("Ajo", "Tomates"), review.finished.map(CookedPantryRow::name))
    }

    @Test
    fun `without the list or the pantry their section does not show, and with nothing to offer there is no dialog`() {
        val ingredients = listOf(Ingredient("Arroz"), Ingredient("Sal"))

        val noPantry = cookedReview("Paella", ingredients, listOf(item("s1", "Arroz")), null)!!
        assertEquals(listOf("s1"), noPantry.crossOff.map(ShoppingItem::id))
        assertTrue(noPantry.finished.isEmpty())

        val noList = cookedReview("Paella", ingredients, null, setOf(pantryId("Arroz")))!!
        assertTrue(noList.crossOff.isEmpty())
        assertEquals(listOf("Arroz"), noList.finished.map(CookedPantryRow::name))

        assertNull(cookedReview("Paella", ingredients, null, null))
        assertNull(cookedReview("Paella", ingredients, emptyList(), emptySet()))
    }

    @Test
    fun `by default everything pending is crossed off, nothing is finished and restock is on`() {
        val review = CookedReview(
            "Paella",
            listOf(item("s1", "Arroz"), item("s2", "Gambas")),
            listOf(CookedPantryRow("sal", "Sal")),
        )

        assertEquals(CookedChoice(setOf("s1", "s2"), emptySet(), restock = true), review.defaultChoice())
    }

    @Test
    fun `the writes follow the choice and restock only names what is finished`() {
        val review = CookedReview(
            "Paella",
            listOf(item("s1", "Arroz"), item("s2", "Gambas")),
            listOf(CookedPantryRow("sal", "Sal"), CookedPantryRow("aceit-oliva", "Aceite de oliva")),
        )
        val choice = review.defaultChoice().toggleCrossOff("s2").toggleFinished("sal")

        val writes = review.writesFor(choice)

        assertEquals(listOf("s1"), writes.crossOff.map(ShoppingItem::id))
        assertEquals(listOf("sal"), writes.finishedIds)
        assertEquals(listOf("Sal"), writes.restockNames)
        assertEquals(emptyList<String>(), review.writesFor(choice.copy(restock = false)).restockNames)
        assertTrue(review.writesFor(CookedChoice(emptySet(), emptySet(), restock = true)).isEmpty)
    }

    private fun item(id: String, name: String, purchased: Boolean = false) = ShoppingItem(
        id = id,
        name = name,
        quantity = null,
        notes = null,
        purchased = purchased,
        createdBy = "u",
        createdByName = "U",
        createdAt = Instant.EPOCH,
        updatedBy = "u",
        updatedAt = Instant.EPOCH,
        purchasedBy = null,
        purchasedByName = null,
        purchasedAt = null,
    )
}
```

- [ ] **Step 2: Tests de `restockFromPantry`.** Añade a `UT/feature/common/PantryShoppingTest.kt` (imports `kotlinx.coroutines.test.runTest`, `ShoppingFailure`, `ShoppingRepositoryException`):

```kotlin
    @Test
    fun `restock from the pantry reads the server list and writes once`() = runTest {
        val shopping = RecordingShoppingRepository()
        shopping.itemsState.value = listOf(shoppingItem("s1", "Tomate"))
        shopping.serverItems = listOf(shoppingItem("s1", "Tomate", purchased = true))

        val plan = restockFromPantry("home", listOf("tomates"), shopping, data)

        assertEquals(listOf("s1"), shopping.relistedIds)
        assertEquals(1, shopping.appliedBatches.size)
        assertTrue(plan.alreadyListed.isEmpty())
    }

    @Test
    fun `restock from the pantry of something already pending writes nothing`() = runTest {
        val shopping = RecordingShoppingRepository()
        shopping.serverItems = listOf(shoppingItem("s1", "Tomate"))

        val plan = restockFromPantry("home", listOf("tomates"), shopping, data)

        assertTrue(shopping.appliedBatches.isEmpty())
        assertEquals(listOf("Tomate"), plan.alreadyListed)
    }

    @Test
    fun `restock from the pantry with an item deleted meanwhile reports a changed list`() = runTest {
        val shopping = RecordingShoppingRepository()
        shopping.serverItems = listOf(shoppingItem("s1", "Tomate", purchased = true))
        shopping.applyFailure = ShoppingRepositoryException(ShoppingFailure.ItemNotFound)

        val failure = runCatching { restockFromPantry("home", listOf("Tomate"), shopping, data) }.exceptionOrNull()

        assertEquals(ShoppingFailure.ItemChanged, (failure as ShoppingRepositoryException).failure)
    }
```

- [ ] **Step 3: Tests del ViewModel de Comidas.** En `UT/feature/meals/MealsViewModelTest.kt` (imports `CatalogIngredient` si falta, `PantryEntry`, `kotlinx.coroutines.flow.flow`):
  1. Adapta los cuatro tests del diálogo antiguo: en `marking a meal cooked offers to cross off its ingredients from the list`, `marking cooked offers items whose name differs only in plural`, `marking cooked reads the list from the server, not from a stale cached snapshot` y `marking cooked when the list cannot be read still marks the meal and offers nothing`, cambia `viewModel.uiState.value.cookedCrossOff?.map(ShoppingItem::name)` por `viewModel.uiState.value.cooked?.crossOff?.map(ShoppingItem::name)`, `viewModel.crossOffCookedIngredients()` por `viewModel.confirmCooked(viewModel.uiState.value.cooked!!.defaultChoice())` y `cookedCrossOff` por `cooked` en las aserciones de `null`.
  2. Añade:

```kotlin
    @Test
    fun `cooking offers what the recipe uses from the pantry, read from the server`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value =
                listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"), Ingredient("Leche", "1", "l"))))
            shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "Arroz"))
            pantryRepository.serverIds = setOf(pantryId("Arroz"), pantryId("Leche"))
            viewModel.observe("home")
            advanceUntilIdle()

            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Arroz con leche").copy(recipeId = "r1"))
            advanceUntilIdle()

            val review = viewModel.uiState.value.cooked!!
            assertEquals("Arroz con leche", review.mealName)
            assertEquals(listOf("Arroz"), review.crossOff.map(ShoppingItem::name))
            assertEquals(listOf(CookedPantryRow(pantryId("Leche"), "Leche")), review.finished)
        }

    @Test
    fun `if the pantry cannot be read the cooking dialog has no finished section`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value =
                listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"), Ingredient("Leche", "1", "l"))))
            shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "Arroz"))
            pantryRepository.itemsFailure = PantryRepositoryException(PantryFailure.Network)
            viewModel.observe("home")
            advanceUntilIdle()

            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
            advanceUntilIdle()

            assertEquals(listOf("Arroz"), viewModel.uiState.value.cooked?.crossOff?.map(ShoppingItem::name))
            assertEquals(emptyList<CookedPantryRow>(), viewModel.uiState.value.cooked?.finished)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `crossing off after cooking puts a recognized ingredient in the pantry`() =
        runTest(mainDispatcherRule.testDispatcher) {
            ingredientRepository.catalogState.value = listOf(CatalogIngredient("arroz", "Arroz"))
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "arroz"))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
            advanceUntilIdle()

            viewModel.confirmCooked(viewModel.uiState.value.cooked!!.defaultChoice())
            advanceUntilIdle()

            assertEquals(listOf("s1"), shoppingRepository.purchasedIds)
            assertEquals(listOf<PantryEntry?>(PantryEntry("Arroz", null)), shoppingRepository.purchasedEntries)
            assertEquals(MealUiMessage.CookedReviewApplied, viewModel.uiState.value.notice)
            assertNull(viewModel.uiState.value.cooked)
        }

    @Test
    fun `finishing after cooking removes it from the pantry and puts it on the shopping list`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
            pantryRepository.serverIds = setOf(pantryId("Leche"))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
            advanceUntilIdle()
            val review = viewModel.uiState.value.cooked!!

            viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")))
            advanceUntilIdle()

            assertEquals(listOf(listOf(pantryId("Leche"))), pantryRepository.removed)
            assertEquals(listOf("Leche"), shoppingRepository.addedNames)
            assertEquals(MealUiMessage.CookedReviewApplied, viewModel.uiState.value.notice)
        }

    @Test
    fun `finishing without restock only removes it from the pantry`() = runTest(mainDispatcherRule.testDispatcher) {
        recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
        pantryRepository.serverIds = setOf(pantryId("Leche"))
        viewModel.observe("home")
        advanceUntilIdle()
        viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
        advanceUntilIdle()
        val review = viewModel.uiState.value.cooked!!

        viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")).copy(restock = false))
        advanceUntilIdle()

        assertEquals(listOf(listOf(pantryId("Leche"))), pantryRepository.removed)
        assertTrue(shoppingRepository.appliedBatches.isEmpty())
        assertTrue(shoppingRepository.addedNames.isEmpty())
    }

    @Test
    fun `confirming the cooking dialog with nothing marked writes nothing`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value =
                listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"), Ingredient("Leche", "1", "l"))))
            shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "Arroz"))
            pantryRepository.serverIds = setOf(pantryId("Leche"))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
            advanceUntilIdle()

            viewModel.confirmCooked(CookedChoice(emptySet(), emptySet(), restock = true))
            advanceUntilIdle()

            assertTrue(shoppingRepository.purchasedIds.isEmpty())
            assertTrue(pantryRepository.removed.isEmpty())
            assertTrue(shoppingRepository.appliedBatches.isEmpty())
            assertNull(viewModel.uiState.value.cooked)
            assertFalse(viewModel.uiState.value.isSaving)
        }

    @Test
    fun `crossing off without the catalog in time waits at most 5 seconds and marks without a pantry entry`() =
        runTest(mainDispatcherRule.testDispatcher) {
            ingredientRepository.catalogOverride = flow { awaitCancellation() }
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "Arroz"))
            viewModel.observe("home")
            runCurrent()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
            runCurrent()
            val review = viewModel.uiState.value.cooked!!

            viewModel.confirmCooked(review.defaultChoice())
            advanceTimeBy(4_999)
            runCurrent()
            assertTrue(shoppingRepository.purchasedIds.isEmpty())

            advanceTimeBy(2)
            runCurrent()
            assertEquals(listOf("s1"), shoppingRepository.purchasedIds)
            assertEquals(listOf<PantryEntry?>(null), shoppingRepository.purchasedEntries)
        }
```

- [ ] **Step 4: El test de 5 s de la Despensa.** En `UT/feature/pantry/PantryViewModelTest.kt`, el test `restock without brands in time adds it without brand or store` pasa a comprobar el límite (ruling 10). Sustituye, desde `viewModel.restock(tomato)`:

```kotlin
        viewModel.restock(tomato)
        advanceTimeBy(4_999)
        runCurrent()
        assertTrue(shopping.addedNames.isEmpty())

        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("Tomate"), shopping.addedNames)
        assertEquals(listOf(null), shopping.addedSupermarkets)
        assertEquals(listOf(null), shopping.addedBrands)
```

y el `advanceUntilIdle()` de después de `viewModel.observe("home")` de ese test por `runCurrent()`. (Este test ya pasa hoy; el cambio hace que falle si el límite no fuera de 5 s.)

- [ ] **Step 5: Verlo en rojo.** JVM de `com.dlunaunizar.bobitos.feature.meals.CookedReviewTest`, `com.dlunaunizar.bobitos.feature.common.PantryShoppingTest` y `com.dlunaunizar.bobitos.feature.meals.MealsViewModelTest`. Esperado: fallo de compilación (`cookedReview`, `CookedReview`, `restockFromPantry`, `cooked`, `confirmCooked`, `CookedReviewApplied` no existen). Copia el fallo en el informe. `PantryViewModelTest` se verá en verde en el Step 12.

- [ ] **Step 6: Lógica pura.** Crea `APP/feature/meals/CookedReview.kt`:

```kotlin
package com.dlunaunizar.bobitos.feature.meals

import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.isInPantry
import com.dlunaunizar.bobitos.core.model.pantryId
import com.dlunaunizar.bobitos.core.model.shoppingMatchKey

// Diálogo «Has cocinado …» (spec 2026-10-13-despensa-design, §3c).

/** Algo de la receta que hay en la despensa: [id] es su `pantryId`; [name], como se escribe en la receta. */
data class CookedPantryRow(val id: String, val name: String)

/**
 * Lo que ofrece «Has cocinado [mealName]»: [crossOff], los artículos pendientes de la receta en la lista (para tacharlos);
 * [finished], lo de la receta que hay en la despensa y no está pendiente («¿Se ha acabado algo?»).
 */
data class CookedReview(
    val mealName: String,
    val crossOff: List<ShoppingItem>,
    val finished: List<CookedPantryRow>,
)

/** Lo marcado en el diálogo: los artículos a tachar, lo que se ha acabado y si se apunta a la compra. */
data class CookedChoice(val crossOffIds: Set<String>, val finishedIds: Set<String>, val restock: Boolean)

/** Lo que hay que escribir al pulsar «Listo». */
internal data class CookedWrites(
    val crossOff: List<ShoppingItem>,
    val finishedIds: List<String>,
    /** Lo que se apunta en la compra; vacío si no se pide. */
    val restockNames: List<String>,
) {
    val isEmpty: Boolean get() = crossOff.isEmpty() && finishedIds.isEmpty()
}

/**
 * Las secciones del diálogo, o `null` si las dos quedan vacías. [items] es la lista y [pantryIds] la despensa, leídas
 * del servidor; `null` si no se han podido leer (entonces su sección no sale). Un ingrediente pendiente en la lista solo
 * sale en «Tachar de la compra». Los repetidos por clave salen una vez; los nombres sin letras ni números, nunca.
 */
internal fun cookedReview(
    mealName: String,
    ingredients: List<Ingredient>,
    items: List<ShoppingItem>?,
    pantryIds: Set<String>?,
): CookedReview? {
    val names = ingredients.map { it.name.trim() }.filter(String::isNotEmpty)
    val keys = names.mapTo(HashSet(), ::shoppingMatchKey)
    val crossOff = items.orEmpty().filter { !it.purchased && shoppingMatchKey(it.name) in keys }
    val pendingKeys = crossOff.mapTo(HashSet()) { shoppingMatchKey(it.name) }
    val finished = if (pantryIds == null) {
        emptyList()
    } else {
        names.filter { shoppingMatchKey(it) !in pendingKeys && isInPantry(it, pantryIds) }
            .distinctBy(::pantryId)
            .map { CookedPantryRow(pantryId(it), it) }
    }
    if (crossOff.isEmpty() && finished.isEmpty()) return null
    return CookedReview(mealName, crossOff, finished)
}

/** Por defecto: tachar todo lo pendiente, nada se ha acabado y, si algo se acaba, apuntarlo a la compra. */
internal fun CookedReview.defaultChoice(): CookedChoice =
    CookedChoice(crossOff.mapTo(HashSet(), ShoppingItem::id), emptySet(), restock = true)

internal fun CookedChoice.toggleCrossOff(id: String): CookedChoice = copy(crossOffIds = crossOffIds.toggled(id))

internal fun CookedChoice.toggleFinished(id: String): CookedChoice = copy(finishedIds = finishedIds.toggled(id))

private fun Set<String>.toggled(id: String): Set<String> = if (id in this) this - id else this + id

/** Lo que escribe [choice], en el orden del diálogo. */
internal fun CookedReview.writesFor(choice: CookedChoice): CookedWrites {
    val finishedRows = finished.filter { it.id in choice.finishedIds }
    return CookedWrites(
        crossOff = crossOff.filter { it.id in choice.crossOffIds },
        finishedIds = finishedRows.map(CookedPantryRow::id),
        restockNames = if (choice.restock) finishedRows.map(CookedPantryRow::name) else emptyList(),
    )
}
```

- [ ] **Step 7: Escritura compartida.**
  1. En `APP/feature/common/IngredientReview.kt`, añade:

```kotlin
/**
 * [ShoppingRepository.applyWrites] de unos cambios hechos sobre la lista tal como se vio: si otro miembro ha borrado un
 * artículo desde entonces, falla con [ShoppingFailure.ItemChanged] (la lista ha cambiado y reintentar lo arregla), no con
 * [ShoppingFailure.ItemNotFound].
 */
internal suspend fun ShoppingRepository.applySeenWrites(spaceId: String, writes: List<ShoppingWrite>) {
    try {
        applyWrites(spaceId, writes)
    } catch (error: ShoppingRepositoryException) {
        if (error.failure == ShoppingFailure.ItemNotFound) {
            throw ShoppingRepositoryException(ShoppingFailure.ItemChanged, error)
        }
        throw error
    }
}
```

  y `applyIngredientReview` queda en: calcular `writes`, `if (writes.isEmpty()) return` y `shoppingRepository.applySeenWrites(spaceId, writes)` (su KDoc remite a `applySeenWrites`).

  2. En `APP/feature/common/PantryShopping.kt`, debajo de `pantryRestock`:

```kotlin
/**
 * «A la compra» de [names] en la Compra de [spaceId]: lee la lista del servidor (con una caché vieja se duplicaría el
 * artículo) y escribe lo que haga falta ([pantryRestock]) en una sola escritura ([applySeenWrites]). Devuelve el plan:
 * lo que ya estaba pendiente va en `alreadyListed`. Lo usan la Despensa («Se acabó») y Comidas («Has cocinado …»).
 */
internal suspend fun restockFromPantry(
    spaceId: String,
    names: List<String>,
    shoppingRepository: ShoppingRepository,
    data: IngredientShoppingData,
): PantryRestock {
    val plan = pantryRestock(names, shoppingRepository.currentItems(spaceId), data)
    if (plan.writes.isNotEmpty()) shoppingRepository.applySeenWrites(spaceId, plan.writes)
    return plan
}
```

  3. En `PantryViewModel.restock`, el cuerpo de `runCatching` pasa a:

```kotlin
            runCatching {
                val plan = restockFromPantry(spaceId, listOf(item.name), shoppingRepository, ingredientData())
                if (plan.writes.isEmpty()) PantryUiMessage.AlreadyInShopping else PantryUiMessage.AddedToShopping
            }
```

  (quita el comentario de la lista del servidor, que ahora está en `restockFromPantry`, y los imports que sobren).

- [ ] **Step 8: Estado y mensajes.**
  - `MealsUiState`: sustituye `cookedCrossOff` por

```kotlin
    // Diálogo «Has cocinado …» tras marcar una comida como cocinada (null = sin diálogo).
    val cooked: CookedReview? = null,
```

  - `MealUiMessage`: sustituye `IngredientsCrossedOff` por `CookedReviewApplied`.

- [ ] **Step 9: ViewModel de Comidas.** En `APP/feature/meals/MealsViewModel.kt`:
  1. `markCooked`:

```kotlin
    // Marca la comida como cocinada y, si su receta tiene ingredientes pendientes en la compra o que hay en la
    // despensa, abre «Has cocinado …» (ver cookedReview). Las dos se leen del servidor; si una no se puede leer, su
    // sección no sale.
    fun markCooked(meal: Meal) {
        val spaceId = observedSpaceId ?: return
        if (mutableUiState.value.isSaving) return
        val ingredients = mutableUiState.value.recipes.firstOrNull { it.id == meal.recipeId }?.ingredients.orEmpty()
        mutableUiState.update { it.copy(isSaving = true, error = null, notice = null) }
        viewModelScope.launch {
            try {
                repository.setCooked(spaceId, meal.id, true)
                val review = if (ingredients.isEmpty()) {
                    null
                } else {
                    val items = runCatching { shoppingRepository.currentItems(spaceId) }
                        .onFailure { if (it is CancellationException) throw it }
                        .getOrNull()
                    cookedReview(meal.name, ingredients, items, pantryRepository.currentIdsOrNull(spaceId))
                }
                mutableUiState.update { it.copy(isSaving = false, notice = MealUiMessage.MealCooked, cooked = review) }
            } catch (error: Throwable) {
                showError(error.toUiMessage())
            }
        }
    }
```

  (el `catch (error: Throwable)` ya estaba; no es nuevo; el import de `shoppingMatchKey` deja de usarse).

  2. Sustituye `crossOffCookedIngredients` y `dismissCookedCrossOff` por:

```kotlin
    // «Listo» en «Has cocinado …»: tacha lo marcado (si es un ingrediente reconocido, entra en la despensa, como al
    // marcar en la Compra), quita de la despensa lo que se ha acabado y, si se pide, lo apunta a la compra.
    fun confirmCooked(choice: CookedChoice) {
        val spaceId = observedSpaceId ?: return
        val writes = mutableUiState.value.cooked?.writesFor(choice) ?: return
        mutableUiState.update { it.copy(cooked = null) }
        if (writes.isEmpty) return
        runAction(MealUiMessage.CookedReviewApplied) {
            val needsCatalog = writes.crossOff.isNotEmpty() || writes.restockNames.isNotEmpty()
            val data = if (needsCatalog) ingredientData() else IngredientShoppingData()
            writes.crossOff.forEach { item ->
                shoppingRepository.setPurchased(spaceId, item.id, true, pantryEntryFor(item.name, data))
            }
            if (writes.finishedIds.isNotEmpty()) pantryRepository.remove(spaceId, writes.finishedIds)
            if (writes.restockNames.isNotEmpty()) {
                restockFromPantry(spaceId, writes.restockNames, shoppingRepository, data)
            }
        }
    }

    fun dismissCooked() = mutableUiState.update { it.copy(cooked = null) }

    // Catálogo para reconocer ingredientes: el que ya escucha la pantalla; si aún no ha llegado, se espera 5 s como máximo
    // y, si no llega, se sigue sin él (lo tachado no entra en la despensa; lo apuntado va sin marca ni tienda).
    private suspend fun ingredientData(): IngredientShoppingData =
        shoppingData.takeIf { it.ingredients.isNotEmpty() } ?: ingredientShoppingSource.firstOrEmpty()
```

  3. Errores de la despensa: en `Throwable.toUiMessage()`, la rama `is PantryRepositoryException -> failure.toUiMessage()`, y:

```kotlin
private fun PantryFailure.toUiMessage(): MealUiMessage = when (this) {
    PantryFailure.NotAuthenticated -> MealUiMessage.NotAuthenticated
    PantryFailure.EmailNotVerified -> MealUiMessage.EmailNotVerified
    PantryFailure.SpaceNotFound -> MealUiMessage.SpaceNotFound
    PantryFailure.PermissionDenied -> MealUiMessage.PermissionDenied
    PantryFailure.Network -> MealUiMessage.NetworkError
    PantryFailure.NameRequired,
    PantryFailure.NameTooLong,
    PantryFailure.Unknown,
    -> MealUiMessage.UnexpectedError
}
```

- [ ] **Step 10: El diálogo.** Crea `APP/feature/meals/CookedDialog.kt`:

```kotlin
package com.dlunaunizar.bobitos.feature.meals

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing

// «Has cocinado …»: tachar de la compra (marcado), «¿Se ha acabado algo?» (sin marcar) y «Apuntar a la compra lo que se
// ha acabado» (marcado; solo se puede tocar si algo se ha marcado como acabado).
@Composable
internal fun CookedDialog(review: CookedReview, onConfirm: (CookedChoice) -> Unit, onDismiss: () -> Unit) {
    var choice by remember(review) { mutableStateOf(review.defaultChoice()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.meals_cooked_title, review.mealName)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (review.crossOff.isNotEmpty()) {
                    CookedSectionTitle(R.string.meals_cooked_cross_off)
                    review.crossOff.forEach { item ->
                        CookedCheckRow(item.name, item.id in choice.crossOffIds) {
                            choice = choice.toggleCrossOff(item.id)
                        }
                    }
                }
                if (review.finished.isNotEmpty()) {
                    CookedSectionTitle(R.string.meals_cooked_finished)
                    review.finished.forEach { row ->
                        CookedCheckRow(row.name, row.id in choice.finishedIds) { choice = choice.toggleFinished(row.id) }
                    }
                    CookedCheckRow(
                        label = stringResource(R.string.meals_cooked_restock),
                        checked = choice.restock,
                        enabled = choice.finishedIds.isNotEmpty(),
                    ) { choice = choice.copy(restock = !choice.restock) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(choice) }) { Text(stringResource(R.string.meals_cooked_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CookedSectionTitle(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
    )
}

@Composable
private fun CookedCheckRow(label: String, checked: Boolean, enabled: Boolean = true, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.sm))
    }
}
```

  En `MealsScreen.kt` (sin cambiar la firma de `MealsScreen`), sustituye el bloque `state.cookedCrossOff?.let { … AlertDialog … }` por:

```kotlin
    state.cooked?.let { review ->
        CookedDialog(review = review, onConfirm = viewModel::confirmCooked, onDismiss = viewModel::dismissCooked)
    }
```

  y en `MealUiMessage.stringResourceId`, `MealUiMessage.CookedReviewApplied -> R.string.meals_notice_cooked_review` en lugar de la rama de `IngredientsCrossedOff`. Quita los imports que sobren.

- [ ] **Step 11: Textos.** En `RES`, borra `meals_cooked_crossoff_title`, `meals_cooked_crossoff_body`, `meals_cooked_crossoff_confirm` y `meals_notice_crossed_off` (comprueba antes con `grep -rn` que nadie más los usa) y añade junto a `meals_cooked_label`:

```xml
    <string name="meals_cooked_title">Has cocinado %1$s</string>
    <string name="meals_cooked_cross_off">Tachar de la compra</string>
    <string name="meals_cooked_finished">¿Se ha acabado algo?</string>
    <string name="meals_cooked_restock">Apuntar a la compra lo que se ha acabado</string>
    <string name="meals_cooked_done">Listo</string>
    <string name="meals_notice_cooked_review">Compra y despensa al día</string>
```

- [ ] **Step 12: Verlo en verde.** JVM de las tres clases del Step 5, de `com.dlunaunizar.bobitos.feature.pantry.PantryViewModelTest` y de `com.dlunaunizar.bobitos.feature.common.IngredientReviewTest`; después GRADLE.

- [ ] **Step 13: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/meals/CookedReview.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/meals/CookedDialog.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/common/IngredientReview.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/common/PantryShopping.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/pantry/PantryViewModel.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModel.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsUiState.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealUiMessage.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsScreen.kt \
  app/src/main/res/values/strings.xml \
  app/src/test/java/com/dlunaunizar/bobitos/feature/meals/CookedReviewTest.kt \
  app/src/test/java/com/dlunaunizar/bobitos/feature/common/PantryShoppingTest.kt \
  app/src/test/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModelTest.kt \
  app/src/test/java/com/dlunaunizar/bobitos/feature/pantry/PantryViewModelTest.kt
git commit -m "feat(despensa): al cocinar, «Has cocinado …» tacha de la compra y pregunta qué se ha acabado"
```

---

### Task 4: «Con lo que hay» en el Recetario y «¿Qué cocino?» en la Despensa

**Files:**
- Create: `APP/feature/common/RecipePantry.kt`
- Modify: `APP/feature/recipes/RecipesViewModel.kt`, `APP/feature/recipes/RecipesUiState.kt`, `APP/feature/recipes/RecipesScreen.kt`
- Modify: `APP/feature/pantry/PantryScreen.kt`
- Modify: `APP/core/navigation/BobitosNavHost.kt`
- Modify: `config/detekt/baseline.xml` (solo la entrada `LongMethod` de `RecipesScreen`)
- Modify: `RES`
- Create (test): `UT/feature/common/RecipePantryTest.kt`
- Test: `UT/feature/recipes/RecipesViewModelTest.kt`

**Interfaces:**
- Consumes: `pantryId` (plan 1); el constructor de `RecipesViewModel` con `pantryRepository` (Tarea 1).
- Produces (los usa la Tarea 5):
  - `internal fun missingFromPantry(recipe: Recipe, pantryIds: Set<String>): List<String>`.
  - `data class PantryLine(@StringRes val res: Int, val args: List<Any> = emptyList())`; `internal fun pantryLine(missing: List<String>): PantryLine`; `@Composable internal fun pantryLineText(line: PantryLine): String`.
  - `data class RecipeRow(val recipe: Recipe, val pantryLine: PantryLine?)`; `internal fun List<Recipe>.withPantryLines(pantryIds: Set<String>?): List<RecipeRow>`.
  - `R.string.recipes_filter_pantry` («Con lo que hay»).
  - `RecipesViewModel.observePantry(spaceId: String?)`, `RecipesViewModel.setPantryOnly(value: Boolean)`; `RecipesUiState.pantryIds: Set<String>?`, `RecipesUiState.pantryOnly: Boolean`.

- [ ] **Step 1: Tests de la lógica pura.** Crea `UT/feature/common/RecipePantryTest.kt`:

```kotlin
package com.dlunaunizar.bobitos.feature.common

import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import com.dlunaunizar.bobitos.core.model.pantryId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RecipePantryTest {
    private val pantry = setOf(pantryId("Sal"), pantryId("Aceite de oliva"))

    @Test
    fun `what is missing keeps the recipe order and names, once per key`() {
        val tortilla = recipe("r1", "Tortilla", "Huevos", "Patatas", "huevo", "Sal", "  Cebolla ")

        assertEquals(listOf("Huevos", "Patatas", "Cebolla"), missingFromPantry(tortilla, pantry))
    }

    @Test
    fun `names without letters or numbers are neither in the pantry nor missing`() {
        val soup = recipe("r1", "Sopa", "—", "¡!", "  ", "Agua")

        assertEquals(listOf("Agua"), missingFromPantry(soup, pantry + ""))
    }

    @Test
    fun `with the pantry only recipes with ingredients show, from fewest missing, then by title`() {
        val recipes = listOf(
            recipe("r1", "Tortilla", "Huevos", "Patatas", "Sal"),
            recipe("r2", "Aliño", "Sal", "Aceite de oliva"),
            recipe("r3", "Sopa", "—"),
            recipe("r4", "Bocadillo"),
            recipe("r5", "Ensalada", "Lechuga", "Sal"),
            recipe("r6", "Arroz blanco", "Arroz", "Ajo"),
        )

        val rows = recipes.withPantryLines(pantry)

        assertEquals(listOf("r2", "r5", "r6", "r1"), rows.map { it.recipe.id })
        assertEquals(PantryLine(R.string.recipes_pantry_all), rows[0].pantryLine)
        assertEquals(PantryLine(R.string.recipes_pantry_missing_one, listOf("lechuga")), rows[1].pantryLine)
    }

    @Test
    fun `without the pantry the recipes stay as they are and have no line`() {
        val recipes = listOf(recipe("r1", "Tortilla", "Huevos"), recipe("r4", "Bocadillo"))

        assertEquals(listOf(RecipeRow(recipes[0], null), RecipeRow(recipes[1], null)), recipes.withPantryLines(null))
    }

    @Test
    fun `the line names up to three and counts the rest`() {
        assertEquals(
            PantryLine(R.string.recipes_pantry_missing_two, listOf("huevos", "leche")),
            pantryLine(listOf("Huevos", "Leche")),
        )
        assertEquals(
            PantryLine(R.string.recipes_pantry_missing_three, listOf("huevos", "leche", "harina")),
            pantryLine(listOf("Huevos", "Leche", "Harina")),
        )
        assertEquals(
            PantryLine(R.string.recipes_pantry_missing_more, listOf(5, "huevos", "leche", 3)),
            pantryLine(listOf("Huevos", "Leche", "Harina", "Azúcar", "Sal")),
        )
    }

    @Test
    fun `the line lowercases the initial but keeps acronyms`() {
        assertEquals(
            PantryLine(R.string.recipes_pantry_missing_two, listOf("ácido cítrico", "AOVE")),
            pantryLine(listOf("Ácido cítrico", "AOVE")),
        )
    }

    private fun recipe(id: String, title: String, vararg names: String) = Recipe(
        id = id,
        ownerUid = "u",
        visibility = RecipeVisibility.PRIVATE,
        title = title,
        description = null,
        category = null,
        ingredients = names.map { Ingredient(it) }.takeIf { it.isNotEmpty() },
        createdBy = "u",
        createdByName = "U",
        createdAt = Instant.EPOCH,
        updatedBy = "u",
        updatedAt = Instant.EPOCH,
    )
}
```

- [ ] **Step 2: Tests del ViewModel.** Añade a `UT/feature/recipes/RecipesViewModelTest.kt` (import `PantryItem`):

```kotlin
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
    fun `the pantry chip switches on and off`() {
        viewModel.setPantryOnly(true)
        assertTrue(viewModel.uiState.value.pantryOnly)

        viewModel.setPantryOnly(false)
        assertFalse(viewModel.uiState.value.pantryOnly)
    }
```

- [ ] **Step 3: Verlo en rojo.** JVM de `com.dlunaunizar.bobitos.feature.common.RecipePantryTest` y `com.dlunaunizar.bobitos.feature.recipes.RecipesViewModelTest`. Esperado: fallo de compilación.

- [ ] **Step 4: Lógica pura.** Crea `APP/feature/common/RecipePantry.kt`:

```kotlin
package com.dlunaunizar.bobitos.feature.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.pantryId

// «Con lo que hay» del Recetario y del selector de receta del planificador (spec 2026-10-13-despensa-design, §4c y §4d).

/**
 * Los ingredientes de [recipe] que no hay en la despensa ([pantryIds]), como se escriben en la receta (recortados), sin
 * repetir por clave y en el orden de la receta. Los nombres sin letras ni números ni están ni faltan.
 */
internal fun missingFromPantry(recipe: Recipe, pantryIds: Set<String>): List<String> =
    recipe.ingredients.orEmpty()
        .map { it.name.trim() }
        .filter { pantryId(it).isNotEmpty() }
        .distinctBy(::pantryId)
        .filterNot { pantryId(it) in pantryIds }

/** La línea de una receta en «Con lo que hay»: su texto y sus argumentos. */
data class PantryLine(@StringRes val res: Int, val args: List<Any> = emptyList())

/** Una receta de la lista y, con «Con lo que hay», su línea. */
data class RecipeRow(val recipe: Recipe, val pantryLine: PantryLine?)

/**
 * Sin [pantryIds], las recetas tal cual y sin línea. Con ellos («Con lo que hay»): solo las que tienen algún ingrediente,
 * de la que menos falta a la que más, después por título y por id, cada una con su línea.
 */
internal fun List<Recipe>.withPantryLines(pantryIds: Set<String>?): List<RecipeRow> {
    if (pantryIds == null) return map { RecipeRow(it, null) }
    return filter { recipe -> recipe.ingredients.orEmpty().any { pantryId(it.name).isNotEmpty() } }
        .map { it to missingFromPantry(it, pantryIds) }
        .sortedWith(compareBy({ it.second.size }, { it.first.title.lowercase() }, { it.first.id }))
        .map { (recipe, missing) -> RecipeRow(recipe, pantryLine(missing)) }
}

/**
 * «Lo tienes todo», «Falta: huevos», «Faltan 2: huevos y leche», «Faltan 3: huevos, leche y harina» o, con más de tres,
 * «Faltan 5: huevos, leche y 3 más». Los nombres van con la inicial en minúscula, salvo las siglas («AOVE»).
 */
internal fun pantryLine(missing: List<String>): PantryLine {
    val names = missing.map(::lowerInitial)
    return when (names.size) {
        0 -> PantryLine(R.string.recipes_pantry_all)
        1 -> PantryLine(R.string.recipes_pantry_missing_one, names)
        2 -> PantryLine(R.string.recipes_pantry_missing_two, names)
        3 -> PantryLine(R.string.recipes_pantry_missing_three, names)
        else -> PantryLine(R.string.recipes_pantry_missing_more, listOf(names.size, names[0], names[1], names.size - 2))
    }
}

// «Huevos» → «huevos»; «AOVE» se queda (dos mayúsculas al principio).
private fun lowerInitial(name: String): String {
    val acronym = name.length > 1 && name[0].isUpperCase() && name[1].isUpperCase()
    return if (acronym) name else name.replaceFirstChar { it.lowercase() }
}

// El spread copia un array de 0-4 argumentos: no importa.
@Suppress("SpreadOperator")
@Composable
internal fun pantryLineText(line: PantryLine): String = stringResource(line.res, *line.args.toTypedArray())
```

- [ ] **Step 5: Textos.** En `RES`, junto a `recipes_filter_all`:

```xml
    <string name="recipes_filter_pantry">Con lo que hay</string>
    <string name="recipes_pantry_all">Lo tienes todo</string>
    <string name="recipes_pantry_missing_one">Falta: %1$s</string>
    <string name="recipes_pantry_missing_two">Faltan 2: %1$s y %2$s</string>
    <string name="recipes_pantry_missing_three">Faltan 3: %1$s, %2$s y %3$s</string>
    <string name="recipes_pantry_missing_more">Faltan %1$d: %2$s, %3$s y %4$d más</string>
```

y junto a los `pantry_*`: `<string name="pantry_what_to_cook">¿Qué cocino?</string>`.

- [ ] **Step 6: ViewModel del Recetario.**
  - `RecipesUiState`, al final:

```kotlin
    // Ids de la despensa del espacio activo; null sin espacio, mientras se lee o si no se puede leer (sin chip).
    val pantryIds: Set<String>? = null,
    // Chip «Con lo que hay» activado.
    val pantryOnly: Boolean = false,
```

  - `RecipesViewModel` (import `PantryItem`):

```kotlin
    private var pantryJob: Job? = null
    private var pantrySpaceId: String? = null

    /** Escucha la despensa de [spaceId] para «Con lo que hay»; con null (sin espacio) la suelta. */
    fun observePantry(spaceId: String?) {
        if (spaceId == pantrySpaceId && pantryJob?.isActive == true) return
        pantryJob?.cancel()
        pantryJob = null
        pantrySpaceId = spaceId
        mutableUiState.update { it.copy(pantryIds = null) }
        if (spaceId == null) return
        pantryJob = viewModelScope.launch {
            pantryRepository.items(spaceId)
                // Si no se puede leer, el chip no sale.
                .catch { mutableUiState.update { state -> state.copy(pantryIds = null) } }
                .collect { items ->
                    mutableUiState.update { it.copy(pantryIds = items.mapTo(HashSet(), PantryItem::id)) }
                }
        }
    }

    fun setPantryOnly(value: Boolean) = mutableUiState.update { it.copy(pantryOnly = value) }
```

  y en `stopObserving()`: `pantryJob?.cancel()`, `pantryJob = null`, `pantrySpaceId = null`.

- [ ] **Step 7: Pantalla del Recetario** (`APP/feature/recipes/RecipesScreen.kt`). Para no subir la complejidad de `RecipesScreen` (ya larga), lo nuevo va en composables aparte:
  1. Firma: dos parámetros nuevos antes de `viewModel`:

```kotlin
    onImportUrlConsumed: () -> Unit = {},
    pantryFilterRequested: Boolean = false,
    onPantryFilterConsumed: () -> Unit = {},
    viewModel: RecipesViewModel = hiltViewModel(),
```

  2. Después del `DisposableEffect(Unit) { … }`:

```kotlin
    PantryFilterEffects(
        spaceId = spaceId,
        requested = pantryFilterRequested,
        onObservePantry = viewModel::observePantry,
        onRequested = {
            viewModel.setPantryOnly(true)
            onPantryFilterConsumed()
        },
    )
```

  con:

```kotlin
// La despensa del espacio activo para «Con lo que hay» y, desde «¿Qué cocino?», el chip activado una sola vez.
@Composable
private fun PantryFilterEffects(
    spaceId: String?,
    requested: Boolean,
    onObservePantry: (String?) -> Unit,
    onRequested: () -> Unit,
) {
    DisposableEffect(spaceId) {
        onObservePantry(spaceId)
        onDispose { onObservePantry(null) }
    }
    LaunchedEffect(requested) {
        if (requested) onRequested()
    }
}
```

  3. `CategoryFilter` pasa a `RecipeFilters`, con el chip primero, y decide él solo si sale. Sustituye `if (categories.isNotEmpty()) { CategoryFilter(...) }` por:

```kotlin
            RecipeFilters(
                categories = categories,
                selected = selectedCategory,
                onSelect = { selectedCategory = it },
                pantryAvailable = state.pantryIds != null,
                pantryOnly = state.pantryOnly,
                onPantryOnly = viewModel::setPantryOnly,
            )
```

  y:

```kotlin
// Fila de filtros: «Con lo que hay» (si se puede leer la despensa) y las categorías. Sin ninguno, no sale.
@Composable
private fun RecipeFilters(
    categories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    pantryAvailable: Boolean,
    pantryOnly: Boolean,
    onPantryOnly: (Boolean) -> Unit,
) {
    if (categories.isEmpty() && !pantryAvailable) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        if (pantryAvailable) {
            item(key = "pantry") {
                FilterChip(
                    selected = pantryOnly,
                    onClick = { onPantryOnly(!pantryOnly) },
                    label = { Text(stringResource(R.string.recipes_filter_pantry)) },
                )
            }
        }
        if (categories.isNotEmpty()) {
            item(key = "cat-all") {
                FilterChip(
                    selected = selected == null,
                    onClick = { onSelect(null) },
                    label = { Text(stringResource(R.string.recipes_filter_all)) },
                )
            }
            items(categories, key = { it }) { category ->
                FilterChip(
                    selected = selected == category,
                    onClick = { onSelect(category) },
                    label = { Text(category) },
                )
            }
        }
    }
}
```

  4. Secciones: en `RecipesScreen`, `val pantryIds = state.pantryIds?.takeIf { state.pantryOnly }` y se pasa como argumento nuevo de las dos llamadas a `recipesSection` (antes de la lambda). En `recipesSection`, el parámetro `pantryIds: Set<String>?` (antes de `onOpen`) y la rama `UiState.Content`:

```kotlin
        is UiState.Content -> {
            val rows = recipes.value.matching(query)
                .filter { category == null || it.category == category }
                .withPantryLines(pantryIds)
            if (rows.isEmpty()) {
                val filtering = query.isNotBlank() || category != null || pantryIds != null
                val noResults = filtering && recipes.value.isNotEmpty()
                item(key = "empty-$titleRes") {
                    EmptyState(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.MenuBook,
                        title = stringResource(
                            if (noResults) R.string.recipes_no_results else R.string.recipes_empty,
                        ),
                    )
                }
            } else {
                items(rows, key = { "$titleRes-${it.recipe.id}" }) { row ->
                    RecipeCard(
                        recipe = row.recipe,
                        pantryLine = row.pantryLine,
                        onClick = { onOpen(row.recipe) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
```

  5. `RecipeCard` gana `pantryLine: PantryLine?` (después de `recipe`) y, debajo de la línea de categoría y autor:

```kotlin
            pantryLine?.let { line ->
                Text(
                    text = pantryLineText(line),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
```

- [ ] **Step 8: Baseline.** En `config/detekt/baseline.xml`, en la entrada `LongMethod:RecipesScreen.kt$…` cambia solo la lista de parámetros, de:

```text
onImportUrlConsumed: () -&gt; Unit = {}, viewModel: RecipesViewModel = hiltViewModel(), )</ID>
```

a:

```text
onImportUrlConsumed: () -&gt; Unit = {}, pantryFilterRequested: Boolean = false, onPantryFilterConsumed: () -&gt; Unit = {}, viewModel: RecipesViewModel = hiltViewModel(), )</ID>
```

  No añadas entradas nuevas: si detekt marca algo más en `RecipesScreen` (por ejemplo, `CyclomaticComplexMethod`), saca más código a composables privados.

- [ ] **Step 9: «¿Qué cocino?».**
  - `PantryScreen`: parámetro nuevo `onWhatToCook: () -> Unit` (después de `canWrite`) y la acción en la barra:

```kotlin
        topBar = {
            BobitosTopBar(
                title = stringResource(R.string.pantry_title),
                onBack = onBack,
                actions = {
                    TextButton(onClick = onWhatToCook) { Text(stringResource(R.string.pantry_what_to_cook)) }
                },
            )
        },
```

  - `BobitosNavHost.kt` (sin cambiar la firma de `BobitosNavHost`), junto a `SHOPPING_PREFILL_KEY`:

```kotlin
internal const val RECIPES_PANTRY_FILTER_KEY = "recipesPantryFilter"
```

  junto a `openShoppingWithName`:

```kotlin
// «¿Qué cocino?» de la Despensa: abre el Recetario con «Con lo que hay» activado (la pantalla lo consume).
private fun NavHostController.openRecipesWithPantry() {
    navigate(BobitosDestination.Recipes.route)
    getBackStackEntry(BobitosDestination.Recipes.route).savedStateHandle[RECIPES_PANTRY_FILTER_KEY] = true
}
```

  la ruta del Recetario:

```kotlin
        composable(BobitosDestination.Recipes.route) { entry ->
            val pantryFilter by entry.savedStateHandle.getStateFlow(RECIPES_PANTRY_FILTER_KEY, false)
                .collectAsStateWithLifecycle()
            RecipesScreen(
                onBack = { navController.popBackStack() },
                canWrite = uiState.syncStatus.canWrite,
                spaceId = uiState.selectedSpace?.id,
                importUrl = pendingRecipeImportUrl,
                onImportUrlConsumed = onRecipeImportUrlConsumed,
                pantryFilterRequested = pantryFilter,
                onPantryFilterConsumed = { entry.savedStateHandle[RECIPES_PANTRY_FILTER_KEY] = false },
            )
        }
```

  y en la ruta de la Despensa, `onWhatToCook = { navController.openRecipesWithPantry() },`.

- [ ] **Step 10: Verlo en verde.** Las dos clases del Step 3 y GRADLE.

- [ ] **Step 11: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/common/RecipePantry.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipesViewModel.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipesUiState.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/recipes/RecipesScreen.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/pantry/PantryScreen.kt \
  app/src/main/java/com/dlunaunizar/bobitos/core/navigation/BobitosNavHost.kt \
  config/detekt/baseline.xml \
  app/src/main/res/values/strings.xml \
  app/src/test/java/com/dlunaunizar/bobitos/feature/common/RecipePantryTest.kt \
  app/src/test/java/com/dlunaunizar/bobitos/feature/recipes/RecipesViewModelTest.kt
git commit -m "feat(despensa): «Con lo que hay» en el Recetario y «¿Qué cocino?» en la Despensa"
```

---

### Task 5: «Con lo que hay» en el selector de receta del planificador y documentación

**Files:**
- Modify: `APP/feature/meals/RecipePicker.kt`
- Modify: `APP/feature/meals/MealsViewModel.kt`, `APP/feature/meals/MealsUiState.kt`, `APP/feature/meals/MealsScreen.kt`
- Modify: `docs/FUNCTIONAL_SPEC.md`, `docs/USER_FLOWS.md`
- Test: `UT/feature/meals/RecipePickerTest.kt`, `UT/feature/meals/MealsViewModelTest.kt`

**Interfaces:**
- Consumes: `withPantryLines`, `RecipeRow`, `PantryLine`, `pantryLine`, `pantryLineText`, `R.string.recipes_filter_pantry` (Tarea 4); el constructor de `MealsViewModel` con `pantryRepository` (Tarea 1).
- Produces: `internal fun List<Recipe>.pickerRows(query: String, pantryIds: Set<String>?): List<RecipeRow>`; `MealsUiState.pantryIds: Set<String>?`; `RecipePickerDialog(recipes, pantryIds, onPick, onDismiss)`.

- [ ] **Step 1: Tests del selector.** En `UT/feature/meals/RecipePickerTest.kt`, el helper `recipe` gana `ingredients: List<String> = emptyList()` (al final) y pasa `ingredients = ingredients.map { Ingredient(it) }.takeIf { it.isNotEmpty() }`. Añade (imports `Ingredient`, `pantryId`, `PantryLine`, `assertTrue`):

```kotlin
    @Test
    fun `without the pantry chip the picker keeps mine first and has no lines`() {
        val rows = recipes.pickerRows("", null)

        assertEquals(listOf("p1", "p2", "g2", "g1"), rows.map { it.recipe.id })
        assertTrue(rows.all { it.pantryLine == null })
    }

    @Test
    fun `with the pantry chip the picker orders by what is missing and adds the line`() {
        val withIngredients = listOf(
            recipe("p1", RecipeVisibility.PRIVATE, "Tortilla", null, listOf("Huevos", "Patatas")),
            recipe("g1", RecipeVisibility.GLOBAL, "Aliño", null, listOf("Sal")),
            recipe("p2", RecipeVisibility.PRIVATE, "Tostada", null),
        )
        val pantry = setOf(pantryId("Sal"), pantryId("Huevos"))

        val rows = withIngredients.pickerRows("", pantry)

        assertEquals(listOf("g1", "p1"), rows.map { it.recipe.id })
        assertEquals(PantryLine(R.string.recipes_pantry_all), rows[0].pantryLine)
        assertEquals(PantryLine(R.string.recipes_pantry_missing_one, listOf("patatas")), rows[1].pantryLine)
        assertEquals(listOf("p1"), withIngredients.pickerRows("tortilla", pantry).map { it.recipe.id })
    }
```

- [ ] **Step 2: Tests del ViewModel.** Añade a `UT/feature/meals/MealsViewModelTest.kt` (import `PantryItem`):

```kotlin
    @Test
    fun `the pantry of the space feeds the recipe picker`() = runTest(mainDispatcherRule.testDispatcher) {
        pantryRepository.itemsState.value = listOf(PantryItem("sal", "Sal", null, null))

        viewModel.observe("home")
        advanceUntilIdle()

        assertEquals("home", pantryRepository.observedSpaceId)
        assertEquals(setOf("sal"), viewModel.uiState.value.pantryIds)
    }

    @Test
    fun `a pantry that cannot be read leaves the picker without the chip`() = runTest(mainDispatcherRule.testDispatcher) {
        pantryRepository.itemsFailure = PantryRepositoryException(PantryFailure.PermissionDenied)
        mealRepository.mealsState.value = listOf(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Lentejas"))

        viewModel.observe("home")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pantryIds)
        assertNull(viewModel.uiState.value.error)
        assertEquals(listOf("Lentejas"), (viewModel.uiState.value.meals as UiState.Content).value.map(Meal::name))
    }
```

- [ ] **Step 3: Verlo en rojo.** JVM de `com.dlunaunizar.bobitos.feature.meals.RecipePickerTest` y `com.dlunaunizar.bobitos.feature.meals.MealsViewModelTest`. Esperado: fallo de compilación (`pickerRows`, `pantryIds`).

- [ ] **Step 4: ViewModel.**
  - `MealsUiState`, después de `recipes`:

```kotlin
    // Ids de la despensa del espacio, para «Con lo que hay» del selector de receta; null si no se puede leer (sin chip).
    val pantryIds: Set<String>? = null,
```

  - `MealsViewModel`: `private var pantryJob: Job? = null`; al final de `observe` (después de `recipesJob`), `observePantry(spaceId)`; en `stopObserving`, `pantryJob?.cancel()` y `pantryJob = null`; y:

```kotlin
    // La despensa del espacio, para el chip «Con lo que hay» del selector de receta; si no se puede leer, sin chip.
    private fun observePantry(spaceId: String) {
        pantryJob?.cancel()
        mutableUiState.update { it.copy(pantryIds = null) }
        pantryJob = viewModelScope.launch {
            pantryRepository.items(spaceId)
                .catch { mutableUiState.update { state -> state.copy(pantryIds = null) } }
                .collect { items ->
                    mutableUiState.update { it.copy(pantryIds = items.mapTo(HashSet(), PantryItem::id)) }
                }
        }
    }
```

- [ ] **Step 5: Selector** (`APP/feature/meals/RecipePicker.kt`):

```kotlin
// Filas del selector: sin [pantryIds], como [forPicker] y sin línea; con ellos, «Con lo que hay» (ver withPantryLines).
internal fun List<Recipe>.pickerRows(query: String, pantryIds: Set<String>?): List<RecipeRow> =
    forPicker(query).withPantryLines(pantryIds)
```

  `RecipePickerDialog` gana `pantryIds: Set<String>?` (después de `recipes`):

```kotlin
// Selector de receta del editor de comidas, con buscador y, si se puede leer la despensa, el chip «Con lo que hay».
// Cada fila: título, «Mía»/«Común» (· categoría) y, con el chip, lo que falta.
@Composable
internal fun RecipePickerDialog(
    recipes: List<Recipe>,
    pantryIds: Set<String>?,
    onPick: (Recipe) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var pantryOnly by rememberSaveable { mutableStateOf(false) }
    val shown = remember(recipes, query, pantryOnly, pantryIds) {
        recipes.pickerRows(query, pantryIds?.takeIf { pantryOnly })
    }
```

  dentro de la `Column`, después del `OutlinedTextField` del buscador:

```kotlin
                if (pantryIds != null) {
                    FilterChip(
                        selected = pantryOnly,
                        onClick = { pantryOnly = !pantryOnly },
                        label = { Text(stringResource(R.string.recipes_filter_pantry)) },
                    )
                }
```

  la lista con `items(shown, key = { it.recipe.id }) { row -> RecipePickerRow(row = row, onClick = { onPick(row.recipe) }) }`, y la fila:

```kotlin
@Composable
private fun RecipePickerRow(row: RecipeRow, onClick: () -> Unit) {
    val recipe = row.recipe
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(text = recipe.title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = listOfNotNull(stringResource(recipeOriginRes(recipe)), recipe.category).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        row.pantryLine?.let { line ->
            Text(
                text = pantryLineText(line),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
```

- [ ] **Step 6: Paso por la pantalla de Comidas** (sin cambiar la firma de `MealsScreen`): `MealEditorHost` y `MealEditor` ganan `pantryIds: Set<String>?` (después de `recipes`); `MealsScreen` pasa `pantryIds = state.pantryIds` y `MealEditor` lo pasa a `RecipePickerDialog`. Si detekt marca `LongMethod` en `MealEditor`, saca el bloque `if (pickerOpen) { … }` a un composable privado `MealRecipePicker(open, recipes, pantryIds, onPick, onDismiss)`.

- [ ] **Step 7: Documentación.**
  - `docs/FUNCTIONAL_SPEC.md`, después de «PANTRY-03. Se acabó»:

```markdown
### PANTRY-04. La despensa en la Compra

- Al añadir a la Compra los ingredientes de una receta o de las comidas, lo que hay en la despensa sale como «Hay en la despensa», con «Omitir» marcado; con «Añadir» se apunta como siempre (o se vuelve a apuntar, si estaba comprado). Si todo queda en «Omitir», la app dice «No hace falta añadir nada».
- Al crear un producto en la Compra, si está en la despensa sale la pista «Hay en la despensa», que no impide guardarlo.

### PANTRY-05. Al cocinar

- Al marcar una comida como cocinada, el diálogo «Has cocinado …» ofrece tachar de la compra lo pendiente de su receta (marcado por defecto; lo tachado entra en la despensa si es un ingrediente) y pregunta «¿Se ha acabado algo?» con lo que de la receta hay en la despensa (sin marcar).
- Lo que se marca como acabado sale de la despensa y, con «Apuntar a la compra lo que se ha acabado» (marcada por defecto), se apunta en la Compra como en PANTRY-03.

### PANTRY-06. Qué cocino con lo que hay

- El Recetario y el selector de receta de Comidas tienen el filtro «Con lo que hay»: solo las recetas con ingredientes, de la que menos falta a la que más, con «Lo tienes todo» o lo que falta («Faltan 2: huevos y leche»).
- En la Despensa, «¿Qué cocino?» abre el Recetario con ese filtro activado.
```

  - `docs/USER_FLOWS.md`, al final de «17. Despensa»:

```markdown
5. Para decidir qué cocinar, pulsa «¿Qué cocino?» (o activa «Con lo que hay» en el Recetario): salen las recetas de la que menos falta a la que más, con lo que falta. Al pulsar «Añadir a la compra» en una receta, lo que hay en la despensa sale como «Hay en la despensa», con «Omitir» marcado.
6. Al planificar una comida, el selector de receta también tiene «Con lo que hay».
7. Al marcar una comida como cocinada, el diálogo «Has cocinado …» ofrece tachar lo pendiente de la compra y pregunta «¿Se ha acabado algo?»: lo marcado sale de la despensa y, si se deja marcada «Apuntar a la compra lo que se ha acabado», se apunta en la Compra.
8. Al añadir a mano en la Compra algo que hay en la despensa, el nombre lo avisa con «Hay en la despensa».
```

- [ ] **Step 8: Verlo en verde.** Las dos clases del Step 3 y GRADLE.

- [ ] **Step 9: Commit.**

```bash
git add app/src/main/java/com/dlunaunizar/bobitos/feature/meals/RecipePicker.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModel.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsUiState.kt \
  app/src/main/java/com/dlunaunizar/bobitos/feature/meals/MealsScreen.kt \
  docs/FUNCTIONAL_SPEC.md docs/USER_FLOWS.md \
  app/src/test/java/com/dlunaunizar/bobitos/feature/meals/RecipePickerTest.kt \
  app/src/test/java/com/dlunaunizar/bobitos/feature/meals/MealsViewModelTest.kt
git commit -m "feat(despensa): «Con lo que hay» en el selector de receta del planificador y documentación"
```

---

## Ejecución (`delegating-plan-execution`, plan grande)

1. Rama `agent/despensa-recetas`, desde `main` (19b1871), con este plan como primer commit.
2. Las tareas 1 a 5, una tras otra, cada una con un `sonnet` que recibe su brief. En el `task-done`: JVM de las clases nombradas en su paso de verde y GRADLE.
3. **Revisión final** con `opus`, con este plan (Review Focus incluido) y los rulings del ledger. Arreglos de Críticos e Importantes con un `sonnet`, cada uno con su test RED→GREEN. **Re-revisión acotada** solo si un arreglo toca `firestore.rules`, `migrations/` o alguno de los cuatro temas de seguridad del método (no se espera ninguno).
4. **`simplify`:** un revisor `sonnet` con los cuatro ángulos; un `sonnet` aplica la lista (a); la (b) va al `code-review`.
5. **`code-review`** en nivel `high`, más los arreglos (misma regla de re-revisión).
6. **Cierre:**
   - en `docs/superpowers/backlog.md`, sección «2026-10-14 — `agent/despensa`», tachar el menor interno del test de 5 s y marcarlo **Resuelto** en `agent/despensa-recetas`;
   - menores aplazados al backlog, en una sección nueva con la fecha y la rama;
   - el usuario decide qué hacer con la rama. **Sin push ni despliegue.**

## Verificación de punta a punta (coordinador, al final)

- **Suites:** GRADLE, NODE y EMU en verde (EMU no cambia, pero se pasa entera).
- **Preparación del emulador:** como en el plan 1 (`npm run emulators` con el `JAVA_HOME` y el `PATH` del JBR de Android Studio, `npm run seed:emulators`, script del Admin SDK en el scratchpad que crea el usuario sembrado y lo liga al espacio, e instalar la app debug). Importar los catálogos al emulador: `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 npm run catalog:import-ingredients -- --project demo-bobitos --apply` y lo mismo con `catalog:import-recipes` (si los scripts piden otro argumento para el emulador, el de los planes anteriores).
- **Recorrido**, con la receta común «Tortilla de patatas» (Patata, Huevos, Cebolla, Aceite de oliva virgen extra, Sal); capturas en tema claro y oscuro de los pasos 2, 3 y 6:
  1. **Despensa:** llenarla con «Patata», «Huevos», «Aceite de oliva virgen extra» y «Sal».
  2. **«¿Qué cocino?»:** en la Despensa, pulsarlo → el Recetario se abre con «Con lo que hay» activado; «Tortilla de patatas» dice «Falta: cebolla» y va antes que las recetas a las que les falta más. Desactivar el chip → vuelve la lista normal, sin líneas.
  3. **Revisión:** «Tortilla de patatas» → «Añadir a la compra» → Patata, Huevos, Aceite y Sal salen como «Hay en la despensa» con «Omitir»; Cebolla, como nueva. Confirmar → en la Compra solo aparece «Cebolla». Repetir con todo en «Omitir» → «No hace falta añadir nada».
  4. **Pista del alta:** en la Compra, «Añadir» → escribir «huevos» → «Hay en la despensa»; escribir «cebolla» → «Ya en la lista».
  5. **Selector:** en Comidas, nueva comida → «Elegir receta» → chip «Con lo que hay» → «Tortilla de patatas» con «Falta: cebolla» → guardar.
  6. **Cocinar:** marcarla como cocinada → «Has cocinado Tortilla de patatas»: «Tachar de la compra» con «Cebolla» marcada; «¿Se ha acabado algo?» con Patata, Huevos, Aceite y Sal sin marcar. Marcar «Huevos» → «Listo» → aviso «Compra y despensa al día». En la Compra, «Cebolla» comprada y «Huevos» pendiente; en la Despensa, «Cebolla» presente y «Huevos» fuera.

## Despliegue (fuera de este plan)

Aplazado por el usuario. Este plan **no cambia `firestore.rules`**, pero las reglas de `pantryItems` del plan 1 siguen sin desplegar: hay que desplegarlas (`npx firebase deploy --only firestore:rules --project dev`) **antes** de distribuir una beta que lleve la Despensa, y desde el mismo commit que se distribuye. Con las reglas antiguas fallan también marcar y desmarcar en la Compra, borrar espacios y, con este plan, abrir el Recetario y Comidas con espacio (escuchan la despensa; sin chip, pero sin error) y confirmar «Has cocinado …» con algo marcado como acabado (`remove` falla con permiso denegado). Pueden ir en el mismo despliegue aplazado del tercer plan de menores (reglas → beta 23), cada paso confirmado por el usuario.
