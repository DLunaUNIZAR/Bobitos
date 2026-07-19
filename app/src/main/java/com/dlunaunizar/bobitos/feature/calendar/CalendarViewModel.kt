package com.dlunaunizar.bobitos.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.data.repository.CalendarRepository
import com.dlunaunizar.bobitos.data.repository.EventInput
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.repository.TaskRepository
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

data class CalendarUiState(
    val focusedDate: LocalDate = LocalDate.now(),
    val mode: CalendarDisplayMode = CalendarDisplayMode.MONTH,
    val events: UiState<List<CalendarEvent>> = UiState.Loading,
    val members: UiState<List<SpaceMember>> = UiState.Loading,
    val tasks: List<TaskItem> = emptyList(),
    val selectedMemberIds: Set<String> = emptySet(),
    val knownMemberIds: Set<String> = emptySet(),
    val saving: Boolean = false,
    val message: String? = null,
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
                    mutable.update { it.copy(members = UiState.Error(error.message)) }
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

    fun save(eventId: String?, input: EventInput) = action {
        val id = spaceId ?: return@action
        if (eventId == null) {
            repository.createEvent(id, input)
        } else {
            repository.updateEvent(id, eventId, input)
        }
    }

    fun delete(eventId: String) = action {
        repository.deleteEvent(spaceId ?: return@action, eventId)
    }

    fun clearMessage() {
        mutable.update { it.copy(message = null) }
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
                    mutable.update { it.copy(events = UiState.Error(error.message)) }
                }
                .collect { events ->
                    mutable.update { it.copy(events = UiState.Content(events)) }
                }
        }
    }

    private fun action(block: suspend () -> Unit) {
        if (mutable.value.saving) return
        mutable.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            try {
                block()
                mutable.update { it.copy(saving = false, message = "Cambios guardados") }
            } catch (error: Throwable) {
                mutable.update {
                    it.copy(
                        saving = false,
                        message = error.message ?: "No se pudo completar la operación",
                    )
                }
            }
        }
    }
}
