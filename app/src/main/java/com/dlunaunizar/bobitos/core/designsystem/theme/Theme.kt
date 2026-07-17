package com.dlunaunizar.bobitos.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = BobitosPurple,
    secondary = BobitosGreen,
)

private val DarkColorScheme = darkColorScheme(
    primary = BobitosPurpleDark,
    secondary = BobitosGreenDark,
)

@Composable
fun BobitosTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = BobitosTypography,
        content = content,
    )
}
