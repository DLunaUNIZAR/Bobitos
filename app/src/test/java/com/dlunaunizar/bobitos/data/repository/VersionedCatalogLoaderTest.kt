package com.dlunaunizar.bobitos.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

private const val DAY = 24L * 60 * 60 * 1000
private const val NOW = 100 * DAY

private class FakeCatalogSource(
    var cache: CatalogPage<String> = CatalogPage(emptyList(), 0),
    var server: CatalogPage<String> = CatalogPage(emptyList(), 0),
    var meta: CatalogMeta = CatalogMeta.Missing,
    var serverFails: Boolean = false,
) : CatalogSource<String> {
    var cacheReads = 0
    var serverReads = 0

    override suspend fun readCache(): CatalogPage<String> {
        cacheReads++
        return cache
    }

    override suspend fun readServer(): CatalogPage<String> {
        serverReads++
        if (serverFails) throw IOException("sin red")
        return server
    }

    override suspend fun readMeta(): CatalogMeta = meta
}

private class FakeSyncStore(var state: CatalogSyncState? = null) : CatalogSyncStore {
    var writes = 0

    override suspend fun read(key: String): CatalogSyncState? = state

    override suspend fun write(key: String, state: CatalogSyncState) {
        writes++
        this.state = state
    }
}

private fun page(vararg items: String) = CatalogPage(items.toList(), items.size)

@OptIn(ExperimentalCoroutinesApi::class)
class VersionedCatalogLoaderTest {
    private val source = FakeCatalogSource()
    private val store = FakeSyncStore()
    private val loader = VersionedCatalogLoader(source, store, "exercises") { NOW }

    @Test
    fun `first launch reads the server and stores version and count`() = runTest {
        source.server = page("a", "b")
        source.meta = CatalogMeta.Known(3)

        val emitted = loader.catalog(MutableSharedFlow()).toListUntilIdle(this)

        assertEquals(listOf(listOf("a", "b")), emitted)
        assertEquals(CatalogSyncState(3, 2, NOW), store.state)
    }

    @Test
    fun `same version and full cache reads no documents from the server`() = runTest {
        source.cache = page("a", "b")
        source.meta = CatalogMeta.Known(3)
        store.state = CatalogSyncState(3, 2, NOW - DAY)

        val emitted = loader.catalog(MutableSharedFlow()).toListUntilIdle(this)

        assertEquals(listOf(listOf("a", "b")), emitted)
        assertEquals(0, source.serverReads)
        assertEquals(0, store.writes)
    }

    @Test
    fun `newer version refreshes from the server`() = runTest {
        source.cache = page("a")
        source.server = page("a", "b")
        source.meta = CatalogMeta.Known(4)
        store.state = CatalogSyncState(3, 1, NOW - DAY)

        val emitted = loader.catalog(MutableSharedFlow()).toListUntilIdle(this)

        assertEquals(listOf(listOf("a"), listOf("a", "b")), emitted)
        assertEquals(CatalogSyncState(4, 2, NOW), store.state)
    }

    @Test
    fun `offline meta keeps the cache and the stored version`() = runTest {
        source.cache = page("a")
        source.meta = CatalogMeta.Unreachable
        val before = CatalogSyncState(3, 1, NOW - DAY)
        store.state = before

        val emitted = loader.catalog(MutableSharedFlow()).toListUntilIdle(this)

        assertEquals(listOf(listOf("a")), emitted)
        assertEquals(0, source.serverReads)
        assertEquals(before, store.state)
    }

    @Test
    fun `missing meta reads the server and stores nothing`() = runTest {
        source.cache = page("a")
        source.server = page("a", "b")
        source.meta = CatalogMeta.Missing

        val emitted = loader.catalog(MutableSharedFlow()).toListUntilIdle(this)

        assertEquals(listOf(listOf("a"), listOf("a", "b")), emitted)
        assertEquals(1, source.serverReads)
        assertNull(store.state)
    }

