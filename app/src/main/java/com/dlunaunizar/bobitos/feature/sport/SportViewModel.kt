package com.dlunaunizar.bobitos.feature.sport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import com.dlunaunizar.bobitos.core.model.SportType
import com.dlunaunizar.bobitos.data.repository.RoutineRepository
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.repository.SportActivityRepository
import com.dlunaunizar.bobitos.data.repository.SportFailure
import com.dlunaunizar.bobitos.data.repository.SportRepositoryException
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
import javax.inject.Inject

@HiltViewModel
class SportViewModel @Inject constructor(
    private val repository: SportActivityRepository,
    private val spaces: SpaceRepository,
    private val routines: RoutineRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SportUiState())
    val uiState: StateFlow<SportUiState> = mutableUiState.asStateFlow()

    private var observedSpaceId: String? = null
    private var observedWeekStart: LocalDate? = null
    private var activitiesJob: Job? = null
    private var membersJob: Job? = null
    private var routinesJob: Job? = null

    fun observe(spaceId: String) {
        if (spaceId == observedSpaceId && activitiesJob?.isActive == true) return
        observedSpaceId = spaceId
        observeWeek(spaceId)
        membersJob?.cancel()
        mutableUiState.update { it.copy(members = UiState.Loading) }
        membersJob = viewModelScope.launch {
            spaces.members(spaceId)
                .catch { error -> mutableUiState.update { it.copy(members = UiState.Error(error.message)) } }
                .collect { members -> mutableUiState.update { it.copy(members = UiState.Content(members)) } }
        }
        observeRoutines()
    }

    // Catálogo de rutinas (comunes + mías, deduplicado) para el picker de la sesión de gimnasio. Es
    // global (independiente del espacio); un fallo se ignora (el picker aparecerá vacío).
    private fun observeRoutines() {
        if (routinesJob?.isActive == true) return
        routinesJob = viewModelScope.launch {
            combine(routines.globalRoutines(), routines.myRoutines()) { global, mine ->
                val mineIds = mine.map(Routine::id).toSet()
                mine + global.filter { it.id !in mineIds }
            }
                .catch { mutableUiState.update { it.copy(routines = emptyList()) } }
                .collect { list -> mutableUiState.update { it.copy(routines = list) } }
        }
    }

    private fun observeWeek(spaceId: String) {
        val weekStart = mutableUiState.value.weekStart
        if (weekStart == observedWeekStart && activitiesJob?.isActive == true) return
        activitiesJob?.cancel()
        observedWeekStart = weekStart
        mutableUiState.update { it.copy(activities = UiState.Loading) }
        activitiesJob = viewModelScope.launch {
            repository.activities(spaceId, weekStart, weekStart.plusWeeks(1))
                .catch { error -> mutableUiState.update { it.copy(activities = UiState.Error(error.message)) } }
                .collect { activities -> mutableUiState.update { it.copy(activities = UiState.Content(activities)) } }
        }
    }

    fun stopObserving() {
        activitiesJob?.cancel()
        membersJob?.cancel()
        routinesJob?.cancel()
        activitiesJob = null
        membersJob = null
        routinesJob = null
        observedSpaceId = null
        observedWeekStart = null
    }

    fun previousWeek() = goToDate(mutableUiState.value.focusedDate.minusWeeks(1))

    fun nextWeek() = goToDate(mutableUiState.value.focusedDate.plusWeeks(1))

    fun selectDay(date: LocalDate) = goToDate(date)

    private fun goToDate(date: LocalDate) {
        mutableUiState.update { it.copy(focusedDate = date) }
        observedSpaceId?.let(::observeWeek)
    }

    fun addActivity(
        date: LocalDate,
        type: SportType,
        name: String,
        participantIds: List<String>,
        routineId: String? = null,
        session: List<RoutineExercise> = emptyList(),
    ) {
        val spaceId = observedSpaceId ?: return
        if (!validate(name)) return
        runAction(SportUiMessage.ActivityAdded) {
            repository.addActivity(spaceId, date, type, name.trim(), participantIds, routineId, session)
        }
    }

    fun updateActivity(
        activityId: String,
        date: LocalDate,
        type: SportType,
        name: String,
        participantIds: List<String>,
        routineId: String? = null,
        session: List<RoutineExercise> = emptyList(),
    ) {
        val spaceId = observedSpaceId ?: return
        if (!validate(name)) return
        runAction(SportUiMessage.ActivityUpdated) {
            repository.updateActivity(spaceId, activityId, date, type, name.trim(), participantIds, routineId, session)
        }
    }

    fun setDone(activityId: String, done: Boolean) {
        val spaceId = observedSpaceId ?: return
        runAction(if (done) SportUiMessage.ActivityDone else SportUiMessage.ActivityPending) {
            repository.setDone(spaceId, activityId, done)
        }
    }

    fun deleteActivity(activityId: String) {
        val spaceId = observedSpaceId ?: return
        // Sin notice: el feedback del borrado (con «Deshacer») lo da el Snackbar de la pantalla.
        runAction(null) { repository.deleteActivity(spaceId, activityId) }
    }

    fun clearFeedback() = mutableUiState.update { it.copy(error = null, notice = null) }

    private fun validate(name: String): Boolean {
        val error = SportValidation.validate(name) ?: return true
        showError(error)
        return false
    }

    private fun showError(message: SportUiMessage) = mutableUiState.update {
        it.copy(isSaving = false, error = message, notice = null)
    }

    private fun runAction(notice: SportUiMessage?, action: suspend () -> Unit) {
        if (mutableUiState.value.isSaving) return
        mutableUiState.update { it.copy(isSaving = true, error = null, notice = null) }
        viewModelScope.launch {
            try {
                action()
                mutableUiState.update { it.copy(isSaving = false, notice = notice) }
            } catch (error: Throwable) {
                showError(error.toUiMessage())
            }
        }
    }
}

private fun Throwable.toUiMessage() = when ((this as? SportRepositoryException)?.failure) {
    SportFailure.NameRequired -> SportUiMessage.NameRequired
    SportFailure.NameTooLong -> SportUiMessage.NameTooLong
    SportFailure.InvalidParticipants -> SportUiMessage.InvalidParticipants
    SportFailure.NotAuthenticated -> SportUiMessage.NotAuthenticated
    SportFailure.EmailNotVerified -> SportUiMessage.EmailNotVerified
    SportFailure.SpaceNotFound -> SportUiMessage.SpaceNotFound
    SportFailure.ActivityNotFound -> SportUiMessage.ActivityNotFound
    SportFailure.PermissionDenied -> SportUiMessage.PermissionDenied
    SportFailure.Network -> SportUiMessage.NetworkError
    SportFailure.Unknown, null -> SportUiMessage.UnexpectedError
}
