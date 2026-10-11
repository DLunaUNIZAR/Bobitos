package com.dlunaunizar.bobitos.feature.exercises

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.foldForSearch
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.ExerciseType

// Desplegables de tipo y de grupo muscular, uno al lado del otro, compartidos por la pantalla Ejercicios y el
// selector. «Todos los …» quita el filtro. Si el grupo elegido ya no está en el catálogo, se sigue ofreciendo
// para que se vea cuál es.
@Composable
internal fun ExerciseFilterMenus(
    groups: List<String>,
    filter: ExerciseFilter,
    onTypeChange: (ExerciseType?) -> Unit,
    onGroupChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedGroup = filter.muscleGroup
    val groupOptions = remember(groups, selectedGroup) { groupFilterOptions(groups, selectedGroup) }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FilterDropdown(
            label = stringResource(R.string.exercises_filter_type_label),
            value = stringResource(typeFilterValueRes(filter.type)),
            options = listOf(
                FilterOption(stringResource(R.string.exercises_filter_all), filter.type == null) { onTypeChange(null) },
            ) + ExerciseType.entries.map { type ->
                FilterOption(stringResource(type.labelRes), filter.type == type) { onTypeChange(type) }
            },
            modifier = Modifier.weight(1f),
        )
        FilterDropdown(
            label = stringResource(R.string.exercises_filter_group_label),
            value = selectedGroup ?: stringResource(R.string.exercises_filter_any),
            options = listOf(
                FilterOption(stringResource(R.string.exercises_filter_all_groups), selectedGroup == null) {
                    onGroupChange(null)
                },
            ) + groupOptions.map { group ->
                FilterOption(group, isSelectedGroup(group, selectedGroup)) { onGroupChange(group) }
            },
            enabled = groupOptions.isNotEmpty(),
            modifier = Modifier.weight(1f),
        )
    }
}

private class FilterOption(val text: String, val selected: Boolean, val onSelect: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    value: String,
    options: List<FilterOption>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { expanded = it && enabled },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.text) },
                    onClick = {
                        expanded = false
                        option.onSelect()
                    },
                    trailingIcon = if (option.selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    modifier = Modifier.semantics { selected = option.selected },
                )
            }
        }
    }
}

// Grupos del menú: los del catálogo sin repetidos (plegados), en su orden, y al final el elegido si ya no está.
internal fun groupFilterOptions(groups: List<String>, selected: String?): List<String> {
    val shown = groups.distinctBy { it.foldForSearch() }
    return if (selected != null && shown.none { isSelectedGroup(it, selected) }) shown + selected else shown
}

internal fun isSelectedGroup(option: String, selected: String?): Boolean =
    selected != null && option.foldForSearch() == selected.foldForSearch()

@StringRes
internal fun typeFilterValueRes(type: ExerciseType?): Int = type?.labelRes ?: R.string.exercises_filter_any
