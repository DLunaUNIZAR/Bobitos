package com.dlunaunizar.bobitos.data.betaupdate

import com.google.firebase.Firebase
import com.google.firebase.appdistribution.AppDistributionRelease
import com.google.firebase.appdistribution.FirebaseAppDistributionException
import com.google.firebase.appdistribution.UpdateStatus
import com.google.firebase.appdistribution.appDistribution
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Implementación sobre `Firebase.appDistribution`; solo se usa en `release`. */
class FirebaseBetaDistribution : BetaDistribution {
    private val sdk get() = Firebase.appDistribution

    override val isAvailable: Boolean = true
    override val isTesterSignedIn: Boolean get() = sdk.isTesterSignedIn

    override suspend fun signInTester() {
        wrapping { sdk.signInTester().await() }
    }

    override suspend fun checkForNewRelease(): BetaRelease? = wrapping {
        sdk.checkForNewRelease().await()?.toBetaRelease()
    }

    override fun updateApp(): Flow<BetaUpdateProgress> = callbackFlow {
        val task = sdk.updateApp()
        var downloaded = false
        task.addOnProgressListener { progress ->
            progress.updateStatus
                .toProgress(progress.apkBytesDownloaded, progress.apkFileTotalBytes)
                ?.let {
                    if (it == BetaUpdateProgress.AwaitingInstall) downloaded = true
                    trySend(it.afterDownload(downloaded))
                }
        }
        task.addOnCompleteListener { result ->
            val failure = result.exception
            if (failure != null) {
                val status = (failure as? FirebaseAppDistributionException)?.errorCode
                val failed = status?.toFailureProgress() ?: BetaUpdateProgress.Failed(BetaUpdateError.UNKNOWN)
                trySend(failed.afterDownload(downloaded))
            }
            close()
        }
        awaitClose()
    }

    private suspend fun <T> wrapping(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirebaseAppDistributionException) {
        throw BetaUpdateException(e.errorCode.toBetaUpdateError(), e)
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        throw BetaUpdateException(BetaUpdateError.UNKNOWN, e)
    }
}

private fun AppDistributionRelease.toBetaRelease() = BetaRelease(
    displayVersion = displayVersion,
    versionCode = versionCode,
    releaseNotes = releaseNotesOrNull(releaseNotes),
)

internal fun FirebaseAppDistributionException.Status.toBetaUpdateError(): BetaUpdateError = when (this) {
    FirebaseAppDistributionException.Status.AUTHENTICATION_CANCELED -> BetaUpdateError.SIGN_IN_CANCELED
    FirebaseAppDistributionException.Status.AUTHENTICATION_FAILURE -> BetaUpdateError.SIGN_IN_FAILED
    FirebaseAppDistributionException.Status.NETWORK_FAILURE -> BetaUpdateError.OFFLINE
    FirebaseAppDistributionException.Status.API_DISABLED -> BetaUpdateError.API_DISABLED
    FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE -> BetaUpdateError.DOWNLOAD_FAILED
    FirebaseAppDistributionException.Status.INSTALLATION_FAILURE -> BetaUpdateError.INSTALL_FAILED
    FirebaseAppDistributionException.Status.NOT_IMPLEMENTED -> BetaUpdateError.NOT_AVAILABLE
    else -> BetaUpdateError.UNKNOWN
}

internal fun FirebaseAppDistributionException.Status.toFailureProgress(): BetaUpdateProgress =
    if (this == FirebaseAppDistributionException.Status.INSTALLATION_CANCELED) {
        BetaUpdateProgress.InstallCanceled
    } else {
        BetaUpdateProgress.Failed(toBetaUpdateError())
    }

/**
 * Con un APK, el SDK no distingue cancelar el instalador de que falle: ambos llegan como fallo de
 * instalación. Si la descarga ya había terminado, se trata como cancelación («Instalar»).
 */
internal fun BetaUpdateProgress.afterDownload(downloaded: Boolean): BetaUpdateProgress =
    if (downloaded && this == BetaUpdateProgress.Failed(BetaUpdateError.INSTALL_FAILED)) {
        BetaUpdateProgress.InstallCanceled
    } else {
        this
    }

internal fun UpdateStatus.toProgress(downloadedBytes: Long, totalBytes: Long): BetaUpdateProgress? = when (this) {
    UpdateStatus.PENDING -> BetaUpdateProgress.Downloading(0, totalBytes)
    UpdateStatus.DOWNLOADING -> BetaUpdateProgress.Downloading(downloadedBytes, totalBytes)
    UpdateStatus.DOWNLOADED -> BetaUpdateProgress.AwaitingInstall
    UpdateStatus.INSTALL_CANCELED -> BetaUpdateProgress.InstallCanceled
    UpdateStatus.DOWNLOAD_FAILED -> BetaUpdateProgress.Failed(BetaUpdateError.DOWNLOAD_FAILED)
    UpdateStatus.INSTALL_FAILED -> BetaUpdateProgress.Failed(BetaUpdateError.INSTALL_FAILED)
    else -> null
}

internal fun releaseNotesOrNull(raw: String?): String? = raw?.takeIf { it.isNotBlank() }
