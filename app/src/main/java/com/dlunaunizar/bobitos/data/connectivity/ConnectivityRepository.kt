package com.dlunaunizar.bobitos.data.connectivity

import kotlinx.coroutines.flow.StateFlow

enum class NetworkStatus {
    OFFLINE,
    ONLINE,
}

interface ConnectivityRepository {
    val status: StateFlow<NetworkStatus>
}
