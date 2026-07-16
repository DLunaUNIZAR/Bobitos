package com.dlunaunizar.bobitos.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.SyncStatus

@Composable
fun SyncStatusBanner(
    status: SyncStatus,
    modifier: Modifier = Modifier,
) {
    if (status == SyncStatus.ONLINE) return

    Surface(
        color = if (status == SyncStatus.OFFLINE) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (status == SyncStatus.REFRESHING) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            Text(
                text = stringResource(
                    if (status == SyncStatus.OFFLINE) {
                        R.string.sync_offline
                    } else {
                        R.string.sync_refreshing
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
