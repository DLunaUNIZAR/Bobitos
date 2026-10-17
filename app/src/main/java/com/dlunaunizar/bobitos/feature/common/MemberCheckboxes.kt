package com.dlunaunizar.bobitos.feature.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.SpaceMember

/** Casillas de participantes de un formulario (evento, comida, actividad): una por miembro del espacio. */
@Composable
internal fun MemberCheckboxes(
    label: String,
    members: List<SpaceMember>,
    selectedIds: List<String>,
    onToggle: (userId: String, selected: Boolean) -> Unit,
) {
    if (members.isEmpty()) return
    Text(text = label, style = MaterialTheme.typography.labelLarge)
    members.forEach { member ->
        val selected = member.userId in selectedIds
        CheckboxRow(
            label = member.displayName,
            checked = selected,
            onCheckedChange = { onToggle(member.userId, it) },
        )
    }
}

/** Fila que marca entera (casilla y texto), como una sola casilla para los lectores de pantalla. */
@Composable
internal fun CheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, modifier = Modifier.padding(start = Spacing.sm))
    }
}
