package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.EventColor
import com.dlunaunizar.bobitos.data.sync.RealtimeMetrics
import com.dlunaunizar.bobitos.data.sync.SyncRepository
import com.dlunaunizar.bobitos.data.sync.WriteNotAllowedException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreCalendarRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val realtimeMetrics: RealtimeMetrics,
) : CalendarRepository {
    private val firestore = FirebaseFirestore.getInstance()

    override fun events(spaceId: String, rangeStart: Instant, rangeEndExclusive: Instant): Flow<List<CalendarEvent>> =
        callbackFlow {
            require(rangeStart < rangeEndExclusive)
            val metricId = realtimeMetrics.listenerStarted("calendar:visible-range")
            val registration = collection(spaceId)
                .whereLessThan(FIELD_START_AT, rangeEndExclusive.timestamp())
                .orderBy(FIELD_START_AT, Query.Direction.DESCENDING)
                .limit(MAX_VISIBLE_EVENTS)
                .addSnapshotListener { snapshot, error ->
                    when {
                        error != null -> close(error.asCalendarException())
                        snapshot != null -> {
                            realtimeMetrics.snapshotReceived(
                                "calendar:visible-range",
                                snapshot.documentChanges.size,
                                snapshot.metadata.isFromCache,
                            )
                            trySend(
                                snapshot.documents.mapNotNull(DocumentSnapshot::toCalendarEvent)
                                    .filter { it.overlaps(rangeStart, rangeEndExclusive) }.sortedBy { it.startAt },
                            )
                        }
                    }
                }
            awaitClose {
                registration.remove()
                realtimeMetrics.listenerStopped(metricId)
            }
        }

    override suspend fun createEvent(spaceId: String, input: EventInput) = operation {
        val user = verifiedUser()
        val value = input.validated()
        val space = firestore.collection(SPACES).document(spaceId)
        val event = collection(spaceId).document()
        val memberRefs = value.participantIds.map { membership(spaceId, it) }
        firestore.runTransaction { tx ->
            if (!tx.get(space).exists()) throw CalendarRepositoryException(CalendarFailure.SpaceNotFound)
            val members = memberRefs.map(tx::get)
            requireParticipants(value.participantIds, members)
            tx.set(event, value.data(user, members.map { it.getString(FIELD_DISPLAY_NAME).orEmpty() }))
        }.await()
        Unit
    }

    override suspend fun updateEvent(spaceId: String, eventId: String, input: EventInput) = operation {
        val user = verifiedUser()
        val value = input.validated()
        val event = collection(spaceId).document(eventId)
        val memberRefs = value.participantIds.map { membership(spaceId, it) }
        firestore.runTransaction { tx ->
            if (!tx.get(event).exists()) throw CalendarRepositoryException(CalendarFailure.EventNotFound)
            val members = memberRefs.map(tx::get)
            requireParticipants(value.participantIds, members)
            tx.update(event, value.updateData(user, members.map { it.getString(FIELD_DISPLAY_NAME).orEmpty() }))
        }.await()
        Unit
    }

    override suspend fun deleteEvent(spaceId: String, eventId: String) = operation {
        verifiedUser()
        val event = collection(spaceId).document(eventId)
        firestore.runTransaction { tx ->
            if (!tx.get(event).exists()) throw CalendarRepositoryException(CalendarFailure.EventNotFound)
            tx.delete(event)
        }.await()
        Unit
    }

    private fun collection(spaceId: String) = firestore.collection(SPACES).document(spaceId).collection(EVENTS)
    private fun membership(spaceId: String, userId: String) =
        firestore.collection(MEMBERSHIPS).document("${spaceId}_$userId")
    private fun requireParticipants(ids: List<String>, docs: List<DocumentSnapshot>) {
        if (docs.zip(ids).any { (doc, id) ->
                !doc.exists() ||
                    doc.getString(FIELD_USER_ID) != id ||
                    doc.getString(FIELD_STATUS) != ACTIVE
            }
        ) {
            throw CalendarRepositoryException(CalendarFailure.InvalidParticipants)
        }
    }
    private fun verifiedUser(): AuthUser {
        val user =
            authRepository.currentUser.value ?: throw CalendarRepositoryException(CalendarFailure.NotAuthenticated)
        if (!user.isEmailVerified) throw CalendarRepositoryException(CalendarFailure.EmailNotVerified)
        return user
    }
    private suspend fun <T> operation(block: suspend () -> T): T = try {
        syncRepository.requireWritable()
        block()
    } catch (e: CalendarRepositoryException) {
        throw e
    } catch (e: WriteNotAllowedException) {
        throw CalendarRepositoryException(CalendarFailure.Network, e)
    } catch (e: Throwable) {
        syncRepository.reportWriteFailure(e)
        throw e.asCalendarException()
    }

    private companion object {
        const val SPACES = "spaces"
        const val EVENTS = "events"
        const val MEMBERSHIPS = "memberships"
        const val FIELD_START_AT = "startAt"
        const val FIELD_USER_ID = "userId"
        const val FIELD_STATUS = "status"
        const val FIELD_DISPLAY_NAME = "displayName"
        const val ACTIVE = "ACTIVE"
        const val MAX_VISIBLE_EVENTS = 250L
    }
}

