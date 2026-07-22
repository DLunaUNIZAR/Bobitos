package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import com.dlunaunizar.bobitos.data.sync.RealtimeMetrics
import com.dlunaunizar.bobitos.data.sync.SyncRepository
import com.dlunaunizar.bobitos.data.sync.WriteNotAllowedException
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreRecipeRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val realtimeMetrics: RealtimeMetrics,
) : RecipeRepository {
    private val firestore = FirebaseFirestore.getInstance()

    override fun globalRecipes(): Flow<List<Recipe>> =
        recipesQuery("recipes:global") { it.whereEqualTo(FIELD_VISIBILITY, RecipeVisibility.GLOBAL.name) }

    override fun myRecipes(): Flow<List<Recipe>> {
        val uid = authRepository.currentUser.value?.id ?: return flowOf(emptyList())
        return recipesQuery("recipes:mine") { it.whereEqualTo(FIELD_OWNER_UID, uid) }
    }

    override fun isCurrentUserRecipeAdmin(): Boolean =
        authRepository.currentUser.value?.id?.let(RecipeAdmins.uids::contains) == true

    override suspend fun createRecipe(
        visibility: RecipeVisibility,
        title: String,
        description: String?,
        category: String?,
        sourceRecipeId: String?,
        ingredients: List<Ingredient>,
        sourceUrl: String?,
    ) = runRecipeOperation {
        val user = requireVerifiedUser()
        val values = validate(title, description, category)
        recipesCollection().document().set(
            mapOf(
                FIELD_OWNER_UID to user.id,
                FIELD_VISIBILITY to visibility.name,
                FIELD_TITLE to values.title,
                FIELD_DESCRIPTION to values.description,
                FIELD_CATEGORY to values.category,
                FIELD_SOURCE_RECIPE_ID to sourceRecipeId,
                FIELD_SOURCE_URL to sourceUrl?.trim()?.takeIf(String::isNotEmpty),
                FIELD_INGREDIENTS to ingredients.toFirestore(),
                FIELD_CREATED_BY to user.id,
                FIELD_CREATED_BY_NAME to user.recipeDisplayName,
                FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                FIELD_UPDATED_BY to user.id,
                FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
            ),
        ).await()
        Unit
    }

    override suspend fun updateRecipe(
        recipeId: String,
        title: String,
        description: String?,
        category: String?,
        ingredients: List<Ingredient>,
    ) = runRecipeOperation {
        val user = requireVerifiedUser()
        val values = validate(title, description, category)
        recipesCollection().document(recipeId).update(
            mapOf(
                FIELD_TITLE to values.title,
                FIELD_DESCRIPTION to values.description,
                FIELD_CATEGORY to values.category,
                FIELD_INGREDIENTS to ingredients.toFirestore(),
                FIELD_UPDATED_BY to user.id,
                FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
            ),
        ).await()
        Unit
    }

    override suspend fun deleteRecipe(recipeId: String) = runRecipeOperation {
        requireVerifiedUser()
        recipesCollection().document(recipeId).delete().await()
        Unit
    }

    private fun recipesCollection() = firestore.collection(RECIPES)

    private fun recipesQuery(scope: String, filter: (CollectionReference) -> Query): Flow<List<Recipe>> = callbackFlow {
        val metricId = realtimeMetrics.listenerStarted(scope)
        val registration = filter(recipesCollection())
            .limit(MAX_VISIBLE_RECIPES)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(error.toRecipeRepositoryException())
                    snapshot != null -> {
                        realtimeMetrics.snapshotReceived(
                            scope,
                            snapshot.documentChanges.size,
                            snapshot.metadata.isFromCache,
                        )
                        trySend(
                            snapshot.documents
                                .mapNotNull(DocumentSnapshot::toRecipe)
                                .sortedWith(compareBy({ it.title.lowercase() }, Recipe::id)),
                        )
                    }
                }
            }
        awaitClose {
            registration.remove()
            realtimeMetrics.listenerStopped(metricId)
        }
    }

    private fun requireVerifiedUser(): AuthUser {
        val user = authRepository.currentUser.value
            ?: throw RecipeRepositoryException(RecipeFailure.NotAuthenticated)
        if (!user.isEmailVerified) {
            throw RecipeRepositoryException(RecipeFailure.EmailNotVerified)
        }
        return user
    }

    private fun validate(title: String, description: String?, category: String?): RecipeValues {
        val normalizedTitle = title.trim()
        val normalizedDescription = description?.trim()?.takeIf(String::isNotEmpty)
        val normalizedCategory = category?.trim()?.takeIf(String::isNotEmpty)
        when {
            normalizedTitle.isEmpty() -> throw RecipeRepositoryException(RecipeFailure.TitleRequired)
            normalizedTitle.length > MAX_TITLE_LENGTH -> throw RecipeRepositoryException(RecipeFailure.TitleTooLong)
            normalizedDescription != null && normalizedDescription.length > MAX_DESCRIPTION_LENGTH ->
                throw RecipeRepositoryException(RecipeFailure.DescriptionTooLong)
            normalizedCategory != null && normalizedCategory.length > MAX_CATEGORY_LENGTH ->
                throw RecipeRepositoryException(RecipeFailure.CategoryTooLong)
        }
        return RecipeValues(normalizedTitle, normalizedDescription, normalizedCategory)
    }

    private suspend inline fun <T> runRecipeOperation(crossinline operation: suspend () -> T): T {
        try {
            syncRepository.requireWritable()
            return operation()
        } catch (error: RecipeRepositoryException) {
            if (error.failure == RecipeFailure.Network) {
                syncRepository.reportWriteFailure(error.cause ?: error)
            }
            throw error
        } catch (error: WriteNotAllowedException) {
            throw RecipeRepositoryException(RecipeFailure.Network, error)
        } catch (error: Throwable) {
            syncRepository.reportWriteFailure(error)
            throw error.toRecipeRepositoryException()
        }
    }

    // Serializa la lista (acotada) a la forma embebida de Firestore. `quantity`/`unit` van como null si faltan.
    private fun List<Ingredient>.toFirestore(): List<Map<String, Any?>> = take(MAX_INGREDIENTS).map { ingredient ->
        mapOf(
            FIELD_INGREDIENT_NAME to ingredient.name,
            FIELD_INGREDIENT_QUANTITY to ingredient.quantity,
            FIELD_INGREDIENT_UNIT to ingredient.unit,
        )
    }

    private data class RecipeValues(val title: String, val description: String?, val category: String?)

    private companion object {
        const val RECIPES = "recipes"
        const val FIELD_OWNER_UID = "ownerUid"
        const val FIELD_VISIBILITY = "visibility"
        const val FIELD_TITLE = "title"
        const val FIELD_DESCRIPTION = "description"
        const val FIELD_CATEGORY = "category"
        const val FIELD_SOURCE_RECIPE_ID = "sourceRecipeId"
        const val FIELD_SOURCE_URL = "sourceUrl"
        const val FIELD_INGREDIENTS = "ingredients"
        const val FIELD_INGREDIENT_NAME = "name"
        const val FIELD_INGREDIENT_QUANTITY = "quantity"
        const val FIELD_INGREDIENT_UNIT = "unit"
        const val MAX_INGREDIENTS = 50
        const val FIELD_CREATED_BY = "createdBy"
        const val FIELD_CREATED_BY_NAME = "createdByName"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_BY = "updatedBy"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val MAX_TITLE_LENGTH = 120
        const val MAX_DESCRIPTION_LENGTH = 1000
        const val MAX_CATEGORY_LENGTH = 60
        const val MAX_VISIBLE_RECIPES = 250L
    }
}

