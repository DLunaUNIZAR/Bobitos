package com.dlunaunizar.bobitos.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.SaveTimeoutException
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.failed
import com.dlunaunizar.bobitos.core.common.started
import com.dlunaunizar.bobitos.core.common.succeeded
import com.dlunaunizar.bobitos.core.common.toFailureKind
import com.dlunaunizar.bobitos.core.common.withEditorSaveTimeout
import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import com.dlunaunizar.bobitos.core.model.RoutineVisibility
import com.dlunaunizar.bobitos.data.repository.ExerciseRepository
import com.dlunaunizar.bobitos.data.repository.RoutineFailure
import com.dlunaunizar.bobitos.data.repository.RoutineRepository
import com.dlunaunizar.bobitos.data.repository.RoutineRepositoryException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
        // Un alta desde la hoja deja `created` (con «Deshacer») en lugar del aviso de guardado.
        runAction(null, editor = true) {
            CreatedItem(repository.createRoutine(visibility, trimmed, description.normalized(), exercises), trimmed)
        }
    }

    fun updateRoutine(routineId: String, title: String, description: String?, exercises: List<RoutineExercise>) {
        if (!validate(title, description)) return
        runAction(RoutineUiMessage.Saved, editor = true) {
            repository.updateRoutine(routineId, title.trim(), description.normalized(), exercises)
        }
    }

    // onDeleted se llama solo si el borrado termina bien: así el «Deshacer» no se ofrece para un borrado
    // fallido. Con [undoable] el aviso lo da el snackbar de «Deshacer» de la pantalla; si no (una común), el suyo.
    fun deleteRoutine(routineId: String, undoable: Boolean = true, onDeleted: () -> Unit = {}) {
        val notice = if (undoable) null else RoutineUiMessage.Deleted
        runAction(notice, onSuccess = onDeleted) { repository.deleteRoutine(routineId) }
    }

    // «Deshacer» el borrado de una rutina personal (con el mismo id; el dueño pasa a ser quien deshace).
    fun restoreRoutine(routine: Routine) {
        runAction(null, force = true) { repository.restoreRoutine(routine) }
    }

    // Copia privada de una común («Guardar como mía»), enlazada con la original por sourceRoutineId.
    fun fork(source: Routine) {
        // «Guardar como mía» deja `created` con la copia (su id, no el de la original) en lugar del aviso de copia.
        runAction(null) {
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

    fun consumeCreated() = mutableUiState.update { it.copy(created = null) }

    // «Deshacer» el alta: borra justo esa rutina con el borrado de siempre (sin aviso; si falla, sale el error).
    fun undoCreate(id: String) = runAction(null, force = true) { repository.deleteRoutine(id) }

    fun consumeEditorSave() = mutableUiState.update { it.copy(editorSave = EditorSaveStatus.IDLE) }

    fun clearFeedback() = mutableUiState.update { it.copy(error = null, notice = null) }

    private fun validate(title: String, description: String?): Boolean {
        val error = RoutinesValidation.validate(title, description) ?: return true
        showError(error, editor = true)
        return false
    }

    private fun showError(message: RoutineUiMessage, editor: Boolean = false) = mutableUiState.update {
        it.copy(isSaving = false, error = message, notice = null, editorSave = it.editorSave.failed(editor))
    }

    // Si la acción devuelve un [CreatedItem], es un alta desde la hoja y queda en `created`.
    private fun runAction(
        successNotice: RoutineUiMessage?,
        editor: Boolean = false,
        force: Boolean = false,
        onSuccess: () -> Unit = {},
        action: suspend () -> Any?,
    ) {
        if (mutableUiState.value.isSaving && !force) return
        mutableUiState.update {
            it.copy(isSaving = true, error = null, notice = null, editorSave = it.editorSave.started(editor))
        }
        viewModelScope.launch {
            runCatching { withEditorSaveTimeout(editor) { action() } }
                .onSuccess { result ->
                    mutableUiState.update {
                        it.copy(
                            isSaving = false,
                            notice = successNotice,
                            created = result as? CreatedItem,
                            editorSave = it.editorSave.succeeded(editor),
                        )
                    }
                    onSuccess()
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    showError(error.toUiMessage(), editor)
                }
        }
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
