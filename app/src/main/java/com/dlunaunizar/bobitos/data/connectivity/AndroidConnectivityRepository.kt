package com.dlunaunizar.bobitos.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AndroidConnectivityRepository @Inject constructor(
    @ApplicationContext context: Context,
) : ConnectivityRepository {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val mutableStatus = MutableStateFlow(connectivityManager.currentStatus())

    override val status: StateFlow<NetworkStatus> = mutableStatus.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publish(network)

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) = publish(networkCapabilities)

        override fun onLost(network: Network) = publish()
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(callback)
    }

    private fun publish(network: Network? = connectivityManager.activeNetwork) {
        mutableStatus.value = connectivityManager.getNetworkCapabilities(network).toStatus()
    }

    private fun publish(capabilities: NetworkCapabilities) {
        mutableStatus.value = capabilities.toStatus()
    }
}

private fun ConnectivityManager.currentStatus(): NetworkStatus =
    getNetworkCapabilities(activeNetwork).toStatus()

private fun NetworkCapabilities?.toStatus(): NetworkStatus {
    val hasUsefulConnection = this != null &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    return if (hasUsefulConnection) NetworkStatus.ONLINE else NetworkStatus.OFFLINE
}
