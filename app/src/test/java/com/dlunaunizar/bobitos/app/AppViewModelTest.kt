package com.dlunaunizar.bobitos.app

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceInvitation
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.core.model.SyncStatus
import com.dlunaunizar.bobitos.data.connectivity.ConnectivityRepository
import com.dlunaunizar.bobitos.data.connectivity.NetworkStatus
import com.dlunaunizar.bobitos.data.repository.ActiveSpaceRepository
import com.dlunaunizar.bobitos.data.repository.AuthRepository
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.sync.SyncRepository
import com.dlunaunizar.bobitos.data.sync.WriteNotAllowedException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
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
                connectivityRepository = FakeConnectivityRepository(),
                syncRepository = FakeSyncRepository(),
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
                connectivityRepository = FakeConnectivityRepository(),
                syncRepository = FakeSyncRepository(),
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

    @Test
    fun `listeners follow navigation scope without duplicates`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = FakeAppSpaceRepository(trackCollectors = true)
            val viewModel = AppViewModel(
                authRepository = FakeAppAuthRepository(),
                spaceRepository = repository,
                activeSpaceRepository = FakeActiveSpaceRepository("home"),
                connectivityRepository = FakeConnectivityRepository(),
                syncRepository = FakeSyncRepository(),
            )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            runCurrent()

            assertEquals(1, repository.activeSpaceCollectors)
            assertEquals(0, repository.allSpacesCollectors)

            viewModel.setRealtimeScope(RealtimeScope.ALL_SPACES)
            runCurrent()
            assertEquals(0, repository.activeSpaceCollectors)
            assertEquals(1, repository.allSpacesCollectors)

            viewModel.setRealtimeScope(RealtimeScope.ALL_SPACES)
            runCurrent()
            assertEquals(1, repository.allSpacesCollectors)

            viewModel.setRealtimeScope(RealtimeScope.PAUSED)
            runCurrent()
            assertEquals(0, repository.activeSpaceCollectors)
            assertEquals(0, repository.allSpacesCollectors)
        }

    @Test
    fun `reconnection refreshes server before enabling writes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val connectivity = FakeConnectivityRepository(NetworkStatus.OFFLINE)
            val syncRepository = FakeSyncRepository()
            val viewModel = AppViewModel(
                authRepository = FakeAppAuthRepository(),
                spaceRepository = FakeAppSpaceRepository(),
                activeSpaceRepository = FakeActiveSpaceRepository("home"),
                connectivityRepository = connectivity,
                syncRepository = syncRepository,
            )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            runCurrent()
            assertEquals(SyncStatus.OFFLINE, viewModel.uiState.value.syncStatus)

            connectivity.mutableStatus.value = NetworkStatus.ONLINE
            runCurrent()

            assertEquals(listOf("home"), syncRepository.refreshedSpaceIds)
            assertEquals(SyncStatus.ONLINE, viewModel.uiState.value.syncStatus)
            syncRepository.requireWritable()
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

private class FakeAppSpaceRepository(
    private val trackCollectors: Boolean = false,
) : SpaceRepository {
    private val summaries = listOf(
        SpaceSummary("home", "Casa", 2, SpaceRole.OWNER),
        SpaceSummary("work", "Trabajo", 3, SpaceRole.MEMBER),
    )
    var allSpacesCollectors = 0
    var activeSpaceCollectors = 0

    override fun spaces(): Flow<List<SpaceSummary>> = trackedFlow(
        onStart = { allSpacesCollectors++ },
        onStop = { allSpacesCollectors-- },
        value = summaries,
    )

    override fun space(spaceId: String): Flow<SpaceSummary?> = trackedFlow(
        onStart = { activeSpaceCollectors++ },
        onStop = { activeSpaceCollectors-- },
        value = summaries.firstOrNull { it.id == spaceId },
    )

    private fun <T> trackedFlow(
        onStart: () -> Unit,
        onStop: () -> Unit,
        value: T,
    ): Flow<T> = if (!trackCollectors) {
        MutableStateFlow(value)
    } else {
        flow {
            onStart()
            try {
                emit(value)
                awaitCancellation()
            } finally {
                onStop()
            }
        }
    }

    override fun members(spaceId: String): Flow<List<SpaceMember>> = MutableStateFlow(emptyList())
    override fun invitations(spaceId: String): Flow<List<SpaceInvitation>> =
        MutableStateFlow(emptyList())
    override suspend fun createSpace(name: String): String = "created"
    override suspend fun renameSpace(spaceId: String, name: String) = Unit
    override suspend fun leaveSpace(spaceId: String) = Unit
    override suspend fun removeMember(spaceId: String, userId: String) = Unit
    override suspend fun transferOwnership(spaceId: String, newOwnerId: String) = Unit
    override suspend fun createInvitation(spaceId: String): SpaceInvitation = error("Not used")
    override suspend fun revokeInvitation(invitationId: String) = Unit
    override suspend fun acceptInvitation(code: String): String = "home"
}

private class FakeConnectivityRepository(
    initialStatus: NetworkStatus = NetworkStatus.ONLINE,
) : ConnectivityRepository {
    val mutableStatus = MutableStateFlow(initialStatus)
    override val status: StateFlow<NetworkStatus> = mutableStatus
}

private class FakeSyncRepository : SyncRepository {
    private val mutableSyncStatus = MutableStateFlow(SyncStatus.REFRESHING)
    override val status: StateFlow<SyncStatus> = mutableSyncStatus
    val refreshedSpaceIds = mutableListOf<String?>()

    override suspend fun refresh(spaceId: String?): Boolean {
        refreshedSpaceIds += spaceId
        mutableSyncStatus.value = SyncStatus.ONLINE
        return true
    }

    override fun requireRefresh() {
        mutableSyncStatus.value = SyncStatus.REFRESHING
    }

    override fun markOffline() {
        mutableSyncStatus.value = SyncStatus.OFFLINE
    }

    override fun requireWritable() {
        if (mutableSyncStatus.value != SyncStatus.ONLINE) throw WriteNotAllowedException()
    }

    override fun reportWriteFailure(error: Throwable) {
        mutableSyncStatus.value = SyncStatus.OFFLINE
    }
}
