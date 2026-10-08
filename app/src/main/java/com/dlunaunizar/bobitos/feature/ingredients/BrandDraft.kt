package com.dlunaunizar.bobitos.feature.ingredients

import androidx.compose.runtime.saveable.listSaver
import com.dlunaunizar.bobitos.core.model.Nutrition

// Borrador del editor de marca: nombre, código de barras y los 6 valores nutricionales como texto
// (el teclado decimal emite coma o punto), para que sobreviva a una rotación.
// Orden de `nutrition`: energía, grasas, carbohidratos, azúcares, proteínas, sal.
internal data class BrandDraft(val name: String, val barcode: String, val nutrition: List<String>) {
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
            ).map { it?.let(::formatNumber).orEmpty() },
        )
    }
}

// Formatea sin decimales innecesarios («120», «1.5»).
internal fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

/** Convierte el borrador en una lista guardable (cabe en un Bundle). */
internal fun BrandDraft.toSaved(): ArrayList<Any?> = arrayListOf(name, barcode, ArrayList(nutrition))

internal fun brandDraftFromSaved(saved: List<Any?>): BrandDraft {
    @Suppress("UNCHECKED_CAST")
    return BrandDraft(
        name = saved[0] as String,
        barcode = saved[1] as String,
        nutrition = (saved[2] as List<String>).toList(),
    )
}

internal val BrandDraftSaver = listSaver<BrandDraft, Any?>(
    save = { it.toSaved() },
    restore = { brandDraftFromSaved(it) },
)
