@file:Suppress("MatchingDeclarationName")

package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing

/** Acción de una hoja de detalle: su texto, lo que hace y si se puede pulsar (p. ej. no mientras se guarda). */
data class InfoSheetAction(val label: String, val onClick: () -> Unit, val enabled: Boolean = true)

/**
 * Sheet de solo lectura (detalle de una receta o una rutina): título, contenido con scroll y, abajo,
 * la acción principal (botón relleno) junto a «Cerrar» (apilados si no caben) y, si hay más, las secundarias en un menú ⋮ del título. No hay nada que descartar, así que el cierre es siempre libre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BobitosInfoSheet(
    title: String,
    onDismiss: () -> Unit,
    primaryAction: InfoSheetAction? = null,
    secondaryActions: List<InfoSheetAction> = emptyList(),
    header: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.lg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                if (secondaryActions.isNotEmpty()) SecondaryActionsMenu(title, secondaryActions)
            }
            if (header != null) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), content = header)
            }
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                content = content,
            )
            SheetButtons(
                close = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
                primary = primaryAction?.let {
                    { Button(onClick = it.onClick, enabled = it.enabled) { Text(it.label) } }
                },
            )
        }
    }
}

/**
 * Botones al pie: «Cerrar» y la acción principal en una fila a la derecha si caben; si no (letra grande),
 * se apilan con la principal arriba, a todo el ancho, y «Cerrar» debajo.
 */
@Composable
internal fun SheetButtons(close: @Composable () -> Unit, primary: (@Composable () -> Unit)?) {
    val gap = Spacing.sm
    Layout(
        content = {
            close()
            primary?.invoke()
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val closeM = measurables[0]
        val primaryM = measurables.getOrNull(1)
        val width = constraints.maxWidth
        val fitsInRow = primaryM == null ||
            closeM.maxIntrinsicWidth(Constraints.Infinity) + gapPx + primaryM.maxIntrinsicWidth(Constraints.Infinity) <=
            width
        if (fitsInRow) {
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val closeP = closeM.measure(loose)
            val primaryP = primaryM?.measure(loose)
            val height = maxOf(closeP.height, primaryP?.height ?: 0)
            layout(width, height) {
                var x = width
                primaryP?.let {
                    x -= it.width
                    it.place(x, (height - it.height) / 2)
                    x -= gapPx
                }
                x -= closeP.width
                closeP.place(x, (height - closeP.height) / 2)
            }
        } else {
            val full = constraints.copy(minWidth = width, minHeight = 0)
            val primaryP = primaryM!!.measure(full)
            val closeP = closeM.measure(full)
            layout(width, primaryP.height + gapPx + closeP.height) {
                primaryP.place(0, 0)
                closeP.place(0, primaryP.height + gapPx)
            }
        }
    }
}

@Composable
private fun SecondaryActionsMenu(title: String, actions: List<InfoSheetAction>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        MoreOptionsButton(itemName = title, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    enabled = action.enabled,
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                )
            }
        }
    }
}
