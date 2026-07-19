package com.dlunaunizar.bobitos.core.designsystem.theme

/**
 * Modo de tema elegido por la persona usuaria.
 *
 * Es independiente del ajuste del sistema: por defecto la app usa [LIGHT] y solo sigue
 * al móvil si se elige explícitamente [SYSTEM].
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}