private fun EventInput.validated(): EventInput {
    val title = title.trim()
    val description = description?.trim()?.takeIf(String::isNotEmpty)
    val invalidAllDayRange =
        allDay && (startDate == null || endDateExclusive == null || startDate >= endDateExclusive)
    val invalidTimedDates = !allDay && (startDate != null || endDateExclusive != null)
    when {
        title.isEmpty() -> throw CalendarRepositoryException(CalendarFailure.TitleRequired)
        title.length > 120 -> throw CalendarRepositoryException(CalendarFailure.TitleTooLong)
        description != null && description.length > 1000 -> throw CalendarRepositoryException(
            CalendarFailure.DescriptionTooLong,
        )
        startAt >= endAt -> throw CalendarRepositoryException(CalendarFailure.InvalidRange)
        runCatching {
            ZoneId.of(timeZone)
        }.isFailure -> throw CalendarRepositoryException(CalendarFailure.InvalidTimeZone)
        participantIds.size > 10 ||
            participantIds.distinct().size != participantIds.size ||
            participantIds.any(
                String::isBlank,
            ) -> throw CalendarRepositoryException(CalendarFailure.InvalidParticipants)
        invalidAllDayRange -> throw CalendarRepositoryException(CalendarFailure.InvalidRange)
        invalidTimedDates -> throw CalendarRepositoryException(CalendarFailure.InvalidRange)
    }
    return copy(title = title, description = description)
}

private fun EventInput.common(names: List<String>) = mapOf(
    "title" to title, "description" to description, "allDay" to allDay,
    "startAt" to startAt.timestamp(), "endAt" to endAt.timestamp(),
    "startDate" to startDate?.toString(), "endDateExclusive" to endDateExclusive?.toString(),
    "timeZone" to timeZone, "color" to color.name, "participantIds" to participantIds,
    "participantNames" to names,
)
private fun EventInput.data(user: AuthUser, names: List<String>) = common(names) + mapOf(
    "createdBy" to user.id,
    "createdByName" to user.displayName.ifBlank { user.email.substringBefore('@') }.take(60),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedBy" to user.id,
    "updatedAt" to FieldValue.serverTimestamp(),
)
private fun EventInput.updateData(user: AuthUser, names: List<String>) = common(names) + mapOf(
    "updatedBy" to user.id,
    "updatedAt" to FieldValue.serverTimestamp(),
)
private fun Instant.timestamp() = Timestamp(Date.from(this))
private fun DocumentSnapshot.toCalendarEvent(): CalendarEvent? {
    val created = getTimestamp("createdAt")?.toDate()?.toInstant() ?: return null
    return CalendarEvent(
        id, getString("title") ?: return null, getString("description"),
        getBoolean("allDay") ?: return null,
        getTimestamp("startAt")?.toDate()?.toInstant() ?: return null,
        getTimestamp("endAt")?.toDate()?.toInstant() ?: return null,
        getString("startDate")?.let(LocalDate::parse), getString("endDateExclusive")?.let(LocalDate::parse),
        getString("timeZone") ?: return null,
        runCatching { EventColor.valueOf(getString("color").orEmpty()) }.getOrNull() ?: return null,
        (
            get(
                "participantIds",
            ) as? List<*>
            )?.filterIsInstance<String>().orEmpty(),
        (
            get(
                "participantNames",
            ) as? List<*>
            )?.filterIsInstance<String>().orEmpty(),
        getString("createdBy") ?: return null, getString("createdByName") ?: return null, created,
        getString("updatedBy") ?: return null, getTimestamp("updatedAt")?.toDate()?.toInstant() ?: created,
    )
}
private fun Throwable.asCalendarException() = CalendarRepositoryException(
    when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> CalendarFailure.PermissionDenied
        FirebaseFirestoreException.Code.NOT_FOUND -> CalendarFailure.EventNotFound
        FirebaseFirestoreException.Code.UNAVAILABLE -> CalendarFailure.Network
        else -> CalendarFailure.Unknown
    },
    this,
)
