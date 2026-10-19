package com.dlunaunizar.bobitos.feature.sport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.SaveFeedbackControls
import com.dlunaunizar.bobitos.core.common.SaveRunner
import com.dlunaunizar.bobitos.core.common.SaveTimeoutException
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.UndoOffer
import com.dlunaunizar.bobitos.core.common.UndoOfferControls
import com.dlunaunizar.bobitos.core.common.UndoOffers
import com.dlunaunizar.bobitos.core.common.Undoable
import com.dlunaunizar.bobitos.core.common.toFailureKind
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import com.dlunaunizar.bobitos.core.model.SportActivity
import com.dlunaunizar.bobitos.core.model.SportType
import com.dlunaunizar.bobitos.data.repository.ExerciseRepository
import com.dlunaunizar.bobitos.data.repository.RoutineRepository
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.repository.SportActivityRepository
import com.dlunaunizar.bobitos.data.repository.SportFailure
import com.dlunaunizar.bobitos.data.repository.SportRepositoryException
import com.dlunaunizar.bobitos.feature.routines.personalOnly
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
    private val exercises: ExerciseRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SportUiState())
    val uiState: StateFlow<SportUiState> = mutableUiState.asStateFlow()
    private val saver =
        SaveRunner(viewModelScope, mutableUiState, SportUiState::save, { copy(save = it) }, Throwable::toUiMessage)
    val feedback: SaveFeedbackControls get() = saver
    private val undo = UndoOffers<DeletedActivity>(viewModelScope)
    val undoOffers: StateFlow<List<UndoOffer<DeletedActivity>>> = undo.offers
    val undoControls: UndoOfferControls get() = undo

    private var observedSpaceId: String? = null
    private var observedWeekStart: LocalDate? = null
    private var activitiesJob: Job? = null
    private var membersJob: Job? = null
    private var routinesJob: Job? = null
    private var exercisesJob: Job? = null
    private var catalogRequested = false

    fun observe(spaceId: String) {
        if (spaceId == observedSpaceId && activitiesJob?.isActive == true) return
        observedSpaceId = spaceId
        observeWeek(spaceId)
        membersJob?.cancel()
        mutableUiState.update { it.copy(members = UiState.Loading) }
        membersJob = viewModelScope.launch {
            spaces.members(spaceId)
                .catch { error -> mutableUiState.update { it.copy(members = UiState.Error(error.toFailureKind())) } }
                .collect { members -> mutableUiState.update { it.copy(members = UiState.Content(members)) } }
        }
        observeRoutines()
        if (catalogRequested) observeExerciseCatalog()
    }

    // Catálogo de rutinas para el picker de la sesión de gimnasio: primero las privadas del usuario y después
    // todas las comunes (`global` ya las trae todas, también las del admin). Es global
    // (independiente del espacio); un fallo se ignora (el picker aparecerá vacío).
    private fun observeRoutines() {
        if (routinesJob?.isActive == true) return
        routinesJob = viewModelScope.launch {
            combine(routines.globalRoutines(), routines.myRoutines()) { global, mine -> mine.personalOnly() + global }
                .catch { mutableUiState.update { it.copy(routines = emptyList()) } }
                .collect { list -> mutableUiState.update { it.copy(routines = list) } }
        }
    }

    // Catálogo de ejercicios para elegir al añadir uno a la sesión. Diferido: lo pide el editor de la sesión
    // al aparecer. Idempotente; global; un fallo se ignora. Al cambiar de espacio la pantalla para y vuelve a
    // observar con el editor aún abierto: la marca se conserva para que observe() lo retome.
    fun observeExerciseCatalog() {
        catalogRequested = true
        if (exercisesJob?.isActive == true) return
        exercisesJob = viewModelScope.launch {
            exercises.catalog()
                .catch { mutableUiState.update { it.copy(exercises = emptyList()) } }
                .collect { list -> mutableUiState.update { it.copy(exercises = list) } }
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
                .catch { error -> mutableUiState.update { it.copy(activities = UiState.Error(error.toFailureKind())) } }
                .collect { activities -> mutableUiState.update { it.copy(activities = UiState.Content(activities)) } }
        }
    }

    fun stopObserving() {
        activitiesJob?.cancel()
        membersJob?.cancel()
        routinesJob?.cancel()
        exercisesJob?.cancel()
        activitiesJob = null
        membersJob = null
        routinesJob = null
        exercisesJob = null
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
        val trimmed = name.trim()
        // Un alta desde la hoja ofrece «Deshacer» (con el espacio de ahora) en lugar del aviso de actividad añadida.
        saver.run<CreatedItem>(editor = true, afterSuccess = { undo.offer(Undoable.Created(spaceId, it)) }) {
            val id = repository.addActivity(spaceId, date, type, trimmed, participantIds, routineId, session)
            CreatedItem(id, trimmed)
        }
    }

    // Única entrada de «Deshacer»: siempre en el espacio donde pasó (no en el que se esté viendo ahora). Restaurar
    // devuelve la actividad con su id y su estado y, con ella, su evento enlazado tal como estaba; deshacer un alta
    // borra solo lo que nadie más ha tocado. No es un guardado del editor ni lleva aviso propio; con force no se
    // descarta en silencio por otra escritura en curso. Si falla (también si otro miembro cambió la actividad), sale
    // el error.
    fun undo(undoable: Undoable<DeletedActivity>) {
        val spaceId = undoable.spaceId ?: return
        saver.run<Unit>(force = true) {
            when (undoable) {
                is Undoable.Created -> repository.undoAddActivity(spaceId, undoable.item.id)
                is Undoable.Deleted -> repository.restoreActivity(spaceId, undoable.item.activity, undoable.item.event)
                is Undoable.Completed -> Unit
            }
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
        saver.run(notice = SportUiMessage.ActivityUpdated, editor = true) {
            repository.updateActivity(spaceId, activityId, date, type, name.trim(), participantIds, routineId, session)
        }
    }

    fun setDone(activityId: String, done: Boolean) {
        val spaceId = observedSpaceId ?: return
        saver.run(notice = if (done) SportUiMessage.ActivityDone else SportUiMessage.ActivityPending) {
            repository.setDone(spaceId, activityId, done)
        }
    }

    // La oferta de «Deshacer» sale solo si el borrado termina bien, con la foto del evento enlazado que ha borrado, y
    // en el espacio de ahora. Sin aviso propio: el snackbar de «Deshacer» ya confirma el borrado.
    fun deleteActivity(activity: SportActivity) {
        val spaceId = observedSpaceId ?: return
        saver.run<CalendarEvent?>(
            afterSuccess = { undo.offer(Undoable.Deleted(spaceId, DeletedActivity(activity, it))) },
        ) { repository.deleteActivity(spaceId, activity.id) }
    }

    private fun validate(name: String): Boolean {
        val error = SportValidation.validate(name) ?: return true
        saver.showEditorError(error)
        return false
    }
}

private fun Throwable.toUiMessage() = if (this is SaveTimeoutException) {
    SportUiMessage.SaveTimeout
} else {
    toRepositoryUiMessage()
}

private fun Throwable.toRepositoryUiMessage() = when ((this as? SportRepositoryException)?.failure) {
    SportFailure.NameRequired -> SportUiMessage.NameRequired
    SportFailure.NameTooLong -> SportUiMessage.NameTooLong
    SportFailure.InvalidParticipants -> SportUiMessage.InvalidParticipants
    SportFailure.NotAuthenticated -> SportUiMessage.NotAuthenticated
    SportFailure.EmailNotVerified -> SportUiMessage.EmailNotVerified
    SportFailure.SpaceNotFound -> SportUiMessage.SpaceNotFound
    SportFailure.ActivityNotFound -> SportUiMessage.ActivityNotFound
    SportFailure.ActivityChanged -> SportUiMessage.ActivityChanged
    SportFailure.PermissionDenied -> SportUiMessage.PermissionDenied
    SportFailure.Network -> SportUiMessage.NetworkError
    SportFailure.Unknown, null -> SportUiMessage.UnexpectedError
}
