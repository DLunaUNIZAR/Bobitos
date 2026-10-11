package com.dlunaunizar.bobitos.core.designsystem.theme

/**
 * Modo de tema elegido por la persona usuaria.
 *
 * Es independiente del ajuste del sistema: por defecto la app usa [DARK] y solo sigue
 * al móvil si se elige explícitamente [SYSTEM].
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}

/** Modo que se aplica mientras la persona no ha elegido otro. */
val DEFAULT_THEME_MODE: ThemeMode = ThemeMode.DARK

/** Convierte el nombre guardado en un modo; `null` o un valor desconocido dan [DEFAULT_THEME_MODE]. */
internal fun parseThemeMode(stored: String?): ThemeMode =
    stored?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: DEFAULT_THEME_MODE
