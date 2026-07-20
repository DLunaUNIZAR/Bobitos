package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.MealSlot
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
class FirestoreMealRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val realtimeMetrics: RealtimeMetrics,
) : MealRepository {
    private val firestore = FirebaseFirestore.getInstance()

    override fun meals(spaceId: String, weekStart: LocalDate, weekEndExclusive: LocalDate): Flow<List<Meal>> =
        callbackFlow {
            require(weekStart < weekEndExclusive)
            val metricId = realtimeMetrics.listenerStarted("meals:week")
            val registration = mealsCollection(spaceId)
                .whereGreaterThanOrEqualTo(FIELD_DATE, weekStart.toString())
                .whereLessThan(FIELD_DATE, weekEndExclusive.toString())
                .orderBy(FIELD_DATE, Query.Direction.ASCENDING)
                .limit(MAX_VISIBLE_MEALS)
                .addSnapshotListener { snapshot, error ->
                    when {
                        error != null -> close(error.toMealRepositoryException())
                        snapshot != null -> {
                            realtimeMetrics.snapshotReceived(
                                scope = "meals:week",
                                changedDocuments = snapshot.documentChanges.size,
                                fromCache = snapshot.metadata.isFromCache,
                            )
                            trySend(
                                snapshot.documents
                                    .mapNotNull(DocumentSnapshot::toMeal)
                                    .sortedWith(
                                        compareBy<Meal> { it.date }
                                            .thenBy { it.slot.ordinal }
                                            .thenBy(Meal::createdAt)
                                            .thenBy(Meal::id),
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

    override suspend fun addMeal(
        spaceId: String,
        date: LocalDate,
        slot: MealSlot,
        name: String,
        participantIds: List<String>,
    ) = runMealOperation {
        val user = requireVerifiedUser()
        val normalizedName = validate(name, participantIds)
        val spaceReference = firestore.collection(SPACES).document(spaceId)
        val mealReference = mealsCollection(spaceId).document()
        val memberReferences = participantIds.map { membership(spaceId, it) }

        firestore.runTransaction { transaction ->
            if (!transaction.get(spaceReference).exists()) {
                throw MealRepositoryException(MealFailure.SpaceNotFound)
            }
            val members = memberReferences.map(transaction::get)
            requireParticipants(participantIds, members)
            transaction.set(
                mealReference,
                mealData(user, date, slot, normalizedName, participantIds, members.displayNames()),
            )
        }.await()
        Unit
    }

    override suspend fun updateMeal(
        spaceId: String,
        mealId: String,
        date: LocalDate,
        slot: MealSlot,
        name: String,
        participantIds: List<String>,
    ) = runMealOperation {
        val user = requireVerifiedUser()
        val normalizedName = validate(name, participantIds)
        val reference = mealsCollection(spaceId).document(mealId)
        val memberReferences = participantIds.map { membership(spaceId, it) }

        firestore.runTransaction { transaction ->
            requireMeal(transaction.get(reference))
            val members = memberReferences.map(transaction::get)
            requireParticipants(participantIds, members)
            transaction.update(
                reference,
                mealUpdateData(user, date, slot, normalizedName, participantIds, members.displayNames()),
            )
        }.await()
        Unit
    }

    override suspend fun deleteMeal(spaceId: String, mealId: String) = runMealOperation {
        requireVerifiedUser()
        val reference = mealsCollection(spaceId).document(mealId)
        firestore.runTransaction { transaction ->
            requireMeal(transaction.get(reference))
            transaction.delete(reference)
        }.await()
        Unit
    }

    private fun mealsCollection(spaceId: String) = firestore
        .collection(SPACES)
        .document(spaceId)
        .collection(MEALS)

    private fun membership(spaceId: String, userId: String) =
        firestore.collection(MEMBERSHIPS).document("${spaceId}_$userId")

    private fun requireVerifiedUser(): AuthUser {
        val user = authRepository.currentUser.value
            ?: throw MealRepositoryException(MealFailure.NotAuthenticated)
        if (!user.isEmailVerified) {
            throw MealRepositoryException(MealFailure.EmailNotVerified)
        }
        return user
    }

    private fun validate(name: String, participantIds: List<String>): String {
        val normalizedName = name.trim()
        when {
            normalizedName.isEmpty() -> throw MealRepositoryException(MealFailure.NameRequired)
            normalizedName.length > MAX_NAME_LENGTH -> throw MealRepositoryException(MealFailure.NameTooLong)
            participantIds.size > MAX_PARTICIPANTS ||
                participantIds.distinct().size != participantIds.size ||
                participantIds.any(String::isBlank) ->
                throw MealRepositoryException(MealFailure.InvalidParticipants)
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
            throw MealRepositoryException(MealFailure.InvalidParticipants)
        }
    }

    private fun requireMeal(snapshot: DocumentSnapshot): DocumentSnapshot {
        if (!snapshot.exists()) {
            throw MealRepositoryException(MealFailure.MealNotFound)
        }
        return snapshot
    }

    private fun List<DocumentSnapshot>.displayNames(): List<String> = map { it.getString(FIELD_DISPLAY_NAME).orEmpty() }

    private fun mealData(
        user: AuthUser,
        date: LocalDate,
        slot: MealSlot,
        name: String,
        participantIds: List<String>,
        participantNames: List<String>,
    ) = commonData(date, slot, name, participantIds, participantNames) + mapOf(
        FIELD_CREATED_BY to user.id,
        FIELD_CREATED_BY_NAME to user.mealDisplayName,
        FIELD_CREATED_AT to FieldValue.serverTimestamp(),
        FIELD_UPDATED_BY to user.id,
        FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
    )

    private fun mealUpdateData(
        user: AuthUser,
        date: LocalDate,
        slot: MealSlot,
        name: String,
        participantIds: List<String>,
        participantNames: List<String>,
    ) = commonData(date, slot, name, participantIds, participantNames) + mapOf(
        FIELD_UPDATED_BY to user.id,
        FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
    )

    private fun commonData(
        date: LocalDate,
        slot: MealSlot,
        name: String,
        participantIds: List<String>,
        participantNames: List<String>,
    ) = mapOf(
        FIELD_DATE to date.toString(),
        FIELD_SLOT to slot.name,
        FIELD_NAME to name,
        FIELD_PARTICIPANT_IDS to participantIds,
        FIELD_PARTICIPANT_NAMES to participantNames,
    )

    private suspend inline fun <T> runMealOperation(crossinline operation: suspend () -> T): T {
        try {
            syncRepository.requireWritable()
            return operation()
        } catch (error: MealRepositoryException) {
            if (error.failure == MealFailure.Network) {
                syncRepository.reportWriteFailure(error.cause ?: error)
            }
            throw error
        } catch (error: WriteNotAllowedException) {
            throw MealRepositoryException(MealFailure.Network, error)
        } catch (error: Throwable) {
            syncRepository.reportWriteFailure(error)
            throw error.toMealRepositoryException()
        }
    }

    private companion object {
        const val SPACES = "spaces"
        const val MEALS = "meals"
        const val MEMBERSHIPS = "memberships"
        const val FIELD_DATE = "date"
        const val FIELD_SLOT = "slot"
        const val FIELD_NAME = "name"
        const val FIELD_PARTICIPANT_IDS = "participantIds"
        const val FIELD_PARTICIPANT_NAMES = "participantNames"
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
        const val MAX_VISIBLE_MEALS = 250L
    }
}

private val AuthUser.mealDisplayName: String
    get() = displayName.ifBlank { email.substringBefore('@') }.take(60)

private fun DocumentSnapshot.toMeal(): Meal? {
    val createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: return null
    val updatedAt = getTimestamp("updatedAt")?.toDate()?.toInstant() ?: createdAt
    val date = getString("date")?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() } ?: return null
    val slot = getString("slot")?.let { value -> runCatching { MealSlot.valueOf(value) }.getOrNull() } ?: return null
    return Meal(
        id = id,
        date = date,
        slot = slot,
        name = getString("name") ?: return null,
        participantIds = (get("participantIds") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        participantNames = (get("participantNames") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        createdBy = getString("createdBy") ?: return null,
        createdByName = getString("createdByName") ?: getString("createdBy") ?: return null,
        createdAt = createdAt,
        updatedBy = getString("updatedBy") ?: getString("createdBy") ?: return null,
        updatedAt = updatedAt,
    )
}

private fun Throwable.toMealRepositoryException(): MealRepositoryException = MealRepositoryException(
    failure = when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> MealFailure.PermissionDenied
        FirebaseFirestoreException.Code.NOT_FOUND -> MealFailure.MealNotFound
        FirebaseFirestoreException.Code.UNAVAILABLE -> MealFailure.Network
        else -> MealFailure.Unknown
    },
    cause = this,
)
