package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.Note
import com.dlunaunizar.bobitos.data.sync.RealtimeMetrics
import com.dlunaunizar.bobitos.data.sync.SyncRepository
import com.dlunaunizar.bobitos.data.sync.WriteNotAllowedException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreNoteRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val realtimeMetrics: RealtimeMetrics,
) : NoteRepository {
    private val firestore = FirebaseFirestore.getInstance()

    override fun notes(spaceId: String): Flow<List<Note>> = callbackFlow {
        val metricId = realtimeMetrics.listenerStarted("notes:active")
        val registration = notesCollection(spaceId)
            .orderBy(FIELD_UPDATED_AT, Query.Direction.DESCENDING)
            .limit(MAX_VISIBLE_NOTES)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(error.toNoteRepositoryException())
                    snapshot != null -> {
                        realtimeMetrics.snapshotReceived(
                            scope = "notes:active",
                            changedDocuments = snapshot.documentChanges.size,
                            fromCache = snapshot.metadata.isFromCache,
                        )
                        // Fijadas primero; dentro, por actualización descendente (orden estable por id).
                        val notes = snapshot.documents.mapNotNull(DocumentSnapshot::toNote)
                            .sortedWith(
                                compareByDescending<Note> { it.pinned }
                                    .thenByDescending(Note::updatedAt)
                                    .thenBy(Note::id),
                            )
                        trySend(notes)
                    }
                }
            }
        awaitClose {
            registration.remove()
            realtimeMetrics.listenerStopped(metricId)
        }
    }

    override suspend fun addNote(spaceId: String, title: String, body: String?) = runNoteOperation {
        val user = requireVerifiedUser()
        val values = validate(title, body)
        val spaceReference = firestore.collection(SPACES).document(spaceId)
        val noteReference = notesCollection(spaceId).document()
        firestore.runTransaction { transaction ->
            if (!transaction.get(spaceReference).exists()) {
                throw NoteRepositoryException(NoteFailure.SpaceNotFound)
            }
            transaction.set(
                noteReference,
                mapOf(
                    FIELD_TITLE to values.title,
                    FIELD_BODY to values.body,
                    FIELD_PINNED to false,
                    FIELD_CREATED_BY to user.id,
                    FIELD_CREATED_BY_NAME to user.noteDisplayName,
                    FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                    FIELD_UPDATED_BY to user.id,
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                ),
            )
        }.await()
        Unit
    }

    override suspend fun updateNote(spaceId: String, noteId: String, title: String, body: String?) = runNoteOperation {
        val user = requireVerifiedUser()
        val values = validate(title, body)
        val noteReference = notesCollection(spaceId).document(noteId)
        firestore.runTransaction { transaction ->
            requireNote(transaction.get(noteReference))
            transaction.update(
                noteReference,
                FIELD_TITLE, values.title,
                FIELD_BODY, values.body,
                FIELD_UPDATED_BY, user.id,
                FIELD_UPDATED_AT, FieldValue.serverTimestamp(),
            )
        }.await()
        Unit
    }

    override suspend fun setPinned(spaceId: String, noteId: String, pinned: Boolean) = runNoteOperation {
        val user = requireVerifiedUser()
        val noteReference = notesCollection(spaceId).document(noteId)
        firestore.runTransaction { transaction ->
            val note = requireNote(transaction.get(noteReference))
            if (note.getBoolean(FIELD_PINNED) != pinned) {
                transaction.update(
                    noteReference,
                    FIELD_PINNED,
                    pinned,
                    FIELD_UPDATED_BY,
                    user.id,
                    FIELD_UPDATED_AT,
                    FieldValue.serverTimestamp(),
                )
            }
        }.await()
        Unit
    }

    override suspend fun deleteNote(spaceId: String, noteId: String) = runNoteOperation {
        requireVerifiedUser()
        val noteReference = notesCollection(spaceId).document(noteId)
        firestore.runTransaction { transaction ->
            requireNote(transaction.get(noteReference))
            transaction.delete(noteReference)
        }.await()
        Unit
    }

    private fun notesCollection(spaceId: String) = firestore.collection(SPACES)
        .document(spaceId).collection(NOTES)

    private fun validate(title: String, body: String?): NoteValues {
        val normalizedTitle = title.trim()
        val normalizedBody = body?.trim()?.takeIf(String::isNotEmpty)
        when {
            normalizedTitle.isEmpty() -> throw NoteRepositoryException(NoteFailure.TitleRequired)
            normalizedTitle.length > MAX_TITLE_LENGTH -> throw NoteRepositoryException(NoteFailure.TitleTooLong)
            normalizedBody != null && normalizedBody.length > MAX_BODY_LENGTH -> {
                throw NoteRepositoryException(NoteFailure.BodyTooLong)
            }
        }
        return NoteValues(normalizedTitle, normalizedBody)
    }

    private fun requireVerifiedUser(): AuthUser {
        val user = authRepository.currentUser.value
            ?: throw NoteRepositoryException(NoteFailure.NotAuthenticated)
        if (!user.isEmailVerified) throw NoteRepositoryException(NoteFailure.EmailNotVerified)
        return user
    }

    private fun requireNote(snapshot: DocumentSnapshot): DocumentSnapshot {
        if (!snapshot.exists()) throw NoteRepositoryException(NoteFailure.NoteNotFound)
        return snapshot
    }

    private suspend inline fun <T> runNoteOperation(crossinline operation: suspend () -> T): T {
        try {
            syncRepository.requireWritable()
            return operation()
        } catch (error: NoteRepositoryException) {
            throw error
        } catch (error: WriteNotAllowedException) {
            throw NoteRepositoryException(NoteFailure.Network, error)
        } catch (error: Throwable) {
            syncRepository.reportWriteFailure(error)
            throw error.toNoteRepositoryException()
        }
    }

    private data class NoteValues(val title: String, val body: String?)

    private companion object {
        const val SPACES = "spaces"
        const val NOTES = "notes"
        const val FIELD_TITLE = "title"
        const val FIELD_BODY = "body"
        const val FIELD_PINNED = "pinned"
        const val FIELD_CREATED_BY = "createdBy"
        const val FIELD_CREATED_BY_NAME = "createdByName"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_BY = "updatedBy"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val MAX_TITLE_LENGTH = 120
        const val MAX_BODY_LENGTH = 5000
        const val MAX_VISIBLE_NOTES = 250L
    }
}

private val AuthUser.noteDisplayName: String
    get() = displayName.ifBlank { email.substringBefore('@') }.take(60)

private fun DocumentSnapshot.toNote(): Note? {
    val createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: return null
    return Note(
        id = id,
        title = getString("title") ?: return null,
        body = getString("body"),
        pinned = getBoolean("pinned") == true,
        createdBy = getString("createdBy") ?: return null,
        createdByName = getString("createdByName") ?: getString("createdBy") ?: return null,
        createdAt = createdAt,
        updatedBy = getString("updatedBy") ?: getString("createdBy") ?: return null,
        updatedAt = getTimestamp("updatedAt")?.toDate()?.toInstant() ?: createdAt,
    )
}

private fun Throwable.toNoteRepositoryException() = NoteRepositoryException(
    failure = when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> NoteFailure.PermissionDenied
        FirebaseFirestoreException.Code.NOT_FOUND -> NoteFailure.NoteNotFound
        FirebaseFirestoreException.Code.UNAVAILABLE -> NoteFailure.Network
        else -> NoteFailure.Unknown
    },
    cause = this,
)
