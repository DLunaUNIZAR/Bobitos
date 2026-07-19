package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.designsystem.theme.ThemeMode
import kotlinx.coroutines.flow.Flow

interface ThemePreferenceRepository {
    /** Modo de tema persistido. Por defecto [ThemeMode.LIGHT]. */
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)
}
