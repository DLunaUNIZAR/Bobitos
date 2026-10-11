package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.slug
import com.dlunaunizar.bobitos.data.sync.RealtimeMetrics
import com.dlunaunizar.bobitos.data.sync.SyncRepository
import com.dlunaunizar.bobitos.data.sync.WriteNotAllowedException
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestorePersonalIngredientRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val realtimeMetrics: RealtimeMetrics,
) : PersonalIngredientRepository {
    private val firestore = FirebaseFirestore.getInstance()

    override fun ingredients(): Flow<List<CatalogIngredient>> {
        val uid = authRepository.currentUser.value?.id ?: return flowOf(emptyList())
        return callbackFlow {
            val metricId = realtimeMetrics.listenerStarted(SCOPE)
            val registration = collection(uid).limit(MAX_INGREDIENTS).addSnapshotListener { snapshot, error ->
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
                                .mapNotNull { doc -> doc.data?.let { parseCatalogIngredient(doc.id, it, true) } }
                                .sortedForIngredientCatalog(),
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

    override suspend fun create(name: String, category: String?, defaultUnit: String?): String = runOperation {
        val uid = requireVerifiedUid()
        val values = validateIngredientInput(name, category, defaultUnit)
        val id = slug(values.name)
        val ref = collection(uid).document(id)
        val fields = values.toFirestoreFields() + mapOf(
            FIELD_CREATED_AT to FieldValue.serverTimestamp(),
            FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
        )
        try {
            firestore.runTransaction { transaction ->
                if (transaction.get(ref).exists()) throw IngredientRepositoryException(IngredientFailure.AlreadyExists)
                transaction.set(ref, fields)
            }.await()
        } catch (error: FirebaseFirestoreException) {
            // Las reglas rechazan el alta si hay un ingrediente común con ese id.
            if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED && commonExists(id)) {
                throw IngredientRepositoryException(IngredientFailure.AlreadyExists, error)
            }
            throw error
        }
        id
    }

    override suspend fun update(id: String, name: String, category: String?, defaultUnit: String?) = runOperation {
        val uid = requireVerifiedUid()
        val values = validateIngredientInput(name, category, defaultUnit)
        val fields = values.toFirestoreFields() + (FIELD_UPDATED_AT to FieldValue.serverTimestamp())
        collection(uid).document(id).update(fields).await()
        Unit
    }

    override suspend fun delete(id: String) = runOperation {
        val uid = requireVerifiedUid()
        val brands = firestore.collection(USERS).document(uid).collection(BRANDS)
            .whereEqualTo(FIELD_INGREDIENT_ID, id)
            .get(Source.SERVER).await().documents
        val batch = firestore.batch()
        brands.forEach { batch.delete(it.reference) }
        batch.delete(collection(uid).document(id))
        val entries = mapOf(id to FieldValue.delete())
        batch.set(
            firestore.collection(INGREDIENT_PREFS_COLLECTION).document(uid),
            mapOf(FIELD_ENTRIES to entries),
            SetOptions.merge(),
        )
        batch.commit().await()
        Unit
    }

    private suspend fun commonExists(id: String): Boolean = try {
        firestore.collection(COMMON_INGREDIENTS).document(id).get(Source.SERVER).await().exists()
    } catch (_: FirebaseFirestoreException) {
        false
    }

    private fun collection(uid: String): CollectionReference =
        firestore.collection(USERS).document(uid).collection(INGREDIENTS)

    private fun requireVerifiedUid(): String {
        val user = authRepository.currentUser.value
            ?: throw IngredientRepositoryException(IngredientFailure.NotAuthenticated)
        if (!user.isEmailVerified) throw IngredientRepositoryException(IngredientFailure.EmailNotVerified)
        return user.id
    }

    private suspend inline fun <T> runOperation(crossinline operation: suspend () -> T): T = try {
        syncRepository.requireWritable()
        operation()
    } catch (error: IngredientRepositoryException) {
        if (error.failure == IngredientFailure.Network) syncRepository.reportWriteFailure(error.cause ?: error)
        throw error
    } catch (error: WriteNotAllowedException) {
        throw IngredientRepositoryException(IngredientFailure.Network, error)
    } catch (@Suppress("TooGenericExceptionCaught") error: Throwable) {
        syncRepository.reportWriteFailure(error)
        throw error.toIngredientRepositoryException()
    }

    private companion object {
        const val USERS = "users"
        const val INGREDIENTS = "ingredients"
        const val COMMON_INGREDIENTS = "ingredients"
        const val BRANDS = "brands"
        const val SCOPE = "ingredients:personal"
        const val FIELD_INGREDIENT_ID = "ingredientId"
        const val FIELD_ENTRIES = "entries"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val MAX_INGREDIENTS = 500L
    }
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
