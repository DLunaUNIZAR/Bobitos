package com.dlunaunizar.bobitos.feature.sport

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Sports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.SportType

@get:StringRes
internal val SportType.labelRes: Int
    get() = when (this) {
        SportType.PADEL -> R.string.sport_type_padel
        SportType.FUTBOL -> R.string.sport_type_futbol
        SportType.CAMINATA -> R.string.sport_type_caminata
        SportType.CORRER -> R.string.sport_type_correr
        SportType.PILATES -> R.string.sport_type_pilates
        SportType.GIMNASIO -> R.string.sport_type_gimnasio
        SportType.OTROS -> R.string.sport_type_otros
    }

internal val SportType.icon: ImageVector
    get() = when (this) {
        SportType.PADEL -> Icons.Rounded.SportsTennis
        SportType.FUTBOL -> Icons.Rounded.SportsSoccer
        SportType.CAMINATA -> Icons.Rounded.DirectionsWalk
        SportType.CORRER -> Icons.Rounded.DirectionsRun
        SportType.PILATES -> Icons.Rounded.SelfImprovement
        SportType.GIMNASIO -> Icons.Rounded.FitnessCenter
        SportType.OTROS -> Icons.Rounded.Sports
    }

// Color de categoría por tipo de actividad (tonos medios, legibles en claro y oscuro).
internal fun SportType.accent(): Color = when (this) {
    SportType.PADEL -> Color(0xFF00897B)
    SportType.FUTBOL -> Color(0xFF2E7D32)
    SportType.CAMINATA -> Color(0xFF6D4C41)
    SportType.CORRER -> Color(0xFFC05621)
    SportType.PILATES -> Color(0xFF7E57C2)
    SportType.GIMNASIO -> Color(0xFF1565C0)
    SportType.OTROS -> Color(0xFF6E6E6E)
}
