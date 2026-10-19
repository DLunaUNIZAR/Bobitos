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
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.data.repository.CalendarRepository
import com.dlunaunizar.bobitos.data.repository.EventInput
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.repository.TaskRepository
import com.dlunaunizar.bobitos.data.repository.toInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class CalendarNotice { SAVED }

data class CalendarUiState(
    val focusedDate: LocalDate = LocalDate.now(),
    val mode: CalendarDisplayMode = CalendarDisplayMode.MONTH,
    val events: UiState<List<CalendarEvent>> = UiState.Loading,
    // Hay otro periodo en camino y `events` conserva el anterior hasta que llegue.
    val refreshing: Boolean = false,
    val members: UiState<List<SpaceMember>> = UiState.Loading,
    val tasks: List<TaskItem> = emptyList(),
    val selectedMemberIds: Set<String> = emptySet(),
    val knownMemberIds: Set<String> = emptySet(),
    val save: SaveState<FailureKind, CalendarNotice> = SaveState(),
)

// Con eventos ya a la vista se conservan mientras llega el periodo nuevo; solo la primera carga es «Cargando».
private fun CalendarUiState.loadingPeriod(): CalendarUiState =
    if (events is UiState.Content) copy(refreshing = true) else copy(events = UiState.Loading, refreshing = false)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: CalendarRepository,
    private val spaces: SpaceRepository,
    private val taskRepository: TaskRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = mutable.asStateFlow()
    private val saver =
        SaveRunner(viewModelScope, mutable, CalendarUiState::save, { copy(save = it) }, Throwable::toFailureKind)
    val feedback: SaveFeedbackControls get() = saver
    private val undo = UndoOffers<CalendarEvent>(viewModelScope)
    val undoOffers: StateFlow<List<UndoOffer<CalendarEvent>>> = undo.offers
    val undoControls: UndoOfferControls get() = undo

    private var spaceId: String? = null
    private var eventJob: Job? = null
    private var memberJob: Job? = null
    private var taskJob: Job? = null

    fun observe(id: String) {
        if (spaceId == id) return
        spaceId = id
        mutable.update {
            it.copy(
                // Los eventos del espacio anterior no se conservan bajo el nuevo.
                events = UiState.Loading,
                refreshing = false,
                members = UiState.Loading,
                selectedMemberIds = emptySet(),
                knownMemberIds = emptySet(),
            )
        }
        observeRange()
        memberJob?.cancel()
        memberJob = viewModelScope.launch {
            spaces.members(id)
                .catch { error ->
                    mutable.update { it.copy(members = UiState.Error(error.toFailureKind())) }
                }
                .collect(::onMembersChanged)
        }
        taskJob?.cancel()
        taskJob = viewModelScope.launch {
            taskRepository.tasks(id)
                .catch { mutable.update { it.copy(tasks = emptyList()) } }
                .collect { tasks -> mutable.update { it.copy(tasks = tasks) } }
        }
    }

    fun stop() {
        eventJob?.cancel()
        memberJob?.cancel()
        taskJob?.cancel()
        spaceId = null
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

    fun goToDate(date: LocalDate) {
        if (date == mutable.value.focusedDate) return
        mutable.update { it.copy(focusedDate = date) }
        observeRange()
    }

    fun toggleMember(userId: String) {
        mutable.update { state ->
            state.copy(selectedMemberIds = toggledSelection(state.selectedMemberIds, state.knownMemberIds, userId))
        }
    }

    fun selectAllMembers() {
        mutable.update { it.copy(selectedMemberIds = it.knownMemberIds) }
    }

    // Un alta ofrece «Deshacer» (con el espacio de ahora) en lugar del aviso de guardado; una edición conserva el suyo.
    // Sin espacio no se escribe nada: la acción acaba en Unit.
    fun save(eventId: String?, input: EventInput) {
        val id = spaceId
        if (eventId == null && id != null) {
            saver.run<CreatedItem>(editor = true, afterSuccess = { undo.offer(Undoable.Created(id, it)) }) {
                CreatedItem(repository.createEvent(id, input), input.title)
            }
        } else {
            saver.run<Unit>(editor = true, notice = if (eventId == null) null else CalendarNotice.SAVED) {
                if (id != null && eventId != null) repository.updateEvent(id, eventId, input)
            }
        }
    }

    // La oferta de «Deshacer» sale solo si el borrado termina bien (y ya sin «guardando»). Sin aviso propio: el
    // snackbar de «Deshacer» ya confirma el borrado. Sin espacio no se escribe nada ni se ofrece nada.
    fun delete(event: CalendarEvent) {
        val id = spaceId ?: return
        saver.run<Unit>(afterSuccess = { undo.offer(Undoable.Deleted(id, event)) }) {
            repository.deleteEvent(id, event.id)
        }
    }

    // Única entrada de «Deshacer»: siempre en el espacio donde pasó. No es un guardado del editor ni lleva aviso
    // propio; con force no se descarta en silencio por otra escritura en curso. Si falla (también si otro miembro
    // cambió lo que se dio de alta), sale el error.
    fun undo(undoable: Undoable<CalendarEvent>) {
        val id = undoable.spaceId ?: return
        saver.run<Unit>(force = true) {
            when (undoable) {
                is Undoable.Created -> repository.undoCreateEvent(id, undoable.item.id)
                is Undoable.Deleted -> repository.restoreEvent(id, undoable.item.id, undoable.item.toInput())
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

    private fun onMembersChanged(members: List<SpaceMember>) {
        mutable.update { state ->
            val currentIds = members.mapTo(mutableSetOf(), SpaceMember::userId)
            state.copy(
                members = UiState.Content(members),
                selectedMemberIds = reconciledSelection(state.selectedMemberIds, state.knownMemberIds, currentIds),
                knownMemberIds = currentIds,
            )
        }
    }

    private fun observeRange() {
        val id = spaceId ?: return
        eventJob?.cancel()
        val state = mutable.value
        val interval = state.focusedDate.visibleInterval(state.mode, ZoneId.systemDefault())
        mutable.update { it.loadingPeriod() }
        eventJob = viewModelScope.launch {
            repository.events(id, interval.start, interval.endExclusive)
                .catch { error ->
                    mutable.update { it.copy(events = UiState.Error(error.toFailureKind()), refreshing = false) }
                }
                .collect { events ->
                    mutable.update { it.copy(events = UiState.Content(events), refreshing = false) }
                }
        }
    }
}
