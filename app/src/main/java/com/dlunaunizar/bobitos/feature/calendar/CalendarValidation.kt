package com.dlunaunizar.bobitos.feature.calendar

import androidx.annotation.StringRes
import com.dlunaunizar.bobitos.R

// Las mismas reglas que aplica el repositorio al guardar: comprobarlas en el editor evita que
// el sheet se cierre (y se pierda lo escrito) con un evento que luego se rechaza.
object CalendarValidation {
    const val MAX_TITLE = 120
    const val MAX_DESCRIPTION = 1000

    enum class Error(@StringRes val stringResourceId: Int) {
        TitleRequired(R.string.calendar_error_title_required),
        TitleTooLong(R.string.calendar_error_title_too_long),
        DescriptionTooLong(R.string.calendar_error_description_too_long),
    }

    fun validate(title: String, description: String): Error? = when {
        title.isBlank() -> Error.TitleRequired
        title.trim().length > MAX_TITLE -> Error.TitleTooLong
        description.trim().length > MAX_DESCRIPTION -> Error.DescriptionTooLong
        else -> null
    }
}
