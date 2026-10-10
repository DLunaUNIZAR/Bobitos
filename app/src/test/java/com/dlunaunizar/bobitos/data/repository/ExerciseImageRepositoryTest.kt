package com.dlunaunizar.bobitos.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseImageRepositoryTest {
    private val hashA = "a".repeat(64)
    private val hashB = "b".repeat(64)
    private val bytesA = byteArrayOf(1, 2, 3)
    private val bytesB = byteArrayOf(9, 8)

    private class FakeSource(
        var cache: StoredImage? = null,
        var server: suspend () -> StoredImage? = { null },
        var cacheFailure: Throwable? = null,
    ) : ImageSource {
        var cacheReads = 0
        var serverReads = 0

        override suspend fun readCache(exerciseId: String): StoredImage? {
            cacheReads++
            cacheFailure?.let { throw it }
            return cache
        }

        override suspend fun readServer(exerciseId: String): StoredImage? {
            serverReads++
            return server()
        }
    }

    @Test
    fun `decideImageRead uses the cache only when the hash matches`() {
        assertEquals(ImageRead.CACHE, decideImageRead(hashA, hashA))
        assertEquals(ImageRead.SERVER, decideImageRead(hashB, hashA))
        assertEquals(ImageRead.SERVER, decideImageRead(null, hashA))
    }

    @Test
    fun `parseImageDoc reads bytes and hash and rejects missing data`() {
        val stored = parseImageDoc(mapOf("data" to bytesA, "hash" to hashA, "contentType" to "image/webp"))!!
        assertEquals(hashA, stored.hash)
        assertArrayEquals(bytesA, stored.image.bytes)
        assertNull(stored.image.author)
        assertNull(stored.image.license)
        assertNull(stored.image.sourceUrl)
        assertNull(parseImageDoc(mapOf("hash" to hashA)))
        assertNull(parseImageDoc(mapOf("data" to bytesA)))
        assertNull(parseImageDoc(mapOf("data" to "texto", "hash" to hashA)))
        assertNull(parseImageDoc(null))
    }

    @Test
    fun `parseImageDoc reads author licence and source url`() {
        val stored = parseImageDoc(
            mapOf(
                "data" to bytesA,
                "hash" to hashA,
                "author" to "Ana",
                "license" to "CC-BY-SA-4.0",
                "sourceUrl" to "https://wger.de/media/a.png",
            ),
        )!!
        assertEquals("Ana", stored.image.author)
        assertEquals("CC-BY-SA-4.0", stored.image.license)
        assertEquals("https://wger.de/media/a.png", stored.image.sourceUrl)
        assertNull(parseImageDoc(mapOf("data" to bytesA, "hash" to hashA, "author" to 5))!!.image.author)
    }

    @Test
    fun `same hash in cache reads no server`() = runTest {
        val cached = LoadedImage(bytesA, "Ana", "CC0-1.0", "https://wger.de/media/a.png")
        val source = FakeSource(cache = StoredImage(hashA, cached))
        val result = CachedExerciseImageRepository(source).image("press", hashA)!!
        assertArrayEquals(bytesA, result.bytes)
        assertEquals(LoadedImage(result.bytes, "Ana", "CC0-1.0", "https://wger.de/media/a.png"), result)
        assertEquals(0, source.serverReads)
    }

    @Test
    fun `different or missing cached hash reads the server`() = runTest {
        val served = StoredImage(hashA, LoadedImage(bytesA, "Eva", "CC-BY-4.0", "s"))
        val stale = FakeSource(cache = StoredImage(hashB, LoadedImage(bytesB)), server = { served })
        val fromServer = CachedExerciseImageRepository(stale).image("press", hashA)!!
        assertArrayEquals(bytesA, fromServer.bytes)
        assertEquals("Eva", fromServer.author)
        assertEquals("CC-BY-4.0", fromServer.license)
        assertEquals("s", fromServer.sourceUrl)
        assertEquals(1, stale.serverReads)

        val empty = FakeSource(server = { StoredImage(hashA, LoadedImage(bytesA)) })
        assertArrayEquals(bytesA, CachedExerciseImageRepository(empty).image("press", hashA)!!.bytes)
        assertEquals(1, empty.serverReads)
    }

    @Test
    fun `an empty cache failure falls back to the server`() = runTest {
        val served = StoredImage(hashA, LoadedImage(bytesA))
        val source = FakeSource(cacheFailure = java.io.IOException("cache"), server = { served })
        assertArrayEquals(bytesA, CachedExerciseImageRepository(source).image("press", hashA)!!.bytes)
    }

    @Test
    fun `server timeout or failure returns null`() = runTest {
        val hanging = FakeSource(server = { awaitCancellation() })
        assertNull(CachedExerciseImageRepository(hanging, timeoutMillis = 50).image("press", hashA))

        val failing = FakeSource(server = { throw java.io.IOException("offline") })
        assertNull(CachedExerciseImageRepository(failing).image("press", hashA))

        val missing = FakeSource(server = { null })
        assertNull(CachedExerciseImageRepository(missing).image("press", hashA))
    }

    @Test
    fun `cancellation is rethrown`() = runTest {
        val source = FakeSource(server = { throw CancellationException("cancelled") })
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { CachedExerciseImageRepository(source).image("press", hashA) }
        }
    }
}
