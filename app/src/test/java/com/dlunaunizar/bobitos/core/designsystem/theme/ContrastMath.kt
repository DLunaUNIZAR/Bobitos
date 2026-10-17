package com.dlunaunizar.bobitos.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

// Utilidades de contraste WCAG compartidas por los tests de color.
private const val TINT = 0.12f
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

// Tinte de tarjeta como categoryCardColors: fondo * 0.88 + color * 0.12.
internal fun tinted(background: Color, color: Color): Color = Color(
    red = background.red * (1 - TINT) + color.red * TINT,
    green = background.green * (1 - TINT) + color.green * TINT,
    blue = background.blue * (1 - TINT) + color.blue * TINT,
)
