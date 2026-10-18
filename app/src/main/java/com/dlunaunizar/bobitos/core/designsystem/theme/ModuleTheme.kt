package com.dlunaunizar.bobitos.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

/** Módulo cuyo color pinta la pantalla actual; null en las pantallas globales, que conservan la marca. */
val LocalAppModule = compositionLocalOf<AppModule?> { null }

/** Copia [base] con los roles de acento de Material (y su tinte) tomados del módulo. */
fun moduleColorScheme(base: ColorScheme, colors: ModuleColors): ColorScheme = base.copy(
    primary = colors.accent,
    onPrimary = colors.onAccent,
    primaryContainer = colors.container,
    onPrimaryContainer = colors.onContainer,
    secondaryContainer = colors.container,
    onSecondaryContainer = colors.onContainer,
    surfaceTint = colors.accent,
)

/**
 * Tiñe de [module] todo lo que herede los roles de acento (FAB, casillas, botones rellenos, hojas,
 * selector de vista, tira de días). Con null no cambia nada. Conserva tipografía y formas.
 */
@Composable
fun ModuleTheme(module: AppModule?, content: @Composable () -> Unit) {
    if (module == null) {
        content()
        return
    }
    val colors = moduleColors(module)
    CompositionLocalProvider(LocalAppModule provides module) {
        MaterialTheme(
            colorScheme = moduleColorScheme(MaterialTheme.colorScheme, colors),
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}
