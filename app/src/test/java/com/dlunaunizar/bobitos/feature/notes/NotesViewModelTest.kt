package com.dlunaunizar.bobitos.feature.notes

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.Note
import com.dlunaunizar.bobitos.data.repository.NoteFailure
import com.dlunaunizar.bobitos.data.repository.NoteRepository
import com.dlunaunizar.bobitos.data.repository.NoteRepositoryException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}

private class FakeNoteRepository : NoteRepository {
    val notesState = MutableStateFlow<List<Note>>(emptyList())
    var addedTitle: String? = null
    var pinnedChange: Pair<String, Boolean>? = null
    var nextFailure: NoteRepositoryException? = null

    override fun notes(spaceId: String): Flow<List<Note>> = notesState

    override suspend fun addNote(spaceId: String, title: String, body: String?) {
        throwNextFailure()
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
