@file:Suppress("MatchingDeclarationName")

package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.SaveFeedbackControls
import com.dlunaunizar.bobitos.core.common.SaveState

/** Lo que toca avisar en el snackbar de la pantalla. */
sealed interface ScreenFeedback<out E, out N> {
    data class Error<E>(val error: E) : ScreenFeedback<E, Nothing>

    data class Notice<N>(val notice: N) : ScreenFeedback<Nothing, N>
}

/**
 * Qué avisar ahora: nada mientras algo cubre la pantalla ([covered]: un editor u otra hoja abierta; el aviso
 * espera y sale al descubrirse); si no, el error gana al aviso. El error del editor nunca sale aquí.
 */
fun <E, N> SaveState<E, N>.screenFeedback(covered: Boolean): ScreenFeedback<E, N>? = when {
    covered -> null
    error != null -> ScreenFeedback.Error(error)
    notice != null -> ScreenFeedback.Notice(notice)
    else -> null
}

/** Muestra el aviso de [save] en el snackbar y lo consume (el error en largo, el aviso en corto). */
@Composable
fun <E, N> SaveFeedbackEffect(
    save: SaveState<E, N>,
    controls: SaveFeedbackControls,
    covered: Boolean,
    errorText: @Composable (E) -> String,
    noticeText: @Composable (N) -> String,
) {
    val feedback = save.screenFeedback(covered)
    val shown = when (feedback) {
        is ScreenFeedback.Error -> Feedback(errorText(feedback.error), isError = true)
        is ScreenFeedback.Notice -> Feedback(noticeText(feedback.notice), isError = false)
        null -> null
    }
    FeedbackSnackbarEffect(shown) {
        when (feedback) {
            is ScreenFeedback.Error -> controls.consumeErrorIf(feedback.error as Any)
            is ScreenFeedback.Notice -> controls.consumeNoticeIf(feedback.notice as Any)
            null -> Unit
        }
    }
}

/**
 * Cierra el editor cuando el servidor confirma (SAVED) y lo suelta. Un FAILED con el editor ya cerrado
 * pasa su error a la pantalla.
 */
@Composable
fun EditorSaveEffect(
    editor: EditorSaveStatus,
    editorOpen: Boolean,
    controls: SaveFeedbackControls,
    onClose: () -> Unit,
) {
    val currentClose by rememberUpdatedState(onClose)
    val currentControls by rememberUpdatedState(controls)
    LaunchedEffect(editor, editorOpen) {
        when {
            editor == EditorSaveStatus.SAVED -> {
                currentClose()
                currentControls.resetEditor()
            }
            editor == EditorSaveStatus.FAILED && !editorOpen -> currentControls.releaseEditorError()
        }
    }
}
