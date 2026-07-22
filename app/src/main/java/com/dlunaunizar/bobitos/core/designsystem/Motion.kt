package com.dlunaunizar.bobitos.core.designsystem

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * true si el sistema tiene las animaciones desactivadas (escala de duración a 0), para respetar la
 * preferencia de «reducir movimiento» y no aplicar transiciones.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
