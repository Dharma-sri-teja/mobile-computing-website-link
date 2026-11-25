package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.model.NetworkType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.ConcurrentHashMap

/**
 * Snapshot emitted in real time whenever Wi-Fi or cellular availability changes.
 */
data class NetworkAvailabilityState(
    val isWifiAvailable: Boolean = false,
    val isWifiValidated: Boolean = false,
    val isCellularAvailable: Boolean = false,
    val isCellularValidated: Boolean = false,
    val activeTransport: NetworkType = NetworkType.NONE,
    val hasValidatedInternet: Boolean = false,
    val lastUpdatedMs: Long = System.currentTimeMillis()
)

/**
 * Reactive Android connectivity monitor built on ConnectivityManager.NetworkCallback
 * and Kotlin callbackFlow. Tracks Wi-Fi and cellular availability independently and
 * cleans up all callbacks automatically when collection stops.
 */
class NetworkMonitorService(context: Context) {

    private val tag = "NetworkMonitorService"
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Emits real-time updates of Wi-Fi and cellular availability via ConnectivityManager.NetworkCallback.
     */
    fun observeNetworkAvailability(): Flow<NetworkAvailabilityState> = callbackFlow {
        val wifiNetworks = ConcurrentHashMap<Network, NetworkCapabilities>()
        val cellularNetworks = ConcurrentHashMap<Network, NetworkCapabilities>()
        var defaultNetwork: Network? = connectivityManager.activeNetwork

        fun buildState(): NetworkAvailabilityState {
            val activeCaps = defaultNetwork?.let {
                try {
                    connectivityManager.getNetworkCapabilities(it)
                } catch (_: Exception) {
                    null
                }
            }

            val wifiAvailable = wifiNetworks.isNotEmpty() ||
                (activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
            val wifiValidated = wifiNetworks.values.any {
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } || (activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                activeCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))

            val cellularAvailable = cellularNetworks.isNotEmpty() ||
                (activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true)
            val cellularValidated = cellularNetworks.values.any {
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } || (activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true &&
                activeCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))

            val activeType = when {
                activeCaps == null -> NetworkType.NONE
                activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
                activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.VPN
                else -> NetworkType.OTHER
            }

            val hasValidated = activeCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true ||
                wifiValidated || cellularValidated

            return NetworkAvailabilityState(
                isWifiAvailable = wifiAvailable,
                isWifiValidated = wifiValidated,
                isCellularAvailable = cellularAvailable,
                isCellularValidated = cellularValidated,
                activeTransport = activeType,
                hasValidatedInternet = hasValidated,
                lastUpdatedMs = System.currentTimeMillis()
            )
        }

        fun emitCurrent() {
            trySend(buildState())
        }

        val wifiCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                connectivityManager.getNetworkCapabilities(network)?.let {
                    wifiNetworks[network] = it
                }
                emitCurrent()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                wifiNetworks[network] = networkCapabilities
                emitCurrent()
            }

            override fun onLost(network: Network) {
                wifiNetworks.remove(network)
                emitCurrent()
            }
        }

        val cellularCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                connectivityManager.getNetworkCapabilities(network)?.let {
                    cellularNetworks[network] = it
                }
                emitCurrent()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                cellularNetworks[network] = networkCapabilities
                emitCurrent()
            }

            override fun onLost(network: Network) {
                cellularNetworks.remove(network)
                emitCurrent()
            }
        }

        val defaultCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                defaultNetwork = network
                emitCurrent()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                defaultNetwork = network
                emitCurrent()
            }

            override fun onLost(network: Network) {
                if (defaultNetwork == network) {
                    defaultNetwork = null
                }
                emitCurrent()
            }
        }

        val wifiRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val cellularRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        var wifiRegistered = false
        var cellularRegistered = false
        var defaultRegistered = false

        try {
            connectivityManager.registerNetworkCallback(wifiRequest, wifiCallback)
            wifiRegistered = true
        } catch (e: Exception) {
            Log.e(tag, "Failed to register Wi-Fi NetworkCallback", e)
        }

        try {
            connectivityManager.registerNetworkCallback(cellularRequest, cellularCallback)
            cellularRegistered = true
        } catch (e: Exception) {
            Log.e(tag, "Failed to register Cellular NetworkCallback", e)
        }

        try {
            connectivityManager.registerDefaultNetworkCallback(defaultCallback)
            defaultRegistered = true
        } catch (e: Exception) {
            Log.e(tag, "Failed to register Default NetworkCallback", e)
        }

        // Emit immediate baseline snapshot upon collection
        emitCurrent()

        awaitClose {
            if (wifiRegistered) runCatching { connectivityManager.unregisterNetworkCallback(wifiCallback) }
            if (cellularRegistered) runCatching { connectivityManager.unregisterNetworkCallback(cellularCallback) }
            if (defaultRegistered) runCatching { connectivityManager.unregisterNetworkCallback(defaultCallback) }
        }
    }.distinctUntilChanged { old, new ->
        old.isWifiAvailable == new.isWifiAvailable &&
            old.isWifiValidated == new.isWifiValidated &&
            old.isCellularAvailable == new.isCellularAvailable &&
            old.isCellularValidated == new.isCellularValidated &&
            old.activeTransport == new.activeTransport &&
            old.hasValidatedInternet == new.hasValidatedInternet
    }
}
