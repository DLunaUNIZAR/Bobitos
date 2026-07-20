package com.dlunaunizar.bobitos.feature.meals

// Vocabulario de mensajes del módulo Comidas. De momento solo cubre la validación del planificador;
// F1-6 (MealsViewModel/MealsUiState) añadirá el estado de UI y el resto de mensajes (fallos de
// repositorio y avisos de éxito), siguiendo el patrón de ShoppingUiMessage.
enum class MealUiMessage {
    NameRequired,
    NameTooLong,
}
