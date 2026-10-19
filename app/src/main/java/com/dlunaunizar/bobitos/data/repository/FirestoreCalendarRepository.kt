package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.data.sync.RealtimeMetrics
import com.dlunaunizar.bobitos.data.sync.SyncRepository
import com.dlunaunizar.bobitos.data.sync.WriteNotAllowedException
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.ZoneId
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

    override suspend fun createEvent(spaceId: String, input: EventInput): String = operation {
        val event = collection(spaceId).document()
        writeNewEvent(spaceId, event, input, restoring = false)
        event.id
    }

    override suspend fun restoreEvent(spaceId: String, eventId: String, input: EventInput) = operation {
        writeNewEvent(spaceId, collection(spaceId).document(eventId), input, restoring = true)
    }

    // El alta y la restauración escriben igual: misma validación, misma transacción y mismo mapa de campos. Al
    // restaurar el evento vuelve con su id (lo que lo enlaza, como una actividad de Deporte, sigue apuntando a él) y
    // solo con los participantes que siguen activos.
    private suspend fun writeNewEvent(
        spaceId: String,
        event: DocumentReference,
        input: EventInput,
        restoring: Boolean,
    ) {
        val user = verifiedUser()
        val value = input.validated()
        val space = firestore.collection(SPACES).document(spaceId)
        val memberRefs = value.participantIds.map { membership(spaceId, it) }
        firestore.runTransaction { tx ->
            if (!tx.get(space).exists()) throw CalendarRepositoryException(CalendarFailure.SpaceNotFound)
            val records = memberRefs.map { tx.get(it).toMembershipRecord() }
            val active = if (restoring) {
                activeMembers(value.participantIds, records)
            } else {
                activeMembersOrThrow(value.participantIds, records) {
                    CalendarRepositoryException(CalendarFailure.InvalidParticipants)
                }
            }
            tx.set(
                event,
                value.copy(participantIds = active.map(ActiveMember::id)).data(user, active.map(ActiveMember::name)),
            )
        }.await()
    }

    // Deshacer un alta solo borra si nadie más ha tocado el evento desde entonces.
    override suspend fun undoCreateEvent(spaceId: String, eventId: String) = operation {
        val user = verifiedUser()
        val event = collection(spaceId).document(eventId)
        firestore.deleteIfLastWriter(
            event,
            user.id,
            notFound = { CalendarRepositoryException(CalendarFailure.EventNotFound) },
            changed = { CalendarRepositoryException(CalendarFailure.EventChanged) },
        )
    }

    override suspend fun updateEvent(spaceId: String, eventId: String, input: EventInput) = operation {
        val user = verifiedUser()
        val value = input.validated()
        val event = collection(spaceId).document(eventId)
        val memberRefs = value.participantIds.map { membership(spaceId, it) }
        firestore.runTransaction { tx ->
            if (!tx.get(event).exists()) throw CalendarRepositoryException(CalendarFailure.EventNotFound)
            val active = activeMembersOrThrow(
                value.participantIds,
                memberRefs.map { tx.get(it).toMembershipRecord() },
            ) { CalendarRepositoryException(CalendarFailure.InvalidParticipants) }
            tx.update(event, value.updateData(user, active.map(ActiveMember::name)))
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
        const val FIELD_UPDATED_BY = "updatedBy"
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

private fun Throwable.asCalendarException() = CalendarRepositoryException(
    when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> CalendarFailure.PermissionDenied
        FirebaseFirestoreException.Code.NOT_FOUND -> CalendarFailure.EventNotFound
        FirebaseFirestoreException.Code.UNAVAILABLE -> CalendarFailure.Network
        else -> CalendarFailure.Unknown
    },
    this,
)
