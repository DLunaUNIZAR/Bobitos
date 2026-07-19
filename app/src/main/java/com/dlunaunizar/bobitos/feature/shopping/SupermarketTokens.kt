package com.dlunaunizar.bobitos.feature.shopping

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.Supermarket

@get:StringRes
internal val Supermarket.labelRes: Int
    get() = when (this) {
        Supermarket.DIA -> R.string.supermarket_dia
        Supermarket.EROSKI -> R.string.supermarket_eroski
        Supermarket.MERCADONA -> R.string.supermarket_mercadona
        Supermarket.CARREFOUR -> R.string.supermarket_carrefour
        Supermarket.ALCAMPO -> R.string.supermarket_alcampo
        Supermarket.OTROS -> R.string.supermarket_otros
        Supermarket.INDIFERENTE -> R.string.supermarket_indiferente
    }

// Color de marca por supermercado (identidad visual sin usar logotipos, por marca registrada).
// Se usa para tintar un icono de tienda genérico; tonos medios legibles en claro y oscuro.
internal fun Supermarket.brandColor(): Color = when (this) {
    Supermarket.DIA -> Color(0xFFC8102E)
    Supermarket.EROSKI -> Color(0xFFEC6608)
    Supermarket.MERCADONA -> Color(0xFF00843D)
    Supermarket.CARREFOUR -> Color(0xFF004E9E)
    Supermarket.ALCAMPO -> Color(0xFFE30613)
    Supermarket.OTROS -> Color(0xFF6E6E6E)
    Supermarket.INDIFERENTE -> Color(0xFF8A8A8A)
}
