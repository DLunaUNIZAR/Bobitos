package com.dlunaunizar.bobitos.feature.notes

object NoteValidation {
    const val MAX_TITLE = 120
    const val MAX_BODY = 5000

    fun validate(title: String, body: String?): NoteUiMessage? = when {
        title.isBlank() -> NoteUiMessage.TitleRequired
        title.trim().length > MAX_TITLE -> NoteUiMessage.TitleTooLong
        body?.trim()?.length?.let { it > MAX_BODY } == true -> NoteUiMessage.BodyTooLong
        else -> null
    }
}
