package com.dlunaunizar.bobitos.core.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.feature.spaces.roleAndMembers

/**
 * Selector de espacio en un bottom sheet: lista los espacios con el actual marcado y ofrece ir a la
 * pantalla completa (crear un espacio o unirse con un código).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpacePickerSheet(switcher: SpaceSwitcher, onManageSpaces: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.lg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.change_space),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(switcher.spaces, key = SpaceSummary::id) { space ->
                    SpaceRow(
                        space = space,
                        selected = space.id == switcher.selectedSpaceId,
                        onClick = {
                            onDismiss()
                            if (space.id != switcher.selectedSpaceId) switcher.onSelect(space)
                        },
                    )
                }
            }
            HorizontalDivider()
            TextButton(
                onClick = {
                    onDismiss()
                    onManageSpaces()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.space_picker_manage)) }
        }
    }
}

@Composable
private fun SpaceRow(space: SpaceSummary, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = space.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = space.roleAndMembers(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
