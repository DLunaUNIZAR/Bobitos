package com.dlunaunizar.bobitos.feature.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.BobitosDates
import java.time.LocalDate
import java.time.LocalTime

// Inicio y fin del evento; el error de fechas va justo debajo, junto a los selectores a los que se refiere.
@Composable
internal fun EventDateFields(
    draft: EventDraft,
    error: String?,
    endFocus: FocusRequester,
    onPicker: (EventPicker) -> Unit,
) {
    val startLabelRes = if (draft.allDay) R.string.calendar_start_date_label else R.string.calendar_start_datetime_label
    val endLabelRes = if (draft.allDay) R.string.calendar_end_date_label else R.string.calendar_end_datetime_label
    DateTimeField(
        label = stringResource(startLabelRes),
        date = draft.startDate,
        time = if (draft.allDay) null else draft.startTime,
        onDateClick = { onPicker(EventPicker.START_DATE) },
        onTimeClick = { onPicker(EventPicker.START_TIME) },
    )
    DateTimeField(
        label = stringResource(endLabelRes),
        date = draft.endDate,
        time = if (draft.allDay) null else draft.endTime,
        onDateClick = { onPicker(EventPicker.END_DATE) },
        onTimeClick = { onPicker(EventPicker.END_TIME) },
        dateFocus = endFocus,
    )
    error?.let {
        Text(
            it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

internal enum class EventPicker { START_DATE, END_DATE, START_TIME, END_TIME }

@Composable
private fun DateTimeField(
    label: String,
    date: LocalDate,
    time: LocalTime?,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    dateFocus: FocusRequester? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onDateClick,
                modifier = if (dateFocus != null) Modifier.focusRequester(dateFocus) else Modifier,
            ) {
                Icon(
                    Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(BobitosDates.shortDate(date))
            }
            if (time != null) {
                OutlinedButton(onClick = onTimeClick) {
                    Icon(
                        Icons.Rounded.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(BobitosDates.time(time))
                }
            }
        }
    }
}
