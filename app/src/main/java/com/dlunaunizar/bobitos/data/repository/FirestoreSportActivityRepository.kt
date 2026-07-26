package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import com.dlunaunizar.bobitos.core.model.SportActivity
import com.dlunaunizar.bobitos.core.model.SportType
import com.dlunaunizar.bobitos.data.sync.RealtimeMetrics
import com.dlunaunizar.bobitos.data.sync.SyncRepository
import com.dlunaunizar.bobitos.data.sync.WriteNotAllowedException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreSportActivityRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val realtimeMetrics: RealtimeMetrics,
) : SportActivityRepository {
    private val firestore = FirebaseFirestore.getInstance()

    override fun activities(
        spaceId: String,
        weekStart: LocalDate,
        weekEndExclusive: LocalDate,
    ): Flow<List<SportActivity>> = callbackFlow {
        require(weekStart < weekEndExclusive)
        val metricId = realtimeMetrics.listenerStarted("activities:week")
        val registration = activitiesCollection(spaceId)
            .whereGreaterThanOrEqualTo(FIELD_DATE, weekStart.toString())
            .whereLessThan(FIELD_DATE, weekEndExclusive.toString())
            .orderBy(FIELD_DATE, Query.Direction.ASCENDING)
            .limit(MAX_VISIBLE_ACTIVITIES)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(error.toSportRepositoryException())
                    snapshot != null -> {
                        realtimeMetrics.snapshotReceived(
                            scope = "activities:week",
                            changedDocuments = snapshot.documentChanges.size,
                            fromCache = snapshot.metadata.isFromCache,
                        )
                        trySend(
                            snapshot.documents
                                .mapNotNull(DocumentSnapshot::toSportActivity)
                                .sortedWith(
                                    compareBy<SportActivity> { it.date }
                                        .thenBy(SportActivity::createdAt)
                                        .thenBy(SportActivity::id),
                                ),
                        )
                    }
                }
            }
        awaitClose {
            registration.remove()
            realtimeMetrics.listenerStopped(metricId)
        }
    }

    override suspend fun addActivity(
        spaceId: String,
        date: LocalDate,
        type: SportType,
        name: String,
        participantIds: List<String>,
        routineId: String?,
        session: List<RoutineExercise>,
    ) = runActivityOperation {
        val user = requireVerifiedUser()
        val normalizedName = validate(name, participantIds)
        val gym = GymSession(type, routineId, session)
        val spaceReference = firestore.collection(SPACES).document(spaceId)
        val activityReference = activitiesCollection(spaceId).document()
        val memberReferences = participantIds.map { membership(spaceId, it) }

        firestore.runTransaction { transaction ->
            if (!transaction.get(spaceReference).exists()) {
                throw SportRepositoryException(SportFailure.SpaceNotFound)
            }
            val members = memberReferences.map(transaction::get)
            requireParticipants(participantIds, members)
            transaction.set(
                activityReference,
                activityData(user, date, type, normalizedName, participantIds, members.displayNames(), gym),
            )
        }.await()
        Unit
    }

    override suspend fun updateActivity(
        spaceId: String,
        activityId: String,
        date: LocalDate,
        type: SportType,
        name: String,
        participantIds: List<String>,
        routineId: String?,
        session: List<RoutineExercise>,
    ) = runActivityOperation {
        val user = requireVerifiedUser()
        val normalizedName = validate(name, participantIds)
        val gym = GymSession(type, routineId, session)
        val reference = activitiesCollection(spaceId).document(activityId)
        val memberReferences = participantIds.map { membership(spaceId, it) }

        firestore.runTransaction { transaction ->
            requireActivity(transaction.get(reference))
            val members = memberReferences.map(transaction::get)
            requireParticipants(participantIds, members)
            transaction.update(
                reference,
                activityUpdateData(user, date, type, normalizedName, participantIds, members.displayNames(), gym),
            )
        }.await()
        Unit
    }

    override suspend fun setDone(spaceId: String, activityId: String, done: Boolean) = runActivityOperation {
        val user = requireVerifiedUser()
        val reference = activitiesCollection(spaceId).document(activityId)
        firestore.runTransaction { transaction ->
            requireActivity(transaction.get(reference))
            transaction.update(
                reference,
                mapOf(
                    FIELD_DONE to done,
                    FIELD_UPDATED_BY to user.id,
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                ),
            )
        }.await()
        Unit
    }

    override suspend fun deleteActivity(spaceId: String, activityId: String) = runActivityOperation {
        requireVerifiedUser()
        val reference = activitiesCollection(spaceId).document(activityId)
        firestore.runTransaction { transaction ->
            requireActivity(transaction.get(reference))
            transaction.delete(reference)
        }.await()
        Unit
    }

    private fun activitiesCollection(spaceId: String) = firestore
        .collection(SPACES)
        .document(spaceId)
        .collection(ACTIVITIES)

    private fun membership(spaceId: String, userId: String) =
        firestore.collection(MEMBERSHIPS).document("${spaceId}_$userId")

    private fun requireVerifiedUser(): AuthUser {
        val user = authRepository.currentUser.value
            ?: throw SportRepositoryException(SportFailure.NotAuthenticated)
        if (!user.isEmailVerified) {
            throw SportRepositoryException(SportFailure.EmailNotVerified)
        }
        return user
    }

    private fun validate(name: String, participantIds: List<String>): String {
        val normalizedName = name.trim()
        when {
            normalizedName.isEmpty() -> throw SportRepositoryException(SportFailure.NameRequired)
            normalizedName.length > MAX_NAME_LENGTH -> throw SportRepositoryException(SportFailure.NameTooLong)
            participantIds.size > MAX_PARTICIPANTS ||
                participantIds.distinct().size != participantIds.size ||
                participantIds.any(String::isBlank) ->
                throw SportRepositoryException(SportFailure.InvalidParticipants)
        }
        return normalizedName
    }

    private fun requireParticipants(ids: List<String>, docs: List<DocumentSnapshot>) {
        if (docs.zip(ids).any { (doc, id) ->
                !doc.exists() ||
                    doc.getString(FIELD_USER_ID) != id ||
                    doc.getString(FIELD_STATUS) != ACTIVE
            }
        ) {
            throw SportRepositoryException(SportFailure.InvalidParticipants)
        }
    }

    private fun requireActivity(snapshot: DocumentSnapshot): DocumentSnapshot {
        if (!snapshot.exists()) {
            throw SportRepositoryException(SportFailure.ActivityNotFound)
        }
        return snapshot
    }

    private fun List<DocumentSnapshot>.displayNames(): List<String> = map { it.getString(FIELD_DISPLAY_NAME).orEmpty() }

    // La rutina y la sesión solo tienen sentido en GIMNASIO: para el resto se fuerzan a vacío para
    // que ningún tipo arrastre datos de sesión ajenos.
    private class GymSession(type: SportType, routineId: String?, session: List<RoutineExercise>) {
        val routineId: String? = routineId?.takeIf { type == SportType.GIMNASIO }
        val session: List<RoutineExercise> = if (type == SportType.GIMNASIO) session else emptyList()
    }

    private fun activityData(
        user: AuthUser,
        date: LocalDate,
        type: SportType,
        name: String,
        participantIds: List<String>,
        participantNames: List<String>,
        gym: GymSession,
    ) = commonData(date, type, name, participantIds, participantNames, gym) + mapOf(
        FIELD_DONE to false,
        FIELD_CREATED_BY to user.id,
        FIELD_CREATED_BY_NAME to user.sportDisplayName,
        FIELD_CREATED_AT to FieldValue.serverTimestamp(),
        FIELD_UPDATED_BY to user.id,
        FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
    )

    private fun activityUpdateData(
        user: AuthUser,
        date: LocalDate,
        type: SportType,
        name: String,
        participantIds: List<String>,
        participantNames: List<String>,
        gym: GymSession,
    ) = commonData(date, type, name, participantIds, participantNames, gym) + mapOf(
        FIELD_UPDATED_BY to user.id,
        FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
    )

    private fun commonData(
        date: LocalDate,
        type: SportType,
        name: String,
        participantIds: List<String>,
        participantNames: List<String>,
        gym: GymSession,
    ) = mapOf(
        FIELD_DATE to date.toString(),
        FIELD_TYPE to type.name,
        FIELD_NAME to name,
        FIELD_PARTICIPANT_IDS to participantIds,
        FIELD_PARTICIPANT_NAMES to participantNames,
        FIELD_ROUTINE_ID to gym.routineId,
        FIELD_SESSION to gym.session.toFirestoreExercises(),
    )

    private suspend inline fun <T> runActivityOperation(crossinline operation: suspend () -> T): T {
        try {
            syncRepository.requireWritable()
            return operation()
        } catch (error: SportRepositoryException) {
            if (error.failure == SportFailure.Network) {
                syncRepository.reportWriteFailure(error.cause ?: error)
            }
            throw error
        } catch (error: WriteNotAllowedException) {
            throw SportRepositoryException(SportFailure.Network, error)
        } catch (error: Throwable) {
            syncRepository.reportWriteFailure(error)
            throw error.toSportRepositoryException()
        }
    }

    private companion object {
        const val SPACES = "spaces"
        const val ACTIVITIES = "activities"
        const val MEMBERSHIPS = "memberships"
        const val FIELD_DATE = "date"
        const val FIELD_TYPE = "type"
        const val FIELD_NAME = "name"
        const val FIELD_PARTICIPANT_IDS = "participantIds"
        const val FIELD_PARTICIPANT_NAMES = "participantNames"
        const val FIELD_ROUTINE_ID = "routineId"
        const val FIELD_SESSION = "session"
        const val FIELD_DONE = "done"
        const val FIELD_CREATED_BY = "createdBy"
        const val FIELD_CREATED_BY_NAME = "createdByName"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_BY = "updatedBy"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_USER_ID = "userId"
        const val FIELD_STATUS = "status"
        const val FIELD_DISPLAY_NAME = "displayName"
        const val ACTIVE = "ACTIVE"
        const val MAX_NAME_LENGTH = 120
        const val MAX_PARTICIPANTS = 10
        const val MAX_VISIBLE_ACTIVITIES = 250L
    }
}

