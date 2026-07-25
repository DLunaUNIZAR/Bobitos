package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.data.reminders.ReminderLeadTime
import kotlinx.coroutines.flow.Flow

/**
 * Persiste las preferencias de recordatorios locales del usuario: si están activados (por defecto
 * desactivados) y con cuánta antelación avisar (por defecto, en el momento).
 */
interface ReminderPreferenceRepository {
    val enabled: Flow<Boolean>
    val leadTime: Flow<ReminderLeadTime>

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setLeadTime(leadTime: ReminderLeadTime)
}
