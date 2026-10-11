package com.dlunaunizar.bobitos.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dlunaunizar.bobitos.core.designsystem.theme.ThemeMode
import com.dlunaunizar.bobitos.core.designsystem.theme.parseThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Singleton
class DataStoreThemePreferenceRepository @Inject constructor(@param:ApplicationContext private val context: Context) :
    ThemePreferenceRepository {
    override val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { preferences ->
        parseThemeMode(preferences[THEME_MODE_KEY])
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }

    private companion object {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    }
}
