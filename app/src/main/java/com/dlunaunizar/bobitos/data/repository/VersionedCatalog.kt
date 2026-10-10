package com.dlunaunizar.bobitos.data.repository

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException

/** Documentos leídos (`rawCount`, incluidos los que no se pudieron interpretar) y los ya interpretados. */
data class CatalogPage<T>(val items: List<T>, val rawCount: Int)

sealed interface CatalogMeta {
    data class Known(val version: Long) : CatalogMeta

    /** Sin documento de versión o sin permiso para leerlo (reglas aún sin desplegar). */
    data object Missing : CatalogMeta

    /** Sin red o sin respuesta a tiempo. */
    data object Unreachable : CatalogMeta
}

data class CatalogSyncState(val version: Long, val count: Int, val fetchedAtMillis: Long)

enum class CatalogLoad { CACHE, SERVER }

const val CATALOG_MAX_AGE_MILLIS: Long = 7L * 24 * 60 * 60 * 1000

internal fun decideCatalogLoad(
    meta: CatalogMeta,
    stored: CatalogSyncState?,
    cachedCount: Int,
    nowMillis: Long,
): CatalogLoad = when {
    cachedCount == 0 -> CatalogLoad.SERVER
    meta is CatalogMeta.Unreachable -> CatalogLoad.CACHE
    meta is CatalogMeta.Missing -> CatalogLoad.SERVER
    meta !is CatalogMeta.Known || stored == null || stored.version != meta.version -> CatalogLoad.SERVER
    cachedCount < stored.count -> CatalogLoad.SERVER
    nowMillis < stored.fetchedAtMillis || nowMillis - stored.fetchedAtMillis > CATALOG_MAX_AGE_MILLIS ->
        CatalogLoad.SERVER
    else -> CatalogLoad.CACHE
}

/**
 * Estado tras una escritura propia: solo si la versión guardada era exactamente la que leyó la
 * transacción (nadie escribió entremedias). Conserva `fetchedAt`: no se ha vuelto a leer todo.
 */
internal fun syncStateAfterOwnWrite(
    stored: CatalogSyncState?,
    previousVersion: Long,
    cachedCount: Int,
): CatalogSyncState? = stored
    ?.takeIf { it.version == previousVersion }
    ?.copy(version = previousVersion + 1, count = cachedCount)

interface CatalogSource<T> {
    suspend fun readCache(): CatalogPage<T>

    suspend fun readServer(): CatalogPage<T>

    suspend fun readMeta(): CatalogMeta
}

class VersionedCatalogLoader<T>(
    private val source: CatalogSource<T>,
    private val store: CatalogSyncStore,
    private val key: String,
    private val now: () -> Long,
) {
    /**
     * Emite la caché (si hay), consulta la versión y solo lee el servidor si hace falta. Cada
     * `localChanges` vuelve a emitir desde la caché, sin leer del servidor.
     */
    fun catalog(localChanges: Flow<Unit>): Flow<List<T>> = channelFlow {
        // UNDISPATCHED: el colector queda suscrito antes de empezar la carga inicial.
        launch(start = CoroutineStart.UNDISPATCHED) {
            localChanges.collect { send(source.readCache().items) }
        }
        load()
    }

    private suspend fun ProducerScope<List<T>>.load() {
        val cache = source.readCache()
        if (cache.rawCount > 0) send(cache.items)
        val meta = source.readMeta()
        val decision = decideCatalogLoad(meta, store.read(key), cache.rawCount, now())
        if (decision == CatalogLoad.SERVER) refreshFromServer(meta, hadCache = cache.rawCount > 0)
    }

    private suspend fun ProducerScope<List<T>>.refreshFromServer(meta: CatalogMeta, hadCache: Boolean) {
        val page = try {
            source.readServer()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            if (hadCache) return else throw error
        }
        if (meta is CatalogMeta.Known) {
            store.write(key, CatalogSyncState(meta.version, page.rawCount, now()))
        }
        send(page.items)
    }

    /** Adopta la versión que acaba de subir esta propia escritura, si nadie se interpuso. */
    suspend fun afterOwnWrite(previousVersion: Long) {
        val state = syncStateAfterOwnWrite(store.read(key), previousVersion, source.readCache().rawCount)
        if (state != null) store.write(key, state)
    }
}
