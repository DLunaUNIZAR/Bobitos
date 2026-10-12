package com.dlunaunizar.bobitos.feature.recipes

// Mensajes de UI del recetario: errores de validación, fallos del repositorio y avisos de éxito
// (mismo patrón que ShoppingUiMessage). La pantalla los mapea a recursos de string.
enum class RecipeUiMessage {
    TitleRequired,
    TitleTooLong,
    DescriptionTooLong,
    CategoryTooLong,
    TooManySteps,
    StepTooLong,
    ServingsInvalid,
    TotalMinutesInvalid,
    NotAuthenticated,
    EmailNotVerified,
    RecipeNotFound,
    PermissionDenied,
    NetworkError,
    UnexpectedError,
    ShoppingChanged,
    SaveTimeout,
    RecipeSaved,
    RecipeDeleted,
    RecipeForked,
    ImportInvalidUrl,
    ImportNetwork,
    ImportNotHtml,
    ImportNoRecipe,
    ImportTooLarge,
    AddedToShopping,
    NothingToAddToShopping,
    NoIngredients,
}
