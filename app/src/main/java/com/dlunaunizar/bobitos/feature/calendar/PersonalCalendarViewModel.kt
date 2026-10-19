package com.dlunaunizar.bobitos.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.FailureKind
import com.dlunaunizar.bobitos.core.common.SaveFeedbackControls
import com.dlunaunizar.bobitos.core.common.SaveRunner
import com.dlunaunizar.bobitos.core.common.SaveState
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.UndoOffer
import com.dlunaunizar.bobitos.core.common.UndoOfferControls
import com.dlunaunizar.bobitos.core.common.UndoOffers
import com.dlunaunizar.bobitos.core.common.Undoable
import com.dlunaunizar.bobitos.core.common.toFailureKind
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.data.repository.CalendarRepository
import com.dlunaunizar.bobitos.data.repository.EventInput
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.repository.toInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class PersonalCalendarEvent(val spaceId: String, val spaceName: String, val event: CalendarEvent)

data class PersonalCalendarUiState(
    val focusedDate: LocalDate = LocalDate.now(),
    val mode: CalendarDisplayMode = CalendarDisplayMode.MONTH,
    val events: UiState<List<PersonalCalendarEvent>> = UiState.Loading,
    // Hay otro periodo en camino y `events` conserva el anterior hasta que llegue.
    val refreshing: Boolean = false,
    val selectedSpaceIds: Set<String> = emptySet(),
    val knownSpaceIds: Set<String> = emptySet(),
    // Miembros del espacio para el que está abierto el editor (para elegir participantes).
    val editorMembers: List<SpaceMember> = emptyList(),
    val save: SaveState<FailureKind, Nothing> = SaveState(),
)

// Con eventos ya a la vista se conservan mientras llega el periodo nuevo; solo la primera carga es «Cargando».
private fun PersonalCalendarUiState.loadingPeriod(): PersonalCalendarUiState =
    if (events is UiState.Content) copy(refreshing = true) else copy(events = UiState.Loading, refreshing = false)

