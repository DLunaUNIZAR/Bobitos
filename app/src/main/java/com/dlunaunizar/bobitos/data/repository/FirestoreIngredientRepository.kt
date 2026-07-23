package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.slug
import com.dlunaunizar.bobitos.data.sync.RealtimeMetrics
import com.dlunaunizar.bobitos.data.sync.SyncRepository
import com.dlunaunizar.bobitos.data.sync.WriteNotAllowedException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreIngredientRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val realtimeMetrics: RealtimeMetrics,
) : IngredientRepository {
    private val firestore = FirebaseFirestore.getInstance()

    override fun catalog(): Flow<List<CatalogIngredient>> = callbackFlow {
        val metricId = realtimeMetrics.listenerStarted(SCOPE)
        val registration = ingredientsCollection()
            .limit(MAX_VISIBLE_INGREDIENTS)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(error.toIngredientRepositoryException())
                    snapshot != null -> {
                        realtimeMetrics.snapshotReceived(
                            SCOPE,
                            snapshot.documentChanges.size,
                            snapshot.metadata.isFromCache,
                        )
                        trySend(
                            snapshot.documents
                                .mapNotNull(DocumentSnapshot::toCatalogIngredient)
                                .sortedWith(compareBy({ it.name.lowercase() }, CatalogIngredient::id)),
                        )
                    }
                }
            }
        awaitClose {
            registration.remove()
            realtimeMetrics.listenerStopped(metricId)
        }
    }

    override fun isCurrentUserCatalogAdmin(): Boolean =
        authRepository.currentUser.value?.id?.let(RecipeAdmins.uids::contains) == true

    override fun currentUserId(): String? = authRepository.currentUser.value?.id

    override suspend fun ingredientById(id: String): CatalogIngredient? =
        runCatching { ingredientsCollection().document(id).get().await().toCatalogIngredient() }.getOrNull()

    override suspend fun createIngredient(name: String, category: String?, defaultUnit: String?) = runOperation {
        val user = requireVerifiedUser()
        val values = validate(name, category, defaultUnit)
        val id = slug(values.name).ifEmpty { throw IngredientRepositoryException(IngredientFailure.NameRequired) }
        ingredientsCollection().document(id).set(
            mapOf(
                FIELD_NAME to values.name,
                FIELD_NAME_LOWER to values.name.lowercase(),
                FIELD_CATEGORY to values.category,
                FIELD_DEFAULT_UNIT to values.unit,
                FIELD_OWNER_UID to user.id,
                FIELD_CREATED_BY to user.id,
                FIELD_CREATED_BY_NAME to user.catalogDisplayName,
                FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                FIELD_UPDATED_BY to user.id,
                FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
            ),
        ).await()
        Unit
    }

    override suspend fun updateIngredient(id: String, name: String, category: String?, defaultUnit: String?) =
        runOperation {
            val user = requireVerifiedUser()
            val values = validate(name, category, defaultUnit)
            ingredientsCollection().document(id).update(
                mapOf(
                    FIELD_NAME to values.name,
                    FIELD_NAME_LOWER to values.name.lowercase(),
                    FIELD_CATEGORY to values.category,
                    FIELD_DEFAULT_UNIT to values.unit,
                    FIELD_UPDATED_BY to user.id,
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                ),
            ).await()
            Unit
        }

    override suspend fun deleteIngredient(id: String) = runOperation {
        requireVerifiedUser()
        ingredientsCollection().document(id).delete().await()
        Unit
    }

    private fun ingredientsCollection() = firestore.collection(INGREDIENTS)

    private fun requireVerifiedUser(): AuthUser {
        val user = authRepository.currentUser.value
            ?: throw IngredientRepositoryException(IngredientFailure.NotAuthenticated)
        if (!user.isEmailVerified) {
            throw IngredientRepositoryException(IngredientFailure.EmailNotVerified)
        }
        return user
    }

    private fun validate(name: String, category: String?, defaultUnit: String?): IngredientValues {
        val normalizedName = name.trim()
        val normalizedCategory = category?.trim()?.takeIf(String::isNotEmpty)
        val normalizedUnit = defaultUnit?.trim()?.takeIf(String::isNotEmpty)
        when {
            normalizedName.isEmpty() -> throw IngredientRepositoryException(IngredientFailure.NameRequired)
            normalizedName.length > MAX_NAME_LENGTH -> throw IngredientRepositoryException(
                IngredientFailure.NameTooLong,
            )
            normalizedCategory != null && normalizedCategory.length > MAX_CATEGORY_LENGTH ->
                throw IngredientRepositoryException(IngredientFailure.CategoryTooLong)
            normalizedUnit != null && normalizedUnit.length > MAX_UNIT_LENGTH ->
                throw IngredientRepositoryException(IngredientFailure.UnitTooLong)
        }
        return IngredientValues(normalizedName, normalizedCategory, normalizedUnit)
    }

    private suspend inline fun <T> runOperation(crossinline operation: suspend () -> T): T {
        try {
            syncRepository.requireWritable()
            return operation()
        } catch (error: IngredientRepositoryException) {
            if (error.failure == IngredientFailure.Network) {
                syncRepository.reportWriteFailure(error.cause ?: error)
            }
            throw error
        } catch (error: WriteNotAllowedException) {
            throw IngredientRepositoryException(IngredientFailure.Network, error)
        } catch (error: Throwable) {
            syncRepository.reportWriteFailure(error)
            throw error.toIngredientRepositoryException()
        }
    }

    private data class IngredientValues(val name: String, val category: String?, val unit: String?)

    private companion object {
        const val INGREDIENTS = "ingredients"
        const val SCOPE = "ingredients:catalog"
        const val FIELD_NAME = "name"
        const val FIELD_NAME_LOWER = "nameLower"
        const val FIELD_CATEGORY = "category"
        const val FIELD_DEFAULT_UNIT = "defaultUnit"
        const val FIELD_OWNER_UID = "ownerUid"
        const val FIELD_CREATED_BY = "createdBy"
        const val FIELD_CREATED_BY_NAME = "createdByName"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_BY = "updatedBy"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val MAX_NAME_LENGTH = 120
        const val MAX_CATEGORY_LENGTH = 60
        const val MAX_UNIT_LENGTH = 24
        const val MAX_VISIBLE_INGREDIENTS = 500L
    }
}

private val AuthUser.catalogDisplayName: String
    get() = displayName.ifBlank { email.substringBefore('@') }.take(60)

private fun DocumentSnapshot.toCatalogIngredient(): CatalogIngredient? {
    val createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: return null
    val updatedAt = getTimestamp("updatedAt")?.toDate()?.toInstant() ?: createdAt
    return CatalogIngredient(
        id = id,
        name = getString("name") ?: return null,
        category = getString("category"),
        defaultUnit = getString("defaultUnit"),
        ownerUid = getString("ownerUid") ?: return null,
        createdBy = getString("createdBy") ?: return null,
        createdByName = getString("createdByName") ?: getString("createdBy") ?: return null,
        createdAt = createdAt,
        updatedBy = getString("updatedBy") ?: getString("createdBy") ?: return null,
        updatedAt = updatedAt,
    )
}

private fun Throwable.toIngredientRepositoryException(): IngredientRepositoryException = IngredientRepositoryException(
    failure = when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> IngredientFailure.PermissionDenied
        FirebaseFirestoreException.Code.NOT_FOUND -> IngredientFailure.IngredientNotFound
        FirebaseFirestoreException.Code.UNAVAILABLE -> IngredientFailure.Network
        else -> IngredientFailure.Unknown
    },
    cause = this,
)
