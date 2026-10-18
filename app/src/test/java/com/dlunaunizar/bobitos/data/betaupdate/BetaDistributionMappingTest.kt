package com.dlunaunizar.bobitos.data.betaupdate

import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status
import com.google.firebase.appdistribution.UpdateStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BetaDistributionMappingTest {
    @Test
    fun `cada Status del SDK se traduce a su error`() {
        val esperado =
            mapOf(
                Status.AUTHENTICATION_CANCELED to BetaUpdateError.SIGN_IN_CANCELED,
                Status.AUTHENTICATION_FAILURE to BetaUpdateError.SIGN_IN_FAILED,
                Status.NETWORK_FAILURE to BetaUpdateError.OFFLINE,
                Status.API_DISABLED to BetaUpdateError.API_DISABLED,
                Status.DOWNLOAD_FAILURE to BetaUpdateError.DOWNLOAD_FAILED,
                Status.INSTALLATION_FAILURE to BetaUpdateError.INSTALL_FAILED,
                Status.NOT_IMPLEMENTED to BetaUpdateError.NOT_AVAILABLE,
                Status.UNKNOWN to BetaUpdateError.UNKNOWN,
            )
        esperado.forEach { (status, error) -> assertEquals(status.name, error, status.toBetaUpdateError()) }
    }

    @Test
    fun `cancelar la instalación no es un error`() {
        assertEquals(BetaUpdateProgress.InstallCanceled, Status.INSTALLATION_CANCELED.toFailureProgress())
        assertEquals(
            BetaUpdateProgress.Failed(BetaUpdateError.OFFLINE),
            Status.NETWORK_FAILURE.toFailureProgress(),
        )
    }

    @Test
    fun `cada UpdateStatus se traduce a su progreso`() {
        assertEquals(BetaUpdateProgress.Downloading(0, 10), UpdateStatus.PENDING.toProgress(5, 10))
        assertEquals(BetaUpdateProgress.Downloading(5, 10), UpdateStatus.DOWNLOADING.toProgress(5, 10))
        assertEquals(BetaUpdateProgress.AwaitingInstall, UpdateStatus.DOWNLOADED.toProgress(5, 10))
        assertEquals(BetaUpdateProgress.InstallCanceled, UpdateStatus.INSTALL_CANCELED.toProgress(5, 10))
        assertEquals(
            BetaUpdateProgress.Failed(BetaUpdateError.DOWNLOAD_FAILED),
            UpdateStatus.DOWNLOAD_FAILED.toProgress(5, 10),
        )
        assertEquals(
            BetaUpdateProgress.Failed(BetaUpdateError.INSTALL_FAILED),
            UpdateStatus.INSTALL_FAILED.toProgress(5, 10),
        )
        assertNull(UpdateStatus.REDIRECTED_TO_PLAY.toProgress(5, 10))
    }

    @Test
    fun `fraction es la parte descargada`() {
        assertEquals(0.25f, BetaUpdateProgress.Downloading(25, 100).fraction)
    }

    @Test
    fun `fraction es null si no se conoce el total`() {
        assertNull(BetaUpdateProgress.Downloading(5, 0).fraction)
        assertNull(BetaUpdateProgress.Downloading(5, -1).fraction)
    }

    @Test
    fun `notas en blanco cuentan como sin notas`() {
        assertNull(releaseNotesOrNull(null))
        assertNull(releaseNotesOrNull("  \n"))
        assertEquals("Hola", releaseNotesOrNull("Hola"))
    }

    @Test
    fun `la distribución desactivada no está disponible`() = runTest {
        assertFalse(DisabledBetaDistribution.isAvailable)
        assertFalse(DisabledBetaDistribution.isTesterSignedIn)
        val e = runCatching { DisabledBetaDistribution.checkForNewRelease() }.exceptionOrNull()
        assertTrue(e is BetaUpdateException)
        assertEquals(BetaUpdateError.NOT_AVAILABLE, (e as BetaUpdateException).error)
    }

    @Test
    fun `tras descargar, un fallo de instalacion cuenta como cancelado`() {
        val failed = BetaUpdateProgress.Failed(BetaUpdateError.INSTALL_FAILED)
        assertEquals(BetaUpdateProgress.InstallCanceled, failed.afterDownload(downloaded = true))
        assertEquals(
            BetaUpdateProgress.InstallCanceled,
            Status.INSTALLATION_FAILURE.toFailureProgress().afterDownload(downloaded = true),
        )
    }

    @Test
    fun `sin descargar, un fallo de instalacion sigue siendo un fallo`() {
        val failed = BetaUpdateProgress.Failed(BetaUpdateError.INSTALL_FAILED)
        assertEquals(failed, failed.afterDownload(downloaded = false))
        val download = BetaUpdateProgress.Failed(BetaUpdateError.DOWNLOAD_FAILED)
        assertEquals(download, download.afterDownload(downloaded = true))
    }
}
