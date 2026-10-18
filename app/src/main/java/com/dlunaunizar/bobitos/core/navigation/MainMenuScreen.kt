package com.dlunaunizar.bobitos.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.messageRes
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosTopBar
import com.dlunaunizar.bobitos.core.designsystem.component.ErrorState
import com.dlunaunizar.bobitos.core.designsystem.component.GroupedRow
import com.dlunaunizar.bobitos.core.designsystem.component.LoadingState
import com.dlunaunizar.bobitos.core.designsystem.component.RowPosition
import com.dlunaunizar.bobitos.core.designsystem.component.SectionHeader
import com.dlunaunizar.bobitos.core.designsystem.component.SyncStatusBanner
import com.dlunaunizar.bobitos.core.designsystem.component.rowPosition
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.core.model.SyncStatus
import com.dlunaunizar.bobitos.feature.spaces.initialsOf

// Inicio (al abrir la app): tus espacios, lo tuyo (calendario personal) y los catálogos globales.
// Es una pantalla global: sin ModuleTheme ni franja.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainMenuScreen(
    spaces: UiState<List<SpaceSummary>>,
    syncStatus: SyncStatus,
    onEnterSpace: (SpaceSummary) -> Unit,
    onManageSpaces: () -> Unit,
    onRetrySpaces: () -> Unit,
    onOpen: (BobitosDestination) -> Unit,
    onProfile: () -> Unit,
) {
    Scaffold(
        topBar = {
            Column {
                BobitosTopBar(
                    titleContent = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    },
                    actions = {
                        IconButton(onClick = onProfile) {
                            Icon(
                                Icons.Rounded.AccountCircle,
                                contentDescription = stringResource(R.string.profile_open),
                            )
                        }
                    },
                )
                SyncStatusBanner(syncStatus)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SpacesRows(
                spaces = spaces,
                onEnterSpace = onEnterSpace,
                onManageSpaces = onManageSpaces,
                onRetrySpaces = onRetrySpaces,
            )
            ModuleRows(
                title = stringResource(R.string.main_menu_mine),
                destinations = listOf(BobitosDestination.MyCalendar),
                onOpen = onOpen,
                supporting = mapOf(
                    BobitosDestination.MyCalendar to stringResource(R.string.main_menu_my_calendar_hint),
                ),
            )
            CatalogRows(onOpen = onOpen)
        }
    }
}

@Composable
private fun SpacesRows(
    spaces: UiState<List<SpaceSummary>>,
    onEnterSpace: (SpaceSummary) -> Unit,
    onManageSpaces: () -> Unit,
    onRetrySpaces: () -> Unit,
) {
    val list = (spaces as? UiState.Content)?.value.orEmpty()
    Column {
        SectionHeader(
            title = stringResource(R.string.spaces_title),
            // Siempre a mano: aunque la lista cargue o falle, «Tus espacios» sigue teniendo camino.
            action = {
                TextButton(onClick = onManageSpaces) { Text(stringResource(R.string.main_menu_manage_spaces)) }
            },
        )
        when {
            list.isNotEmpty() -> list.forEachIndexed { index, space ->
                SpaceRow(space, rowPosition(index, list.size)) { onEnterSpace(space) }
            }
            spaces is UiState.Loading -> LoadingState(Modifier.fillMaxWidth().padding(vertical = Spacing.lg))
            spaces is UiState.Error -> ErrorState(
                Modifier.fillMaxWidth().padding(vertical = Spacing.lg),
                message = stringResource(spaces.failure.messageRes()),
                onRetry = onRetrySpaces,
            )
            spaces is UiState.Content -> GroupedRow(position = RowPosition.SINGLE, onClick = onManageSpaces) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                RowTitle(stringResource(R.string.space_picker_manage), Modifier.weight(1f))
                RowArrow()
            }
        }
    }
}

@Composable
private fun SpaceRow(space: SpaceSummary, position: RowPosition, onClick: () -> Unit) {
    val members = pluralStringResource(R.plurals.space_members, space.memberCount, space.memberCount)
    val label = stringResource(R.string.main_menu_space_row, space.name, members)
    GroupedRow(position = position, onClick = onClick) {
        Box(
            modifier = Modifier
                .padding(end = Spacing.md)
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initialsOf(space.name),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
        // Una sola etiqueta para el lector de pantalla: «Casa · 3 miembros».
        Column(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics { text = AnnotatedString(label) },
        ) {
            Text(space.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                members,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RowArrow()
    }
}
