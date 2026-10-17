package com.dlunaunizar.bobitos.feature.shopping

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.KebabDining
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.LocalDarkTheme
import com.dlunaunizar.bobitos.core.designsystem.theme.themedColor
import com.dlunaunizar.bobitos.core.model.Supermarket

@get:StringRes
internal val Supermarket.labelRes: Int
    get() = when (this) {
        Supermarket.DIA -> R.string.supermarket_dia
        Supermarket.EROSKI -> R.string.supermarket_eroski
        Supermarket.MERCADONA -> R.string.supermarket_mercadona
        Supermarket.CARREFOUR -> R.string.supermarket_carrefour
        Supermarket.ALCAMPO -> R.string.supermarket_alcampo
        Supermarket.CARNICERIA -> R.string.supermarket_carniceria
        Supermarket.PESCADERIA -> R.string.supermarket_pescaderia
        Supermarket.FRUTERIA -> R.string.supermarket_fruteria
        Supermarket.PANADERIA -> R.string.supermarket_panaderia
        Supermarket.OTROS -> R.string.supermarket_otros
        Supermarket.INDIFERENTE -> R.string.supermarket_indiferente
    }

// Color de marca por supermercado (identidad visual sin usar logotipos, por marca registrada).
// Se usa para tintar un icono de tienda genérico; con variante clara y oscura que pasan contraste.
internal fun Supermarket.brandColor(dark: Boolean): Color = when (this) {
    Supermarket.DIA -> themedColor(dark, 0xFFA91930, 0xFFF19DAB)
    Supermarket.EROSKI -> themedColor(dark, 0xFF923F05, 0xFFF99D5C)
    Supermarket.MERCADONA -> themedColor(dark, 0xFF00662F, 0xFF00CC5E)
    Supermarket.CARREFOUR -> themedColor(dark, 0xFF004D9C, 0xFF73B8FF)
    Supermarket.ALCAMPO -> themedColor(dark, 0xFFAE050F, 0xFFFC979D)
    Supermarket.CARNICERIA -> themedColor(dark, 0xFF943D38, 0xFFDCA7A5)
    Supermarket.PESCADERIA -> themedColor(dark, 0xFF235D7E, 0xFF7FBBDB)
    Supermarket.FRUTERIA -> themedColor(dark, 0xFF3C621E, 0xFF7DC643)
    Supermarket.PANADERIA -> themedColor(dark, 0xFF75511C, 0xFFDBAC67)
    Supermarket.OTROS -> themedColor(dark, 0xFF595959, 0xFFB4B4B4)
    Supermarket.INDIFERENTE -> themedColor(dark, 0xFF595959, 0xFFB4B4B4)
}

@Composable
internal fun Supermarket.brandColor(): Color = brandColor(LocalDarkTheme.current)

// Las tiendas de barrio tienen icono propio; supermercados, Otros e Indiferente comparten el de tienda.
internal fun Supermarket.icon(): ImageVector = when (this) {
    Supermarket.CARNICERIA -> Icons.Rounded.KebabDining
    Supermarket.PESCADERIA -> Icons.Rounded.SetMeal
    Supermarket.FRUTERIA -> Icons.Rounded.Eco
    Supermarket.PANADERIA -> Icons.Rounded.BakeryDining
    Supermarket.DIA,
    Supermarket.EROSKI,
    Supermarket.MERCADONA,
    Supermarket.CARREFOUR,
    Supermarket.ALCAMPO,
    Supermarket.OTROS,
    Supermarket.INDIFERENTE,
    -> Icons.Rounded.Storefront
}
