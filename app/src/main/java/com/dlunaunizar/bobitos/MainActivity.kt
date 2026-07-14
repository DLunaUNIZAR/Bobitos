package com.dlunaunizar.bobitos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.app.AppViewModel
import com.dlunaunizar.bobitos.app.BobitosApp
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            BobitosTheme {
                BobitosApp(
                    uiState = uiState,
                    onSpaceSelected = viewModel::selectSpace,
                )
            }
        }
    }
}

