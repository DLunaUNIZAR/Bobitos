package com.dlunaunizar.bobitos.feature.meals

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BreakfastDining
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.themedColor
import com.dlunaunizar.bobitos.core.model.MealSlot

@get:StringRes
internal val MealSlot.labelRes: Int
    get() = when (this) {
        MealSlot.DESAYUNO -> R.string.meal_slot_desayuno
        MealSlot.COMIDA -> R.string.meal_slot_comida
        MealSlot.CENA -> R.string.meal_slot_cena
    }

internal val MealSlot.icon: ImageVector
    get() = when (this) {
        MealSlot.DESAYUNO -> Icons.Rounded.BreakfastDining
        MealSlot.COMIDA -> Icons.Rounded.LunchDining
        MealSlot.CENA -> Icons.Rounded.DinnerDining
    }

// Acento por franja (variante clara y oscura).
internal fun MealSlot.accent(dark: Boolean): Color = when (this) {
    MealSlot.DESAYUNO -> themedColor(dark, 0xFF8F4100, 0xFFFF9A47)
    MealSlot.COMIDA -> themedColor(dark, 0xFF256528, 0xFF68C76D)
    MealSlot.CENA -> themedColor(dark, 0xFF5E35B1, 0xFFBEABE6)
}
