package com.dlunaunizar.bobitos.feature.ingredients

// Mensajes de UI del catálogo de ingredientes (mismo patrón que RecipeUiMessage). La pantalla los
// traduce a recursos de string.
enum class IngredientUiMessage {
    NameRequired,
    NameTooLong,
    CategoryTooLong,
    UnitTooLong,
    AlreadyExists,
    NotAuthenticated,
    EmailNotVerified,
    NotFound,
    PermissionDenied,
    NetworkError,
    UnexpectedError,
    Saved,
    Deleted,
    PrefSaved,
    PrefCleared,
}
