package com.dlunaunizar.bobitos.feature.shopping

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.feature.common.FeaturePlaceholder

@Composable
fun ShoppingScreen(modifier: Modifier = Modifier) {
    FeaturePlaceholder(
        titleRes = R.string.shopping_title,
        descriptionRes = R.string.shopping_placeholder,
        modifier = modifier,
    )
}

