package com.dlunaunizar.bobitos.feature.betaupdate

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosInfoSheet
import com.dlunaunizar.bobitos.core.designsystem.component.InfoSheetAction
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.data.betaupdate.BetaUpdateError
import com.dlunaunizar.bobitos.data.betaupdate.BetaUpdateProgress
import java.util.Locale

private const val BYTES_PER_MB = 1024f * 1024f
private val SpanishLocale: Locale = Locale.forLanguageTag("es-ES")

/** Mensaje en palabras normales de cada fallo; nunca el texto de la excepción. */
@StringRes
internal fun BetaUpdateError.messageRes(): Int = when (this) {
    BetaUpdateError.SIGN_IN_CANCELED -> R.string.beta_update_error_sign_in_canceled
    BetaUpdateError.SIGN_IN_FAILED -> R.string.beta_update_error_sign_in_failed
    BetaUpdateError.OFFLINE -> R.string.beta_update_error_offline
    BetaUpdateError.API_DISABLED -> R.string.beta_update_error_api_disabled
    BetaUpdateError.DOWNLOAD_FAILED -> R.string.beta_update_error_download_failed
    BetaUpdateError.INSTALL_FAILED -> R.string.beta_update_error_install_failed
    BetaUpdateError.NOT_AVAILABLE -> R.string.beta_update_error_not_available
    BetaUpdateError.UNKNOWN -> R.string.beta_update_error_unknown
}

/**
 * Hoja de la actualización: arriba el estado (descarga, instalación o fallo), después «Novedades» con las
 * notas y, al pie, la acción que toque junto a «Cerrar».
 */
@Composable
fun BetaUpdateSheet(state: BetaUpdateSheetState, onStartDownload: () -> Unit, onClose: () -> Unit) {
    val progress = state.progress
    val notes = state.release.releaseNotes?.takeIf { it.isNotBlank() }
    val primaryLabel = when (progress) {
        null -> stringResource(R.string.beta_update_download_install)
        BetaUpdateProgress.InstallCanceled -> stringResource(R.string.beta_update_install)
        is BetaUpdateProgress.Failed -> stringResource(R.string.retry)
        is BetaUpdateProgress.Downloading, BetaUpdateProgress.AwaitingInstall -> null
    }
    BobitosInfoSheet(
        title = stringResource(R.string.beta_update_sheet_title, state.release.displayVersion),
        onDismiss = onClose,
        primaryAction = primaryLabel?.let { InfoSheetAction(it, onStartDownload) },
        header = progress?.let { { SheetStatus(it) } },
    ) {
        if (notes != null) {
            Text(
                text = stringResource(R.string.beta_update_notes_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = notes, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ColumnScope.SheetStatus(progress: BetaUpdateProgress) {
    when (progress) {
        is BetaUpdateProgress.Downloading -> DownloadStatus(progress)
        BetaUpdateProgress.AwaitingInstall -> StatusText(stringResource(R.string.beta_update_awaiting_install))
        BetaUpdateProgress.InstallCanceled -> StatusText(stringResource(R.string.beta_update_install_canceled))
        is BetaUpdateProgress.Failed ->
            StatusText(stringResource(progress.error.messageRes()), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun StatusText(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun DownloadStatus(progress: BetaUpdateProgress.Downloading) {
    val fraction = progress.fraction
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StatusText(stringResource(R.string.beta_update_downloading))
        if (fraction == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) },
            )
            // Sin región viva: el avance lo anuncia la barra y no habría que repetirlo en cada cambio.
            Text(
                text = stringResource(
                    R.string.beta_update_progress_percent,
                    (fraction * 100).toInt(),
                    megabytes(progress.downloadedBytes),
                    megabytes(progress.totalBytes),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.beta_update_download_keeps_going),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun megabytes(bytes: Long): String = String.format(SpanishLocale, "%.1f", bytes / BYTES_PER_MB)
