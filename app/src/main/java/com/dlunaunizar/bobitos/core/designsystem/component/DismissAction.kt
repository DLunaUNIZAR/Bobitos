package com.dlunaunizar.bobitos.core.designsystem.component

enum class DismissAction { CLOSE, ASK_DISCARD, BLOCK }

/** Qué hacer cuando la persona intenta cerrar un formulario (gesto, tocar fuera o «Cancelar»). */
fun dismissActionFor(dirty: Boolean, saving: Boolean): DismissAction = when {
    saving -> DismissAction.BLOCK
    dirty -> DismissAction.ASK_DISCARD
    else -> DismissAction.CLOSE
}
