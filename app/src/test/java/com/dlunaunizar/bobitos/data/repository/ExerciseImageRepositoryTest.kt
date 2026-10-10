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
        assertArrayEquals(bytesA, stored.bytes)
        assertNull(parseImageDoc(mapOf("hash" to hashA)))
        assertNull(parseImageDoc(mapOf("data" to bytesA)))
        assertNull(parseImageDoc(mapOf("data" to "texto", "hash" to hashA)))
        assertNull(parseImageDoc(null))
    }

    @Test
    fun `same hash in cache reads no server`() = runTest {
        val source = FakeSource(cache = StoredImage(hashA, bytesA))
        val result = CachedExerciseImageRepository(source).imageBytes("press", hashA)
        assertArrayEquals(bytesA, result)
        assertEquals(0, source.serverReads)
    }

    @Test
    fun `different or missing cached hash reads the server`() = runTest {
        val stale = FakeSource(cache = StoredImage(hashB, bytesB), server = { StoredImage(hashA, bytesA) })
        assertArrayEquals(bytesA, CachedExerciseImageRepository(stale).imageBytes("press", hashA))
        assertEquals(1, stale.serverReads)

        val empty = FakeSource(server = { StoredImage(hashA, bytesA) })
        assertArrayEquals(bytesA, CachedExerciseImageRepository(empty).imageBytes("press", hashA))
        assertEquals(1, empty.serverReads)
    }

    @Test
    fun `an empty cache failure falls back to the server`() = runTest {
        val source = FakeSource(cacheFailure = java.io.IOException("cache"), server = { StoredImage(hashA, bytesA) })
        assertArrayEquals(bytesA, CachedExerciseImageRepository(source).imageBytes("press", hashA))
    }

    @Test
    fun `server timeout or failure returns null`() = runTest {
        val hanging = FakeSource(server = { awaitCancellation() })
        assertNull(CachedExerciseImageRepository(hanging, timeoutMillis = 50).imageBytes("press", hashA))

        val failing = FakeSource(server = { throw java.io.IOException("offline") })
        assertNull(CachedExerciseImageRepository(failing).imageBytes("press", hashA))

        val missing = FakeSource(server = { null })
        assertNull(CachedExerciseImageRepository(missing).imageBytes("press", hashA))
    }

    @Test
    fun `cancellation is rethrown`() = runTest {
        val source = FakeSource(server = { throw CancellationException("cancelled") })
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { CachedExerciseImageRepository(source).imageBytes("press", hashA) }
        }
    }
}
