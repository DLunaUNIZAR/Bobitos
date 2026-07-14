package com.dlunaunizar.bobitos.feature.tasks

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.feature.common.FeaturePlaceholder

@Composable
fun TasksScreen(modifier: Modifier = Modifier) {
    FeaturePlaceholder(
        titleRes = R.string.tasks_title,
        descriptionRes = R.string.tasks_placeholder,
        modifier = modifier,
    )
}

