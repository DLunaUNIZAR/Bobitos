package com.dlunaunizar.bobitos.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dlunaunizar.bobitos.data.reminders.ReminderLeadTime
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

    override val leadTime: Flow<ReminderLeadTime> = context.remindersDataStore.data.map { preferences ->
        ReminderLeadTime.fromName(preferences[LEAD_TIME_KEY])
    }

    override suspend fun setEnabled(enabled: Boolean) {
        context.remindersDataStore.edit { preferences ->
            preferences[ENABLED_KEY] = enabled
        }
    }

    override suspend fun setLeadTime(leadTime: ReminderLeadTime) {
        context.remindersDataStore.edit { preferences ->
            preferences[LEAD_TIME_KEY] = leadTime.name
        }
    }

    private companion object {
        val ENABLED_KEY = booleanPreferencesKey("reminders_enabled")
        val LEAD_TIME_KEY = stringPreferencesKey("reminders_lead_time")
    }
}
