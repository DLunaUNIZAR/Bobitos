package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseInput
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
class FirestoreExerciseRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val realtimeMetrics: RealtimeMetrics,
) : ExerciseRepository {
    private val firestore = FirebaseFirestore.getInstance()

    override fun catalog(): Flow<List<CatalogExercise>> = callbackFlow {
        val metricId = realtimeMetrics.listenerStarted(SCOPE)
        val registration = exercisesCollection()
            .orderBy(FIELD_NAME_LOWER)
            .limit(MAX_VISIBLE_EXERCISES)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(error.toExerciseRepositoryException())
                    snapshot != null -> {
                        realtimeMetrics.snapshotReceived(
                            SCOPE,
                            snapshot.documentChanges.size,
                            snapshot.metadata.isFromCache,
                        )
                        trySend(
                            snapshot.documents
                                .mapNotNull(DocumentSnapshot::toCatalogExercise)
                                .sortedForCatalog(),
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

    override suspend fun exerciseById(id: String): CatalogExercise? =
        runCatching { exercisesCollection().document(id).get().await().toCatalogExercise() }.getOrNull()

    override suspend fun createExercise(input: ExerciseInput) = runOperation {
        val user = requireVerifiedUser()
        val values = validateExerciseInput(input)
        val id = slug(values.name).ifEmpty { throw ExerciseRepositoryException(ExerciseFailure.NameRequired) }
        exercisesCollection().document(id).set(
            values.toFirestoreFields() + mapOf(
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

    override suspend fun updateExercise(id: String, input: ExerciseInput) = runOperation {
        val user = requireVerifiedUser()
        val values = validateExerciseInput(input)
        exercisesCollection().document(id).update(
            values.toFirestoreFields() + mapOf(
                FIELD_UPDATED_BY to user.id,
                FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
            ),
        ).await()
        Unit
    }

    override suspend fun deleteExercise(id: String) = runOperation {
        requireVerifiedUser()
        exercisesCollection().document(id).delete().await()
        Unit
    }

    private fun exercisesCollection() = firestore.collection(EXERCISES)

    private fun requireVerifiedUser(): AuthUser {
        val user = authRepository.currentUser.value
            ?: throw ExerciseRepositoryException(ExerciseFailure.NotAuthenticated)
        if (!user.isEmailVerified) {
            throw ExerciseRepositoryException(ExerciseFailure.EmailNotVerified)
        }
        return user
    }

    private suspend inline fun <T> runOperation(crossinline operation: suspend () -> T): T {
        try {
            syncRepository.requireWritable()
            return operation()
        } catch (error: ExerciseRepositoryException) {
            if (error.failure == ExerciseFailure.Network) {
                syncRepository.reportWriteFailure(error.cause ?: error)
            }
            throw error
        } catch (error: WriteNotAllowedException) {
            throw ExerciseRepositoryException(ExerciseFailure.Network, error)
        } catch (error: Throwable) {
            syncRepository.reportWriteFailure(error)
            throw error.toExerciseRepositoryException()
        }
    }

    private companion object {
        const val EXERCISES = "exercises"
        const val SCOPE = "exercises:catalog"
        const val FIELD_NAME_LOWER = "nameLower"
        const val FIELD_OWNER_UID = "ownerUid"
        const val FIELD_CREATED_BY = "createdBy"
        const val FIELD_CREATED_BY_NAME = "createdByName"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_BY = "updatedBy"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val MAX_VISIBLE_EXERCISES = 1000L
    }
}

private val AuthUser.catalogDisplayName: String
    get() = displayName.ifBlank { email.substringBefore('@') }.take(60)

private fun DocumentSnapshot.toCatalogExercise(): CatalogExercise? = parseCatalogExercise(
    id = id,
    data = data.orEmpty(),
    createdAt = getTimestamp("createdAt")?.toDate()?.toInstant(),
    updatedAt = getTimestamp("updatedAt")?.toDate()?.toInstant(),
)

private fun Throwable.toExerciseRepositoryException(): ExerciseRepositoryException = ExerciseRepositoryException(
    failure = when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> ExerciseFailure.PermissionDenied
        FirebaseFirestoreException.Code.NOT_FOUND -> ExerciseFailure.ExerciseNotFound
        FirebaseFirestoreException.Code.UNAVAILABLE -> ExerciseFailure.Network
        else -> ExerciseFailure.Unknown
    },
    cause = this,
)
