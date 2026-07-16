package com.dlunaunizar.bobitos.app

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.data.repository.ActiveSpaceRepository
import com.dlunaunizar.bobitos.data.repository.AuthRepository
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `restores the active space for the authenticated user`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val activeRepository = FakeActiveSpaceRepository("home")
            val viewModel = AppViewModel(
                authRepository = FakeAppAuthRepository(),
                spaceRepository = FakeAppSpaceRepository(),
                activeSpaceRepository = activeRepository,
            )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            advanceUntilIdle()

            assertEquals("home", viewModel.uiState.value.selectedSpace?.id)
        }

    @Test
    fun `selects immediately while persisting in background`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val activeRepository = FakeActiveSpaceRepository(null, saveDelayMillis = 1_000)
            val viewModel = AppViewModel(
                authRepository = FakeAppAuthRepository(),
                spaceRepository = FakeAppSpaceRepository(),
                activeSpaceRepository = activeRepository,
            )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            runCurrent()

            viewModel.selectSpace("work")
            runCurrent()

            assertEquals("work", viewModel.uiState.value.selectedSpace?.id)
            assertNull(activeRepository.savedSpaceId)

            advanceTimeBy(1_000)
            runCurrent()
            assertEquals("work", activeRepository.savedSpaceId)
        }
}

private class FakeActiveSpaceRepository(
    initialSpaceId: String?,
    private val saveDelayMillis: Long = 0,
) : ActiveSpaceRepository {
    private val activeId = MutableStateFlow(initialSpaceId)
    var savedSpaceId: String? = initialSpaceId

    override fun activeSpaceId(userId: String): Flow<String?> = activeId

    override suspend fun setActiveSpace(userId: String, spaceId: String?) {
        delay(saveDelayMillis)
        savedSpaceId = spaceId
        activeId.value = spaceId
    }
}

private class FakeAppAuthRepository : AuthRepository {
    override val currentUser: StateFlow<AuthUser?> = MutableStateFlow(
        AuthUser("user", "David", "david@example.com", true),
    )

    override suspend fun register(displayName: String, email: String, password: String) = Unit
    override suspend fun signIn(email: String, password: String) = Unit
    override suspend fun sendEmailVerification() = Unit
    override suspend fun refreshCurrentUser(): AuthUser = requireNotNull(currentUser.value)
    override suspend fun sendPasswordReset(email: String) = Unit
    override suspend fun updateDisplayName(displayName: String) = Unit
    override fun signOut() = Unit
}

private class FakeAppSpaceRepository : SpaceRepository {
    override val spaces: Flow<List<SpaceSummary>> = MutableStateFlow(
        listOf(
            SpaceSummary("home", "Casa", 2, SpaceRole.OWNER),
            SpaceSummary("work", "Trabajo", 3, SpaceRole.MEMBER),
        ),
    )

    override fun members(spaceId: String): Flow<List<SpaceMember>> = MutableStateFlow(emptyList())
    override suspend fun createSpace(name: String): String = "created"
    override suspend fun renameSpace(spaceId: String, name: String) = Unit
    override suspend fun leaveSpace(spaceId: String) = Unit
    override suspend fun removeMember(spaceId: String, userId: String) = Unit
    override suspend fun transferOwnership(spaceId: String, newOwnerId: String) = Unit
}
