package com.dlunaunizar.bobitos.feature.betaupdate

import com.dlunaunizar.bobitos.BuildConfig
import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.CurrentDayProvider
import com.dlunaunizar.bobitos.data.betaupdate.BetaDistribution
import com.dlunaunizar.bobitos.data.betaupdate.BetaRelease
import com.dlunaunizar.bobitos.data.betaupdate.BetaUpdateError
import com.dlunaunizar.bobitos.data.betaupdate.BetaUpdateException
import com.dlunaunizar.bobitos.data.betaupdate.BetaUpdateProgress
import com.dlunaunizar.bobitos.data.repository.BetaUpdatePreferenceRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

private val r25 = BetaRelease("0.1.0-beta.25", 25, "Notas")
private val r26 = BetaRelease("0.1.0-beta.26", 26, null)

private class FakeBetaDistribution(override val isAvailable: Boolean = true, signedIn: Boolean = true) :
    BetaDistribution {
    override var isTesterSignedIn: Boolean = signedIn
    var signInError: BetaUpdateError? = null
    var signInGate: CompletableDeferred<Unit>? = null
    var checkResult: BetaRelease? = null
    var checkError: BetaUpdateError? = null
    var checkGate: CompletableDeferred<Unit>? = null
    var signInCalls = 0
    var checkCalls = 0
    var updateCalls = 0
    val updates = MutableSharedFlow<BetaUpdateProgress>(extraBufferCapacity = 16)

    override suspend fun signInTester() {
        signInCalls++
        signInGate?.await()
        signInError?.let { throw BetaUpdateException(it) }
        isTesterSignedIn = true
    }

    override suspend fun checkForNewRelease(): BetaRelease? {
        checkCalls++
        checkGate?.await()
        checkError?.let { throw BetaUpdateException(it) }
        return checkResult
    }

    override fun updateApp(): Flow<BetaUpdateProgress> = flow {
        updateCalls++
        emitAll(updates)
    }
}

private class FakeBetaUpdatePreferenceRepository : BetaUpdatePreferenceRepository {
    val last = MutableStateFlow<LocalDate?>(null)
    val postponed = MutableStateFlow<Long?>(null)
    override val lastAutoCheckDate: Flow<LocalDate?> = last
    override val postponedVersionCode: Flow<Long?> = postponed

    override suspend fun setLastAutoCheckDate(date: LocalDate) {
        last.value = date
    }

    override suspend fun setPostponedVersionCode(versionCode: Long) {
        postponed.value = versionCode
    }
}

private class FakeCurrentDayProvider(start: LocalDate) : CurrentDayProvider() {
    val day = MutableStateFlow(start)

    override fun today(zone: ZoneId): Flow<LocalDate> = day
}

class BetaUpdateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 10, 18)
    private val distribution = FakeBetaDistribution()
    private val preferences = FakeBetaUpdatePreferenceRepository()
    private val days = FakeCurrentDayProvider(today)

    private fun viewModel(dist: FakeBetaDistribution = distribution) = BetaUpdateViewModel(dist, preferences, days)

    private val BetaUpdateViewModel.state get() = uiState.value

    // Aviso automático

    @Test
    fun sinSesionDeTesterAbrirLaAppNoComprueba() = runTest {
        val dist = FakeBetaDistribution(signedIn = false)
        val vm = viewModel(dist)
        vm.onAppForeground()
        assertEquals(0, dist.checkCalls)
        assertEquals(0, dist.signInCalls)
    }

    @Test
    fun enDesarrolloAbrirLaAppNoComprueba() = runTest {
        val dist = FakeBetaDistribution(isAvailable = false)
        val vm = viewModel(dist)
        vm.onAppForeground()
        assertEquals(0, dist.checkCalls)
    }

    @Test
    fun alAbrirConUnaVersionNuevaAvisaYGastaElDia() = runTest {
        distribution.checkResult = r25
        val vm = viewModel()
        vm.onAppForeground()
        assertEquals(r25, vm.state.prompt)
        assertEquals(today, preferences.last.value)
    }

    @Test
    fun alAbrirSinVersionNuevaGastaElDiaSinAvisar() = runTest {
        val vm = viewModel()
        vm.onAppForeground()
        assertNull(vm.state.prompt)
        assertEquals(today, preferences.last.value)
        assertEquals(1, distribution.checkCalls)
    }

    @Test
    fun soloComprueba1VezAlDia() = runTest {
        val vm = viewModel()
        vm.onAppForeground()
        vm.onAppForeground()
        assertEquals(1, distribution.checkCalls)
        days.day.value = today.plusDays(1)
        vm.onAppForeground()
        assertEquals(2, distribution.checkCalls)
    }

    @Test
    fun unaComprobacionAutomaticaFallidaNoGastaElDiaNiMuestraNada() = runTest {
        for (error in listOf(BetaUpdateError.OFFLINE, BetaUpdateError.API_DISABLED)) {
            val dist = FakeBetaDistribution()
            val prefs = FakeBetaUpdatePreferenceRepository()
            val vm = BetaUpdateViewModel(dist, prefs, days)
            dist.checkError = error
            vm.onAppForeground()
            assertNull(vm.state.prompt)
            assertEquals(ManualCheckState.Idle, vm.state.manualCheck)
            assertNull(prefs.last.value)
            assertEquals(1, dist.checkCalls)
            vm.onAppForeground()
            assertEquals(2, dist.checkCalls)
        }
    }

    @Test
    fun masTardePosponeEsaVersion() = runTest {
        distribution.checkResult = r25
        val vm = viewModel()
        vm.onAppForeground()
        vm.postponePrompt()
        assertEquals(25L, preferences.postponed.value)
        assertNull(vm.state.prompt)

        days.day.value = today.plusDays(1)
        vm.onAppForeground()
        assertNull(vm.state.prompt)

        distribution.checkResult = r26
        days.day.value = today.plusDays(2)
        vm.onAppForeground()
        assertEquals(r26, vm.state.prompt)
    }

    @Test
    fun cerrarElAvisoNoPospone() = runTest {
        distribution.checkResult = r25
        val vm = viewModel()
        vm.onAppForeground()
        vm.dismissPrompt()
        assertNull(vm.state.prompt)
        assertNull(preferences.postponed.value)

        days.day.value = today.plusDays(1)
        vm.onAppForeground()
        assertEquals(r25, vm.state.prompt)
    }

    @Test
    fun actualizarAbreLaHojaYEmpiezaLaDescarga() = runTest {
        distribution.checkResult = r25
        val vm = viewModel()
        vm.onAppForeground()
        vm.acceptPrompt()
        assertNull(vm.state.prompt)
        assertEquals(r25, vm.state.sheet?.release)
        assertEquals(1, distribution.updateCalls)

        distribution.updates.emit(BetaUpdateProgress.Downloading(5, 10))
        assertEquals(BetaUpdateProgress.Downloading(5, 10), vm.state.sheet?.progress)
    }

    @Test
    fun dosAperturasSeguidasConLaComprobacionEnCursoLlamanUnaVezAlSdk() = runTest {
        distribution.checkGate = CompletableDeferred()
        val vm = viewModel()
        vm.onAppForeground()
        vm.onAppForeground()
        assertEquals(1, distribution.checkCalls)
        distribution.checkGate?.complete(Unit)
        assertEquals(1, distribution.checkCalls)
    }

    @Test
    fun conLaHojaAbiertaOUnaComprobacionManualEnCursoAbrirLaAppNoComprueba() = runTest {
        distribution.checkResult = r25
        val vm = viewModel()
        vm.checkManually()
        assertEquals(r25, vm.state.sheet?.release)
        assertNull(preferences.last.value)
        vm.onAppForeground()
        assertEquals(1, distribution.checkCalls)

        val dist = FakeBetaDistribution()
        dist.checkGate = CompletableDeferred()
        val vm2 = viewModel(dist)
        vm2.checkManually()
        assertEquals(ManualCheckState.Checking, vm2.state.manualCheck)
        vm2.onAppForeground()
        assertEquals(1, dist.checkCalls)
    }

    @Test
    fun conElAvisoAbiertoAbrirLaAppNoComprueba() = runTest {
        distribution.checkResult = r25
        val vm = viewModel()
        vm.onAppForeground()
        days.day.value = today.plusDays(1)
        vm.onAppForeground()
        assertEquals(1, distribution.checkCalls)
    }

    // Comprobación manual

    @Test
    fun sinSesionBuscarIniciaSesionYLuegoComprueba() = runTest {
        val dist = FakeBetaDistribution(signedIn = false)
        val vm = viewModel(dist)
        vm.checkManually()
        assertEquals(1, dist.signInCalls)
        assertEquals(1, dist.checkCalls)
        assertTrue(vm.state.testerSignedIn)
    }

    @Test
    fun siSeCancelaElInicioDeSesionSeMuestraYNoSeComprueba() = runTest {
        val dist = FakeBetaDistribution(signedIn = false)
        dist.signInError = BetaUpdateError.SIGN_IN_CANCELED
        val vm = viewModel(dist)
        vm.checkManually()
        assertEquals(ManualCheckState.Failed(BetaUpdateError.SIGN_IN_CANCELED), vm.state.manualCheck)
        assertEquals(0, dist.checkCalls)
        assertEquals(false, vm.state.testerSignedIn)
    }

    @Test
    fun mientrasBuscaElEstadoEsChecking() = runTest {
        distribution.checkGate = CompletableDeferred()
        val vm = viewModel()
        vm.checkManually()
        assertEquals(ManualCheckState.Checking, vm.state.manualCheck)
        distribution.checkGate?.complete(Unit)
        assertEquals(ManualCheckState.UpToDate, vm.state.manualCheck)
    }

    @Test
    fun sinVersionNuevaDiceQueTienesLaUltima() = runTest {
        val vm = viewModel()
        vm.checkManually()
        assertEquals(ManualCheckState.UpToDate, vm.state.manualCheck)
        assertNull(vm.state.sheet)
    }

    @Test
    fun unErrorAlBuscarSeMuestra() = runTest {
        distribution.checkError = BetaUpdateError.OFFLINE
        val vm = viewModel()
        vm.checkManually()
        assertEquals(ManualCheckState.Failed(BetaUpdateError.OFFLINE), vm.state.manualCheck)
    }

    @Test
    fun buscarEnsenaLaVersionPospuestaYNoDescargaSola() = runTest {
        preferences.postponed.value = 25
        distribution.checkResult = r25
        val vm = viewModel()
        vm.checkManually()
        assertEquals(BetaUpdateSheetState(r25, null), vm.state.sheet)
        assertEquals(ManualCheckState.Idle, vm.state.manualCheck)
        assertEquals(0, distribution.updateCalls)
    }

    @Test
    fun enDesarrolloBuscarNoHaceNada() = runTest {
        val dist = FakeBetaDistribution(isAvailable = false)
        val vm = viewModel(dist)
        vm.checkManually()
        assertEquals(0, dist.signInCalls)
        assertEquals(0, dist.checkCalls)
        assertEquals(ManualCheckState.Idle, vm.state.manualCheck)
    }

    // Hoja

    @Test
    fun cancelarLaInstalacionPermiteInstalarOtraVez() = runTest {
        distribution.checkResult = r25
        val vm = viewModel()
        vm.checkManually()
        vm.startDownload()
        distribution.updates.emit(BetaUpdateProgress.InstallCanceled)
        assertEquals(BetaUpdateProgress.InstallCanceled, vm.state.sheet?.progress)
        vm.startDownload()
        assertEquals(2, distribution.updateCalls)
    }

    @Test
    fun unFalloDeDescargaPermiteReintentar() = runTest {
        distribution.checkResult = r25
        val vm = viewModel()
        vm.checkManually()
        vm.startDownload()
        distribution.updates.emit(BetaUpdateProgress.Failed(BetaUpdateError.DOWNLOAD_FAILED))
        vm.startDownload()
        assertEquals(2, distribution.updateCalls)
    }

    @Test
    fun startDownloadConUnaDescargaVivaNoLanzaOtra() = runTest {
        distribution.checkResult = r25
        val vm = viewModel()
        vm.checkManually()
        vm.startDownload()
        vm.startDownload()
        assertEquals(1, distribution.updateCalls)
    }

    @Test
    fun cerrarLaHojaNoCortaLaDescargaYBuscarLaReabreConSuProgreso() = runTest {
        distribution.checkResult = r25
        val vm = viewModel()
        vm.checkManually()
        vm.startDownload()
        vm.closeSheet()
        assertNull(vm.state.sheet)

        distribution.updates.emit(BetaUpdateProgress.Downloading(7, 10))
        vm.checkManually()
        assertEquals(BetaUpdateProgress.Downloading(7, 10), vm.state.sheet?.progress)
        assertEquals(1, distribution.checkCalls)
    }

    @Test
    fun laVersionInstaladaEsLaDeLaCompilacion() = runTest {
        assertEquals(BuildConfig.VERSION_NAME, viewModel().state.installedVersion)
    }
}
