package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.Note
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun notes(spaceId: String): Flow<List<Note>>

    suspend fun addNote(spaceId: String, title: String, body: String?)

    suspend fun updateNote(spaceId: String, noteId: String, title: String, body: String?)

    suspend fun setPinned(spaceId: String, noteId: String, pinned: Boolean)

    suspend fun deleteNote(spaceId: String, noteId: String)
}

enum class NoteFailure {
    TitleRequired,
    TitleTooLong,
    BodyTooLong,
    NotAuthenticated,
    EmailNotVerified,
    SpaceNotFound,
    NoteNotFound,
    PermissionDenied,
    Network,
    Unknown,
}

class NoteRepositoryException(val failure: NoteFailure, cause: Throwable? = null) : Exception(cause)
