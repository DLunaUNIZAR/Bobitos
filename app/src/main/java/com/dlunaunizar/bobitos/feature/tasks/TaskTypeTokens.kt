package com.dlunaunizar.bobitos.feature.tasks

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.ui.graphics.vector.ImageVector
import com.dlunaunizar.bobitos.R
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
