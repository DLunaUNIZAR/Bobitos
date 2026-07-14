package com.dlunaunizar.bobitos.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.dlunaunizar.bobitos.core.navigation.BobitosNavHost

@Composable
fun BobitosApp(
    uiState: AppUiState,
    onSpaceSelected: (String) -> Unit,
) {
    val navController = rememberNavController()

    BobitosNavHost(
        navController = navController,
        uiState = uiState,
        onSpaceSelected = onSpaceSelected,
    )
}

