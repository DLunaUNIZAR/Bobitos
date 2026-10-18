package com.dlunaunizar.bobitos.feature.exercises

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.foldForSearch
import com.dlunaunizar.bobitos.core.designsystem.component.FilterDropdown
import com.dlunaunizar.bobitos.core.designsystem.component.FilterDropdownRow
import com.dlunaunizar.bobitos.core.designsystem.component.FilterOption
import com.dlunaunizar.bobitos.core.model.ExerciseType

internal const val EXERCISE_FILTER_TYPE_TAG = "exercise-filter-type"
internal const val EXERCISE_FILTER_GROUP_TAG = "exercise-filter-group"

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
    FilterDropdownRow(modifier) {
        FilterDropdown(
            label = stringResource(R.string.exercises_filter_type_label),
            value = stringResource(typeFilterValueRes(filter.type)),
            options = listOf(
                FilterOption(stringResource(R.string.exercises_filter_all), filter.type == null) { onTypeChange(null) },
            ) + ExerciseType.entries.map { type ->
                FilterOption(stringResource(type.labelRes), filter.type == type) { onTypeChange(type) }
            },
            tag = EXERCISE_FILTER_TYPE_TAG,
            active = filter.type != null,
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
            tag = EXERCISE_FILTER_GROUP_TAG,
            enabled = groupOptions.isNotEmpty(),
            active = selectedGroup != null,
            modifier = Modifier.weight(1f),
        )
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
