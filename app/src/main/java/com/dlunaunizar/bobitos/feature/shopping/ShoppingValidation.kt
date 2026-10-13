package com.dlunaunizar.bobitos.feature.shopping

import com.dlunaunizar.bobitos.core.model.ShoppingQuantity

object ShoppingValidation {
    const val MAX_NAME_LENGTH = 120
    const val MAX_QUANTITY_LENGTH = ShoppingQuantity.MAX_LENGTH
    const val MAX_NOTES_LENGTH = 500

    // El mismo límite que las reglas y FirestoreShoppingRepository.
    const val MAX_BRAND_LENGTH = 60

    fun validate(name: String, quantity: String?, notes: String?, brand: String? = null): ShoppingUiMessage? = when {
        name.isBlank() -> ShoppingUiMessage.NameRequired
        name.trim().length > MAX_NAME_LENGTH -> ShoppingUiMessage.NameTooLong
        quantity?.trim()?.length?.let { it > MAX_QUANTITY_LENGTH } == true -> {
            ShoppingUiMessage.QuantityTooLong
        }
        notes?.trim()?.length?.let { it > MAX_NOTES_LENGTH } == true -> {
            ShoppingUiMessage.NotesTooLong
        }
        brand?.trim()?.length?.let { it > MAX_BRAND_LENGTH } == true -> ShoppingUiMessage.BrandTooLong
        else -> null
    }
}
