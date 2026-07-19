package com.dlunaunizar.bobitos.core.designsystem.theme

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Colores de card teñidos suavemente con [accent] (color de categoría: supermercado,
 * tipo de tarea, módulo…). El tinte es translúcido sobre la superficie y el contenido
 * conserva el color por defecto (onSurface), de modo que sigue siendo legible tanto en
 * claro como en oscuro. Pensado para dar identidad de color sin comprometer el contraste.
 */
@Composable
fun categoryCardColors(accent: Color): CardColors =
    CardDefaults.cardColors(containerColor = accent.copy(alpha = CATEGORY_TINT_ALPHA))

private const val CATEGORY_TINT_ALPHA = 0.12f
