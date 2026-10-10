package com.dlunaunizar.bobitos.feature.notes

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.EDITOR_SAVE_TIMEOUT_MILLIS
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.Note
import com.dlunaunizar.bobitos.data.repository.NoteFailure
import com.dlunaunizar.bobitos.data.repository.NoteRepository
import com.dlunaunizar.bobitos.data.repository.NoteRepositoryException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeNoteRepository()
    private val viewModel = NotesViewModel(repository)

    @Test
    fun `observes notes for the space`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.notesState.value = listOf(note("n1", "Wifi"))

        viewModel.observe("home")
        advanceUntilIdle()

        assertEquals(
            listOf("Wifi"),
            (viewModel.uiState.value.notes as UiState.Content).value.map(Note::title),
        )
    }

    @Test
    fun `adding a note trims the title and reports success`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        viewModel.addNote("  Wifi  ", "clave: 1234")
        advanceUntilIdle()

        assertEquals("Wifi", repository.addedTitle)
        assertEquals(NoteUiMessage.NoteAdded, viewModel.uiState.value.notice)
    }

    @Test
    fun `an invalid note never reaches the repository`() {
        viewModel.observe("home")
        viewModel.addNote("   ", null)

        assertNull(repository.addedTitle)
        assertEquals(NoteUiMessage.TitleRequired, viewModel.uiState.value.error)
    }

    @Test
    fun `toggling pin delegates to the repository`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        viewModel.setPinned("n1", true)
        advanceUntilIdle()

        assertEquals("n1" to true, repository.pinnedChange)
        assertEquals(NoteUiMessage.NotePinned, viewModel.uiState.value.notice)
    }

    @Test
    fun `network failure is shown explicitly`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.nextFailure = NoteRepositoryException(NoteFailure.Network)

        viewModel.observe("home")
        viewModel.deleteNote("n1")
        advanceUntilIdle()

        assertEquals(NoteUiMessage.NetworkError, viewModel.uiState.value.error)
    }

    @Test
    fun `editor save is SAVED only after the repository answers`() = runTest(mainDispatcherRule.testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.addGate = gate
        viewModel.observe("home")

        viewModel.addNote("Wifi", null)
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.editorSave)
        assertTrue(viewModel.uiState.value.isSaving)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.editorSave)
        assertFalse(viewModel.uiState.value.isSaving)
        viewModel.consumeEditorSave()
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)
    }

    @Test
    fun `editor save failure or timeout leaves FAILED`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        repository.nextFailure = NoteRepositoryException(NoteFailure.Network)
        viewModel.addNote("Wifi", null)
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
        assertEquals(NoteUiMessage.NetworkError, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)

        repository.nextFailure = null
        viewModel.consumeEditorSave()
        repository.hang = true
        viewModel.addNote("Wifi", null)
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.editorSave)
        advanceTimeBy(EDITOR_SAVE_TIMEOUT_MILLIS + 1)
        runCurrent()

        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
        assertEquals(NoteUiMessage.SaveTimeout, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
    }
}

private class FakeNoteRepository : NoteRepository {
    var addGate: CompletableDeferred<Unit>? = null

    // Nunca responde; como los repositorios reales, convierte la cancelación en otra excepción.
    var hang = false

    val notesState = MutableStateFlow<List<Note>>(emptyList())
    var addedTitle: String? = null
    var pinnedChange: Pair<String, Boolean>? = null
    var nextFailure: NoteRepositoryException? = null

    override fun notes(spaceId: String): Flow<List<Note>> = notesState

    override suspend fun addNote(spaceId: String, title: String, body: String?) {
        throwNextFailure()
        if (hang) {
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                throw NoteRepositoryException(NoteFailure.Unknown)
            }
        }
        addGate?.await()
        addedTitle = title
    }

    override suspend fun updateNote(spaceId: String, noteId: String, title: String, body: String?) {
        throwNextFailure()
    }

    override suspend fun setPinned(spaceId: String, noteId: String, pinned: Boolean) {
        throwNextFailure()
        pinnedChange = noteId to pinned
    }

    override suspend fun deleteNote(spaceId: String, noteId: String) {
        throwNextFailure()
    }

    private fun throwNextFailure() {
        nextFailure?.let { throw it }
    }
}

private fun note(id: String, title: String) = Note(
    id = id,
    title = title,
    body = null,
    pinned = false,
    createdBy = "owner",
    createdByName = "David",
    createdAt = Instant.EPOCH,
    updatedBy = "owner",
    updatedAt = Instant.EPOCH,
)
