package com.dlunaunizar.bobitos.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

// Utilidades de contraste WCAG compartidas por los tests de color.
private const val TINT_LIGHT = 0.07f
private const val TINT_DARK = 0.12f
private const val CHIP_TINT = 0.12f
private const val OFFSET = 0.05
private const val LINEAR_THRESHOLD = 0.03928
private const val LINEAR_DIVISOR = 12.92
private const val GAMMA_OFFSET = 0.055
private const val GAMMA_SCALE = 1.055
private const val GAMMA = 2.4
private const val WEIGHT_R = 0.2126
private const val WEIGHT_G = 0.7152
private const val WEIGHT_B = 0.0722

private fun channel(value: Float): Double {
    val v = value.toDouble()
    return if (v <= LINEAR_THRESHOLD) v / LINEAR_DIVISOR else ((v + GAMMA_OFFSET) / GAMMA_SCALE).pow(GAMMA)
}

internal fun luminance(c: Color): Double =
    WEIGHT_R * channel(c.red) + WEIGHT_G * channel(c.green) + WEIGHT_B * channel(c.blue)

internal fun contrast(a: Color, b: Color): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (maxOf(la, lb) + OFFSET) / (minOf(la, lb) + OFFSET)
}

private fun mix(background: Color, color: Color, alpha: Float) = Color(
    red = background.red * (1 - alpha) + color.red * alpha,
    green = background.green * (1 - alpha) + color.green * alpha,
    blue = background.blue * (1 - alpha) + color.blue * alpha,
)

// Tinte de tarjeta y de fila de categoría: 0,07 en claro y 0,12 en oscuro.
internal fun tinted(background: Color, color: Color, dark: Boolean): Color =
    mix(background, color, if (dark) TINT_DARK else TINT_LIGHT)

// Fondo del InfoChip: no es una fila, conserva el 0,12 en ambos temas.
internal fun chipTinted(background: Color, color: Color): Color = mix(background, color, CHIP_TINT)
