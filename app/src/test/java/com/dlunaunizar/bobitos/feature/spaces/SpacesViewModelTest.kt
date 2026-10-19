package com.dlunaunizar.bobitos.feature.spaces

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.EDITOR_SAVE_TIMEOUT_MILLIS
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.FailureKind
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.InvitationStatus
import com.dlunaunizar.bobitos.core.model.SpaceInvitation
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.data.repository.SpaceFailure
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.repository.SpaceRepositoryException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
class SpacesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSpaceRepository()

    // Por `lazy`: el SaveRunner toca viewModelScope al construirse, antes de que la regla ponga el dispatcher.
    private val viewModel by lazy { SpacesViewModel(repository) }

    @Test
    fun `create space trims its name and reports success`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.createSpace("  Casa  ")
        advanceUntilIdle()

        assertEquals("Casa", repository.createdName)
        assertEquals(SpaceUiMessage.SpaceCreated, viewModel.uiState.value.save.notice)
        assertFalse(viewModel.uiState.value.save.isSaving)
        assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.save.editor)
        assertNull(viewModel.uiState.value.save.error)
    }

    @Test
    fun aLoadFailureNeverExposesTheExceptionText() = runTest(mainDispatcherRule.testDispatcher) {
        repository.loadFailure = RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions.")

        viewModel.observeSpaceSettings("home", includeInvitations = true)
        advanceUntilIdle()

        assertEquals(UiState.Error(FailureKind.UNKNOWN), viewModel.uiState.value.members)
        assertEquals(UiState.Error(FailureKind.UNKNOWN), viewModel.uiState.value.invitations)
    }

    @Test
    fun `invalid name never reaches repository`() {
        viewModel.createSpace("  ")

        assertNull(repository.createdName)
        assertEquals(SpaceUiMessage.NameRequired, viewModel.uiState.value.save.editorError)
        assertNull(viewModel.uiState.value.save.error)
        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.save.editor)
    }

    @Test
    fun `owner transfer requirement is exposed as a specific message`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.nextFailure = SpaceRepositoryException(SpaceFailure.OwnerMustTransfer)

        viewModel.leaveSpace("home")
        advanceUntilIdle()

        assertEquals(SpaceUiMessage.OwnerMustTransfer, viewModel.uiState.value.save.error)
    }

    @Test
    fun `connection loss while saving finishes as network error`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.createGate = CompletableDeferred()

        viewModel.createSpace("Casa")
        runCurrent()
        assertTrue(viewModel.uiState.value.save.isSaving)

        repository.nextFailure = SpaceRepositoryException(SpaceFailure.Network)
        repository.createGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(SpaceUiMessage.NetworkError, viewModel.uiState.value.save.editorError)
        assertNull(viewModel.uiState.value.save.error)
        assertFalse(viewModel.uiState.value.save.isSaving)
    }

    @Test
    fun `members are observed for selected space`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observeSpaceSettings("home", includeInvitations = false)
        advanceUntilIdle()

        val state = viewModel.uiState.value.members as UiState.Content
        assertEquals("owner", state.value.single().userId)
    }

    @Test
    fun `valid invitation is accepted and exposes destination space`() = runTest(mainDispatcherRule.testDispatcher) {
        val token = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

        viewModel.acceptInvitation(token.lowercase())
        advanceUntilIdle()

        assertEquals(token.lowercase(), repository.acceptedCode)
        assertEquals("home", viewModel.uiState.value.acceptedSpaceId)
        assertEquals(SpaceUiMessage.InvitationAccepted, viewModel.uiState.value.save.notice)
    }

    @Test
    fun `invalid invitation code never reaches repository`() {
        viewModel.acceptInvitation("invalid")

        assertNull(repository.acceptedCode)
        assertEquals(SpaceUiMessage.InvalidInvitationCode, viewModel.uiState.value.save.editorError)
    }

    @Test fun `space deletion delegates and reports success`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.deleteSpace("home")
        advanceUntilIdle()
        assertEquals("home", repository.deletedSpaceId)
        assertEquals(SpaceUiMessage.SpaceDeleted, viewModel.uiState.value.save.notice)
    }

    @Test
    fun `create, accept and rename are SAVED only after confirm`() = runTest(mainDispatcherRule.testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.createGate = gate
        viewModel.createSpace("Casa")
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.save.editor)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.save.editor)
        viewModel.feedback.resetEditor()
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.save.editor)

        val renameGate = CompletableDeferred<Unit>()
        repository.renameGate = renameGate
        viewModel.renameSpace("home", "Nueva")
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.save.editor)
        renameGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.save.editor)
        viewModel.feedback.resetEditor()

        val acceptGate = CompletableDeferred<Unit>()
        repository.acceptGate = acceptGate
        viewModel.acceptInvitation("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567")
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.save.editor)
        acceptGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.save.editor)
    }

    @Test
    fun `acceptInvitation sets acceptedSpaceId and SAVED together`() = runTest(mainDispatcherRule.testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.acceptGate = gate
        viewModel.acceptInvitation("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567")
        assertNull(viewModel.uiState.value.acceptedSpaceId)

        gate.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("home", state.acceptedSpaceId)
        assertEquals(EditorSaveStatus.SAVED, state.save.editor)
    }

    @Test
    fun `validation errors and timeouts leave FAILED`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.createSpace("  ")
        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.save.editor)
        viewModel.feedback.resetEditor()
        viewModel.acceptInvitation("invalid")
        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.save.editor)
        viewModel.feedback.resetEditor()

        repository.hang = true
        viewModel.createSpace("Casa")
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.save.editor)
        advanceTimeBy(EDITOR_SAVE_TIMEOUT_MILLIS + 1)
        runCurrent()

        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.save.editor)
        assertEquals(SpaceUiMessage.SaveTimeout, viewModel.uiState.value.save.editorError)
        assertFalse(viewModel.uiState.value.save.isSaving)
    }

    @Test
    fun `a failed create shows the error only in the dialog`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.nextFailure = SpaceRepositoryException(SpaceFailure.Network)

        viewModel.createSpace("Casa")
        advanceUntilIdle()

        val save = viewModel.uiState.value.save
        assertEquals(SpaceUiMessage.NetworkError, save.editorError)
        assertNull(save.error)
        assertEquals(EditorSaveStatus.FAILED, save.editor)
    }

    @Test
    fun `a validation error while another action is in flight keeps saving`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val gate = CompletableDeferred<Unit>()
            repository.renameGate = gate
            viewModel.renameSpace("home", "Nueva")
            runCurrent()
            assertTrue(viewModel.uiState.value.save.isSaving)

            viewModel.createSpace("  ")

            val save = viewModel.uiState.value.save
            assertTrue(save.isSaving)
            assertEquals(SpaceUiMessage.NameRequired, save.editorError)
            gate.complete(Unit)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.save.isSaving)
        }

    @Test
    fun `leave, remove and delete never touch editorSave`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.leaveSpace("home")
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.save.editor)
        advanceUntilIdle()
        viewModel.removeMember("home", "u")
        advanceUntilIdle()
        viewModel.deleteSpace("home")
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.save.editor)

        repository.nextFailure = SpaceRepositoryException(SpaceFailure.Network)
        viewModel.leaveSpace("home")
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.save.editor)
        assertTrue(viewModel.uiState.value.save.error != null)
    }
}

