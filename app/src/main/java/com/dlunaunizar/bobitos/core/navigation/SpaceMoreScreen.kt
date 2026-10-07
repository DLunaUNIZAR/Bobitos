package com.dlunaunizar.bobitos.core.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing

/** «Más»: los módulos que no caben en la barra inferior (Comidas, Deporte y Notas). */
@Composable
internal fun SpaceMoreScreen(onOpen: (BobitosDestination) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SpaceHomeCard(
            destination = BobitosDestination.Meals,
            count = 0,
            onClick = { onOpen(BobitosDestination.Meals) },
        )
        SpaceHomeCard(
            destination = BobitosDestination.Sport,
            count = 0,
            onClick = { onOpen(BobitosDestination.Sport) },
        )
        SpaceHomeCard(
            destination = BobitosDestination.Notes,
            count = 0,
            onClick = { onOpen(BobitosDestination.Notes) },
        )
    }
}
