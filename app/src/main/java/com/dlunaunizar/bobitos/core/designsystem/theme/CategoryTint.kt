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
    CardDefaults.cardColors(containerColor = accent.copy(alpha = CATEGORY_TINT_ALPHA))

/** Tinte opaco de una fila agrupada: [accent] al 12 % sobre `surfaceContainer`, como [categoryCardColors]. */
@Composable
fun categoryRowTint(accent: Color): Color =
    accent.copy(alpha = CATEGORY_TINT_ALPHA).compositeOver(MaterialTheme.colorScheme.surfaceContainer)

private const val CATEGORY_TINT_ALPHA = 0.12f

/** Elige entre la variante clara y la oscura de un color de categoría (valores ARGB). */
internal fun themedColor(dark: Boolean, light: Long, darkValue: Long): Color = Color(if (dark) darkValue else light)
