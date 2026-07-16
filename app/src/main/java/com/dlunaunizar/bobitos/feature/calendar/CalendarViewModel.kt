package com.dlunaunizar.bobitos.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.data.repository.CalendarRepository
import com.dlunaunizar.bobitos.data.repository.EventInput
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(), val selectedDate: LocalDate = LocalDate.now(),
    val events: UiState<List<CalendarEvent>> = UiState.Loading,
    val members: UiState<List<SpaceMember>> = UiState.Loading,
    val agenda: Boolean = false, val saving: Boolean = false, val message: String? = null,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: CalendarRepository, private val spaces: SpaceRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow(CalendarUiState()); val uiState: StateFlow<CalendarUiState> = mutable.asStateFlow()
    private var spaceId: String? = null; private var eventJob: Job? = null; private var memberJob: Job? = null
    fun observe(id: String) { if (spaceId != id) { spaceId=id; observeRange(); memberJob?.cancel(); memberJob=viewModelScope.launch {
        spaces.members(id).catch { mutable.update { s -> s.copy(members=UiState.Error(it.message)) } }.collect { value -> mutable.update { it.copy(members=UiState.Content(value)) } }
    } } }
    fun stop() { eventJob?.cancel(); memberJob?.cancel(); spaceId=null }
    fun previous() { mutable.update { it.copy(month=it.month.minusMonths(1), selectedDate=it.month.minusMonths(1).atDay(1)) }; observeRange() }
    fun next() { mutable.update { it.copy(month=it.month.plusMonths(1), selectedDate=it.month.plusMonths(1).atDay(1)) }; observeRange() }
    fun select(date: LocalDate) = mutable.update { it.copy(selectedDate=date) }
    fun toggleMode() = mutable.update { it.copy(agenda=!it.agenda) }
    fun save(eventId: String?, input: EventInput) = action { val id=spaceId ?: return@action; if(eventId==null) repository.createEvent(id,input) else repository.updateEvent(id,eventId,input) }
    fun delete(eventId: String) = action { repository.deleteEvent(spaceId ?: return@action,eventId) }
    fun clearMessage() = mutable.update { it.copy(message=null) }
    private fun observeRange() { val id=spaceId ?: return; eventJob?.cancel(); val interval=mutable.value.month.visibleInterval(ZoneId.systemDefault()); mutable.update { it.copy(events=UiState.Loading) }; eventJob=viewModelScope.launch {
        repository.events(id,interval.start,interval.endExclusive).catch { mutable.update { s -> s.copy(events=UiState.Error(it.message)) } }.collect { value -> mutable.update { it.copy(events=UiState.Content(value)) } }
    } }
    private fun action(block: suspend () -> Unit) { if(mutable.value.saving)return; mutable.update { it.copy(saving=true,message=null) }; viewModelScope.launch { try { block(); mutable.update { it.copy(saving=false,message="Cambios guardados") } } catch(e:Throwable) { mutable.update { it.copy(saving=false,message=e.message ?: "No se pudo completar la operación") } } } }
}
