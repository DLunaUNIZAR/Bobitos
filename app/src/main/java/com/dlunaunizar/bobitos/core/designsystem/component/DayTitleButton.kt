package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.BobitosDates
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import java.time.LocalDate

/** Fecha del día enfocado como botón: abre el selector de fecha y entrega el día elegido a [onPick]. */
@Composable
fun DayTitleButton(date: LocalDate, onPick: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = stringResource(R.string.pick_day), role = Role.Button) { picking = true }
            .padding(end = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = BobitosDates.longDay(date), style = MaterialTheme.typography.titleMedium)
        Icon(Icons.Rounded.ExpandMore, contentDescription = null)
    }
    if (picking) {
        AppDatePickerDialog(
            initialDate = date,
            onConfirm = {
                picking = false
                onPick(it)
            },
            onDismiss = { picking = false },
        )
    }
}
