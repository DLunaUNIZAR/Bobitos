package com.dlunaunizar.bobitos.core.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.core.designsystem.theme.categoryCardColors

// Tarjeta de un destino (menú principal y «Más»): icono con el color del módulo y título.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModuleCard(destination: BobitosDestination, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = destination.moduleColor()?.let { categoryCardColors(it) } ?: CardDefaults.cardColors(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                destination.icon,
                contentDescription = null,
                tint = destination.moduleColor() ?: MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(destination.titleRes),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
