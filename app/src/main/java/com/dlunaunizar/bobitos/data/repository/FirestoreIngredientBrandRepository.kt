package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.data.sync.RealtimeMetrics
import com.dlunaunizar.bobitos.data.sync.SyncRepository
import com.dlunaunizar.bobitos.data.sync.WriteNotAllowedException
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
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

    override fun brands(): Flow<List<IngredientBrand>> {
        val uid = authRepository.currentUser.value?.id ?: return flowOf(emptyList())
        return callbackFlow {
            val metricId = realtimeMetrics.listenerStarted(SCOPE)
            val registration = brandsCollection(uid)
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
                                    .mapNotNull { doc -> doc.data?.let { parseIngredientBrand(doc.id, it) } }
                                    .sortedForBrands(),
                            )
                        }
                    }
                }
            awaitClose {
                registration.remove()
                realtimeMetrics.listenerStopped(metricId)
            }
        }
    }

    override suspend fun addBrand(input: BrandInput): String = runOperation {
        val uid = requireVerifiedUid()
        val values = validateBrandInput(input)
        val document = brandsCollection(uid).document()
        val fields = values.toFirestoreFields().toMutableMap()
        fields[FIELD_CREATED_AT] = FieldValue.serverTimestamp()
        fields[FIELD_UPDATED_AT] = FieldValue.serverTimestamp()
        document.set(fields).await()
        document.id
    }

    override suspend fun updateBrand(brandId: String, input: BrandInput) = runOperation {
        val uid = requireVerifiedUid()
        val values = validateBrandInput(input)
        val fields = values.toUpdateFields().toMutableMap()
        fields[FIELD_UPDATED_AT] = FieldValue.serverTimestamp()
        brandsCollection(uid).document(brandId).update(fields).await()
        Unit
    }

    override suspend fun setFavorite(brandId: String, favorite: Boolean) = runOperation {
        val uid = requireVerifiedUid()
        brandsCollection(uid).document(brandId)
            .update(mapOf("favorite" to favorite, FIELD_UPDATED_AT to FieldValue.serverTimestamp()))
            .await()
        Unit
    }

    override suspend fun deleteBrand(brandId: String) = runOperation {
        val uid = requireVerifiedUid()
        brandsCollection(uid).document(brandId).delete().await()
        Unit
    }

    private fun brandsCollection(uid: String): CollectionReference =
        firestore.collection(USERS).document(uid).collection(BRANDS)

    private fun requireVerifiedUid(): String {
        val user = authRepository.currentUser.value
            ?: throw BrandRepositoryException(BrandFailure.NotAuthenticated)
        if (!user.isEmailVerified) throw BrandRepositoryException(BrandFailure.EmailNotVerified)
        return user.id
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

    private companion object {
        const val USERS = "users"
        const val BRANDS = "brands"
        const val SCOPE = "ingredients:brands"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val MAX_BRANDS = 1000L
    }
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