private val AuthUser.sportDisplayName: String
    get() = displayName.ifBlank { email.substringBefore('@') }.take(60)

private fun DocumentSnapshot.toSportActivity(): SportActivity? {
    val createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: return null
    val updatedAt = getTimestamp("updatedAt")?.toDate()?.toInstant() ?: createdAt
    val date = getString("date")?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() } ?: return null
    val type = getString("type")?.let { value -> runCatching { SportType.valueOf(value) }.getOrNull() } ?: return null
    return SportActivity(
        id = id,
        date = date,
        type = type,
        name = getString("name") ?: return null,
        participantIds = (get("participantIds") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        participantNames = (get("participantNames") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        done = getBoolean("done") == true,
        routineId = getString("routineId"),
        session = parseRoutineExercises(get("session")).orEmpty(),
        createdBy = getString("createdBy") ?: return null,
        createdByName = getString("createdByName") ?: getString("createdBy") ?: return null,
        createdAt = createdAt,
        updatedBy = getString("updatedBy") ?: getString("createdBy") ?: return null,
        updatedAt = updatedAt,
    )
}

private fun Throwable.toSportRepositoryException(): SportRepositoryException = SportRepositoryException(
    failure = when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> SportFailure.PermissionDenied
        FirebaseFirestoreException.Code.NOT_FOUND -> SportFailure.ActivityNotFound
        FirebaseFirestoreException.Code.UNAVAILABLE -> SportFailure.Network
        else -> SportFailure.Unknown
    },
    cause = this,
)
