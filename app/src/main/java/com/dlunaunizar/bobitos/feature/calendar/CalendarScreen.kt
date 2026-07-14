package com.dlunaunizar.bobitos.feature.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.feature.common.FeaturePlaceholder

@Composable
fun CalendarScreen(modifier: Modifier = Modifier) {
    FeaturePlaceholder(
        titleRes = R.string.calendar_title,
        descriptionRes = R.string.calendar_placeholder,
        modifier = modifier,
    )
}

