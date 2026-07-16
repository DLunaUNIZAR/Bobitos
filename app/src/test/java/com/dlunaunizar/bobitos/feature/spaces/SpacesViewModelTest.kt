package com.dlunaunizar.bobitos.feature.spaces

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.InvitationStatus
import com.dlunaunizar.bobitos.core.model.SpaceInvitation
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.data.repository.SpaceFailure
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.repository.SpaceRepositoryException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpacesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSpaceRepository()
    private val viewModel = SpacesViewModel(repository)

    @Test
    fun `create space trims its name and reports success`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.createSpace("  Casa  ")
            advanceUntilIdle()

            assertEquals("Casa", repository.createdName)
            assertEquals(SpaceUiMessage.SpaceCreated, viewModel.uiState.value.notice)
            assertFalse(viewModel.uiState.value.isLoading)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `invalid name never reaches repository`() {
        viewModel.createSpace("  ")

        assertNull(repository.createdName)
        assertEquals(SpaceUiMessage.NameRequired, viewModel.uiState.value.error)
    }

    @Test
    fun `owner transfer requirement is exposed as a specific message`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.nextFailure = SpaceRepositoryException(SpaceFailure.OwnerMustTransfer)

            viewModel.leaveSpace("home")
            advanceUntilIdle()

            assertEquals(SpaceUiMessage.OwnerMustTransfer, viewModel.uiState.value.error)
        }

    @Test
    fun `members are observed for selected space`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.observeSpaceSettings("home", includeInvitations = false)
            advanceUntilIdle()

            val state = viewModel.uiState.value.members as UiState.Content
            assertEquals("owner", state.value.single().userId)
        }

    @Test
    fun `valid invitation is accepted and exposes destination space`() =
        runTest(mainDispatcherRule.testDispatcher) {
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
}

private class FakeSpaceRepository : SpaceRepository {
    override val spaces: Flow<List<SpaceSummary>> = flowOf(emptyList())
    var createdName: String? = null
    var acceptedCode: String? = null
    var nextFailure: SpaceRepositoryException? = null

    override fun members(spaceId: String): Flow<List<SpaceMember>> = flowOf(
        listOf(SpaceMember("owner", "David", SpaceRole.OWNER)),
    )

    override fun invitations(spaceId: String): Flow<List<SpaceInvitation>> = flowOf(emptyList())

    override suspend fun createSpace(name: String): String {
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
