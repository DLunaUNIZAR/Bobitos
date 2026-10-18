package com.dlunaunizar.bobitos.core.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosLargeTopBar
import com.dlunaunizar.bobitos.core.designsystem.component.CollapsibleBandState
import com.dlunaunizar.bobitos.core.designsystem.component.HeaderBandState
import com.dlunaunizar.bobitos.core.designsystem.component.LocalCollapsibleBand
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
 * en la fila de iconos, el selector del espacio (cambiar de espacio) a la izquierda y a la derecha las
 * acciones que registre la pantalla con `TopBarActions`, ajustes y perfil. Abajo, las pestañas Hoy, Calendario, Tareas, Compra y
 * Más. [module] tiñe la barra superior y el contenido, no la barra inferior, que ya usa el color de cada
 * pestaña. [eyebrow] va encima del título (el saludo de Hoy).
 *
 * La barra se encoge al desplazar la lista del contenido, con la `CollapsibleHeaderBand` de la pantalla
 * dentro. Bajo ella el marco dibuja el remate redondeado de la franja teñida, salvo que la pantalla
 * ponga su propia `ModuleHeaderBand` fija.
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
    eyebrow: String? = null,
    content: @Composable () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val topBarActions = remember { TopBarActionsState() }
    val headerBand = remember { HeaderBandState() }
    val collapsibleBand = remember { CollapsibleBandState() }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ModuleTheme(module) {
                Column {
                    BobitosLargeTopBar(
                        title = title,
                        scrollBehavior = scrollBehavior,
                        spaceSelector = { SpaceSelector(spaceName, onClick = { switcher.onPickerOpenChange(true) }) },
                        eyebrow = eyebrow,
                        band = collapsibleBand.content,
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
                    LocalCollapsibleBand provides collapsibleBand,
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
private val TabLabelStep = 0.5.sp
private val TabLabelPadding = 4.dp

/**
 * Barra inferior de un espacio: las cinco etiquetas comparten tamaño, el mayor que deja caber la
 * más larga en su pestaña (entre labelMedium y 10 sp).
 */
@Composable
internal fun WorkspaceNavigationBar(selectedTab: BobitosDestination, onTabSelected: (BobitosDestination) -> Unit) {
    val dark = LocalDarkTheme.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    val titles = BobitosDestination.workspaceTabs.map { stringResource(it.titleRes) }
    BoxWithConstraints {
        val tabWidthPx = with(density) { ((maxWidth / titles.size) - TabLabelPadding * 2).roundToPx() }
        // Una sola medición por ancho de pestaña, escala de letra y textos: no se repite en cada fotograma.
        val labelSize = remember(tabWidthPx, density.fontScale, density.density, titles, labelStyle) {
            sharedLabelSize(measurer, titles, labelStyle, tabWidthPx)
        }
        NavigationBar {
            BobitosDestination.workspaceTabs.forEachIndexed { index, tab ->
                val selected = tab == selectedTab
                val module = tab.appModule()
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTabSelected(tab) },
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = {
                        Text(
                            text = titles[index],
                            fontSize = labelSize,
                            fontWeight = if (selected && module != null) FontWeight.SemiBold else null,
                            maxLines = 1,
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
}

// Mide con la etiqueta en negrita media (el caso más ancho) para que el tamaño no salte al cambiar de pestaña.
private fun sharedLabelSize(measurer: TextMeasurer, titles: List<String>, style: TextStyle, widthPx: Int): TextUnit {
    val max = style.fontSize
    var size = max
    while (size > TabLabelMinSize) {
        val fits = titles.all {
            measurer.measure(it, style.copy(fontSize = size), maxLines = 1, softWrap = false).size.width <= widthPx
        }
        if (fits) return size
        size = (size.value - TabLabelStep.value).sp
    }
    return TabLabelMinSize
}

// El espacio es un botón en la fila de iconos: cambia de espacio. Hasta dos líneas para los nombres largos.
@Composable
private fun SpaceSelector(spaceName: String, onClick: () -> Unit) {
    val description = stringResource(R.string.change_space_named, spaceName)
    Row(
        modifier = Modifier
            .offset(x = -Spacing.xs) // el texto queda alineado con el título grande
            .clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = stringResource(R.string.change_space), role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.xs),
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
