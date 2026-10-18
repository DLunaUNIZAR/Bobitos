package com.dlunaunizar.bobitos.feature.betaupdate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.BuildConfig
import com.dlunaunizar.bobitos.core.common.CurrentDayProvider
import com.dlunaunizar.bobitos.data.betaupdate.BetaDistribution
import com.dlunaunizar.bobitos.data.betaupdate.BetaRelease
import com.dlunaunizar.bobitos.data.betaupdate.BetaUpdateError
import com.dlunaunizar.bobitos.data.betaupdate.BetaUpdateException
import com.dlunaunizar.bobitos.data.betaupdate.BetaUpdateProgress
import com.dlunaunizar.bobitos.data.repository.BetaUpdatePreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/** Estado de la comprobación manual de «Perfil». */
sealed interface ManualCheckState {
    data object Idle : ManualCheckState

    data object Checking : ManualCheckState

    data object UpToDate : ManualCheckState

    data class Failed(val error: BetaUpdateError) : ManualCheckState
}

/** Hoja de la actualización. Con [progress] a `null`, la versión está lista para descargar. */
data class BetaUpdateSheetState(val release: BetaRelease, val progress: BetaUpdateProgress? = null)

data class BetaUpdateUiState(
    val installedVersion: String,
    val available: Boolean,
    val testerSignedIn: Boolean,
    val manualCheck: ManualCheckState = ManualCheckState.Idle,
    val prompt: BetaRelease? = null,
    val sheet: BetaUpdateSheetState? = null,
)

/** Acciones de la interfaz de actualizaciones; las implementa [BetaUpdateViewModel]. */
interface BetaUpdateActions {
    fun checkManually()

    fun acceptPrompt()

    fun postponePrompt()

    fun dismissPrompt()

    fun startDownload()

    fun closeSheet()
}

/**
 * Estado compartido por el aviso automático, la hoja de la actualización y la fila de «Perfil».
 * Es de nivel de actividad: sobrevive a los giros y a las vueltas desde el navegador o el instalador.
 */
@HiltViewModel
class BetaUpdateViewModel @Inject constructor(
    private val distribution: BetaDistribution,
    private val preferences: BetaUpdatePreferenceRepository,
    private val currentDay: CurrentDayProvider,
) : ViewModel(),
    BetaUpdateActions {
    private val _uiState =
        MutableStateFlow(
            BetaUpdateUiState(
                installedVersion = BuildConfig.VERSION_NAME,
                available = distribution.isAvailable,
                testerSignedIn = distribution.isTesterSignedIn,
            ),
        )
    val uiState: StateFlow<BetaUpdateUiState> = _uiState.asStateFlow()

    private var autoChecking = false
    private var downloadJob: Job? = null
    private var downloadRelease: BetaRelease? = null
    private var lastProgress: BetaUpdateProgress? = null

    /** La app pasó a primer plano: comprueba en silencio si toca (como mucho una vez al día). */
    fun onAppForeground() {
        if (autoChecking || isBusy(_uiState.value)) return
        autoChecking = true
        viewModelScope.launch {
            try {
                autoCheck()
            } finally {
                autoChecking = false
            }
        }
    }

    /** Con el aviso o la hoja abiertos, o una comprobación manual en curso, no se comprueba. */
    private fun isBusy(state: BetaUpdateUiState): Boolean =
        state.prompt != null || state.sheet != null || state.manualCheck == ManualCheckState.Checking

    private suspend fun autoCheck() {
        val today = currentDay.today(ZoneId.systemDefault()).first()
        val lastCheck = preferences.lastAutoCheckDate.first()
        if (!shouldAutoCheck(distribution.isAvailable, _uiState.value.testerSignedIn, today, lastCheck)) return
        val release =
            try {
                distribution.checkForNewRelease()
            } catch (_: BetaUpdateException) {
                return
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return
            }
        preferences.setLastAutoCheckDate(today)
        val state = _uiState.value
        if (state.sheet == null &&
            state.prompt == null &&
            shouldPrompt(release, preferences.postponedVersionCode.first())
        ) {
            _uiState.update { it.copy(prompt = release) }
        }
    }

    override fun postponePrompt() {
        val release = _uiState.value.prompt ?: return
        _uiState.update { it.copy(prompt = null) }
        viewModelScope.launch { preferences.setPostponedVersionCode(release.versionCode) }
    }

    override fun dismissPrompt() {
        _uiState.update { it.copy(prompt = null) }
    }

    override fun acceptPrompt() {
        val release = _uiState.value.prompt ?: return
        _uiState.update { it.copy(prompt = null, sheet = BetaUpdateSheetState(release)) }
        startDownload()
    }

    override fun checkManually() {
        val state = _uiState.value
        if (!state.available || state.manualCheck == ManualCheckState.Checking) return
        if (downloadInProgress()) {
            downloadRelease?.let { release ->
                _uiState.update { it.copy(sheet = BetaUpdateSheetState(release, lastProgress)) }
            }
            return
        }
        _uiState.update { it.copy(manualCheck = ManualCheckState.Checking) }
        viewModelScope.launch {
            try {
                if (!_uiState.value.testerSignedIn) {
                    distribution.signInTester()
                    _uiState.update { it.copy(testerSignedIn = true) }
                }
                val release = distribution.checkForNewRelease()
                _uiState.update {
                    if (release == null) {
                        it.copy(manualCheck = ManualCheckState.UpToDate)
                    } else {
                        it.copy(manualCheck = ManualCheckState.Idle, sheet = BetaUpdateSheetState(release))
                    }
                }
            } catch (e: BetaUpdateException) {
                failManualCheck(e.error)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failManualCheck(BetaUpdateError.UNKNOWN)
            }
        }
    }

    private fun failManualCheck(error: BetaUpdateError) {
        _uiState.update { it.copy(manualCheck = ManualCheckState.Failed(error)) }
    }

    /** Hay una recogida viva que no ha acabado en cancelación ni en fallo. */
    private fun downloadInProgress(): Boolean {
        val progress = lastProgress
        return downloadJob?.isActive == true &&
            progress != BetaUpdateProgress.InstallCanceled &&
            progress !is BetaUpdateProgress.Failed
    }

    override fun startDownload() {
        // Con una descarga en marcha no se lanza otra recogida; tras cancelar o fallar, la vieja
        // (aún viva hasta que el SDK cierre el flujo) se cancela antes de empezar la nueva.
        if (downloadInProgress()) return
        val release = _uiState.value.sheet?.release ?: return
        downloadJob?.cancel()
        downloadRelease = release
        publishProgress(BetaUpdateProgress.Downloading(0, 0))
        downloadJob =
            viewModelScope.launch {
                distribution.updateApp().collect { publishProgress(it) }
            }
    }

    private fun publishProgress(progress: BetaUpdateProgress) {
        lastProgress = progress
        _uiState.update { state -> state.copy(sheet = state.sheet?.copy(progress = progress)) }
    }

    override fun closeSheet() {
        _uiState.update { it.copy(sheet = null) }
    }
}
