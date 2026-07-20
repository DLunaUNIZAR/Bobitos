package com.dlunaunizar.bobitos.feature.recipes

// Mensajes de UI del recetario. De momento solo cubre la validación; se ampliará con los fallos
// de repositorio y avisos de éxito (patrón de ShoppingUiMessage) al llegar el repo y la pantalla.
enum class RecipeUiMessage {
    TitleRequired,
    TitleTooLong,
    DescriptionTooLong,
    CategoryTooLong,
}