    @Test
    fun `cache with fewer documents than stored refreshes`() = runTest {
        source.cache = page("a")
        source.server = page("a", "b", "c")
        source.meta = CatalogMeta.Known(3)
        store.state = CatalogSyncState(3, 3, NOW - DAY)

        loader.catalog(MutableSharedFlow()).toListUntilIdle(this)

        assertEquals(1, source.serverReads)
        assertEquals(CatalogSyncState(3, 3, NOW), store.state)
    }

    @Test
    fun `older than seven days or clock going back refreshes`() {
        val cached = 2
        val meta = CatalogMeta.Known(3)
        fun decide(fetchedAt: Long) = decideCatalogLoad(meta, CatalogSyncState(3, 2, fetchedAt), cached, NOW)

        assertEquals(CatalogLoad.CACHE, decide(NOW - CATALOG_MAX_AGE_MILLIS))
        assertEquals(CatalogLoad.SERVER, decide(NOW - CATALOG_MAX_AGE_MILLIS - 1))
        assertEquals(CatalogLoad.SERVER, decide(NOW + 1))
        assertEquals(CatalogLoad.SERVER, decideCatalogLoad(meta, null, cached, NOW))
        assertEquals(CatalogLoad.SERVER, decideCatalogLoad(CatalogMeta.Unreachable, null, 0, NOW))
    }

    @Test
    fun `server failure with cache keeps the cache and without cache fails`() = runTest {
        source.serverFails = true
        source.meta = CatalogMeta.Known(4)
        source.cache = page("a")
        val before = CatalogSyncState(3, 1, NOW - DAY)
        store.state = before

        val emitted = loader.catalog(MutableSharedFlow()).toListUntilIdle(this)

        assertEquals(listOf(listOf("a")), emitted)
        assertEquals(before, store.state)

        source.cache = CatalogPage(emptyList(), 0)
        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking { loader.catalog(MutableSharedFlow()).toList() }
        }
    }

    @Test
    fun `own write right after the stored version is adopted without refetch`() = runTest {
        source.cache = page("a", "b", "c")
        store.state = CatalogSyncState(3, 2, NOW - DAY)

        loader.afterOwnWrite(previousVersion = 3)

        assertEquals(CatalogSyncState(4, 3, NOW - DAY), store.state)
        assertEquals(0, source.serverReads)
    }

    @Test
    fun `own write racing another write is not adopted`() = runTest {
        source.cache = page("a", "b", "c")
        val before = CatalogSyncState(2, 2, NOW - DAY)
        store.state = before

        loader.afterOwnWrite(previousVersion = 3)

        assertEquals(before, store.state)
        assertEquals(0, store.writes)
        assertNull(syncStateAfterOwnWrite(null, 0, 1))
    }

    @Test
    fun `local change re-emits from the cache only`() = runTest(UnconfinedTestDispatcher()) {
        source.cache = page("a")
        source.meta = CatalogMeta.Known(3)
        store.state = CatalogSyncState(3, 1, NOW - DAY)
        val changes = MutableSharedFlow<Unit>()
        val emitted = mutableListOf<List<String>>()
        val job = launch { loader.catalog(changes).collect { emitted += it } }

        source.cache = page("a", "b")
        changes.emit(Unit)

        assertEquals(listOf(listOf("a"), listOf("a", "b")), emitted)
        assertEquals(0, source.serverReads)
        job.cancel()
    }
}

// Recoge hasta que el flujo termina o se queda esperando solo cambios locales.
private suspend fun <T> kotlinx.coroutines.flow.Flow<List<T>>.toListUntilIdle(
    scope: kotlinx.coroutines.CoroutineScope,
): List<List<T>> {
    val out = mutableListOf<List<T>>()
    val job = scope.launch { collect { out += it } }
    kotlinx.coroutines.yield()
    scope.testScheduler().advanceUntilIdle()
    job.cancel()
    return out
}

private fun kotlinx.coroutines.CoroutineScope.testScheduler() =
    (this as kotlinx.coroutines.test.TestScope).testScheduler