private val AuthUser.recipeDisplayName: String
    get() = displayName.ifBlank { email.substringBefore('@') }.take(60)

private fun DocumentSnapshot.toRecipe(): Recipe? {
    val createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: return null
    val updatedAt = getTimestamp("updatedAt")?.toDate()?.toInstant() ?: createdAt
    val visibility = getString("visibility")?.let { value ->
        runCatching { RecipeVisibility.valueOf(value) }.getOrNull()
    } ?: return null
    return Recipe(
        id = id,
        ownerUid = getString("ownerUid") ?: return null,
        visibility = visibility,
        title = getString("title") ?: return null,
        description = getString("description"),
        category = getString("category"),
        sourceRecipeId = getString("sourceRecipeId"),
        sourceUrl = getString("sourceUrl"),
        ingredients = parseIngredients(),
        createdBy = getString("createdBy") ?: return null,
        createdByName = getString("createdByName") ?: getString("createdBy") ?: return null,
        createdAt = createdAt,
        updatedBy = getString("updatedBy") ?: getString("createdBy") ?: return null,
        updatedAt = updatedAt,
    )
}

// Retro-compat: campo ausente → null; presente → lista con parseo por elemento (descarta los malformados).
private fun DocumentSnapshot.parseIngredients(): List<Ingredient>? {
    val raw = get("ingredients") as? List<*> ?: return null
    return raw.mapNotNull { element ->
        val map = element as? Map<*, *> ?: return@mapNotNull null
        val name = (map["name"] as? String)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        Ingredient(
            name = name,
            quantity = (map["quantity"] as? String)?.takeIf(String::isNotBlank),
            unit = (map["unit"] as? String)?.takeIf(String::isNotBlank),
        )
    }
}

private fun Throwable.toRecipeRepositoryException(): RecipeRepositoryException = RecipeRepositoryException(
    failure = when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> RecipeFailure.PermissionDenied
        FirebaseFirestoreException.Code.NOT_FOUND -> RecipeFailure.RecipeNotFound
        FirebaseFirestoreException.Code.UNAVAILABLE -> RecipeFailure.Network
        else -> RecipeFailure.Unknown
    },
    cause = this,
)
