package com.dlunaunizar.bobitos.feature.notes

import androidx.compose.runtime.saveable.listSaver
import com.dlunaunizar.bobitos.core.model.Note

// Borrador del editor de nota: dos textos, para que sobreviva a una rotación.
internal data class NoteDraft(val title: String, val body: String) {
    companion object {
        fun of(note: Note?) = NoteDraft(title = note?.title.orEmpty(), body = note?.body.orEmpty())
    }
}

internal val NoteDraftSaver = listSaver<NoteDraft, Any?>(
    save = { listOf(it.title, it.body) },
    restore = { NoteDraft(title = it[0] as String, body = it[1] as String) },
)
