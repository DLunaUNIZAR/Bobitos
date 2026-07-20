package com.dlunaunizar.bobitos.feature.recipes

object RecipesValidation {
    const val MAX_TITLE_LENGTH = 120
    const val MAX_DESCRIPTION_LENGTH = 1000
    const val MAX_CATEGORY_LENGTH = 60

    fun validate(title: String, description: String?, category: String?): RecipeUiMessage? = when {
        title.isBlank() -> RecipeUiMessage.TitleRequired
        title.trim().length > MAX_TITLE_LENGTH -> RecipeUiMessage.TitleTooLong
        description?.trim()?.length?.let { it > MAX_DESCRIPTION_LENGTH } == true -> {
            RecipeUiMessage.DescriptionTooLong
        }
        category?.trim()?.length?.let { it > MAX_CATEGORY_LENGTH } == true -> {
            RecipeUiMessage.CategoryTooLong
        }
        else -> null
    }
}
