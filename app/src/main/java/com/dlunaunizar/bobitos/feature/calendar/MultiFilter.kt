package com.dlunaunizar.bobitos.feature.calendar

import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import com.dlunaunizar.bobitos.core.designsystem.component.FilterDropdownRow
import com.dlunaunizar.bobitos.core.designsystem.component.FilterOption
import com.dlunaunizar.bobitos.core.designsystem.component.MultiSelectFilterDropdown

/**
 * Filtro múltiple de una fila: [items] son pares de id y nombre; sin ninguno elegido o con todos dice [allText], con
 * uno su nombre y con varios su número según [countPlural].
 */
@Composable
internal fun MultiFilter(
    label: String,
    allText: String,
    @PluralsRes countPlural: Int,
    items: List<Pair<String, String>>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val value = multiFilterValue(selectedIds, items)
    FilterDropdownRow(modifier) {
        MultiSelectFilterDropdown(
            label = label,
            value = when (value) {
                MultiFilterValue.All -> allText
                is MultiFilterValue.One -> value.name
                is MultiFilterValue.Some -> pluralStringResource(countPlural, value.count, value.count)
            },
            options = listOf(FilterOption(allText, value == MultiFilterValue.All, onSelect = onSelectAll)) +
                items.map { (id, name) -> FilterOption(name, id in selectedIds) { onToggle(id) } },
            active = value != MultiFilterValue.All,
            modifier = Modifier.weight(1f),
            tag = tag,
        )
    }
}
