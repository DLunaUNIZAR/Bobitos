package com.dlunaunizar.bobitos.data.sync

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealtimeMetrics @Inject constructor() {
    private val nextId = AtomicLong(0)
    private val activeListeners = ConcurrentHashMap<Long, String>()
    private val remoteDocuments = AtomicLong(0)

    fun listenerStarted(scope: String): Long {
        val id = nextId.incrementAndGet()
        activeListeners[id] = scope
        Log.d(LOG_TAG, "listener+ scope=$scope active=${activeListeners.size}")
        return id
    }

    fun listenerStopped(id: Long) {
        val scope = activeListeners.remove(id) ?: return
        Log.d(LOG_TAG, "listener- scope=$scope active=${activeListeners.size}")
    }

    fun snapshotReceived(
        scope: String,
        changedDocuments: Int,
        fromCache: Boolean,
    ) {
        val total = if (fromCache) {
            remoteDocuments.get()
        } else {
            remoteDocuments.addAndGet(changedDocuments.toLong())
        }
        Log.d(
            LOG_TAG,
            "snapshot scope=$scope source=${if (fromCache) "cache" else "server"} " +
                "changed=$changedDocuments remoteDocuments=$total",
        )
    }

    internal fun activeCount(): Int = activeListeners.size

    private companion object {
        const val LOG_TAG = "BobitosRealtime"
    }
}
