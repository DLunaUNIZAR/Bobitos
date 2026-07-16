package com.dlunaunizar.bobitos.feature.shopping

object ShoppingValidation {
    const val MAX_NAME_LENGTH = 120
    const val MAX_QUANTITY_LENGTH = 40
    const val MAX_NOTES_LENGTH = 500

    fun validate(name: String, quantity: String?, notes: String?): ShoppingUiMessage? = when {
        name.isBlank() -> ShoppingUiMessage.NameRequired
        name.trim().length > MAX_NAME_LENGTH -> ShoppingUiMessage.NameTooLong
        quantity?.trim()?.length?.let { it > MAX_QUANTITY_LENGTH } == true -> {
            ShoppingUiMessage.QuantityTooLong
        }
        notes?.trim()?.length?.let { it > MAX_NOTES_LENGTH } == true -> {
            ShoppingUiMessage.NotesTooLong
        }
        else -> null
    }
}
