package com.dlunaunizar.bobitos.feature.calendar

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.LocalDarkTheme
import com.dlunaunizar.bobitos.core.designsystem.theme.themedColor
import com.dlunaunizar.bobitos.core.model.EventColor

// Paleta categórica de los colores de evento (los elige la persona por evento), independiente
// del tema de marca, con variante clara y oscura. Se usan como banda, punto o muestra (>= 3:1).
internal fun EventColor.accent(dark: Boolean): Color = when (this) {
    EventColor.BLUE -> themedColor(dark, 0xFF1153B9, 0xFF8CB5F5)
    EventColor.GREEN -> themedColor(dark, 0xFF256528, 0xFF68C76D)
    EventColor.ORANGE -> themedColor(dark, 0xFF904019, 0xFFE9A481)
    EventColor.PURPLE -> themedColor(dark, 0xFF6940B0, 0xFFBEABE0)
    EventColor.RED -> themedColor(dark, 0xFFA82222, 0xFFECA0A0)
    EventColor.TEAL -> themedColor(dark, 0xFF006359, 0xFF00C7B3)
}

@Composable
internal fun EventColor.accent(): Color = accent(LocalDarkTheme.current)

@get:StringRes
internal val EventColor.labelRes: Int
    get() = when (this) {
        EventColor.BLUE -> R.string.calendar_color_blue
        EventColor.GREEN -> R.string.calendar_color_green
        EventColor.ORANGE -> R.string.calendar_color_orange
        EventColor.PURPLE -> R.string.calendar_color_purple
        EventColor.RED -> R.string.calendar_color_red
        EventColor.TEAL -> R.string.calendar_color_teal
    }

// Marca de selección sobre la muestra de color: negro o blanco, el que más contraste dé.
internal fun swatchCheckColor(swatch: Color): Color {
    val l = swatch.luminance()
    val onWhite = (WHITE_LUMINANCE + CONTRAST_OFFSET) / (l + CONTRAST_OFFSET)
    val onBlack = (l + CONTRAST_OFFSET) / (BLACK_LUMINANCE + CONTRAST_OFFSET)
    return if (onWhite >= onBlack) Color.White else Color.Black
}

private const val WHITE_LUMINANCE = 1f
private const val BLACK_LUMINANCE = 0f
private const val CONTRAST_OFFSET = 0.05f
