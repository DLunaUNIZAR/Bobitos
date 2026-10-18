package com.dlunaunizar.bobitos.data.betaupdate

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Una beta publicada en App Distribution. */
data class BetaRelease(val displayVersion: String, val versionCode: Long, val releaseNotes: String?)

/** Motivos de fallo que la app sabe explicar, sin exponer los tipos del SDK. */
enum class BetaUpdateError {
    SIGN_IN_CANCELED,
    SIGN_IN_FAILED,
    OFFLINE,
    API_DISABLED,
    DOWNLOAD_FAILED,
    INSTALL_FAILED,
    NOT_AVAILABLE,
    UNKNOWN,
}

class BetaUpdateException(val error: BetaUpdateError, cause: Throwable? = null) : Exception(cause)

/** Estados de la descarga e instalación de una beta. */
sealed interface BetaUpdateProgress {
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : BetaUpdateProgress {
        /** Parte descargada entre 0 y 1, o `null` si no se conoce el tamaño total. */
        val fraction: Float?
            get() =
                if (totalBytes <= 0) {
                    null
                } else {
                    (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                }
    }

    /** Descargada: Android pide confirmar la instalación. */
    data object AwaitingInstall : BetaUpdateProgress

    data object InstallCanceled : BetaUpdateProgress

    data class Failed(val error: BetaUpdateError) : BetaUpdateProgress
}

/** Envoltorio de Firebase App Distribution con tipos propios. */
interface BetaDistribution {
    val isAvailable: Boolean
    val isTesterSignedIn: Boolean

    /** @throws BetaUpdateException si falla el inicio de sesión. */
    suspend fun signInTester()

    /** @throws BetaUpdateException si falla la comprobación. */
    suspend fun checkForNewRelease(): BetaRelease?

    /**
     * Emite los estados hasta que acaba la tarea del SDK y entonces termina.
     * Un fallo llega como [BetaUpdateProgress.Failed] o [BetaUpdateProgress.InstallCanceled], nunca como excepción.
     */
    fun updateApp(): Flow<BetaUpdateProgress>
}

/** Versión `debug`: nunca llama al SDK. */
object DisabledBetaDistribution : BetaDistribution {
    override val isAvailable: Boolean = false
    override val isTesterSignedIn: Boolean = false

    override suspend fun signInTester(): Unit = throw BetaUpdateException(BetaUpdateError.NOT_AVAILABLE)

    override suspend fun checkForNewRelease(): BetaRelease? = throw BetaUpdateException(BetaUpdateError.NOT_AVAILABLE)

    override fun updateApp(): Flow<BetaUpdateProgress> =
        flowOf(BetaUpdateProgress.Failed(BetaUpdateError.NOT_AVAILABLE))
}
