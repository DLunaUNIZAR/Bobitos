package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.Nutrition
import com.dlunaunizar.bobitos.data.sync.RealtimeMetrics
import com.dlunaunizar.bobitos.data.sync.SyncRepository
import com.dlunaunizar.bobitos.data.sync.WriteNotAllowedException
import com.google.firebase.firestore.CollectionReference
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
class FirestoreIngredientBrandRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val realtimeMetrics: RealtimeMetrics,
) : IngredientBrandRepository {
    private val firestore = FirebaseFirestore.getInstance()

    override fun brands(ingredientId: String): Flow<List<IngredientBrand>> = callbackFlow {
        val metricId = realtimeMetrics.listenerStarted(SCOPE)
        val registration = brandsCollection(ingredientId)
            .limit(MAX_BRANDS)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(error.toBrandRepositoryException())
                    snapshot != null -> {
                        realtimeMetrics.snapshotReceived(
                            SCOPE,
                            snapshot.documentChanges.size,
                            snapshot.metadata.isFromCache,
                        )
                        trySend(
                            snapshot.documents
                                .mapNotNull { it.toIngredientBrand(ingredientId) }
                                .sortedWith(compareBy({ it.name.lowercase() }, IngredientBrand::id)),
                        )
                    }
                }
            }
        awaitClose {
            registration.remove()
            realtimeMetrics.listenerStopped(metricId)
        }
    }

    override suspend fun addBrand(ingredientId: String, name: String, barcode: String?, nutrition: Nutrition?) =
        runOperation {
            val user = requireVerifiedUser()
            val values = validate(name, barcode)
            val base = mutableMapOf<String, Any?>(
                FIELD_NAME to values.name,
                FIELD_OWNER_UID to user.id,
                FIELD_CREATED_BY to user.id,
                FIELD_CREATED_BY_NAME to user.brandDisplayName,
                FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                FIELD_UPDATED_BY to user.id,
                FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
            )
            values.barcode?.let { base[FIELD_BARCODE] = it }
            base.putAll(nutrition.toNonNullFields())
            brandsCollection(ingredientId).document().set(base).await()
            Unit
        }

    override suspend fun updateBrand(
        ingredientId: String,
        brandId: String,
        name: String,
        barcode: String?,
        nutrition: Nutrition?,
    ) = runOperation {
        val user = requireVerifiedUser()
        val values = validate(name, barcode)
        val update = mutableMapOf<String, Any?>(
            FIELD_NAME to values.name,
            FIELD_BARCODE to (values.barcode ?: FieldValue.delete()),
            FIELD_UPDATED_BY to user.id,
            FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
        )
        update.putAll(nutrition.toUpdateFields())
        brandsCollection(ingredientId).document(brandId).update(update).await()
        Unit
    }

    override suspend fun deleteBrand(ingredientId: String, brandId: String) = runOperation {
        requireVerifiedUser()
        brandsCollection(ingredientId).document(brandId).delete().await()
        Unit
    }

    private fun brandsCollection(ingredientId: String): CollectionReference =
        firestore.collection(INGREDIENTS).document(ingredientId).collection(BRANDS)

    private fun requireVerifiedUser(): AuthUser {
        val user = authRepository.currentUser.value
            ?: throw BrandRepositoryException(BrandFailure.NotAuthenticated)
        if (!user.isEmailVerified) throw BrandRepositoryException(BrandFailure.EmailNotVerified)
        return user
    }

    private fun validate(name: String, barcode: String?): BrandValues {
        val normalizedName = name.trim()
        val normalizedBarcode = barcode?.trim()?.takeIf(String::isNotEmpty)
        when {
            normalizedName.isEmpty() -> throw BrandRepositoryException(BrandFailure.NameRequired)
            normalizedName.length > MAX_NAME_LENGTH -> throw BrandRepositoryException(BrandFailure.NameTooLong)
            normalizedBarcode != null && normalizedBarcode.length > MAX_BARCODE_LENGTH ->
                throw BrandRepositoryException(BrandFailure.BarcodeTooLong)
        }
        return BrandValues(normalizedName, normalizedBarcode)
    }

    private suspend inline fun <T> runOperation(crossinline operation: suspend () -> T): T {
        try {
            syncRepository.requireWritable()
            return operation()
        } catch (error: BrandRepositoryException) {
            if (error.failure == BrandFailure.Network) syncRepository.reportWriteFailure(error.cause ?: error)
            throw error
        } catch (error: WriteNotAllowedException) {
            throw BrandRepositoryException(BrandFailure.Network, error)
        } catch (error: Throwable) {
            syncRepository.reportWriteFailure(error)
            throw error.toBrandRepositoryException()
        }
    }

    private data class BrandValues(val name: String, val barcode: String?)

    private companion object {
        const val INGREDIENTS = "ingredients"
        const val BRANDS = "brands"
        const val SCOPE = "ingredients:brands"
        const val FIELD_NAME = "name"
        const val FIELD_BARCODE = "barcode"
        const val FIELD_OWNER_UID = "ownerUid"
        const val FIELD_CREATED_BY = "createdBy"
        const val FIELD_CREATED_BY_NAME = "createdByName"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_BY = "updatedBy"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val MAX_NAME_LENGTH = 120
        const val MAX_BARCODE_LENGTH = 32
        const val MAX_BRANDS = 100L
    }
}

