package com.dlunaunizar.bobitos.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.dlunaunizar.bobitos.R

// Nunito (SIL OFL) empaquetada como fuente variable: un único fichero cubre todos
// los pesos mediante el eje "wght" (Android soporta fuentes variables desde API 26).
@OptIn(ExperimentalTextApi::class)
private fun nunito(weight: Int) = Font(
    resId = R.font.nunito_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val Nunito = FontFamily(
    nunito(weight = 400),
    nunito(weight = 500),
    nunito(weight = 600),
    nunito(weight = 700),
    nunito(weight = 800),
)

private val base = Typography()

// Escala Material 3 con una sola familia; jerarquía por tamaño y peso (cálida y redondeada).
internal val BobitosTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Nunito),
    displayMedium = base.displayMedium.copy(fontFamily = Nunito),
    displaySmall = base.displaySmall.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Nunito, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Nunito),
    bodyMedium = base.bodyMedium.copy(fontFamily = Nunito),
    bodySmall = base.bodySmall.copy(fontFamily = Nunito),
    labelLarge = base.labelLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontFamily = Nunito, fontWeight = FontWeight.Medium),
)
