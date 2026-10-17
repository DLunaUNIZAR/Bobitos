package com.dlunaunizar.bobitos.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.dlunaunizar.bobitos.core.model.EventColor
import com.dlunaunizar.bobitos.core.model.ExerciseType
import com.dlunaunizar.bobitos.core.model.MealSlot
import com.dlunaunizar.bobitos.core.model.SportType
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskType
import com.dlunaunizar.bobitos.feature.calendar.accent
import com.dlunaunizar.bobitos.feature.calendar.swatchCheckColor
import com.dlunaunizar.bobitos.feature.exercises.accent
import com.dlunaunizar.bobitos.feature.meals.accent
import com.dlunaunizar.bobitos.feature.shopping.brandColor
import com.dlunaunizar.bobitos.feature.sport.accent
import com.dlunaunizar.bobitos.feature.tasks.accent
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class CategoryContrastTest {
    private val themes = listOf(false, true)

    private fun themeName(dark: Boolean) = if (dark) "dark" else "light"

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

    private fun background(dark: Boolean) = if (dark) backgroundDark else backgroundLight

    private fun cardBackground(dark: Boolean) = if (dark) surfaceContainerHighestDark else surfaceContainerHighestLight

    private fun allColors(dark: Boolean): List<Pair<String, Color>> =
        TaskType.entries.map { "$it" to it.accent(dark) } +
            TaskPriority.entries.map { "$it" to it.accent(dark) } +
            Supermarket.entries.map { "$it" to it.brandColor(dark) } +
            EventColor.entries.map { "$it" to it.accent(dark) } +
            SportType.entries.map { "$it" to it.accent(dark) } +
            ExerciseType.entries.map { "$it" to it.accent(dark) } +
            MealSlot.entries.map { "$it" to it.accent(dark) }

    @Test
    fun `every category color reaches 3 to 1 as graphic on background and on its tinted card`() {
        themes.forEach { dark ->
            allColors(dark).forEach { (name, c) ->
                val onBackground = contrast(c, background(dark))
                val onCard = contrast(c, tinted(background(dark), c))
                assertTrue("$name ${themeName(dark)} on background: $onBackground", onBackground >= MIN_GRAPHIC)
                assertTrue("$name ${themeName(dark)} on card: $onCard", onCard >= MIN_GRAPHIC)
            }
        }
    }

    @Test
    fun `exercise type reaches 4_5 to 1 as InfoChip text`() {
        themes.forEach { dark ->
            ExerciseType.entries.forEach { type ->
                val c = type.accent(dark)
                val chipBackground = tinted(cardBackground(dark), c)
                val ratio = contrast(c, chipBackground)
                assertTrue("$type ${themeName(dark)} chip text: $ratio", ratio >= MIN_TEXT)
            }
        }
    }

    @Test
    fun `task type reaches 4_5 to 1 as MetaChip text on the tinted card`() {
        themes.forEach { dark ->
            TaskType.entries.forEach { type ->
                val c = type.accent(dark)
                val ratio = contrast(c, tinted(background(dark), c))
                assertTrue("$type ${themeName(dark)} meta text: $ratio", ratio >= MIN_TEXT)
            }
        }
    }

    @Test
    fun `swatch check mark reaches 3 to 1 on every event color`() {
        themes.forEach { dark ->
            EventColor.entries.forEach { color ->
                val swatch = color.accent(dark)
                val ratio = contrast(swatchCheckColor(swatch), swatch)
                assertTrue("$color ${themeName(dark)} check: $ratio", ratio >= MIN_GRAPHIC)
            }
        }
    }

    private companion object {
        const val MIN_GRAPHIC = 3.0
        const val MIN_TEXT = 4.5
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
