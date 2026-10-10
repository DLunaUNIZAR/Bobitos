package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing

/**
 * Formulario en un bottom sheet: título, contenido con scroll y botones fijos abajo (siempre
 * visibles con el teclado abierto). Si hay cambios sin guardar ([dirty], que solo se evalúa al intentar
 * cerrar para no recalcularlo en cada pulsación), deslizar, tocar fuera o
 * «Cancelar» pide confirmar el descarte; mientras se guarda ([saving]) no se puede cerrar y el botón dice «Guardando…».
 * [errorMessage] se pinta dentro del formulario, encima de los botones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BobitosFormSheet(
    title: String,
    confirmLabel: String,
    confirmEnabled: Boolean,
    saving: Boolean,
    dirty: () -> Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    errorMessage: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var askDiscard by rememberSaveable { mutableStateOf(false) }
    val currentDirty by rememberUpdatedState(dirty)
    val currentSaving by rememberUpdatedState(saving)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            if (target != SheetValue.Hidden) {
                true
            } else {
                when (dismissActionFor(currentDirty(), currentSaving)) {
                    DismissAction.CLOSE -> true
                    DismissAction.ASK_DISCARD -> {
                        askDiscard = true
                        false
                    }
                    DismissAction.BLOCK -> false
                }
            }
        },
    )
    val requestClose = {
        when (dismissActionFor(dirty(), saving)) {
            DismissAction.CLOSE -> onDismiss()
            DismissAction.ASK_DISCARD -> askDiscard = true
            DismissAction.BLOCK -> Unit
        }
    }
    // Atrás lo gestiona el BackHandler de abajo: con el atrás predictivo, un cierre vetado por
    // confirmValueChange dejaría el sheet encogido tras «Seguir editando».
    ModalBottomSheet(
        onDismissRequest = requestClose,
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        BackHandler(onBack = requestClose)
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.lg)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                content = content,
            )
            FormSheetActions(
                errorMessage = errorMessage,
                confirmLabel = confirmLabel,
                confirmEnabled = confirmEnabled,
                saving = saving,
                onCancel = requestClose,
                onConfirm = onConfirm,
            )
        }
    }
    if (askDiscard) {
        DiscardChangesDialog(
            onDiscard = {
                askDiscard = false
                onDismiss()
            },
            onKeepEditing = { askDiscard = false },
        )
    }
}

@Composable
private fun DiscardChangesDialog(onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
    BobitosDialog(
        title = stringResource(R.string.discard_changes_title),
        message = stringResource(R.string.discard_changes_message),
        confirmLabel = stringResource(R.string.discard_changes_confirm),
        destructive = true,
        dismissLabel = stringResource(R.string.discard_changes_keep_editing),
        onConfirm = onDiscard,
        onDismiss = onKeepEditing,
    )
}

@Composable
private fun FormSheetActions(
    errorMessage: String?,
    confirmLabel: String,
    confirmEnabled: Boolean,
    saving: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
        ) {
            TextButton(onClick = onCancel, enabled = !saving) {
                Text(stringResource(R.string.cancel))
            }
            Button(onClick = onConfirm, enabled = confirmEnabled && !saving) {
                Text(if (saving) stringResource(R.string.write_saving) else confirmLabel)
            }
        }
    }
}
