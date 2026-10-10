package com.dlunaunizar.bobitos.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreCatalogSyncStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `sync state round-trips in DataStore`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher())
        val dataStore = PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
            File(folder.root, "catalog_sync.preferences_pb")
        }
        val store = DataStoreCatalogSyncStore(dataStore)

        assertNull(store.read("exercises"))
        store.write("exercises", CatalogSyncState(version = 7, count = 250, fetchedAtMillis = 1234L))
        store.write("recipes", CatalogSyncState(version = 1, count = 3, fetchedAtMillis = 99L))

        assertEquals(CatalogSyncState(7, 250, 1234L), store.read("exercises"))
        assertEquals(CatalogSyncState(1, 3, 99L), store.read("recipes"))
    }
}
