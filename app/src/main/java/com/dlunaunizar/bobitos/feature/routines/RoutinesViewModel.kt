package com.dlunaunizar.bobitos.feature.routines

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
import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import com.dlunaunizar.bobitos.core.model.RoutineVisibility
import com.dlunaunizar.bobitos.data.repository.ExerciseRepository
import com.dlunaunizar.bobitos.data.repository.RoutineFailure
import com.dlunaunizar.bobitos.data.repository.RoutineRepository
import com.dlunaunizar.bobitos.data.repository.RoutineRepositoryException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val repository: RoutineRepository,
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(RoutinesUiState())
    val uiState: StateFlow<RoutinesUiState> = mutableUiState.asStateFlow()
    private val saver =
        SaveRunner(viewModelScope, mutableUiState, RoutinesUiState::save, { copy(save = it) }, Throwable::toUiMessage)
    val feedback: SaveFeedbackControls get() = saver
    private val undo = UndoOffers<Routine>(viewModelScope)
    val undoOffers: StateFlow<List<UndoOffer<Routine>>> = undo.offers
    val undoControls: UndoOfferControls get() = undo

    private var globalJob: Job? = null
    private var mineJob: Job? = null
    private var exercisesJob: Job? = null
    private var observing = false

    fun observe() {
        if (observing) return
        observing = true
        mutableUiState.update { it.copy(isAdmin = repository.isCurrentUserRoutineAdmin()) }
        globalJob = viewModelScope.launch {
            repository.globalRoutines()
                .catch { error -> mutableUiState.update { it.copy(global = UiState.Error(error.toFailureKind())) } }
                .collect { list -> mutableUiState.update { it.copy(global = UiState.Content(list)) } }
        }
        mineJob = viewModelScope.launch {
            repository.myRoutines()
                .catch { error -> mutableUiState.update { it.copy(mine = UiState.Error(error.toFailureKind())) } }
                // «Mis rutinas» = solo privadas; las GLOBAL (también las del admin) llegan por `global`.
                .collect { list -> mutableUiState.update { it.copy(mine = UiState.Content(list.personalOnly())) } }
        }
    }

    // Reintento tras un fallo de carga: reinicia solo las dos listas de rutinas.
    fun retry() {
        globalJob?.cancel()
        mineJob?.cancel()
        observing = false
        mutableUiState.update { it.copy(global = UiState.Loading, mine = UiState.Loading) }
        observe()
    }

    // Catálogo de ejercicios para el editor de rutina. Diferido: lo pide el editor al aparecer. Idempotente;
    // un fallo se ignora (el selector quedará vacío).
    fun observeExerciseCatalog() {
        if (exercisesJob?.isActive == true) return
        exercisesJob = viewModelScope.launch {
            exerciseRepository.catalog()
                .catch { mutableUiState.update { it.copy(exercises = emptyList()) } }
                .collect { list -> mutableUiState.update { it.copy(exercises = list) } }
        }
    }

    fun stopObserving() {
        globalJob?.cancel()
        mineJob?.cancel()
        exercisesJob?.cancel()
        globalJob = null
        mineJob = null
        exercisesJob = null
        observing = false
    }

    fun setQuery(query: String) = mutableUiState.update { it.copy(query = query) }

    fun createRoutine(
        visibility: RoutineVisibility,
        title: String,
        description: String?,
        exercises: List<RoutineExercise>,
    ) {
        if (!validate(title, description)) return
        val trimmed = title.trim()
        // Un alta desde la hoja ofrece «Deshacer» en lugar del aviso de guardado.
        saver.run<CreatedItem>(editor = true, afterSuccess = { undo.offer(Undoable.Created(null, it)) }) {
            CreatedItem(repository.createRoutine(visibility, trimmed, description.normalized(), exercises), trimmed)
        }
    }

    fun updateRoutine(routineId: String, title: String, description: String?, exercises: List<RoutineExercise>) {
        if (!validate(title, description)) return
        saver.run(notice = RoutineUiMessage.Saved, editor = true) {
            repository.updateRoutine(routineId, title.trim(), description.normalized(), exercises)
        }
    }

    // Una personal ofrece «Deshacer» solo si el borrado termina bien y sin aviso propio (el snackbar ya lo confirma);
    // una común (la borra el admin) no se puede deshacer y avisa con el suyo.
    fun deleteRoutine(routine: Routine) {
        if (routine.canUndoDelete()) {
            saver.run(afterSuccess = { undo.offer(Undoable.Deleted(null, routine)) }) {
                repository.deleteRoutine(routine.id)
            }
        } else {
            saver.run(notice = RoutineUiMessage.Deleted) { repository.deleteRoutine(routine.id) }
        }
    }

    // Única entrada de «Deshacer». Sin aviso propio; si falla, sale el error.
    fun undo(undoable: Undoable<Routine>) {
        saver.run(force = true) {
            when (undoable) {
                // Borra justo esa rutina con el borrado de siempre.
                is Undoable.Created -> repository.deleteRoutine(undoable.item.id)
                // Con el mismo id; el dueño pasa a ser quien deshace.
                is Undoable.Deleted -> repository.restoreRoutine(undoable.item)
                is Undoable.Completed -> Unit
            }
        }
    }

    // Copia privada de una común («Guardar como mía»), enlazada con la original por sourceRoutineId.
    fun fork(source: Routine) {
        // «Guardar como mía» ofrece «Deshacer» de la copia (su id, no el de la original) en lugar del aviso de copia.
        saver.run<CreatedItem>(afterSuccess = { undo.offer(Undoable.Created(null, it)) }) {
            val id = repository.createRoutine(
                visibility = RoutineVisibility.PRIVATE,
                title = source.title,
                description = source.description,
                exercises = source.exercises.orEmpty(),
                sourceRoutineId = source.id,
            )
            CreatedItem(id, source.title)
        }
    }

    private fun validate(title: String, description: String?): Boolean {
        val error = RoutinesValidation.validate(title, description) ?: return true
        saver.showEditorError(error)
        return false
    }
}

// Solo las personales se pueden devolver: las comunes las borra el admin y se confirman con un diálogo.
internal fun Routine.canUndoDelete(): Boolean = visibility == RoutineVisibility.PRIVATE

private fun String?.normalized() = this?.trim()?.takeIf(String::isNotEmpty)

private fun Throwable.toUiMessage() = if (this is SaveTimeoutException) {
    RoutineUiMessage.SaveTimeout
} else {
    toRepositoryUiMessage()
}

private fun Throwable.toRepositoryUiMessage() = when ((this as? RoutineRepositoryException)?.failure) {
    RoutineFailure.TitleRequired -> RoutineUiMessage.TitleRequired
    RoutineFailure.TitleTooLong -> RoutineUiMessage.TitleTooLong
    RoutineFailure.DescriptionTooLong -> RoutineUiMessage.DescriptionTooLong
    RoutineFailure.NotAuthenticated -> RoutineUiMessage.NotAuthenticated
    RoutineFailure.EmailNotVerified -> RoutineUiMessage.EmailNotVerified
    RoutineFailure.RoutineNotFound -> RoutineUiMessage.NotFound
    RoutineFailure.PermissionDenied -> RoutineUiMessage.PermissionDenied
    RoutineFailure.Network -> RoutineUiMessage.NetworkError
    RoutineFailure.Unknown, null -> RoutineUiMessage.UnexpectedError
}
