package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.EventColor
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import com.dlunaunizar.bobitos.core.model.SportActivity
import com.dlunaunizar.bobitos.core.model.SportType
import com.dlunaunizar.bobitos.data.sync.RealtimeMetrics
import com.dlunaunizar.bobitos.data.sync.SyncRepository
import com.dlunaunizar.bobitos.data.sync.WriteNotAllowedException
import com.google.firebase.firestore.DocumentReference
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
import java.time.ZoneId
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
    ): String = runActivityOperation {
        val user = requireVerifiedUser()
        val draft = ActivityDraft(date, type, validate(name, participantIds), participantIds, routineId, session)
        val activityReference = activitiesCollection(spaceId).document()
        writeNewActivity(spaceId, user, activityReference, draft, restore = null)
        activityReference.id
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
            val snapshot = requireActivity(transaction.get(reference))
            // Reutiliza el evento enlazado. Solo una actividad previa a esta función (sin eventId) crea
            // uno nuevo: si el evento enlazado ya no existe es que se borró a propósito desde el
            // calendario y no se recrea. Todas las lecturas antes de escribir.
            val existingEventId = snapshot.getString(FIELD_EVENT_ID)
            val eventReference = existingEventId
                ?.let { eventsCollection(spaceId).document(it) }
                ?: eventsCollection(spaceId).document()
            val eventSnapshot = existingEventId?.let { transaction.get(eventReference) }
            val active = activeMembersOrThrow(
                participantIds,
                memberReferences.map { transaction.get(it).toMembershipRecord() },
            ) { SportRepositoryException(SportFailure.InvalidParticipants) }
            val names = active.map(ActiveMember::name)
            // El organizador del evento es siempre el creador de la actividad, no quien edita.
            val organizerId = snapshot.getString(FIELD_CREATED_BY) ?: user.id
            val organizerName = snapshot.getString(FIELD_CREATED_BY_NAME) ?: user.sportDisplayName
            transaction.update(
                reference,
                activityUpdateData(user, date, type, normalizedName, participantIds, names, gym, eventReference.id),
            )
            when {
                eventSnapshot == null -> transaction.set(
                    eventReference,
                    newSportEventData(user, organizerId, organizerName, date, normalizedName, participantIds, names),
                )
                eventSnapshot.exists() -> transaction.update(
                    eventReference,
                    sportEventUpdateData(
                        user,
                        eventSnapshot,
                        snapshot.getString(FIELD_DATE),
                        date,
                        normalizedName,
                        eventParticipants(organizerId, organizerName, participantIds, names),
                    ),
                )
                else -> Unit
            }
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

    override suspend fun deleteActivity(spaceId: String, activityId: String): CalendarEvent? = runActivityOperation {
        requireVerifiedUser()
        val reference = activitiesCollection(spaceId).document(activityId)
        firestore.runTransaction { transaction ->
            val snapshot = requireActivity(transaction.get(reference))
            val eventReference = snapshot.getString(FIELD_EVENT_ID)?.let { eventsCollection(spaceId).document(it) }
            // Se resuelve la existencia del evento antes de escribir (Firestore exige leer antes de escribir).
            val eventSnapshot = eventReference?.let(transaction::get)?.takeIf { it.exists() }
            transaction.delete(reference)
            eventSnapshot?.let { transaction.delete(it.reference) }
            // La foto del evento tal como estaba al borrar, para que «Deshacer» lo devuelva igual (null si no se lee).
            eventSnapshot?.toCalendarEvent()
        }.await()
    }

    override suspend fun restoreActivity(spaceId: String, activity: SportActivity, event: CalendarEvent?) =
        runActivityOperation {
            val user = requireVerifiedUser()
            val draft = ActivityDraft(
                activity.date,
                activity.type,
                validate(activity.name, activity.participantIds),
                activity.participantIds,
                activity.routineId,
                activity.session,
            )
            writeNewActivity(
                spaceId,
                user,
                activitiesCollection(spaceId).document(activity.id),
                draft,
                restore = Restore(activity.done, event),
            )
        }

    // El alta y la restauración escriben igual: misma validación, misma transacción y mismo mapa de campos. Al dar de
    // alta, un participante que no es miembro activo es un error. Al restaurar se quita (de la actividad y de su
    // evento), la actividad vuelve ya hecha si lo estaba y enlazada solo a un evento que vuelve con ella.
    private suspend fun writeNewActivity(
        spaceId: String,
        user: AuthUser,
        activityReference: DocumentReference,
        draft: ActivityDraft,
        restore: Restore?,
    ) {
        val gym = GymSession(draft.type, draft.routineId, draft.session)
        val spaceReference = firestore.collection(SPACES).document(spaceId)
        val eventParticipantIds = restore?.event?.participantIds.orEmpty()
        val checkedIds = (draft.participantIds + eventParticipantIds).distinct()
        val memberReferences = checkedIds.map { membership(spaceId, it) }
        val restoredEvent = restore?.event
        val newEvent = if (restore == null) eventsCollection(spaceId).document() else null

        firestore.runTransaction { transaction ->
            if (!transaction.get(spaceReference).exists()) {
                throw SportRepositoryException(SportFailure.SpaceNotFound)
            }
            val records = checkedIds.zip(memberReferences.map { transaction.get(it).toMembershipRecord() }).toMap()
            val participantRecords = draft.participantIds.map(records::get)
            val active = if (restore == null) {
                activeMembersOrThrow(draft.participantIds, participantRecords) {
                    SportRepositoryException(SportFailure.InvalidParticipants)
                }
            } else {
                activeMembers(draft.participantIds, participantRecords)
            }
            val ids = active.map(ActiveMember::id)
            val names = active.map(ActiveMember::name)
            transaction.set(
                activityReference,
                activityData(
                    user,
                    draft.date,
                    draft.type,
                    draft.name,
                    ids,
                    names,
                    gym,
                    newEvent?.id ?: restoredEvent?.id,
                    restore?.done ?: false,
                ),
            )
            when {
                newEvent != null -> transaction.set(
                    newEvent,
                    newSportEventData(user, user.id, user.sportDisplayName, draft.date, draft.name, ids, names),
                )
                restoredEvent != null -> transaction.set(
                    eventsCollection(spaceId).document(restoredEvent.id),
                    eventDataForRestore(
                        user,
                        restoredEvent,
                        activeMembers(eventParticipantIds, eventParticipantIds.map(records::get)),
                    ),
                )
            }
        }.await()
    }

    override suspend fun undoAddActivity(spaceId: String, activityId: String) = runActivityOperation {
        val user = requireVerifiedUser()
        val reference = activitiesCollection(spaceId).document(activityId)
        firestore.runTransaction { transaction ->
            val snapshot = requireActivity(transaction.get(reference))
            val eventReference = snapshot.getString(FIELD_EVENT_ID)?.let { eventsCollection(spaceId).document(it) }
            // Lecturas antes de escrituras. Si el evento enlazado ya no existe, solo se borra la actividad.
            val eventSnapshot = eventReference?.let(transaction::get)?.takeIf { it.exists() }
            val touched = !isLastWriter(snapshot.getString(FIELD_UPDATED_BY), user.id) ||
                eventSnapshot?.let { !isLastWriter(it.getString(FIELD_UPDATED_BY), user.id) } == true
            if (touched) throw SportRepositoryException(SportFailure.ActivityChanged)
            transaction.delete(reference)
            eventSnapshot?.let { transaction.delete(it.reference) }
        }.await()
        Unit
    }

    private fun activitiesCollection(spaceId: String) = firestore
        .collection(SPACES)
        .document(spaceId)
        .collection(ACTIVITIES)

    private fun eventsCollection(spaceId: String) = firestore
        .collection(SPACES)
        .document(spaceId)
        .collection(EVENTS)

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

    private fun requireActivity(snapshot: DocumentSnapshot): DocumentSnapshot {
        if (!snapshot.exists()) {
            throw SportRepositoryException(SportFailure.ActivityNotFound)
        }
        return snapshot
    }

    private fun activityUpdateData(
        user: AuthUser,
        date: LocalDate,
        type: SportType,
        name: String,
        participantIds: List<String>,
        participantNames: List<String>,
        gym: GymSession,
        eventId: String?,
    ) = commonData(date, type, name, participantIds, participantNames, gym, eventId) + mapOf(
        FIELD_UPDATED_BY to user.id,
        FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
    )

    // Evento de calendario (todo el día) que refleja una actividad deportiva. Debe tener EXACTAMENTE
    // las 16 claves del contrato de eventos (reglas con hasOnly).
    private fun newSportEventData(
        user: AuthUser,
        organizerId: String,
        organizerName: String,
        date: LocalDate,
        name: String,
        participantIds: List<String>,
        participantNames: List<String>,
    ): Map<String, Any?> {
        val (ids, names) = eventParticipants(organizerId, organizerName, participantIds, participantNames)
        return mapOf(
            EV_TITLE to name,
            EV_DESCRIPTION to null,
            EV_ALL_DAY to true,
            EV_COLOR to EventColor.BLUE.name,
            FIELD_PARTICIPANT_IDS to ids,
            FIELD_PARTICIPANT_NAMES to names,
            FIELD_CREATED_BY to user.id,
            FIELD_CREATED_BY_NAME to user.sportDisplayName,
            FIELD_CREATED_AT to FieldValue.serverTimestamp(),
            FIELD_UPDATED_BY to user.id,
            FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
        ) + dayFields(date, ZoneId.systemDefault())
    }

    // Al editar solo se tocan los campos que dependen de la actividad (título, participantes y, si
    // cambia el día, las fechas): la descripción, el color o el horario que el usuario haya puesto al
    // evento desde el calendario se conservan.
    private fun sportEventUpdateData(
        user: AuthUser,
        event: DocumentSnapshot,
        previousDate: String?,
        date: LocalDate,
        name: String,
        participants: Pair<List<String>, List<String>>,
    ): Map<String, Any?> {
        val data = mutableMapOf<String, Any?>(
            EV_TITLE to name,
            FIELD_PARTICIPANT_IDS to participants.first,
            FIELD_PARTICIPANT_NAMES to participants.second,
            FIELD_UPDATED_BY to user.id,
            FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
        )
        // Un evento al que el usuario ya puso horario (no es de todo el día) no se mueve de día.
        if (previousDate != date.toString() && event.getBoolean(EV_ALL_DAY) != false) {
            val zone = event.getString(EV_TIME_ZONE)
                ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
                ?: ZoneId.systemDefault()
            data += dayFields(date, zone)
        }
        return data
    }

    private fun dayFields(date: LocalDate, zone: ZoneId): Map<String, Any?> = mapOf(
        EV_START_AT to date.atStartOfDay(zone).toInstant().timestamp(),
        EV_END_AT to date.plusDays(1).atStartOfDay(zone).toInstant().timestamp(),
        EV_START_DATE to date.toString(),
        EV_END_DATE_EXCLUSIVE to date.plusDays(1).toString(),
        EV_TIME_ZONE to zone.id,
        EV_ALL_DAY to true,
    )

    // El calendario personal solo muestra eventos donde el usuario es participante, así que el evento
    // incluye al organizador (creador de la actividad) para que una actividad sin participantes
    // aparezca en algún calendario personal. Los participantes van primero: si con el organizador se
    // superase el máximo, el que se queda fuera es el organizador y nunca un participante.
    private fun eventParticipants(
        organizerId: String,
        organizerName: String,
        participantIds: List<String>,
        participantNames: List<String>,
    ): Pair<List<String>, List<String>> {
        val ids = participantIds.toMutableList()
        val names = participantNames.toMutableList()
        if (organizerId !in ids && ids.size < MAX_EVENT_PARTICIPANTS) {
            ids += organizerId
            names += organizerName
        }
        return ids to names
    }

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
        const val EVENTS = "events"
        const val MEMBERSHIPS = "memberships"
        const val FIELD_DATE = "date"
        const val FIELD_PARTICIPANT_IDS = "participantIds"
        const val FIELD_PARTICIPANT_NAMES = "participantNames"
        const val FIELD_EVENT_ID = "eventId"
        const val EV_TITLE = "title"
        const val EV_DESCRIPTION = "description"
        const val EV_ALL_DAY = "allDay"
        const val EV_START_AT = "startAt"
        const val EV_END_AT = "endAt"
        const val EV_START_DATE = "startDate"
        const val EV_END_DATE_EXCLUSIVE = "endDateExclusive"
        const val EV_TIME_ZONE = "timeZone"
        const val EV_COLOR = "color"
        const val FIELD_DONE = "done"
        const val FIELD_CREATED_BY = "createdBy"
        const val FIELD_CREATED_BY_NAME = "createdByName"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_BY = "updatedBy"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val MAX_NAME_LENGTH = 120
        const val MAX_PARTICIPANTS = 10
        const val MAX_EVENT_PARTICIPANTS = 10
        const val MAX_VISIBLE_ACTIVITIES = 250L
    }
}

