package com.dlunaunizar.bobitos.core.designsystem.theme

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * Colores de card teñidos suavemente con [accent] (color de categoría: supermercado,
 * tipo de tarea, módulo…). El tinte es translúcido sobre la superficie y el contenido
 * conserva el color por defecto (onSurface), de modo que sigue siendo legible tanto en
 * claro como en oscuro. Pensado para dar identidad de color sin comprometer el contraste.
 */
@Composable
fun categoryCardColors(accent: Color): CardColors =
    CardDefaults.cardColors(containerColor = accent.copy(alpha = categoryTintAlpha(LocalDarkTheme.current)))

/**
 * Tinte opaco de una fila agrupada: [accent] sobre `surfaceContainer`, como [categoryCardColors]
 * (7 % en claro, 12 % en oscuro).
 */
@Composable
fun categoryRowTint(accent: Color): Color =
    categoryTint(accent, MaterialTheme.colorScheme.surfaceContainer, LocalDarkTheme.current)

/** [accent] opaco sobre [under]: suave en claro para que las filas no parezcan baldosas pastel. */
internal fun categoryTint(accent: Color, under: Color, dark: Boolean): Color =
    accent.copy(alpha = categoryTintAlpha(dark)).compositeOver(under)

private fun categoryTintAlpha(dark: Boolean): Float = if (dark) CATEGORY_TINT_ALPHA_DARK else CATEGORY_TINT_ALPHA_LIGHT

private const val CATEGORY_TINT_ALPHA_DARK = 0.12f
private const val CATEGORY_TINT_ALPHA_LIGHT = 0.07f

/** Elige entre la variante clara y la oscura de un color de categoría (valores ARGB). */
internal fun themedColor(dark: Boolean, light: Long, darkValue: Long): Color = Color(if (dark) darkValue else light)
