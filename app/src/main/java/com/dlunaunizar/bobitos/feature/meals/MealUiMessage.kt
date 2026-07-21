package com.dlunaunizar.bobitos.feature.meals

// Mensajes de UI del módulo Comidas: errores de validación, fallos del repositorio y avisos de
// éxito (mismo patrón que ShoppingUiMessage). La pantalla los mapea a recursos de string.
enum class MealUiMessage {
    NameRequired,
    NameTooLong,
    InvalidParticipants,
    NotAuthenticated,
    EmailNotVerified,
    SpaceNotFound,
    MealNotFound,
    PermissionDenied,
    NetworkError,
    UnexpectedError,
    MealAdded,
    MealUpdated,
    MealDeleted,
    IngredientsAddedToShopping,
}
