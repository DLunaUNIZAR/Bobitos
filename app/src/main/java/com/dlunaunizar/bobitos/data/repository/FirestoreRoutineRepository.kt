package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.ExerciseSet
import com.dlunaunizar.bobitos.core.model.ExerciseType
import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import com.dlunaunizar.bobitos.core.model.RoutineVisibility
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
class FirestoreRoutineRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val realtimeMetrics: RealtimeMetrics,
) : RoutineRepository {
    private val firestore = FirebaseFirestore.getInstance()

    override fun globalRoutines(): Flow<List<Routine>> =
        routinesQuery("routines:global") { it.whereEqualTo(FIELD_VISIBILITY, RoutineVisibility.GLOBAL.name) }

    override fun myRoutines(): Flow<List<Routine>> {
        val uid = authRepository.currentUser.value?.id ?: return flowOf(emptyList())
        return routinesQuery("routines:mine") { it.whereEqualTo(FIELD_OWNER_UID, uid) }
    }

    override fun isCurrentUserRoutineAdmin(): Boolean =
        authRepository.currentUser.value?.id?.let(RecipeAdmins.uids::contains) == true

    override suspend fun createRoutine(
        visibility: RoutineVisibility,
        title: String,
        description: String?,
        exercises: List<RoutineExercise>,
    ) = runRoutineOperation {
        val user = requireVerifiedUser()
        val values = validate(title, description)
        routinesCollection().document().set(
            mapOf(
                FIELD_OWNER_UID to user.id,
                FIELD_VISIBILITY to visibility.name,
                FIELD_TITLE to values.title,
                FIELD_DESCRIPTION to values.description,
                FIELD_EXERCISES to exercises.toFirestore(),
                FIELD_CREATED_BY to user.id,
                FIELD_CREATED_BY_NAME to user.routineDisplayName,
                FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                FIELD_UPDATED_BY to user.id,
                FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
            ),
        ).await()
        Unit
    }

    override suspend fun updateRoutine(
        routineId: String,
        title: String,
        description: String?,
        exercises: List<RoutineExercise>,
    ) = runRoutineOperation {
        val user = requireVerifiedUser()
        val values = validate(title, description)
        routinesCollection().document(routineId).update(
            mapOf(
                FIELD_TITLE to values.title,
                FIELD_DESCRIPTION to values.description,
                FIELD_EXERCISES to exercises.toFirestore(),
                FIELD_UPDATED_BY to user.id,
                FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
            ),
        ).await()
        Unit
    }

    override suspend fun deleteRoutine(routineId: String) = runRoutineOperation {
        requireVerifiedUser()
        routinesCollection().document(routineId).delete().await()
        Unit
    }

    private fun routinesCollection() = firestore.collection(ROUTINES)

    private fun routinesQuery(scope: String, filter: (CollectionReference) -> Query): Flow<List<Routine>> =
        callbackFlow {
            val metricId = realtimeMetrics.listenerStarted(scope)
            val registration = filter(routinesCollection())
                .limit(MAX_VISIBLE_ROUTINES)
                .addSnapshotListener { snapshot, error ->
                    when {
                        error != null -> close(error.toRoutineRepositoryException())
                        snapshot != null -> {
                            realtimeMetrics.snapshotReceived(
                                scope,
                                snapshot.documentChanges.size,
                                snapshot.metadata.isFromCache,
                            )
                            trySend(
                                snapshot.documents
                                    .mapNotNull(DocumentSnapshot::toRoutine)
                                    .sortedWith(compareBy({ it.title.lowercase() }, Routine::id)),
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
            ?: throw RoutineRepositoryException(RoutineFailure.NotAuthenticated)
        if (!user.isEmailVerified) {
            throw RoutineRepositoryException(RoutineFailure.EmailNotVerified)
        }
        return user
    }

    private fun validate(title: String, description: String?): RoutineValues {
        val normalizedTitle = title.trim()
        val normalizedDescription = description?.trim()?.takeIf(String::isNotEmpty)
        when {
            normalizedTitle.isEmpty() -> throw RoutineRepositoryException(RoutineFailure.TitleRequired)
            normalizedTitle.length > MAX_TITLE_LENGTH -> throw RoutineRepositoryException(RoutineFailure.TitleTooLong)
            normalizedDescription != null && normalizedDescription.length > MAX_DESCRIPTION_LENGTH ->
                throw RoutineRepositoryException(RoutineFailure.DescriptionTooLong)
        }
        return RoutineValues(normalizedTitle, normalizedDescription)
    }

    // Serializa la lista (acotada) de ejercicios a la forma embebida de Firestore.
    private fun List<RoutineExercise>.toFirestore(): List<Map<String, Any?>> = take(MAX_EXERCISES).map { exercise ->
        mapOf(
            FIELD_EX_NAME to exercise.name,
            FIELD_EX_ID to exercise.exerciseId,
            FIELD_EX_TYPE to exercise.type.name,
            FIELD_EX_SETS to
                exercise.sets.take(MAX_SETS).map { mapOf(FIELD_SET_REPS to it.reps, FIELD_SET_WEIGHT to it.weight) },
            FIELD_EX_DURATION to exercise.durationMinutes,
            FIELD_EX_LEVEL to exercise.level,
            FIELD_EX_NOTES to exercise.notes,
        )
    }

    private suspend inline fun <T> runRoutineOperation(crossinline operation: suspend () -> T): T {
        try {
            syncRepository.requireWritable()
            return operation()
        } catch (error: RoutineRepositoryException) {
            if (error.failure == RoutineFailure.Network) {
                syncRepository.reportWriteFailure(error.cause ?: error)
            }
            throw error
        } catch (error: WriteNotAllowedException) {
            throw RoutineRepositoryException(RoutineFailure.Network, error)
        } catch (error: Throwable) {
            syncRepository.reportWriteFailure(error)
            throw error.toRoutineRepositoryException()
        }
    }

    private data class RoutineValues(val title: String, val description: String?)

    private companion object {
        const val ROUTINES = "routines"
        const val FIELD_OWNER_UID = "ownerUid"
        const val FIELD_VISIBILITY = "visibility"
        const val FIELD_TITLE = "title"
        const val FIELD_DESCRIPTION = "description"
        const val FIELD_EXERCISES = "exercises"
        const val FIELD_EX_NAME = "name"
        const val FIELD_EX_ID = "exerciseId"
        const val FIELD_EX_TYPE = "type"
        const val FIELD_EX_SETS = "sets"
        const val FIELD_SET_REPS = "reps"
        const val FIELD_SET_WEIGHT = "weight"
        const val FIELD_EX_DURATION = "durationMinutes"
        const val FIELD_EX_LEVEL = "level"
        const val FIELD_EX_NOTES = "notes"
        const val FIELD_CREATED_BY = "createdBy"
        const val FIELD_CREATED_BY_NAME = "createdByName"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_BY = "updatedBy"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val MAX_EXERCISES = 30
        const val MAX_SETS = 20
        const val MAX_TITLE_LENGTH = 120
        const val MAX_DESCRIPTION_LENGTH = 1000
        const val MAX_VISIBLE_ROUTINES = 250L
    }
}

private val AuthUser.routineDisplayName: String
    get() = displayName.ifBlank { email.substringBefore('@') }.take(60)

private fun DocumentSnapshot.toRoutine(): Routine? {
    val createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: return null
    val updatedAt = getTimestamp("updatedAt")?.toDate()?.toInstant() ?: createdAt
    val visibility = getString("visibility")?.let { value ->
        runCatching { RoutineVisibility.valueOf(value) }.getOrNull()
    } ?: return null
    return Routine(
        id = id,
        ownerUid = getString("ownerUid") ?: return null,
        visibility = visibility,
        title = getString("title") ?: return null,
        description = getString("description"),
        exercises = parseExercises(),
        createdBy = getString("createdBy") ?: return null,
        createdByName = getString("createdByName") ?: getString("createdBy") ?: return null,
        createdAt = createdAt,
        updatedBy = getString("updatedBy") ?: getString("createdBy") ?: return null,
        updatedAt = updatedAt,
    )
}

// Retro-compat: campo ausente → null; presente → lista parseada por elemento (descarta los malformados).
private fun DocumentSnapshot.parseExercises(): List<RoutineExercise>? {
    val raw = get("exercises") as? List<*> ?: return null
    return raw.mapNotNull { element ->
        val map = element as? Map<*, *> ?: return@mapNotNull null
        val name = (map["name"] as? String)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val type = (map["type"] as? String)?.let { runCatching { ExerciseType.valueOf(it) }.getOrNull() }
            ?: ExerciseType.OTROS
        RoutineExercise(
            name = name,
            exerciseId = (map["exerciseId"] as? String)?.takeIf(String::isNotBlank),
            type = type,
            sets = (map["sets"] as? List<*>).orEmpty().mapNotNull { setElement ->
                val setMap = setElement as? Map<*, *> ?: return@mapNotNull null
                ExerciseSet(
                    reps = (setMap["reps"] as? Number)?.toInt(),
                    weight = (setMap["weight"] as? Number)?.toDouble(),
                )
            },
            durationMinutes = (map["durationMinutes"] as? Number)?.toInt(),
            level = (map["level"] as? String)?.takeIf(String::isNotBlank),
            notes = (map["notes"] as? String)?.takeIf(String::isNotBlank),
        )
    }
}

private fun Throwable.toRoutineRepositoryException(): RoutineRepositoryException = RoutineRepositoryException(
    failure = when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> RoutineFailure.PermissionDenied
        FirebaseFirestoreException.Code.NOT_FOUND -> RoutineFailure.RoutineNotFound
        FirebaseFirestoreException.Code.UNAVAILABLE -> RoutineFailure.Network
        else -> RoutineFailure.Unknown
    },
    cause = this,
)
