package com.dlunaunizar.bobitos.core.common

/**
 * Estado de guardado de un área, embebido en su `UiState`. [E] es el tipo del error y [N] el del aviso.
 * [running] cuenta las acciones en curso (puede haber más de una si alguna se fuerza); [editor] y [editorError]
 * son solo del editor abierto; [error] y [notice] son los de la pantalla; [created] es lo último creado desde una hoja.
 */
data class SaveState<E, N>(
    val running: Int = 0,
    val editor: EditorSaveStatus = EditorSaveStatus.IDLE,
    val editorError: E? = null,
    val error: E? = null,
    val notice: N? = null,
    val created: CreatedItem? = null,
) {
    val isSaving: Boolean get() = running > 0
}
