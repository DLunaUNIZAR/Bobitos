package com.dlunaunizar.bobitos.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.foldForSearch
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.ExerciseType

// Chips de tipo y de grupo muscular, compartidos por la pantalla Ejercicios y el selector. Tocar el chip
// elegido, o «Todos», quita el filtro. Si el grupo elegido ya no está en el catálogo, se sigue mostrando
// para poder quitarlo.
@Composable
internal fun ExerciseFilterChips(
    groups: List<String>,
    filter: ExerciseFilter,
    onTypeChange: (ExerciseType?) -> Unit,
    onGroupChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedGroup = filter.muscleGroup
    val selectedKey = selectedGroup?.foldForSearch()
    val shownGroups = if (selectedGroup != null && groups.none { it.foldForSearch() == selectedKey }) {
        groups + selectedGroup
    } else {
        groups
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            item(key = ALL_KEY) {
                FilterChip(
                    selected = filter.type == null,
                    onClick = { onTypeChange(null) },
                    label = { Text(stringResource(R.string.exercises_filter_all)) },
                )
            }
            items(ExerciseType.entries, key = { it.name }) { type ->
                FilterChip(
                    selected = filter.type == type,
                    onClick = { onTypeChange(if (filter.type == type) null else type) },
                    label = { Text(stringResource(type.labelRes)) },
                )
            }
        }
        if (shownGroups.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                item(key = ALL_KEY) {
                    FilterChip(
                        selected = selectedGroup == null,
                        onClick = { onGroupChange(null) },
                        label = { Text(stringResource(R.string.exercises_filter_all_groups)) },
                    )
                }
                items(shownGroups, key = { it.foldForSearch() }) { group ->
                    val selected = selectedGroup != null && selectedGroup.foldForSearch() == group.foldForSearch()
                    FilterChip(
                        selected = selected,
                        onClick = { onGroupChange(if (selected) null else group) },
                        label = { Text(group) },
                    )
                }
            }
        }
    }
}

private const val ALL_KEY = ":all"
