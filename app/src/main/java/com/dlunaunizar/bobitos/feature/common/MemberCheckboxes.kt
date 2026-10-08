package com.dlunaunizar.bobitos.feature.common

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = member.userId in selectedIds,
                onCheckedChange = { checked -> onToggle(member.userId, checked) },
            )
            Text(member.displayName)
        }
    }
}
