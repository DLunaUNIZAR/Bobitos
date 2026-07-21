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

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding")

@Singleton
class DataStoreOnboardingPreferenceRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : OnboardingPreferenceRepository {
    override val welcomeSeen: Flow<Boolean> = context.onboardingDataStore.data.map { preferences ->
        preferences[WELCOME_SEEN_KEY] ?: false
    }

    override suspend fun markWelcomeSeen() {
        context.onboardingDataStore.edit { preferences ->
            preferences[WELCOME_SEEN_KEY] = true
        }
    }

    private companion object {
        val WELCOME_SEEN_KEY = booleanPreferencesKey("welcome_seen")
    }
}