private val NUTRITION_FIELDS = listOf("energyKcal", "fat", "carbohydrates", "sugars", "protein", "salt")

private fun Nutrition?.values(): Map<String, Double?> = mapOf(
    "energyKcal" to this?.energyKcal,
    "fat" to this?.fat,
    "carbohydrates" to this?.carbohydrates,
    "sugars" to this?.sugars,
    "protein" to this?.protein,
    "salt" to this?.salt,
)

// Para crear: solo los valores presentes (las claves ausentes quedan fuera del documento).
private fun Nutrition?.toNonNullFields(): Map<String, Any> =
    values().filterValues { it != null }.mapValues { it.value as Any }

// Para actualizar: los presentes se escriben; los ausentes se borran del documento.
private fun Nutrition?.toUpdateFields(): Map<String, Any> {
    val current = values()
    return NUTRITION_FIELDS.associateWith { key -> current[key] ?: FieldValue.delete() }
}

private val AuthUser.brandDisplayName: String
    get() = displayName.ifBlank { email.substringBefore('@') }.take(60)

private fun DocumentSnapshot.toIngredientBrand(ingredientId: String): IngredientBrand? {
    val createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: return null
    val updatedAt = getTimestamp("updatedAt")?.toDate()?.toInstant() ?: createdAt
    val nutrition = Nutrition(
        energyKcal = getDouble("energyKcal"),
        fat = getDouble("fat"),
        carbohydrates = getDouble("carbohydrates"),
        sugars = getDouble("sugars"),
        protein = getDouble("protein"),
        salt = getDouble("salt"),
    )
    return IngredientBrand(
        id = id,
        ingredientId = ingredientId,
        name = getString("name") ?: return null,
        barcode = getString("barcode"),
        nutrition = nutrition.takeUnless(Nutrition::isEmpty),
        ownerUid = getString("ownerUid") ?: return null,
        createdBy = getString("createdBy") ?: return null,
        createdByName = getString("createdByName") ?: getString("createdBy") ?: return null,
        createdAt = createdAt,
        updatedBy = getString("updatedBy") ?: getString("createdBy") ?: return null,
        updatedAt = updatedAt,
    )
}

private fun Throwable.toBrandRepositoryException(): BrandRepositoryException = BrandRepositoryException(
    failure = when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> BrandFailure.PermissionDenied
        FirebaseFirestoreException.Code.NOT_FOUND -> BrandFailure.BrandNotFound
        FirebaseFirestoreException.Code.UNAVAILABLE -> BrandFailure.Network
        else -> BrandFailure.Unknown
    },
    cause = this,
)
