package com.dlunaunizar.bobitos.feature.ingredients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.data.repository.MAX_STORES
import com.dlunaunizar.bobitos.feature.shopping.SupermarketIcon
import com.dlunaunizar.bobitos.feature.shopping.labelRes

/**
 * Una chip por tienda elegible; las elegidas llevan delante su número de orden («1 · Mercadona»).
 * Con [MAX_STORES] elegidas, las demás se desactivan; las elegidas siguen activas para poder quitarlas.
 * Lo comparten «Dónde lo compro» de la ficha y el editor de marca.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StoreChips(
    selected: List<Supermarket>,
    onToggle: (Supermarket) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SELECTABLE_STORES.forEach { store ->
            val position = selected.indexOf(store) + 1
            val label = stringResource(store.labelRes)
            FilterChip(
                selected = position > 0,
                onClick = { onToggle(store) },
                enabled = enabled && (position > 0 || selected.size < MAX_STORES),
                label = {
                    Text(
                        if (position > 0) {
                            stringResource(R.string.ingredients_store_ordered, position, label)
                        } else {
                            label
                        },
                    )
                },
                leadingIcon = { SupermarketIcon(store) },
            )
        }
    }
}
