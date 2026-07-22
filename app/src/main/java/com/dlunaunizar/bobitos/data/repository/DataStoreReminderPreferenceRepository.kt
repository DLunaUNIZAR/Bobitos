package com.dlunaunizar.bobitos.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.remindersDataStore by preferencesDataStore(name = "reminders")

@Singleton
class DataStoreReminderPreferenceRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ReminderPreferenceRepository {
    override val enabled: Flow<Boolean> = context.remindersDataStore.data.map { preferences ->
        preferences[ENABLED_KEY] ?: false
    }

    override suspend fun setEnabled(enabled: Boolean) {
        context.remindersDataStore.edit { preferences ->
            preferences[ENABLED_KEY] = enabled
        }
    }

    private companion object {
        val ENABLED_KEY = booleanPreferencesKey("reminders_enabled")
    }
}
