package com.dlunaunizar.bobitos.feature.ingredients

import android.os.Parcelable
import com.dlunaunizar.bobitos.core.common.formatDecimal
import com.dlunaunizar.bobitos.core.model.Nutrition
import kotlinx.parcelize.Parcelize

// Borrador del editor de marca: nombre, código de barras y los 6 valores nutricionales como texto
// (el teclado decimal emite coma o punto). Parcelable para que sobreviva a una rotación.
// Orden de `nutrition`: energía, grasas, carbohidratos, azúcares, proteínas, sal.
@Parcelize
internal data class BrandDraft(val name: String, val barcode: String, val nutrition: List<String>) : Parcelable {
    fun withNutrition(index: Int, value: String) = copy(
        nutrition = nutrition.mapIndexed { i, current -> if (i == index) value else current },
    )

    /** Nutrición para guardar; null si no se ha rellenado ningún valor. */
    fun toNutrition(): Nutrition? = Nutrition(
        energyKcal = parseNutritionValue(nutrition[ENERGY]),
        fat = parseNutritionValue(nutrition[FAT]),
        carbohydrates = parseNutritionValue(nutrition[CARBS]),
        sugars = parseNutritionValue(nutrition[SUGARS]),
        protein = parseNutritionValue(nutrition[PROTEIN]),
        salt = parseNutritionValue(nutrition[SALT]),
    ).takeUnless(Nutrition::isEmpty)

    companion object {
        private const val ENERGY = 0
        private const val FAT = 1
        private const val CARBS = 2
        private const val SUGARS = 3
        private const val PROTEIN = 4
        private const val SALT = 5

        fun of(name: String, barcode: String, nutrition: Nutrition?) = BrandDraft(
            name = name,
            barcode = barcode,
            nutrition = listOf(
                nutrition?.energyKcal,
                nutrition?.fat,
                nutrition?.carbohydrates,
                nutrition?.sugars,
                nutrition?.protein,
                nutrition?.salt,
            ).map { it?.let(::formatDecimal).orEmpty() },
        )
    }
}
