package com.dlunaunizar.bobitos.feature.calendar

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.EventColor

// Paleta categórica de los colores de evento (los elige la persona por evento), independiente
// del tema de marca. Tonos medios legibles como banda o punto tanto en claro como en oscuro.
// Se usan como acento (banda/punto), nunca como fondo de texto, por lo que el contraste se mantiene.
internal fun EventColor.accent(): Color = when (this) {
    EventColor.BLUE -> Color(0xFF1F6FEB)
    EventColor.GREEN -> Color(0xFF2E7D32)
    EventColor.ORANGE -> Color(0xFFC05621)
    EventColor.PURPLE -> Color(0xFF7E57C2)
    EventColor.RED -> Color(0xFFC62828)
    EventColor.TEAL -> Color(0xFF00897B)
}

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
