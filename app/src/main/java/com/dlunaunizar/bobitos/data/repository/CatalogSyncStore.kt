package com.dlunaunizar.bobitos.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

interface CatalogSyncStore {
    suspend fun read(key: String): CatalogSyncState?

    suspend fun write(key: String, state: CatalogSyncState)
}

@Singleton
class DataStoreCatalogSyncStore @Inject constructor(private val dataStore: DataStore<Preferences>) :
    CatalogSyncStore {
    override suspend fun read(key: String): CatalogSyncState? {
        val preferences = dataStore.data.first()
        val version = preferences[longPreferencesKey("${key}_version")] ?: return null
        val count = preferences[intPreferencesKey("${key}_count")] ?: return null
        val fetchedAt = preferences[longPreferencesKey("${key}_fetched_at")] ?: return null
        return CatalogSyncState(version, count, fetchedAt)
    }

    override suspend fun write(key: String, state: CatalogSyncState) {
        dataStore.edit { preferences ->
            preferences[longPreferencesKey("${key}_version")] = state.version
            preferences[intPreferencesKey("${key}_count")] = state.count
            preferences[longPreferencesKey("${key}_fetched_at")] = state.fetchedAtMillis
        }
    }
}
