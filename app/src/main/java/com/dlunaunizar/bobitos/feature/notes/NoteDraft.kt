package com.dlunaunizar.bobitos.feature.notes

import android.os.Parcelable
import com.dlunaunizar.bobitos.core.model.Note
import kotlinx.parcelize.Parcelize

// Borrador del editor de nota. Parcelable para que rememberSaveable lo conserve al girar la pantalla.
@Parcelize
internal data class NoteDraft(val title: String, val body: String) : Parcelable {
    companion object {
        fun of(note: Note?) = NoteDraft(title = note?.title.orEmpty(), body = note?.body.orEmpty())
    }
}
