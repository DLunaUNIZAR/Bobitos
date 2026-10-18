package com.dlunaunizar.bobitos.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.betaUpdateDataStore by preferencesDataStore(name = "beta_update")

@Singleton
class DataStoreBetaUpdatePreferenceRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BetaUpdatePreferenceRepository {
    private val data: Flow<Preferences> = context.betaUpdateDataStore.data.tolerateReadErrors()

    override val lastAutoCheckDate: Flow<LocalDate?> = data.map { preferences ->
        preferences[LAST_AUTO_CHECK_KEY]?.let(LocalDate::ofEpochDay)
    }

    override val postponedVersionCode: Flow<Long?> = data.map { preferences ->
        preferences[POSTPONED_VERSION_KEY]
    }

    override suspend fun setLastAutoCheckDate(date: LocalDate) {
        context.betaUpdateDataStore.edit { preferences ->
            preferences[LAST_AUTO_CHECK_KEY] = date.toEpochDay()
        }
    }

    override suspend fun setPostponedVersionCode(versionCode: Long) {
        context.betaUpdateDataStore.edit { preferences ->
            preferences[POSTPONED_VERSION_KEY] = versionCode
        }
    }

    private companion object {
        val LAST_AUTO_CHECK_KEY = longPreferencesKey("last_auto_check_epoch_day")
        val POSTPONED_VERSION_KEY = longPreferencesKey("postponed_version_code")
    }
}

/** Un fichero ilegible o corrupto ([IOException]) se lee como vacío: la comprobación automática es silenciosa. */
internal fun Flow<Preferences>.tolerateReadErrors(): Flow<Preferences> = catch { error ->
    if (error is IOException) emit(emptyPreferences()) else throw error
}
