package com.dlunaunizar.bobitos.feature.spaces

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.InvitationStatus
import com.dlunaunizar.bobitos.core.model.SpaceInvitation
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.data.repository.SpaceFailure
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.repository.SpaceRepositoryException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SpacesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSpaceRepository()
    private val viewModel = SpacesViewModel(repository)

    @Test
    fun `create space trims its name and reports success`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.createSpace("  Casa  ")
        advanceUntilIdle()

        assertEquals("Casa", repository.createdName)
        assertEquals(SpaceUiMessage.SpaceCreated, viewModel.uiState.value.notice)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(WriteStatus.SAVED, viewModel.uiState.value.writeStatus)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `invalid name never reaches repository`() {
        viewModel.createSpace("  ")

        assertNull(repository.createdName)
        assertEquals(SpaceUiMessage.NameRequired, viewModel.uiState.value.error)
        assertEquals(WriteStatus.ERROR, viewModel.uiState.value.writeStatus)
    }

    @Test
    fun `owner transfer requirement is exposed as a specific message`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.nextFailure = SpaceRepositoryException(SpaceFailure.OwnerMustTransfer)

        viewModel.leaveSpace("home")
        advanceUntilIdle()

        assertEquals(SpaceUiMessage.OwnerMustTransfer, viewModel.uiState.value.error)
    }

    @Test
    fun `connection loss while saving finishes as network error`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.createGate = CompletableDeferred()

        viewModel.createSpace("Casa")
        assertEquals(WriteStatus.SAVING, viewModel.uiState.value.writeStatus)

        repository.nextFailure = SpaceRepositoryException(SpaceFailure.Network)
        repository.createGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(WriteStatus.ERROR, viewModel.uiState.value.writeStatus)
        assertEquals(SpaceUiMessage.NetworkError, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
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
        assertEquals(SpaceUiMessage.InvitationAccepted, viewModel.uiState.value.notice)
    }

    @Test
    fun `invalid invitation code never reaches repository`() {
        viewModel.acceptInvitation("invalid")

        assertNull(repository.acceptedCode)
        assertEquals(SpaceUiMessage.InvalidInvitationCode, viewModel.uiState.value.error)
    }

    @Test fun `space deletion delegates and reports success`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.deleteSpace("home")
        advanceUntilIdle()
        assertEquals("home", repository.deletedSpaceId)
        assertEquals(SpaceUiMessage.SpaceDeleted, viewModel.uiState.value.notice)
    }
}

private class FakeSpaceRepository : SpaceRepository {
    override fun spaces(): Flow<List<SpaceSummary>> = flowOf(emptyList())
    override fun space(spaceId: String): Flow<SpaceSummary?> = flowOf(null)
    var createdName: String? = null
    var acceptedCode: String? = null
    var deletedSpaceId: String? = null
    var nextFailure: SpaceRepositoryException? = null
    var createGate: CompletableDeferred<Unit>? = null

    override fun members(spaceId: String): Flow<List<SpaceMember>> = flowOf(
        listOf(SpaceMember("owner", "David", SpaceRole.OWNER)),
    )

    override fun invitations(spaceId: String): Flow<List<SpaceInvitation>> = flowOf(emptyList())

    override suspend fun createSpace(name: String): String {
        createGate?.await()
        throwNextFailure()
        createdName = name
        return "created"
    }

    override suspend fun renameSpace(spaceId: String, name: String) {
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
        throwNextFailure()
        acceptedCode = code
        return "home"
    }

    private fun throwNextFailure() {
        nextFailure?.let { throw it }
    }
}
