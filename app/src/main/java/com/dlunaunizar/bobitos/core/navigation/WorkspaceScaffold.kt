package com.dlunaunizar.bobitos.core.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosLargeTopBar
import com.dlunaunizar.bobitos.core.designsystem.component.HeaderBandState
import com.dlunaunizar.bobitos.core.designsystem.component.LocalHeaderBandState
import com.dlunaunizar.bobitos.core.designsystem.component.LocalSnackbarHostState
import com.dlunaunizar.bobitos.core.designsystem.component.LocalTopBarActions
import com.dlunaunizar.bobitos.core.designsystem.component.ModuleHeaderCap
import com.dlunaunizar.bobitos.core.designsystem.component.SyncStatusBanner
import com.dlunaunizar.bobitos.core.designsystem.component.TopBarActionsState
import com.dlunaunizar.bobitos.core.designsystem.theme.AppModule
import com.dlunaunizar.bobitos.core.designsystem.theme.LocalDarkTheme
import com.dlunaunizar.bobitos.core.designsystem.theme.ModulePalette
import com.dlunaunizar.bobitos.core.designsystem.theme.ModuleTheme
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.designsystem.theme.onSurfaceDark
import com.dlunaunizar.bobitos.core.designsystem.theme.onSurfaceLight
import com.dlunaunizar.bobitos.core.model.SyncStatus

/**
 * Marco común de las pantallas de un espacio: barra superior con el título de la pantalla en grande y,
 * encima, el selector del espacio (cambiar de espacio); a la derecha las acciones que registre la
 * pantalla con `TopBarActions`, ajustes y perfil. Abajo, las pestañas Hoy, Calendario, Tareas, Compra y
 * Más. [module] tiñe la barra superior y el contenido, no la barra inferior, que ya usa el color de cada
 * pestaña. [supportingText] va bajo el título (el saludo de Hoy).
 *
 * La barra se encoge al desplazar la lista del contenido. Bajo ella el marco dibuja el remate redondeado
 * de la franja teñida, salvo que la pantalla ponga su propia `ModuleHeaderBand`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspaceScaffold(
    selectedTab: BobitosDestination,
    title: String,
    module: AppModule?,
    spaceName: String,
    onTabSelected: (BobitosDestination) -> Unit,
    switcher: SpaceSwitcher,
    onManageSpaces: () -> Unit,
    onSpaceSettings: () -> Unit,
    onProfile: () -> Unit,
    syncStatus: SyncStatus,
    supportingText: String? = null,
    content: @Composable () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val topBarActions = remember { TopBarActionsState() }
    val headerBand = remember { HeaderBandState() }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ModuleTheme(module) {
                Column {
                    BobitosLargeTopBar(
                        title = title,
                        scrollBehavior = scrollBehavior,
                        overline = { SpaceSelector(spaceName, onClick = { switcher.onPickerOpenChange(true) }) },
                        supportingText = supportingText,
                        actions = {
                            topBarActions.actions?.invoke(this)
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
                    ModuleHeaderCap(headerBand)
                    SyncStatusBanner(syncStatus)
                }
            }
        },
        bottomBar = {
            WorkspaceNavigationBar(selectedTab = selectedTab, onTabSelected = onTabSelected)
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ModuleTheme(module) {
                CompositionLocalProvider(
                    LocalSnackbarHostState provides snackbarHostState,
                    LocalTopBarActions provides topBarActions,
                    LocalHeaderBandState provides headerBand,
                ) {
                    content()
                }
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

/** Colores de una pestaña con módulo: indicador opaco, icono y etiqueta seleccionados. */
internal data class TabColors(val indicator: Color, val selectedIcon: Color, val selectedLabel: Color)

internal fun tabColors(module: AppModule, dark: Boolean): TabColors {
    val colors = ModulePalette.colors(module, dark)
    return TabColors(
        indicator = colors.container,
        selectedIcon = colors.onContainer,
        selectedLabel = if (dark) onSurfaceDark else onSurfaceLight,
    )
}

private val TabLabelMinSize = 10.sp

/** Barra inferior de un espacio: las etiquetas encogen un poco antes de cortarse. */
@Composable
internal fun WorkspaceNavigationBar(selectedTab: BobitosDestination, onTabSelected: (BobitosDestination) -> Unit) {
    val dark = LocalDarkTheme.current
    NavigationBar {
        BobitosDestination.workspaceTabs.forEach { tab ->
            val selected = tab == selectedTab
            val module = tab.appModule()
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = {
                    Text(
                        text = stringResource(tab.titleRes),
                        fontWeight = if (selected && module != null) FontWeight.SemiBold else null,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = TabLabelMinSize,
                            maxFontSize = MaterialTheme.typography.labelMedium.fontSize,
                        ),
                    )
                },
                colors = module?.let {
                    val c = tabColors(it, dark)
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = c.selectedIcon,
                        selectedTextColor = c.selectedLabel,
                        indicatorColor = c.indicator,
                    )
                } ?: NavigationBarItemDefaults.colors(),
            )
        }
    }
}

// El espacio es un botón pequeño sobre el título: cambia de espacio. Hasta dos líneas para los nombres largos.
@Composable
private fun SpaceSelector(spaceName: String, onClick: () -> Unit) {
    val description = stringResource(R.string.change_space_named, spaceName)
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = stringResource(R.string.change_space), role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .heightIn(min = 48.dp)
            .padding(start = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = spaceName,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
    }
}
