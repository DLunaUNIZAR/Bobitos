package com.dlunaunizar.bobitos.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

enum class AppModule { SHOPPING, TASKS, CALENDAR, MEALS, SPORT, NOTES, PANTRY }

/**
 * Color de identidad de un módulo: acento (iconos, texto de enlace, indicadores), lo que se
 * pinta encima del acento usado como relleno ([onAccent]) y contenedor con su texto.
 */
@Immutable
data class ModuleColors(val accent: Color, val onAccent: Color, val container: Color, val onContainer: Color)

/** Lo provee [BobitosTheme] para que los colores de módulo sigan el tema elegido en la app. */
val LocalDarkTheme = compositionLocalOf { false }

object ModulePalette {
    fun colors(module: AppModule, dark: Boolean): ModuleColors =
        if (dark) darkColors.getValue(module) else lightColors.getValue(module)

    /** Tinte opaco de fila y de cabecera: el acento sobre el fondo, como `categoryCardColors` pero sin alfa. */
    fun tint(module: AppModule, dark: Boolean): Color {
        val alpha = if (dark) TINT_ALPHA_DARK else TINT_ALPHA_LIGHT
        return colors(
            module,
            dark,
        ).accent.copy(alpha = alpha).compositeOver(if (dark) backgroundDark else backgroundLight)
    }

    private const val TINT_ALPHA_DARK = 0.16f
    private const val TINT_ALPHA_LIGHT = 0.12f

    // Acentos claros oscurecidos dentro de su tono para leerse como texto (4,5:1) sobre el fondo y el tinte.
    private val lightColors = mapOf(
        AppModule.SHOPPING to ModuleColors(Color(0xFFA9461A), Color.White, Color(0xFFFBE3D4), Color(0xFF3B1A08)),
        AppModule.TASKS to ModuleColors(Color(0xFF7048B8), Color.White, Color(0xFFE8DEF8), Color(0xFF241046)),
        AppModule.CALENDAR to ModuleColors(Color(0xFF0D6B64), Color.White, Color(0xFFD0F0EB), Color(0xFF00201C)),
        AppModule.MEALS to ModuleColors(Color(0xFFAD1457), Color.White, Color(0xFFF9DCE8), Color(0xFF3E0721)),
        AppModule.SPORT to ModuleColors(Color(0xFF1565C0), Color.White, Color(0xFFD6E6FA), Color(0xFF05213F)),
        AppModule.NOTES to ModuleColors(Color(0xFF7A5C00), Color.White, Color(0xFFFFE9A8), Color(0xFF2B2000)),
        AppModule.PANTRY to ModuleColors(Color(0xFF276B2B), Color.White, Color(0xFFCDEFC8), Color(0xFF0A2A0D)),
    )

    private val darkColors = mapOf(
        AppModule.SHOPPING to ModuleColors(Color(0xFFF2A173), OnAccentDark, Color(0xFF5A2A0E), Color(0xFFFBE3D4)),
        AppModule.TASKS to ModuleColors(Color(0xFFB9A0EA), OnAccentDark, Color(0xFF3D2A66), Color(0xFFE8DEF8)),
        AppModule.CALENDAR to ModuleColors(Color(0xFF55CDBF), OnAccentDark, Color(0xFF004D44), Color(0xFFD0F0EB)),
        AppModule.MEALS to ModuleColors(Color(0xFFF08DB5), OnAccentDark, Color(0xFF63123A), Color(0xFFF9DCE8)),
        AppModule.SPORT to ModuleColors(Color(0xFF7FB2F0), OnAccentDark, Color(0xFF173F6E), Color(0xFFD6E6FA)),
        AppModule.NOTES to ModuleColors(Color(0xFFE8C35A), OnAccentDark, Color(0xFF4D3B00), Color(0xFFFFE9A8)),
        AppModule.PANTRY to ModuleColors(Color(0xFF8FD18A), OnAccentDark, Color(0xFF1F4D1C), Color(0xFFCDEFC8)),
    )
}

// Sobre los acentos claros del tema oscuro va el fondo oscuro de la app.
private val OnAccentDark = Color(0xFF141513)

@Composable
fun moduleColors(module: AppModule): ModuleColors = ModulePalette.colors(module, LocalDarkTheme.current)

/** Tinte opaco de fila y de cabecera del módulo en el tema actual. */
@Composable
fun moduleTint(module: AppModule): Color = ModulePalette.tint(module, LocalDarkTheme.current)
