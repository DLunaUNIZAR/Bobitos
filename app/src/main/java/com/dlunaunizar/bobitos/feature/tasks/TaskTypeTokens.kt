package com.dlunaunizar.bobitos.feature.tasks

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.LocalDarkTheme
import com.dlunaunizar.bobitos.core.designsystem.theme.themedColor
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskType

@get:StringRes
internal val TaskType.labelRes: Int
    get() = when (this) {
        TaskType.LIMPIEZA -> R.string.task_type_cleaning
        TaskType.MEDICO -> R.string.task_type_medical
        TaskType.COMPRAS -> R.string.task_type_shopping
        TaskType.HOGAR -> R.string.task_type_home
        TaskType.OTROS -> R.string.task_type_other
    }

internal val TaskType.icon: ImageVector
    get() = when (this) {
        TaskType.LIMPIEZA -> Icons.Rounded.CleaningServices
        TaskType.MEDICO -> Icons.Rounded.MedicalServices
        TaskType.COMPRAS -> Icons.Rounded.ShoppingBag
        TaskType.HOGAR -> Icons.Rounded.Home
        TaskType.OTROS -> Icons.Rounded.Label
    }

// Color de categoría por tipo de tarea, con variante clara (oscura sobre fondo claro) y oscura
// (clara sobre fondo oscuro). Cada una pasa 4,5:1 como texto sobre la tarjeta teñida.
internal fun TaskType.accent(dark: Boolean): Color = when (this) {
    TaskType.LIMPIEZA -> themedColor(dark, 0xFF006359, 0xFF00C7B3)
    TaskType.MEDICO -> themedColor(dark, 0xFFA82222, 0xFFECA0A0)
    TaskType.COMPRAS -> themedColor(dark, 0xFF904019, 0xFFE9A481)
    TaskType.HOGAR -> themedColor(dark, 0xFF3E50B2, 0xFFAAB2E1)
    TaskType.OTROS -> themedColor(dark, 0xFF595959, 0xFFB4B4B4)
}

@Composable
internal fun TaskType.accent(): Color = accent(LocalDarkTheme.current)

// Acento de prioridad para la bandera de la línea de metadatos (rojo = alta, ámbar = media, gris = baja).
internal fun TaskPriority.accent(dark: Boolean): Color = when (this) {
    TaskPriority.HIGH -> themedColor(dark, 0xFFA62323, 0xFFEBA0A0)
    TaskPriority.MEDIUM -> themedColor(dark, 0xFF7C4E03, 0xFFF9A927)
    TaskPriority.LOW -> themedColor(dark, 0xFF595959, 0xFFB4B4B4)
}

@Composable
internal fun TaskPriority.accent(): Color = accent(LocalDarkTheme.current)
