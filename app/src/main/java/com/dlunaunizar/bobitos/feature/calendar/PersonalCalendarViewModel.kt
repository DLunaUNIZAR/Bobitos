package com.dlunaunizar.bobitos.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.data.repository.CalendarRepository
import com.dlunaunizar.bobitos.data.repository.EventInput
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
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
    val selectedSpaceIds: Set<String> = emptySet(),
    val knownSpaceIds: Set<String> = emptySet(),
    // Miembros del espacio para el que está abierto el editor (para elegir participantes).
    val editorMembers: List<SpaceMember> = emptyList(),
    val saving: Boolean = false,
    val message: String? = null,
)

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

    fun observe(currentUserId: String, currentSpaces: List<SpaceSummary>) {
        if (userId == currentUserId && spaces == currentSpaces) return
        userId = currentUserId
        spaces = currentSpaces
        val currentIds = currentSpaces.mapTo(mutableSetOf(), SpaceSummary::id)
        mutable.update { state ->
            val newIds = currentIds - state.knownSpaceIds
            state.copy(
                selectedSpaceIds = (state.selectedSpaceIds intersect currentIds) + newIds,
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

    fun previous() = move(-1)

    fun next() = move(1)

    fun select(date: LocalDate) {
        mutable.update { it.copy(focusedDate = date) }
    }

    fun toggleSpace(spaceId: String) {
        mutable.update { state ->
            state.copy(
                selectedSpaceIds = if (spaceId in state.selectedSpaceIds) {
                    state.selectedSpaceIds - spaceId
                } else {
                    state.selectedSpaceIds + spaceId
                },
            )
        }
    }

    fun selectAllSpaces() {
        mutable.update { it.copy(selectedSpaceIds = it.knownSpaceIds) }
    }

    fun clearSpaceSelection() {
        mutable.update { it.copy(selectedSpaceIds = emptySet()) }
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

    fun saveEvent(spaceId: String, eventId: String?, input: EventInput) = action {
        if (eventId == null) repository.createEvent(spaceId, input) else repository.updateEvent(spaceId, eventId, input)
    }

    fun deleteEvent(spaceId: String, eventId: String) = action { repository.deleteEvent(spaceId, eventId) }

    fun clearMessage() = mutable.update { it.copy(message = null) }

    private fun action(block: suspend () -> Unit) {
        if (mutable.value.saving) return
        mutable.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { mutable.update { it.copy(saving = false) } }
                .onFailure { error -> mutable.update { it.copy(saving = false, message = error.message ?: "error") } }
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
            mutable.update { it.copy(events = UiState.Content(emptyList())) }
            return
        }

        val state = mutable.value
        val interval = state.focusedDate.visibleInterval(state.mode, ZoneId.systemDefault())
        mutable.update { it.copy(events = UiState.Loading) }
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
                mutable.update { it.copy(events = UiState.Error(error.message)) }
            }.collect { events ->
                mutable.update { it.copy(events = UiState.Content(events)) }
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
