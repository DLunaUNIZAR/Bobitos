package com.dlunaunizar.bobitos.feature.sport

object SportValidation {
    const val MAX_NAME = 120

    fun validate(name: String): SportUiMessage? = when {
        name.isBlank() -> SportUiMessage.NameRequired
        name.trim().length > MAX_NAME -> SportUiMessage.NameTooLong
        else -> null
    }
}
