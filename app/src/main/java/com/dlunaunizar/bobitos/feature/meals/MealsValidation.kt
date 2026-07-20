package com.dlunaunizar.bobitos.feature.meals

object MealsValidation {
    const val MAX_NAME_LENGTH = 120

    fun validate(name: String): MealUiMessage? = when {
        name.isBlank() -> MealUiMessage.NameRequired
        name.trim().length > MAX_NAME_LENGTH -> MealUiMessage.NameTooLong
        else -> null
    }
}
