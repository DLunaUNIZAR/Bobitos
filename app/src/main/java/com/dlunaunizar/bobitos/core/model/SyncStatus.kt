package com.dlunaunizar.bobitos.core.model

enum class SyncStatus {
    OFFLINE,
    REFRESHING,
    ONLINE,
}

val SyncStatus.canWrite: Boolean
    get() = this == SyncStatus.ONLINE
