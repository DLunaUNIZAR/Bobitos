package com.dlunaunizar.bobitos.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.component.GroupedRow
import com.dlunaunizar.bobitos.core.designsystem.component.SectionHeader
import com.dlunaunizar.bobitos.core.designsystem.component.rowPosition
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.designsystem.theme.moduleColors
import com.dlunaunizar.bobitos.core.designsystem.theme.moduleTint

/** «Más»: los módulos que no caben en la barra inferior y, debajo, los catálogos. */
@Composable
internal fun SpaceMoreScreen(onOpen: (BobitosDestination) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ModuleRows(
            title = stringResource(R.string.more_group_space),
            destinations = BobitosDestination.moreDestinations,
            onOpen = onOpen,
        )
        CatalogRows(onOpen = onOpen)
    }
}

/** Grupo de filas de módulos: cada una teñida de su módulo, con el icono en la pastilla de «Catálogos» y una flecha. */
@Composable
internal fun ModuleRows(
    title: String,
    destinations: List<BobitosDestination>,
    onOpen: (BobitosDestination) -> Unit,
    supporting: Map<BobitosDestination, String> = emptyMap(),
) {
    Column {
        SectionHeader(title = title)
        destinations.forEachIndexed { index, destination ->
            val module = destination.appModule()
            val colors = module?.let { moduleColors(it) }
            GroupedRow(
                position = rowPosition(index, destinations.size),
                tint = module?.let { moduleTint(it) },
                onClick = { onOpen(destination) },
            ) {
                IconPill(
                    container = colors?.container ?: MaterialTheme.colorScheme.secondaryContainer,
                    content = colors?.onContainer ?: MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Icon(destination.icon, contentDescription = null, tint = it)
                }
                Column(Modifier.weight(1f)) {
                    RowTitle(stringResource(destination.titleRes))
                    supporting[destination]?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Spacing.xs),
                        )
                    }
                }
                RowArrow()
            }
        }
    }
}

/** Grupo «Catálogos»: filas neutras con el icono en una pastilla del contenedor de su módulo. */
@Composable
internal fun CatalogRows(onOpen: (BobitosDestination) -> Unit) {
    val destinations = BobitosDestination.catalogDestinations
    Column {
        SectionHeader(title = stringResource(R.string.more_group_catalogs))
        destinations.forEachIndexed { index, destination ->
            val colors = destination.appModule()?.let { moduleColors(it) }
            GroupedRow(position = rowPosition(index, destinations.size), onClick = { onOpen(destination) }) {
                IconPill(
                    container = colors?.container ?: MaterialTheme.colorScheme.secondaryContainer,
                    content = colors?.onContainer ?: MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Icon(destination.icon, contentDescription = null, tint = it)
                }
                RowTitle(stringResource(destination.titleRes), Modifier.weight(1f))
                RowArrow()
            }
        }
    }
}

@Composable
private fun IconPill(container: Color, content: Color, icon: @Composable (Color) -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = Spacing.md)
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        icon(content)
    }
}

@Composable
internal fun RowTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier.padding(start = Spacing.xs),
    )
}

@Composable
internal fun RowArrow() {
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
