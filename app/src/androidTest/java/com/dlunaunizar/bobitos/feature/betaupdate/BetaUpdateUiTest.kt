package com.dlunaunizar.bobitos.feature.betaupdate

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.data.betaupdate.BetaRelease
import com.dlunaunizar.bobitos.data.betaupdate.BetaUpdateError
import com.dlunaunizar.bobitos.data.betaupdate.BetaUpdateProgress
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BetaUpdateUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val release = BetaRelease("0.1.0-beta.25", 25, "Receta nueva y arreglos.")

    private fun sheet(
        release: BetaRelease = this.release,
        progress: BetaUpdateProgress? = null,
        onStart: () -> Unit = {
        },
    ) = compose.setContent {
        BobitosTheme {
            BetaUpdateSheet(BetaUpdateSheetState(release, progress), onStartDownload = onStart, onClose = {})
        }
    }

    private fun row(state: BetaUpdateUiState) =
        compose.setContent { BobitosTheme { AppVersionRow(state = state, onCheck = {}) } }

    private fun state(
        available: Boolean = true,
        signedIn: Boolean = true,
        manual: ManualCheckState = ManualCheckState.Idle,
    ) = BetaUpdateUiState("0.1.0-beta.24", available, signedIn, manual)

    @Test
    fun elAvisoMuestraLaVersionYSusDosBotones() {
        var updates = 0
        var laters = 0
        compose.setContent {
            BobitosTheme { BetaUpdatePrompt(release, onUpdate = { updates++ }, onLater = { laters++ }, onDismiss = {}) }
        }
        compose.onNodeWithText("Hay una beta nueva").assertIsDisplayed()
        compose.onNodeWithText("0.1.0-beta.25", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Actualizar").performClick()
        compose.onNodeWithText("Más tarde").performClick()
        assertEquals(1, updates)
        assertEquals(1, laters)
    }

    @Test
    fun laHojaListaOfreceDescargarYEnseñaLasNotas() {
        sheet()
        compose.onNodeWithText("Descargar e instalar").assertIsDisplayed()
        compose.onNodeWithText("Novedades").assertIsDisplayed()
        compose.onNodeWithText("Receta nueva y arreglos.").assertIsDisplayed()
    }

    @Test
    fun sinNotasNoSeMuestraNovedades() {
        sheet(release.copy(releaseNotes = null))
        compose.onAllNodesWithText("Novedades").assertCountEquals(0)
    }

    @Test
    fun laDescargaMuestraSuPorcentaje() {
        sheet(progress = BetaUpdateProgress.Downloading(5_242_880, 10_485_760))
        compose.onNodeWithText("50 %", substring = true).assertIsDisplayed()
    }

    @Test
    fun sinTotalNoSeMuestraPorcentaje() {
        sheet(progress = BetaUpdateProgress.Downloading(100, 0))
        compose.onAllNodesWithText("%", substring = true).assertCountEquals(0)
    }

    @Test
    fun instalacionCanceladaOfreceInstalar() {
        var starts = 0
        sheet(progress = BetaUpdateProgress.InstallCanceled, onStart = { starts++ })
        compose.onNodeWithText("Instalar").performClick()
        assertEquals(1, starts)
    }

    @Test
    fun unFalloOfreceReintentar() {
        sheet(progress = BetaUpdateProgress.Failed(BetaUpdateError.DOWNLOAD_FAILED))
        compose.onNodeWithText("Reintentar").assertIsDisplayed()
    }

    @Test
    fun laFilaEnDesarrolloSoloMuestraLaVersion() {
        row(state(available = false, signedIn = false))
        compose.onNodeWithText("Versión").assertIsDisplayed()
        compose.onNodeWithText("0.1.0-beta.24", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("Buscar actualizaciones").assertCountEquals(0)
    }

    @Test
    fun laFilaSinSesionAvisaDeLaCuentaDeGoogle() {
        row(state(signedIn = false))
        compose.onNodeWithText("Buscar actualizaciones").assertIsDisplayed()
        compose.onNodeWithText("cuenta de Google", substring = true).assertIsDisplayed()
    }

    @Test
    fun laFilaMientrasBuscaDesactivaElBoton() {
        row(state(manual = ManualCheckState.Checking))
        compose.onNodeWithText("Buscar actualizaciones").assertIsNotEnabled()
    }

    @Test
    fun laFilaAlDiaDiceQueTienesLaUltima() {
        row(state(manual = ManualCheckState.UpToDate))
        compose.onNodeWithText("Tienes la última versión", substring = true).assertIsDisplayed()
    }

    @Test
    fun laFilaConErrorPermiteVolverABuscar() {
        row(state(manual = ManualCheckState.Failed(BetaUpdateError.OFFLINE)))
        compose.onNodeWithText("Buscar actualizaciones").assertIsEnabled()
    }
}
