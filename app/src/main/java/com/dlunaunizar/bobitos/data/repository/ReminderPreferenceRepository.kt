package com.dlunaunizar.bobitos.data.repository

import kotlinx.coroutines.flow.Flow

/** Persiste si el usuario ha activado los recordatorios locales (por defecto desactivados). */
interface ReminderPreferenceRepository {
    val enabled: Flow<Boolean>

    suspend fun setEnabled(enabled: Boolean)
}
