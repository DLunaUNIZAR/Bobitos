package com.dlunaunizar.bobitos.feature.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.SaveFeedbackControls
import com.dlunaunizar.bobitos.core.common.SaveRunner
import com.dlunaunizar.bobitos.core.common.SaveTimeoutException
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseInput
import com.dlunaunizar.bobitos.core.model.slug
import com.dlunaunizar.bobitos.data.repository.ExerciseFailure
import com.dlunaunizar.bobitos.data.repository.ExerciseImageRepository
import com.dlunaunizar.bobitos.data.repository.ExerciseRepository
import com.dlunaunizar.bobitos.data.repository.ExerciseRepositoryException
import com.dlunaunizar.bobitos.data.repository.LoadedImage
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
class ExercisesViewModel @Inject constructor(
    private val repository: ExerciseRepository,
    private val imageRepository: ExerciseImageRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ExercisesUiState())
    val uiState: StateFlow<ExercisesUiState> = mutableUiState.asStateFlow()
    private val saver =
        SaveRunner(viewModelScope, mutableUiState, ExercisesUiState::save, { copy(save = it) }, Throwable::toUiMessage)
    val feedback: SaveFeedbackControls get() = saver

    private var catalogJob: Job? = null
    private var observing = false

    // Bytes de la imagen de la ficha (caché local si el hash coincide); null si no hay o no se puede leer.
    suspend fun loadImage(exercise: CatalogExercise): LoadedImage? =
        exercise.image?.let { imageRepository.image(exercise.id, it.hash) }

    fun observe() {
        if (observing) return
        observing = true
        mutableUiState.update {
            it.copy(isAdmin = repository.isCurrentUserCatalogAdmin(), currentUid = repository.currentUserId())
        }
        startCatalog()
    }

    private fun startCatalog() {
        catalogJob = viewModelScope.launch {
            repository.catalog()
                .catch {
                    // Sin red y sin caché: mensaje propio, no el texto técnico de Firebase.
                    mutableUiState.update {
                        it.copy(catalog = UiState.Error(), catalogError = ExerciseUiMessage.CatalogUnavailable)
                    }
                }
                .collect { list ->
                    mutableUiState.update { it.copy(catalog = UiState.Content(list), catalogError = null) }
                }
        }
    }

    fun retryCatalog() {
        if (!observing) return
        catalogJob?.cancel()
        mutableUiState.update { it.copy(catalog = UiState.Loading, catalogError = null) }
        startCatalog()
    }

    fun stopObserving() {
        catalogJob?.cancel()
        catalogJob = null
        observing = false
    }

    fun createExercise(input: ExerciseInput) {
        val trimmed = input.name.trim()
        if (trimmed.isEmpty()) {
            saver.showEditorError(ExerciseUiMessage.NameRequired)
            return
        }
        if (catalogContains(slug(trimmed))) {
            saver.showEditorError(ExerciseUiMessage.AlreadyExists)
            return
        }
        // Un alta desde la hoja deja `created` (con «Deshacer») en lugar del aviso de guardado.
        saver.create(editor = true) { CreatedItem(repository.createExercise(input.copy(name = trimmed)), trimmed) }
    }

    // «Deshacer» el alta: borra justo ese ejercicio con el borrado de siempre (sin aviso; si falla, sale el error).
    fun undoCreate(id: String) = saver.run(force = true) { repository.deleteExercise(id) }

    fun updateExercise(id: String, input: ExerciseInput) {
        if (input.name.trim().isEmpty()) {
            saver.showEditorError(ExerciseUiMessage.NameRequired)
            return
        }
        saver.run(notice = ExerciseUiMessage.Saved, editor = true) {
            repository.updateExercise(id, input.copy(name = input.name.trim()))
        }
    }

    // onDeleted se llama solo si el borrado termina bien: así el «Deshacer» no se ofrece para un borrado fallido.
    // Con [undoable] el aviso lo da el snackbar de «Deshacer» de la pantalla; si no (ficha del catálogo común), el suyo.
    fun deleteExercise(id: String, undoable: Boolean = true, onDeleted: () -> Unit = {}) {
        val notice = if (undoable) null else ExerciseUiMessage.Deleted
        saver.run(notice = notice, afterSuccess = { onDeleted() }) { repository.deleteExercise(id) }
    }

    // «Deshacer» el borrado de un ejercicio propio (con el mismo id; el dueño pasa a ser quien deshace).
    fun restoreExercise(exercise: CatalogExercise) {
        saver.run(force = true) { repository.restoreExercise(exercise) }
    }

    private fun catalogContains(id: String): Boolean =
        (mutableUiState.value.catalog as? UiState.Content)?.value?.any { it.id == id } == true
}

// Solo se devuelven los propios sin fuente ni imagen: lo que el admin borra del catálogo común se confirma con un diálogo.
internal fun CatalogExercise.canUndoDelete(currentUid: String?): Boolean =
    ownerUid == currentUid && source == null && image == null

private fun Throwable.toUiMessage(): ExerciseUiMessage = if (this is SaveTimeoutException) {
    ExerciseUiMessage.SaveTimeout
} else {
    toRepositoryUiMessage()
}

private fun Throwable.toRepositoryUiMessage(): ExerciseUiMessage =
    when ((this as? ExerciseRepositoryException)?.failure) {
        ExerciseFailure.NameRequired -> ExerciseUiMessage.NameRequired
        ExerciseFailure.NameTooLong -> ExerciseUiMessage.NameTooLong
        ExerciseFailure.MuscleGroupTooLong -> ExerciseUiMessage.MuscleGroupTooLong
        ExerciseFailure.DescriptionTooLong -> ExerciseUiMessage.DescriptionTooLong
        ExerciseFailure.NotAuthenticated -> ExerciseUiMessage.NotAuthenticated
        ExerciseFailure.EmailNotVerified -> ExerciseUiMessage.EmailNotVerified
        ExerciseFailure.ExerciseNotFound -> ExerciseUiMessage.NotFound
        ExerciseFailure.PermissionDenied -> ExerciseUiMessage.PermissionDenied
        ExerciseFailure.AlreadyExists -> ExerciseUiMessage.AlreadyExists
        ExerciseFailure.Network -> ExerciseUiMessage.NetworkError
        ExerciseFailure.Unknown, null -> ExerciseUiMessage.UnexpectedError
    }
