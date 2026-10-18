package com.dlunaunizar.bobitos.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.FailureKind
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.failed
import com.dlunaunizar.bobitos.core.common.started
import com.dlunaunizar.bobitos.core.common.succeeded
import com.dlunaunizar.bobitos.core.common.toFailureKind
import com.dlunaunizar.bobitos.core.common.withEditorSaveTimeout
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.data.repository.CalendarRepository
import com.dlunaunizar.bobitos.data.repository.EventInput
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    val saving: Boolean = false,
    val editorSave: EditorSaveStatus = EditorSaveStatus.IDLE,
    val error: FailureKind? = null,
    // Alta recién hecha desde la hoja: la pantalla la avisa con «Deshacer» y la consume.
    val created: CreatedItem? = null,
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

    private var userId: String? = null
    private var spaces: List<SpaceSummary> = emptyList()
    private var eventJob: Job? = null
    private var membersJob: Job? = null

    // Espacio de cada evento creado desde la hoja: el calendario personal elige el espacio al crear y «Deshacer»
    // necesita saber dónde borrar.
    private val createdSpaces = mutableMapOf<String, String>()

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

    fun saveEvent(spaceId: String, eventId: String?, input: EventInput) = action(editor = true) {
        if (eventId == null) {
            val id = repository.createEvent(spaceId, input)
            createdSpaces[id] = spaceId
            CreatedItem(id, input.title)
        } else {
            repository.updateEvent(spaceId, eventId, input)
        }
    }

    // onDeleted se llama solo si el borrado termina bien: así el «Deshacer» no se ofrece para un borrado fallido.
    // Sin aviso propio: el snackbar de «Deshacer» ya confirma el borrado.
    fun deleteEvent(spaceId: String, eventId: String, onDeleted: () -> Unit = {}) =
        action(onSuccess = onDeleted) { repository.deleteEvent(spaceId, eventId) }

    // «Deshacer» el borrado: el evento vuelve con su id, al espacio en que estaba. No es un guardado del editor ni
    // lleva aviso propio; si falla, sale el error.
    fun restore(spaceId: String, eventId: String, input: EventInput) = action(force = true) {
        repository.createEvent(spaceId, input, eventId)
        null
    }

    fun consumeCreated() = mutable.update { it.copy(created = null) }

    // «Deshacer» el alta: borra justo ese evento, en el espacio donde se creó, con el borrado de siempre.
    fun undoCreate(id: String) {
        val spaceId = createdSpaces[id] ?: return
        action(force = true) { repository.deleteEvent(spaceId, id) }
    }

    fun consumeEditorSave() = mutable.update { it.copy(editorSave = EditorSaveStatus.IDLE) }

    fun clearMessage() = mutable.update { it.copy(error = null) }

    private fun action(
        editor: Boolean = false,
        force: Boolean = false,
        onSuccess: () -> Unit = {},
        block: suspend () -> Any?,
    ) {
        if (mutable.value.saving && !force) return
        mutable.update { it.copy(saving = true, error = null, editorSave = it.editorSave.started(editor)) }
        viewModelScope.launch {
            runCatching { withEditorSaveTimeout(editor) { block() } }
                .onSuccess { result ->
                    mutable.update {
                        it.copy(
                            saving = false,
                            created = result as? CreatedItem,
                            editorSave = it.editorSave.succeeded(editor),
                        )
                    }
                    onSuccess()
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    mutable.update {
                        it.copy(
                            saving = false,
                            error = error.toFailureKind(),
                            editorSave = it.editorSave.failed(editor),
                        )
                    }
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
