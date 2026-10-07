package com.dlunaunizar.bobitos.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppModule { SHOPPING, TASKS, CALENDAR, MEALS, SPORT }

/** Color de identidad de un módulo: acento (iconos, indicadores) y contenedor con su texto. */
@Immutable
data class ModuleColors(val accent: Color, val container: Color, val onContainer: Color)

/** Lo provee [BobitosTheme] para que los colores de módulo sigan el tema elegido en la app. */
val LocalDarkTheme = compositionLocalOf { false }

object ModulePalette {
    fun colors(module: AppModule, dark: Boolean): ModuleColors =
        if (dark) darkColors.getValue(module) else lightColors.getValue(module)

    private val lightColors = mapOf(
        AppModule.SHOPPING to ModuleColors(Color(0xFFC05621), Color(0xFFFBE3D4), Color(0xFF3B1A08)),
        AppModule.TASKS to ModuleColors(Color(0xFF7E57C2), Color(0xFFE8DEF8), Color(0xFF241046)),
        AppModule.CALENDAR to ModuleColors(Color(0xFF00897B), Color(0xFFD0F0EB), Color(0xFF00201C)),
        AppModule.MEALS to ModuleColors(Color(0xFFAD1457), Color(0xFFF9DCE8), Color(0xFF3E0721)),
        AppModule.SPORT to ModuleColors(Color(0xFF1565C0), Color(0xFFD6E6FA), Color(0xFF05213F)),
    )

    private val darkColors = mapOf(
        AppModule.SHOPPING to ModuleColors(Color(0xFFF2A173), Color(0xFF5A2A0E), Color(0xFFFBE3D4)),
        AppModule.TASKS to ModuleColors(Color(0xFFB9A0EA), Color(0xFF3D2A66), Color(0xFFE8DEF8)),
        AppModule.CALENDAR to ModuleColors(Color(0xFF55CDBF), Color(0xFF004D44), Color(0xFFD0F0EB)),
        AppModule.MEALS to ModuleColors(Color(0xFFF08DB5), Color(0xFF63123A), Color(0xFFF9DCE8)),
        AppModule.SPORT to ModuleColors(Color(0xFF7FB2F0), Color(0xFF173F6E), Color(0xFFD6E6FA)),
    )
}

@Composable
fun moduleColors(module: AppModule): ModuleColors = ModulePalette.colors(module, LocalDarkTheme.current)
