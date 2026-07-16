package com.dlunaunizar.bobitos.data.sync

import com.dlunaunizar.bobitos.core.model.SyncStatus
import kotlinx.coroutines.flow.StateFlow

interface SyncRepository {
    val status: StateFlow<SyncStatus>

    suspend fun refresh(spaceId: String?): Boolean

    fun requireRefresh()

    fun markOffline()

    fun requireWritable()

    fun reportWriteFailure(error: Throwable)
}

class WriteNotAllowedException : Exception()
