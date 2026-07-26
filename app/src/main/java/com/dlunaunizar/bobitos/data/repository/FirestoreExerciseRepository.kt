package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseType
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
                                .sortedWith(compareBy({ it.name.lowercase() }, CatalogExercise::id)),
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

    override suspend fun createExercise(name: String, type: ExerciseType, muscleGroup: String?) = runOperation {
        val user = requireVerifiedUser()
        val values = validate(name, muscleGroup)
        val id = slug(values.name).ifEmpty { throw ExerciseRepositoryException(ExerciseFailure.NameRequired) }
        exercisesCollection().document(id).set(
            mapOf(
                FIELD_NAME to values.name,
                FIELD_NAME_LOWER to values.name.lowercase(),
                FIELD_TYPE to type.name,
                FIELD_MUSCLE_GROUP to values.muscleGroup,
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

    override suspend fun updateExercise(id: String, name: String, type: ExerciseType, muscleGroup: String?) =
        runOperation {
            val user = requireVerifiedUser()
            val values = validate(name, muscleGroup)
            exercisesCollection().document(id).update(
                mapOf(
                    FIELD_NAME to values.name,
                    FIELD_NAME_LOWER to values.name.lowercase(),
                    FIELD_TYPE to type.name,
                    FIELD_MUSCLE_GROUP to values.muscleGroup,
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

    private fun validate(name: String, muscleGroup: String?): ExerciseValues {
        val normalizedName = name.trim()
        val normalizedMuscle = muscleGroup?.trim()?.takeIf(String::isNotEmpty)
        when {
            normalizedName.isEmpty() -> throw ExerciseRepositoryException(ExerciseFailure.NameRequired)
            normalizedName.length > MAX_NAME_LENGTH -> throw ExerciseRepositoryException(ExerciseFailure.NameTooLong)
            normalizedMuscle != null && normalizedMuscle.length > MAX_MUSCLE_LENGTH ->
                throw ExerciseRepositoryException(ExerciseFailure.MuscleGroupTooLong)
        }
        return ExerciseValues(normalizedName, normalizedMuscle)
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

    private data class ExerciseValues(val name: String, val muscleGroup: String?)

    private companion object {
        const val EXERCISES = "exercises"
        const val SCOPE = "exercises:catalog"
        const val FIELD_NAME = "name"
        const val FIELD_NAME_LOWER = "nameLower"
        const val FIELD_TYPE = "type"
        const val FIELD_MUSCLE_GROUP = "muscleGroup"
        const val FIELD_OWNER_UID = "ownerUid"
        const val FIELD_CREATED_BY = "createdBy"
        const val FIELD_CREATED_BY_NAME = "createdByName"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_BY = "updatedBy"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val MAX_NAME_LENGTH = 120
        const val MAX_MUSCLE_LENGTH = 60
        const val MAX_VISIBLE_EXERCISES = 500L
    }
}

private val AuthUser.catalogDisplayName: String
    get() = displayName.ifBlank { email.substringBefore('@') }.take(60)

private fun DocumentSnapshot.toCatalogExercise(): CatalogExercise? {
    val createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: return null
    val updatedAt = getTimestamp("updatedAt")?.toDate()?.toInstant() ?: createdAt
    val type =
        getString("type")?.let { value -> runCatching { ExerciseType.valueOf(value) }.getOrNull() } ?: return null
    return CatalogExercise(
        id = id,
        name = getString("name") ?: return null,
        type = type,
        muscleGroup = getString("muscleGroup"),
        ownerUid = getString("ownerUid") ?: return null,
        createdBy = getString("createdBy") ?: return null,
        createdByName = getString("createdByName") ?: getString("createdBy") ?: return null,
        createdAt = createdAt,
        updatedBy = getString("updatedBy") ?: getString("createdBy") ?: return null,
        updatedAt = updatedAt,
    )
}

private fun Throwable.toExerciseRepositoryException(): ExerciseRepositoryException = ExerciseRepositoryException(
    failure = when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> ExerciseFailure.PermissionDenied
        FirebaseFirestoreException.Code.NOT_FOUND -> ExerciseFailure.ExerciseNotFound
        FirebaseFirestoreException.Code.UNAVAILABLE -> ExerciseFailure.Network
        else -> ExerciseFailure.Unknown
    },
    cause = this,
)
