package com.dlunaunizar.bobitos.feature.recipes

import com.dlunaunizar.bobitos.core.model.RecipeLimits
import com.dlunaunizar.bobitos.core.model.cleanRecipeSteps

object RecipesValidation {
    const val MAX_TITLE_LENGTH = 120
    const val MAX_DESCRIPTION_LENGTH = 1000
    const val MAX_CATEGORY_LENGTH = 60

    // Los pasos se miden ya limpios (sin espacios en los extremos ni vacíos), como se guardan.
    fun validate(
        title: String,
        description: String?,
        category: String?,
        steps: List<String> = emptyList(),
        servings: Int? = null,
        totalMinutes: Int? = null,
    ): RecipeUiMessage? {
        val descriptionLength = description?.trim()?.length ?: 0
        val categoryLength = category?.trim()?.length ?: 0
        val cleanSteps = cleanRecipeSteps(steps)
        return when {
            title.isBlank() -> RecipeUiMessage.TitleRequired
            title.trim().length > MAX_TITLE_LENGTH -> RecipeUiMessage.TitleTooLong
            descriptionLength > MAX_DESCRIPTION_LENGTH -> RecipeUiMessage.DescriptionTooLong
            categoryLength > MAX_CATEGORY_LENGTH -> RecipeUiMessage.CategoryTooLong
            cleanSteps.size > RecipeLimits.MAX_STEPS -> RecipeUiMessage.TooManySteps
            cleanSteps.any { it.length > RecipeLimits.MAX_STEP_LENGTH } -> RecipeUiMessage.StepTooLong
            servings != null && servings !in RecipeLimits.SERVINGS -> RecipeUiMessage.ServingsInvalid
            totalMinutes != null && totalMinutes !in RecipeLimits.TOTAL_MINUTES -> RecipeUiMessage.TotalMinutesInvalid
            else -> null
        }
    }
}
