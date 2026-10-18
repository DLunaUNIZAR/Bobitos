package com.dlunaunizar.bobitos.data.betaupdate

import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status
import com.google.firebase.appdistribution.UpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class BetaUpdateProgressTrackerTest {
    @Test
    fun `cancelling the installer after the download comes as install canceled`() {
        val tracker = BetaUpdateProgressTracker()
        assertEquals(BetaUpdateProgress.AwaitingInstall, tracker.onProgress(UpdateStatus.DOWNLOADED, 10, 10))
        assertEquals(BetaUpdateProgress.InstallCanceled, tracker.onFailure(Status.INSTALLATION_FAILURE))
    }

    @Test
    fun `an install failed status after the download comes as install canceled`() {
        val tracker = BetaUpdateProgressTracker()
        tracker.onProgress(UpdateStatus.DOWNLOADED, 10, 10)
        assertEquals(BetaUpdateProgress.InstallCanceled, tracker.onProgress(UpdateStatus.INSTALL_FAILED, 10, 10))
    }

    @Test
    fun `an install failure before the download stays a failure`() {
        val tracker = BetaUpdateProgressTracker()
        assertEquals(
            BetaUpdateProgress.Failed(BetaUpdateError.INSTALL_FAILED),
            tracker.onFailure(Status.INSTALLATION_FAILURE),
        )
    }

    @Test
    fun `a download failure after the download stays a failure`() {
        val tracker = BetaUpdateProgressTracker()
        tracker.onProgress(UpdateStatus.DOWNLOADED, 10, 10)
        assertEquals(
            BetaUpdateProgress.Failed(BetaUpdateError.DOWNLOAD_FAILED),
            tracker.onFailure(Status.DOWNLOAD_FAILURE),
        )
    }

    @Test
    fun `an unknown failure is unknown`() {
        val tracker = BetaUpdateProgressTracker()
        assertEquals(BetaUpdateProgress.Failed(BetaUpdateError.UNKNOWN), tracker.onFailure(null))
    }
}