private class FakeSpaceRepository : SpaceRepository {
    override fun spaces(): Flow<List<SpaceSummary>> = flowOf(emptyList())
    override fun space(spaceId: String): Flow<SpaceSummary?> = flowOf(null)
    var createdName: String? = null
    var acceptedCode: String? = null
    var deletedSpaceId: String? = null
    var nextFailure: SpaceRepositoryException? = null
    var loadFailure: Throwable? = null
    var createGate: CompletableDeferred<Unit>? = null
    var renameGate: CompletableDeferred<Unit>? = null
    var acceptGate: CompletableDeferred<Unit>? = null

    // Nunca responde; como los repositorios reales, convierte la cancelación en otra excepción.
    var hang = false

    override fun members(spaceId: String): Flow<List<SpaceMember>> = loadFailure?.let { error -> flow { throw error } }
        ?: flowOf(listOf(SpaceMember("owner", "David", SpaceRole.OWNER)))

    override fun invitations(spaceId: String): Flow<List<SpaceInvitation>> =
        loadFailure?.let { error -> flow { throw error } } ?: flowOf(emptyList())

    override suspend fun createSpace(name: String): String {
        if (hang) {
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                throw SpaceRepositoryException(SpaceFailure.Unknown)
            }
        }
        createGate?.await()
        throwNextFailure()
        createdName = name
        return "created"
    }

    override suspend fun renameSpace(spaceId: String, name: String) {
        renameGate?.await()
        throwNextFailure()
    }

    override suspend fun leaveSpace(spaceId: String) {
        throwNextFailure()
    }

    override suspend fun removeMember(spaceId: String, userId: String) {
        throwNextFailure()
    }

    override suspend fun transferOwnership(spaceId: String, newOwnerId: String) {
        throwNextFailure()
    }

    override suspend fun deleteSpace(spaceId: String) {
        throwNextFailure()
        deletedSpaceId = spaceId
    }

    override suspend fun createInvitation(spaceId: String): SpaceInvitation {
        throwNextFailure()
        return SpaceInvitation(
            id = "A".repeat(32),
            spaceId = spaceId,
            expiresAt = Instant.now(),
            status = InvitationStatus.ACTIVE,
        )
    }

    override suspend fun revokeInvitation(invitationId: String) {
        throwNextFailure()
    }

    override suspend fun acceptInvitation(code: String): String {
        acceptGate?.await()
        throwNextFailure()
        acceptedCode = code
        return "home"
    }

    private fun throwNextFailure() {
        nextFailure?.let { throw it }
    }
}
