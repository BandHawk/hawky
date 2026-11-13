package com.example.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DeviceNetworkState(val label: String, val isConnected: Boolean) {
    OFFLINE("Offline / Airplane", false),
    WIFI("Wi-Fi Connected", true),
    CELLULAR("Cellular Connected", true),
    OTHER("Network Connected", true),
}

class ConnectivityMonitor(context: Context) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkState = MutableStateFlow(readCurrentState())
    val networkState: StateFlow<DeviceNetworkState> = _networkState.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _networkState.value = readCurrentState()
        }

        override fun onLost(network: Network) {
            _networkState.value = readCurrentState()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            _networkState.value = readCurrentState()
        }
    }

    init {
        runCatching { cm.registerDefaultNetworkCallback(callback) }
    }

    private fun readCurrentState(): DeviceNetworkState {
        val active = cm.activeNetwork ?: return DeviceNetworkState.OFFLINE
        val caps = cm.getNetworkCapabilities(active) ?: return DeviceNetworkState.OFFLINE
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (!hasInternet) return DeviceNetworkState.OFFLINE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> DeviceNetworkState.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> DeviceNetworkState.CELLULAR
            else -> DeviceNetworkState.OTHER
        }
    }

    fun release() {
        runCatching { cm.unregisterNetworkCallback(callback) }
    }
}
