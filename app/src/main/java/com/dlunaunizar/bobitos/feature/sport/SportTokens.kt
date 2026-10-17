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
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.LocalDarkTheme
import com.dlunaunizar.bobitos.core.designsystem.theme.themedColor
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

// Color de categoría por tipo de actividad (variante clara y oscura).
internal fun SportType.accent(dark: Boolean): Color = when (this) {
    SportType.PADEL -> themedColor(dark, 0xFF006359, 0xFF00C7B3)
    SportType.FUTBOL -> themedColor(dark, 0xFF256528, 0xFF68C76D)
    SportType.CAMINATA -> themedColor(dark, 0xFF6D4C41, 0xFFCAAFA6)
    SportType.CORRER -> themedColor(dark, 0xFF904019, 0xFFE9A481)
    SportType.PILATES -> themedColor(dark, 0xFF6940B0, 0xFFBEABE0)
    SportType.GIMNASIO -> themedColor(dark, 0xFF1257A5, 0xFF85B8F2)
    SportType.OTROS -> themedColor(dark, 0xFF595959, 0xFFB4B4B4)
}

@Composable
internal fun SportType.accent(): Color = accent(LocalDarkTheme.current)
