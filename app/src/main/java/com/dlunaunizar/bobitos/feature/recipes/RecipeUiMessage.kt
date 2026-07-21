package com.dlunaunizar.bobitos.feature.recipes

// Mensajes de UI del recetario: errores de validación, fallos del repositorio y avisos de éxito
// (mismo patrón que ShoppingUiMessage). La pantalla los mapea a recursos de string.
enum class RecipeUiMessage {
    TitleRequired,
    TitleTooLong,
    DescriptionTooLong,
    CategoryTooLong,
    NotAuthenticated,
    EmailNotVerified,
    RecipeNotFound,
    PermissionDenied,
    NetworkError,
    UnexpectedError,
    RecipeSaved,
    RecipeDeleted,
    RecipeForked,
}
