package com.dlunaunizar.bobitos.data.repository

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Persiste el estado del aviso automático de betas: última comprobación terminada y versión pospuesta. */
interface BetaUpdatePreferenceRepository {
    val lastAutoCheckDate: Flow<LocalDate?>
    val postponedVersionCode: Flow<Long?>

    suspend fun setLastAutoCheckDate(date: LocalDate)

    suspend fun setPostponedVersionCode(versionCode: Long)
}
