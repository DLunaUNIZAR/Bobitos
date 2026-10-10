package com.dlunaunizar.bobitos.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

// Imagen de un ejercicio almacenada en Firestore (colección exerciseImages, un documento por ejercicio).
interface ExerciseImageRepository {
    // Bytes (WebP) de la imagen con ese hash; null si no están disponibles (sin red, sin documento, error).
    suspend fun imageBytes(exerciseId: String, hash: String): ByteArray?
}

enum class ImageRead { CACHE, SERVER }

// Si la copia local tiene el hash que pide la ficha no hace falta ir al servidor.
internal fun decideImageRead(cachedHash: String?, wantedHash: String): ImageRead =
    if (cachedHash == wantedHash) ImageRead.CACHE else ImageRead.SERVER

internal class StoredImage(val hash: String, val bytes: ByteArray)

// `data` llega como Blob de Firestore (ya convertido a ByteArray por quien lee) y `hash` como texto.
internal fun parseImageDoc(map: Map<String, Any?>?): StoredImage? {
    val data = map?.get("data") as? ByteArray ?: return null
    val hash = map["hash"] as? String ?: return null
    return StoredImage(hash, data)
}

// Lectura de un documento de imagen; las excepciones de lectura se tratan como «no disponible».
internal interface ImageSource {
    suspend fun readCache(exerciseId: String): StoredImage?

    suspend fun readServer(exerciseId: String): StoredImage?
}

internal class CachedExerciseImageRepository(
    private val source: ImageSource,
    private val timeoutMillis: Long = SERVER_TIMEOUT_MILLIS,
) : ExerciseImageRepository {
    override suspend fun imageBytes(exerciseId: String, hash: String): ByteArray? {
        val cached = attempt { source.readCache(exerciseId) }
        if (decideImageRead(cached?.hash, hash) == ImageRead.CACHE) return cached?.bytes
        return attempt { withTimeoutOrNull(timeoutMillis) { source.readServer(exerciseId) } }?.bytes
    }

    private suspend fun <T> attempt(block: suspend () -> T?): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    companion object {
        const val SERVER_TIMEOUT_MILLIS = 10_000L
    }
}
