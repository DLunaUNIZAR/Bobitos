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
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.data.repository.CalendarRepository
import com.dlunaunizar.bobitos.data.repository.EventInput
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    val members: UiState<List<SpaceMember>> = UiState.Loading,
    val tasks: List<TaskItem> = emptyList(),
    val selectedMemberIds: Set<String> = emptySet(),
    val knownMemberIds: Set<String> = emptySet(),
    val saving: Boolean = false,
    val editorSave: EditorSaveStatus = EditorSaveStatus.IDLE,
    val error: FailureKind? = null,
    val notice: CalendarNotice? = null,
    // Alta recién hecha desde la hoja: la pantalla la avisa con «Deshacer» y la consume.
    val created: CreatedItem? = null,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: CalendarRepository,
    private val spaces: SpaceRepository,
    private val taskRepository: TaskRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = mutable.asStateFlow()

    private var spaceId: String? = null
    private var eventJob: Job? = null
    private var memberJob: Job? = null
    private var taskJob: Job? = null

    fun observe(id: String) {
        if (spaceId == id) return
        spaceId = id
        mutable.update {
            it.copy(
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
            state.copy(
                selectedMemberIds = if (userId in state.selectedMemberIds) {
                    state.selectedMemberIds - userId
                } else {
                    state.selectedMemberIds + userId
                },
            )
        }
    }

    fun selectAllMembers() {
        mutable.update { it.copy(selectedMemberIds = it.knownMemberIds) }
    }

    fun clearMemberSelection() {
        mutable.update { it.copy(selectedMemberIds = emptySet()) }
    }

    // Un alta deja `created` (con «Deshacer») en lugar del aviso de guardado; una edición conserva el suyo.
    fun save(eventId: String?, input: EventInput) = action(
        editor = true,
        notice = if (eventId == null) null else CalendarNotice.SAVED,
    ) {
        val id = spaceId ?: return@action null
        if (eventId == null) {
            CreatedItem(repository.createEvent(id, input), input.title)
        } else {
            repository.updateEvent(id, eventId, input)
        }
    }

    // Con force: un «Deshacer» pulsado con otra escritura en curso no se descarta en silencio. Sin aviso propio.
    fun restore(eventId: String, input: EventInput) = action(notice = null, force = true) {
        repository.createEvent(spaceId ?: return@action null, input, eventId)
    }

    // onDeleted se llama solo si el borrado termina bien (y ya sin «guardando»): así el deshacer no se
    // ofrece para un borrado fallido ni se descarta por pulsarlo mientras el borrado sigue en curso.
    // Sin aviso propio: el snackbar de «Deshacer» ya confirma el borrado.
    fun delete(eventId: String, onDeleted: () -> Unit = {}) = action(notice = null, onSuccess = onDeleted) {
        repository.deleteEvent(spaceId ?: return@action null, eventId)
    }

    fun consumeCreated() = mutable.update { it.copy(created = null) }

    // «Deshacer» el alta: borra justo ese evento con el borrado de siempre (sin aviso; si falla, sale el error).
    fun undoCreate(id: String) = action(notice = null, force = true) {
        repository.deleteEvent(spaceId ?: return@action null, id)
    }

    fun consumeEditorSave() = mutable.update { it.copy(editorSave = EditorSaveStatus.IDLE) }

    fun clearMessage() {
        mutable.update { it.copy(error = null, notice = null) }
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
            val newIds = currentIds - state.knownMemberIds
            state.copy(
                members = UiState.Content(members),
                selectedMemberIds = (state.selectedMemberIds intersect currentIds) + newIds,
                knownMemberIds = currentIds,
            )
        }
    }

    private fun observeRange() {
        val id = spaceId ?: return
        eventJob?.cancel()
        val state = mutable.value
        val interval = state.focusedDate.visibleInterval(state.mode, ZoneId.systemDefault())
        mutable.update { it.copy(events = UiState.Loading) }
        eventJob = viewModelScope.launch {
            repository.events(id, interval.start, interval.endExclusive)
                .catch { error ->
                    mutable.update { it.copy(events = UiState.Error(error.toFailureKind())) }
                }
                .collect { events ->
                    mutable.update { it.copy(events = UiState.Content(events)) }
                }
        }
    }

    private fun action(
        editor: Boolean = false,
        notice: CalendarNotice? = CalendarNotice.SAVED,
        onSuccess: () -> Unit = {},
        force: Boolean = false,
        block: suspend () -> Any?,
    ) {
        if (mutable.value.saving && !force) return
        mutable.update {
            it.copy(saving = true, error = null, notice = null, editorSave = it.editorSave.started(editor))
        }
        viewModelScope.launch {
            runCatching { withEditorSaveTimeout(editor) { block() } }
                .onSuccess { result ->
                    mutable.update {
                        it.copy(
                            saving = false,
                            error = null,
                            notice = notice,
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
                            notice = null,
                            editorSave = it.editorSave.failed(editor),
                        )
                    }
                }
        }
    }
}
