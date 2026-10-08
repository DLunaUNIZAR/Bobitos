package com.dlunaunizar.bobitos.core.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosTopBar
import com.dlunaunizar.bobitos.core.designsystem.component.LocalSnackbarHostState
import com.dlunaunizar.bobitos.core.designsystem.component.SyncStatusBanner
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.SyncStatus

/**
 * Marco común de las pantallas de un espacio: barra superior con el chip del espacio (cambiar de
 * espacio), ajustes y perfil, y barra inferior con las pestañas Hoy, Calendario, Tareas, Compra y Más.
 * [screenTitle] es el subtítulo del módulo abierto (null en Hoy y Más).
 */
@Composable
internal fun WorkspaceScaffold(
    selectedTab: BobitosDestination,
    screenTitle: String?,
    spaceName: String,
    onTabSelected: (BobitosDestination) -> Unit,
    switcher: SpaceSwitcher,
    onManageSpaces: () -> Unit,
    onSpaceSettings: () -> Unit,
    onProfile: () -> Unit,
    syncStatus: SyncStatus,
    content: @Composable () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                BobitosTopBar(
                    titleContent = {
                        SpaceTitle(spaceName, screenTitle, onClick = { switcher.onPickerOpenChange(true) })
                    },
                    actions = {
                        IconButton(onClick = onSpaceSettings) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.space_settings),
                            )
                        }
                        IconButton(onClick = onProfile) {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = stringResource(R.string.profile_open),
                            )
                        }
                    },
                )
                SyncStatusBanner(syncStatus)
            }
        },
        bottomBar = {
            NavigationBar {
                BobitosDestination.workspaceTabs.forEach { tab ->
                    val accent = tab.moduleColor()
                    NavigationBarItem(
                        selected = tab == selectedTab,
                        onClick = { onTabSelected(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = {
                            Text(
                                text = stringResource(tab.titleRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        colors = accent?.let {
                            NavigationBarItemDefaults.colors(
                                selectedIconColor = it,
                                selectedTextColor = it,
                                indicatorColor = it.copy(alpha = 0.2f),
                            )
                        } ?: NavigationBarItemDefaults.colors(),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
                content()
            }
        }
    }
    if (switcher.pickerOpen) {
        SpacePickerSheet(
            switcher = switcher,
            onManageSpaces = onManageSpaces,
            onDismiss = { switcher.onPickerOpenChange(false) },
        )
    }
}

// El nombre del espacio es un botón: cambia de espacio. Debajo, el módulo abierto.
@Composable
private fun SpaceTitle(spaceName: String, screenTitle: String?, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(
                    onClickLabel = stringResource(R.string.change_space),
                    role = Role.Button,
                    onClick = onClick,
                )
                .heightIn(min = 48.dp)
                .padding(start = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = spaceName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
        }
        screenTitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
    }
}
