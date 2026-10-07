package com.dlunaunizar.bobitos.core.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.core.designsystem.theme.categoryCardColors

// Tarjeta de un destino (menú principal y «Más»): icono con el color del módulo, título y,
// si hay, un contador.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpaceHomeCard(destination: BobitosDestination, count: Int, onClick: () -> Unit) {
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
            if (count > 0) {
                SpaceHomeCountBadge(count = count, color = destination.moduleColor())
            }
        }
    }
}

@Composable
internal fun SpaceHomeCountBadge(count: Int, color: Color?) {
    val badgeColor = color ?: MaterialTheme.colorScheme.primary
    Surface(shape = CircleShape, color = badgeColor) {
        Text(
            text = count.toString(),
            modifier = Modifier
                .defaultMinSize(minWidth = 24.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            color = contentColorFor(badgeColor),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}