@HiltViewModel
class PersonalCalendarViewModel @Inject constructor(
    private val repository: CalendarRepository,
    private val spaceRepository: SpaceRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow(PersonalCalendarUiState())
    val uiState: StateFlow<PersonalCalendarUiState> = mutable.asStateFlow()
    private val saver = SaveRunner(
        viewModelScope,
        mutable,
        PersonalCalendarUiState::save,
        { copy(save = it) },
        Throwable::toFailureKind,
    )
    val feedback: SaveFeedbackControls get() = saver
    private val undo = UndoOffers<CalendarEvent>(viewModelScope)
    val undoOffers: StateFlow<List<UndoOffer<CalendarEvent>>> = undo.offers
    val undoControls: UndoOfferControls get() = undo

    private var userId: String? = null
    private var spaces: List<SpaceSummary> = emptyList()
    private var eventJob: Job? = null
    private var membersJob: Job? = null

    fun observe(currentUserId: String, currentSpaces: List<SpaceSummary>) {
        if (userId == currentUserId && spaces == currentSpaces) return
        userId = currentUserId
        spaces = currentSpaces
        val currentIds = currentSpaces.mapTo(mutableSetOf(), SpaceSummary::id)
        mutable.update { state ->
            state.copy(
                selectedSpaceIds = reconciledSelection(state.selectedSpaceIds, state.knownSpaceIds, currentIds),
                knownSpaceIds = currentIds,
            )
        }
        observeRange()
    }

    fun stop() {
        eventJob?.cancel()
        membersJob?.cancel()
        userId = null
        spaces = emptyList()
    }

    fun setMode(mode: CalendarDisplayMode) {
        if (mode == mutable.value.mode) return
        mutable.update { it.copy(mode = mode) }
        observeRange()
    }

    // Vuelve a suscribir los eventos del periodo actual (muestra la carga y, si sigue fallando, el error otra vez).
    fun retryEvents() = observeRange()

    fun previous() = move(-1)

    fun next() = move(1)

    fun select(date: LocalDate) {
        mutable.update { it.copy(focusedDate = date) }
    }

    fun toggleSpace(spaceId: String) {
        mutable.update { state ->
            state.copy(selectedSpaceIds = toggledSelection(state.selectedSpaceIds, state.knownSpaceIds, spaceId))
        }
    }

    fun selectAllSpaces() {
        mutable.update { it.copy(selectedSpaceIds = it.knownSpaceIds) }
    }

    // Observa los miembros del espacio elegido mientras el editor está abierto (para participantes).
    fun observeEditorMembers(spaceId: String) {
        membersJob?.cancel()
        mutable.update { it.copy(editorMembers = emptyList()) }
        membersJob = viewModelScope.launch {
            spaceRepository.members(spaceId)
                .catch { mutable.update { it.copy(editorMembers = emptyList()) } }
                .collect { members -> mutable.update { it.copy(editorMembers = members) } }
        }
    }

    fun clearEditorMembers() {
        membersJob?.cancel()
        membersJob = null
        mutable.update { it.copy(editorMembers = emptyList()) }
    }

    fun saveEvent(spaceId: String, eventId: String?, input: EventInput) {
        if (eventId == null) {
            // El calendario personal elige el espacio al crear: la oferta de «Deshacer» lo lleva consigo.
            saver.run<CreatedItem>(editor = true, afterSuccess = { undo.offer(Undoable.Created(spaceId, it)) }) {
                CreatedItem(repository.createEvent(spaceId, input), input.title)
            }
        } else {
            saver.run<Unit>(editor = true) { repository.updateEvent(spaceId, eventId, input) }
        }
    }

    // La oferta de «Deshacer» sale solo si el borrado termina bien. Sin aviso propio: el snackbar de «Deshacer» ya
    // confirma el borrado.
    fun deleteEvent(spaceId: String, event: CalendarEvent) =
        saver.run<Unit>(afterSuccess = { undo.offer(Undoable.Deleted(spaceId, event)) }) {
            repository.deleteEvent(spaceId, event.id)
        }

    // Única entrada de «Deshacer»: siempre en el espacio donde pasó. No es un guardado del editor ni lleva aviso
    // propio; si falla (también si otro miembro cambió lo que se dio de alta), sale el error.
    fun undo(undoable: Undoable<CalendarEvent>) {
        val spaceId = undoable.spaceId ?: return
        saver.run<Unit>(force = true) {
            when (undoable) {
                is Undoable.Created -> repository.undoCreateEvent(spaceId, undoable.item.id)
                is Undoable.Deleted -> repository.restoreEvent(spaceId, undoable.item.id, undoable.item.toInput())
                is Undoable.Completed -> Unit
            }
        }
    }

    private fun move(amount: Long) {
        mutable.update { state ->
            state.copy(focusedDate = state.focusedDate.move(state.mode, amount))
        }
        observeRange()
    }

    private fun observeRange() {
        val currentUserId = userId ?: return
        eventJob?.cancel()
        if (spaces.isEmpty()) {
            mutable.update { it.copy(events = UiState.Content(emptyList()), refreshing = false) }
            return
        }

        val state = mutable.value
        val interval = state.focusedDate.visibleInterval(state.mode, ZoneId.systemDefault())
        mutable.update { it.loadingPeriod() }
        val observedSpaces = spaces
        val sources = observedSpaces.map { space ->
            repository.events(space.id, interval.start, interval.endExclusive)
        }
        eventJob = viewModelScope.launch {
            combine(sources) { eventsBySpace ->
                aggregatePersonalEvents(
                    currentUserId,
                    observedSpaces,
                    eventsBySpace.toList(),
                )
            }.catch { error ->
                mutable.update { it.copy(events = UiState.Error(error.toFailureKind()), refreshing = false) }
            }.collect { events ->
                mutable.update { it.copy(events = UiState.Content(events), refreshing = false) }
            }
        }
    }
}

internal fun aggregatePersonalEvents(
    userId: String,
    spaces: List<SpaceSummary>,
    eventsBySpace: List<List<CalendarEvent>>,
): List<PersonalCalendarEvent> = spaces.zip(eventsBySpace)
    .flatMap { (space, events) ->
        events.forParticipant(userId).map { event ->
            PersonalCalendarEvent(space.id, space.name, event)
        }
    }
    .sortedBy { it.event.startAt }
