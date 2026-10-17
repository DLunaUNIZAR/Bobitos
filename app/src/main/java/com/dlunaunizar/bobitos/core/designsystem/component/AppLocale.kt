package com.dlunaunizar.bobitos.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.dlunaunizar.bobitos.core.common.BobitosDates

/**
 * Da a [content] la configuración y el contexto con locale `es-ES`, para que los selectores de Material salgan en
 * español y con la semana empezando en lunes aunque el dispositivo esté en otro idioma.
 */
@Composable
fun AppLocale(content: @Composable () -> Unit) {
    val base = LocalContext.current
    val configuration = LocalConfiguration.current
    val spanish = remember(base, configuration) {
        Configuration(configuration).apply { setLocale(BobitosDates.locale) }
    }
    val context = remember(base, spanish) { base.createConfigurationContext(spanish) }
    CompositionLocalProvider(
        LocalConfiguration provides spanish,
        LocalContext provides context,
    ) { content() }
}
