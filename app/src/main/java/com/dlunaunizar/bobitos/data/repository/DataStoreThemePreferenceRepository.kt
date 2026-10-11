package com.dlunaunizar.bobitos.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dlunaunizar.bobitos.core.designsystem.theme.ThemeMode
import com.dlunaunizar.bobitos.core.designsystem.theme.parseThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Singleton
class DataStoreThemePreferenceRepository @Inject constructor(@param:ApplicationContext private val context: Context) :
    ThemePreferenceRepository {
    override val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.themeModes()

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }
}

internal val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

/** Sin tema guardado -> el de por defecto; los errores de lectura ([IOException]) no tumban la app. */
internal fun Flow<Preferences>.themeModes(): Flow<ThemeMode> = catch { error ->
    if (error is IOException) emit(emptyPreferences()) else throw error
}.map { preferences ->
    parseThemeMode(preferences[THEME_MODE_KEY])
}
