package com.dlunaunizar.bobitos.feature.shopping

import androidx.compose.ui.graphics.Color
import com.dlunaunizar.bobitos.core.designsystem.theme.backgroundDark
import com.dlunaunizar.bobitos.core.designsystem.theme.backgroundLight
import com.dlunaunizar.bobitos.core.model.Supermarket
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class SupermarketContrastTest {
    private val checked = listOf(Supermarket.DIA, Supermarket.CARNICERIA)

    private fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= LINEAR_THRESHOLD) v / LINEAR_DIVISOR else ((v + GAMMA_OFFSET) / GAMMA_SCALE).pow(GAMMA)
    }

    private fun luminance(c: Color): Double =
        WEIGHT_R * channel(c.red) + WEIGHT_G * channel(c.green) + WEIGHT_B * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + OFFSET) / (minOf(la, lb) + OFFSET)
    }

    // Tinte de tarjeta como categoryCardColors: fondo * 0.88 + color * 0.12.
    private fun tinted(background: Color, color: Color): Color = Color(
        red = background.red * (1 - TINT) + color.red * TINT,
        green = background.green * (1 - TINT) + color.green * TINT,
        blue = background.blue * (1 - TINT) + color.blue * TINT,
    )

    private fun assertReaches(background: Color) {
        checked.forEach { store ->
            val icon = store.brandColor()
            val onBackground = contrast(icon, background)
            val onCard = contrast(icon, tinted(background, icon))
            assertTrue("$store on background: $onBackground", onBackground >= MIN_CONTRAST)
            assertTrue("$store on card: $onCard", onCard >= MIN_CONTRAST)
        }
    }

    @Test
    fun `DIA and butcher icons reach 3 to 1 on the dark background and their dark card`() {
        assertReaches(backgroundDark)
    }

    @Test
    fun `DIA and butcher icons reach 3 to 1 on the light background and their light card`() {
        assertReaches(backgroundLight)
    }

    private companion object {
        const val MIN_CONTRAST = 3.0
        const val TINT = 0.12f
        const val OFFSET = 0.05
        const val LINEAR_THRESHOLD = 0.03928
        const val LINEAR_DIVISOR = 12.92
        const val GAMMA_OFFSET = 0.055
        const val GAMMA_SCALE = 1.055
        const val GAMMA = 2.4
        const val WEIGHT_R = 0.2126
        const val WEIGHT_G = 0.7152
        const val WEIGHT_B = 0.0722
    }
}
