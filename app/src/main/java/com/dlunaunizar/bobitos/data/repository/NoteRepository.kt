package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Cuántas notas se leen: las más recientemente actualizadas. Si hay más, la pantalla lo avisa. */
const val MAX_VISIBLE_NOTES = 250

/** Las notas que se ven y si hay más antiguas que no caben en [MAX_VISIBLE_NOTES]. */
data class NotePage(val notes: List<Note>, val truncated: Boolean = false)

interface NoteRepository {
    fun notes(spaceId: String): Flow<List<Note>>

    /** Como [notes], con la bandera de que hay notas más antiguas que no se leen. */
    fun notePage(spaceId: String): Flow<NotePage> = notes(spaceId).map { NotePage(it) }

    suspend fun addNote(spaceId: String, title: String, body: String?): String

    suspend fun updateNote(spaceId: String, noteId: String, title: String, body: String?)

    suspend fun setPinned(spaceId: String, noteId: String, pinned: Boolean)

    suspend fun deleteNote(spaceId: String, noteId: String)

    /**
     * «Deshacer» un alta: borra [noteId] solo si quien deshace fue el último en escribirla. Si no existe, falla con
     * [NoteFailure.NoteNotFound]; si la ha cambiado otro miembro, con [NoteFailure.NoteChanged].
     */
    suspend fun undoAddNote(spaceId: String, noteId: String)

    /**
     * «Deshacer» un borrado: vuelve a crear la nota con su id, título, cuerpo y fijado. La autoría y las fechas
     * pasan a ser de quien deshace, porque las reglas de alta las exigen así.
     */
    suspend fun restoreNote(spaceId: String, note: Note)
}

enum class NoteFailure {
    TitleRequired,
    TitleTooLong,
    BodyTooLong,
    NotAuthenticated,
    EmailNotVerified,
    SpaceNotFound,
    NoteNotFound,
    NoteChanged,
    PermissionDenied,
    Network,
    Unknown,
}

class NoteRepositoryException(val failure: NoteFailure, cause: Throwable? = null) : Exception(cause)
