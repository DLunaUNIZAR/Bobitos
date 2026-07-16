package com.dlunaunizar.bobitos.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.activeSpaceDataStore by preferencesDataStore(name = "active_space")

@Singleton
class DataStoreActiveSpaceRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ActiveSpaceRepository {
    override fun activeSpaceId(userId: String): Flow<String?> =
        context.activeSpaceDataStore.data.map { preferences ->
            preferences[preferenceKey(userId)]
        }

    override suspend fun setActiveSpace(userId: String, spaceId: String?) {
        context.activeSpaceDataStore.edit { preferences ->
            val key = preferenceKey(userId)
            if (spaceId == null) {
                preferences.remove(key)
            } else {
                preferences[key] = spaceId
            }
        }
    }

    private fun preferenceKey(userId: String) =
        stringPreferencesKey("active_space_$userId")
}
