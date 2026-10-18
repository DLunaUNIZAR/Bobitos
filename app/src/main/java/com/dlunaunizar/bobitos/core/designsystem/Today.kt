package com.dlunaunizar.bobitos.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.core.common.CurrentDayProvider
import java.time.LocalDate
import java.time.ZoneId

/**
 * El día de hoy. Vuelve a leerse al volver a la app y cambia solo a medianoche, así que una pantalla
 * abierta toda la noche no se queda con el «hoy» de ayer.
 */
@Composable
fun rememberToday(zone: ZoneId = ZoneId.systemDefault()): LocalDate {
    val provider = remember { CurrentDayProvider() }
    val today by remember(zone) { provider.today(zone) }.collectAsStateWithLifecycle(initialValue = LocalDate.now(zone))
    return today
}