// La rutina y la sesión solo tienen sentido en GIMNASIO: para el resto se fuerzan a vacío para
// que ningún tipo arrastre datos de sesión ajenos.
internal class GymSession(type: SportType, routineId: String?, session: List<RoutineExercise>) {
    val routineId: String? = routineId?.takeIf { type == SportType.GIMNASIO }
    val session: List<RoutineExercise> = if (type == SportType.GIMNASIO) session else emptyList()
}

private class ActivityDraft(
    val date: LocalDate,
    val type: SportType,
    val name: String,
    val participantIds: List<String>,
    val routineId: String?,
    val session: List<RoutineExercise>,
)

// Lo que distingue restaurar de dar de alta: si la actividad estaba hecha y la foto de su evento (null: vuelve sin él).
private class Restore(val done: Boolean, val event: CalendarEvent?)

// Mapa de campos de una actividad nueva: el alta la escribe no hecha y la restauración, como estaba.
internal fun activityData(
    user: AuthUser,
    date: LocalDate,
    type: SportType,
    name: String,
    participantIds: List<String>,
    participantNames: List<String>,
    gym: GymSession,
    eventId: String?,
    done: Boolean,
): Map<String, Any?> = commonData(date, type, name, participantIds, participantNames, gym, eventId) + mapOf(
    "done" to done,
    "createdBy" to user.id,
    "createdByName" to user.sportDisplayName,
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedBy" to user.id,
    "updatedAt" to FieldValue.serverTimestamp(),
)

private fun commonData(
    date: LocalDate,
    type: SportType,
    name: String,
    participantIds: List<String>,
    participantNames: List<String>,
    gym: GymSession,
    eventId: String?,
) = mapOf(
    "date" to date.toString(),
    "type" to type.name,
    "name" to name,
    "participantIds" to participantIds,
    "participantNames" to participantNames,
    "routineId" to gym.routineId,
    "session" to gym.session.toFirestoreExercises(),
    "eventId" to eventId,
)

// El evento vuelve con los datos que tenía (título, horario, color…) y solo con los participantes que siguen activos,
// en su orden y con el nombre de su membresía; solo la autoría y las fechas de creación son de quien deshace.
internal fun eventDataForRestore(user: AuthUser, event: CalendarEvent, active: List<ActiveMember>): Map<String, Any?> =
    event.toInput().copy(participantIds = active.map(ActiveMember::id)).data(user, active.map(ActiveMember::name))

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
        eventId = getString("eventId"),
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
