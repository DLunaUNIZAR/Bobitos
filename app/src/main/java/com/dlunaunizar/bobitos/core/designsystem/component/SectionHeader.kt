package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing

/**
 * Cabecera de sección común: «Título · N». Con [onToggle] la parte del título es pulsable
 * (plegable) y anuncia su estado; [action] queda al final, fuera del área pulsable. Con [sticky]
 * lleva fondo opaco para usarse en un `stickyHeader`; [icon] es decorativo y va delante del título.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    expanded: Boolean? = null,
    onToggle: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    sticky: Boolean = false,
    icon: ImageVector? = null,
) {
    val label = if (count != null) stringResource(R.string.section_header_count, title, count) else title
    val stateText = when (expanded) {
        true -> stringResource(R.string.state_expanded)
        false -> stringResource(R.string.state_collapsed)
        null -> null
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (sticky) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier)
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val toggleModifier = if (onToggle != null) {
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { if (stateText != null) stateDescription = stateText }
        } else {
            Modifier.weight(1f)
        }
        Row(toggleModifier, verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.padding(end = Spacing.sm))
            }
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f, fill = false).semantics { heading() },
            )
            if (onToggle != null && expanded != null) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                )
            }
        }
        if (action != null) action()
    }
}
