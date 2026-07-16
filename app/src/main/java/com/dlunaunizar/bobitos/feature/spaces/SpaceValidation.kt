package com.dlunaunizar.bobitos.feature.spaces

object SpaceValidation {
    const val MAX_NAME_LENGTH = 60

    fun validateName(name: String): SpaceUiMessage? = when {
        name.isBlank() -> SpaceUiMessage.NameRequired
        name.trim().length > MAX_NAME_LENGTH -> SpaceUiMessage.NameTooLong
        else -> null
    }
}
