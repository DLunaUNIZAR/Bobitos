package com.dlunaunizar.bobitos.feature.tasks

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.dlunaunizar.bobitos.R
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

// Color de categoría por tipo de tarea (tonos medios, legibles como acento/tinte suave
// en claro y oscuro), en la línea de EventColor.accent().
internal fun TaskType.accent(): Color = when (this) {
    TaskType.LIMPIEZA -> Color(0xFF00897B)
    TaskType.MEDICO -> Color(0xFFC62828)
    TaskType.COMPRAS -> Color(0xFFC05621)
    TaskType.HOGAR -> Color(0xFF3F51B5)
    TaskType.OTROS -> Color(0xFF6E6E6E)
}

// Acento discreto de prioridad para el punto de la tarjeta (rojo = alta, ámbar = media, gris = baja).
// Se superpone al tinte por tipo sin competir con él.
internal fun TaskPriority.accent(): Color = when (this) {
    TaskPriority.HIGH -> Color(0xFFD32F2F)
    TaskPriority.MEDIUM -> Color(0xFFF9A825)
    TaskPriority.LOW -> Color(0xFF9E9E9E)
}
