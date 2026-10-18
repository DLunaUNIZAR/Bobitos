package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.LocalAppModule
import com.dlunaunizar.bobitos.core.designsystem.theme.moduleTint

/**
 * Barra superior de las pantallas: el título grande es la pantalla (`headlineMedium`) y, al desplazar
 * la lista, se encoge a una línea (`titleLarge`) con el [scrollBehavior] (de salida hasta encoger).
 * Es la variante mediana de Material 3: la grande gastaría demasiado alto con el [overline] encima.
 * Material fija el alto abierto en dp, así que se calcula con el de las líneas (que crece con la letra).
 *
 * Abierta lleva, de arriba abajo, [overline] (el selector del espacio), el título y [supportingText]
 * (el saludo de Hoy). Encogida solo queda el título: el [overline] se retira. El contenedor, abierto
 * y encogido, es el tinte del módulo de [LocalAppModule]; sin módulo es la superficie.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BobitosLargeTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    overline: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val module = LocalAppModule.current
    val container = module?.let { moduleTint(it) } ?: MaterialTheme.colorScheme.surface
    val expanded = scrollBehavior.state.collapsedFraction < EXPANDED_UNTIL
    val titleLines = if (title.length > LONG_TITLE_CHARS) 2 else 1
    val expandedHeight = with(LocalDensity.current) {
        val titleStyle = MaterialTheme.typography.headlineMedium
        val supportingStyle = MaterialTheme.typography.bodyMedium
        TopAppBarDefaults.MediumAppBarCollapsedHeight +
            (if (overline != null) OverlineHeight else 0.dp) +
            titleStyle.lineHeight.toDp() * titleLines +
            (if (supportingText != null) supportingStyle.lineHeight.toDp() else 0.dp) +
            ExpandedBottomPadding
    }
    MediumTopAppBar(
        title = { BarTitle(title, supportingText, overline, expanded, titleLines) },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.navigate_back),
                    )
                }
            }
        },
        actions = actions,
        expandedHeight = expandedHeight,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = container,
            scrolledContainerColor = container,
        ),
        scrollBehavior = scrollBehavior,
    )
}

// Pasada esta fracción de encogimiento el selector del espacio deja de mostrarse.
private const val EXPANDED_UNTIL = 0.5f

// Un título más largo que esto puede ocupar dos líneas abierto, y la barra deja sitio para ellas.
private const val LONG_TITLE_CHARS = 16

// Alto del selector del espacio (destino táctil de 48 dp) y aire bajo el título.
private val OverlineHeight = 48.dp
private val ExpandedBottomPadding = 24.dp

@Composable
private fun BarTitle(
    title: String,
    supportingText: String?,
    overline: (@Composable () -> Unit)?,
    expanded: Boolean,
    titleLines: Int,
) {
    Column {
        if (overline != null && expanded) overline()
        Text(
            text = title,
            style = with(MaterialTheme.typography) { if (expanded) headlineMedium else titleLarge },
            maxLines = if (expanded) titleLines else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        if (supportingText != null && expanded) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
