@file:Suppress("MatchingDeclarationName")

package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/**
 * Estado de validación de un formulario: no se enseña ningún error hasta el primer intento de guardar;
 * desde entonces los errores se recalculan en cada composición y desaparecen al corregirlos.
 */
@Stable
class FormValidationState(attempted: Boolean = false) {
    var attempted: Boolean by mutableStateOf(attempted)

    /** El [error] solo es visible tras el primer intento de guardar. */
    fun <E> visible(error: E?): E? = error.takeIf { attempted }

    /** Marca el intento y guarda ([onValid]) o lleva el foco al primer error ([onInvalid]). */
    fun submit(isValid: Boolean, onValid: () -> Unit, onInvalid: () -> Unit) {
        attempted = true
        if (isValid) onValid() else onInvalid()
    }

    companion object {
        val Saver: Saver<FormValidationState, Boolean> = Saver(
            save = { it.attempted },
            restore = { FormValidationState(it) },
        )
    }
}

/** Recuerda el [FormValidationState] también al girar el móvil. */
@Composable
fun rememberFormValidationState(): FormValidationState =
    rememberSaveable(saver = FormValidationState.Saver) { FormValidationState() }

/** `supportingText` de un campo con error: el texto va bajo el campo, o nada si no hay error visible. */
fun errorSupportingText(message: String?): (@Composable () -> Unit)? = message?.let {
    { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
}
