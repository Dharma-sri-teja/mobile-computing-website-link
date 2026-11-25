package com.example.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.telephony.CellSignalStrength
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.model.CellularDetails
import com.example.model.NetworkSnapshot
import com.example.model.NetworkType
import com.example.model.PermissionSummary
import com.example.model.WifiDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Real-time Android network telemetry detector.
 * Tracks default, Wi-Fi, Cellular, Ethernet, and VPN interfaces independently using
 * ConnectivityManager.NetworkCallback, WifiManager/WifiInfo, and TelephonyManager/SignalStrength.
 */
class NetworkDetector(private val context: Context) {

    private val tag = "NetworkDetector"

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private val _snapshot = MutableStateFlow(NetworkSnapshot())
    val snapshot: StateFlow<NetworkSnapshot> = _snapshot.asStateFlow()

    @Volatile
    private var defaultNetwork: Network? = null

    @Volatile
    private var boundNetworkType: NetworkType? = null

    @Volatile
    private var wifiNetwork: Network? = null

    @Volatile
    private var cellularNetwork: Network? = null

    @Volatile
    private var ethernetNetwork: Network? = null

    @Volatile
    private var vpnNetwork: Network? = null

    private val capabilitiesMap = ConcurrentHashMap<Network, NetworkCapabilities>()

    @Volatile
    private var latestWifiInfoFromCallback: WifiInfo? = null

    @Volatile
    private var latestSignalStrength: SignalStrength? = null

    @Volatile
    private var isRegistered = false

    private var telephonyCallback: Any? = null

    @Suppress("DEPRECATION")
    private var legacyPhoneStateListener: PhoneStateListener? = null

    private var onConnectivityChangedListener: ((NetworkSnapshot, NetworkSnapshot) -> Unit)? = null

    fun setOnConnectivityChangedListener(listener: (previous: NetworkSnapshot, current: NetworkSnapshot) -> Unit) {
        onConnectivityChangedListener = listener
    }

    fun getWifiNetwork(): Network? = wifiNetwork
    fun getCellularNetwork(): Network? = cellularNetwork
    fun getActiveOrDefaultNetwork(): Network? {
        return connectivityManager.boundNetworkForProcess ?: defaultNetwork ?: connectivityManager.activeNetwork
    }

    fun setBoundNetworkType(type: NetworkType?) {
        boundNetworkType = type
        refreshSnapshot("App traffic binding updated")
    }

    fun checkPermissions(): PermissionSummary {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val phone = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return PermissionSummary(
            fineLocationGranted = fine,
            coarseLocationGranted = coarse,
            phoneStateGranted = phone,
            notificationsGranted = notif
        )
    }

    private fun createCallback(
        onAvailableAction: (Network) -> Unit,
        onCapabilitiesAction: (Network, NetworkCapabilities) -> Unit,
        onLostAction: (Network) -> Unit
    ): ConnectivityManager.NetworkCallback {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onAvailable(network: Network) = onAvailableAction(network)
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                    onCapabilitiesAction(network, caps)
                override fun onLost(network: Network) = onLostAction(network)
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = onAvailableAction(network)
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                    onCapabilitiesAction(network, caps)
                override fun onLost(network: Network) = onLostAction(network)
            }
        }
    }

    private val defaultCallback = createCallback(
        onAvailableAction = { network ->
            defaultNetwork = network
            connectivityManager.getNetworkCapabilities(network)?.let { capabilitiesMap[network] = it }
            refreshSnapshot("Default network available")
        },
        onCapabilitiesAction = { network, caps ->
            defaultNetwork = network
            capabilitiesMap[network] = caps
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                (caps.transportInfo as? WifiInfo)?.let { latestWifiInfoFromCallback = it }
            }
            refreshSnapshot("Active network capabilities updated")
        },
        onLostAction = { network ->
            if (defaultNetwork == network) defaultNetwork = null
            capabilitiesMap.remove(network)
            refreshSnapshot("Default network lost")
        }
    )

    private val wifiCallback = createCallback(
        onAvailableAction = { network ->
            wifiNetwork = network
            connectivityManager.getNetworkCapabilities(network)?.let { capabilitiesMap[network] = it }
            refreshSnapshot("Wi-Fi interface available")
        },
        onCapabilitiesAction = { network, caps ->
            wifiNetwork = network
            capabilitiesMap[network] = caps
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                (caps.transportInfo as? WifiInfo)?.let { latestWifiInfoFromCallback = it }
            }
            refreshSnapshot("Wi-Fi telemetry updated")
        },
        onLostAction = { network ->
            if (wifiNetwork == network) {
                wifiNetwork = null
                latestWifiInfoFromCallback = null
            }
            capabilitiesMap.remove(network)
            refreshSnapshot("Wi-Fi disconnected")
        }
    )

    private val cellularCallback = createCallback(
        onAvailableAction = { network ->
            cellularNetwork = network
            connectivityManager.getNetworkCapabilities(network)?.let { capabilitiesMap[network] = it }
            refreshSnapshot("Cellular interface available")
        },
        onCapabilitiesAction = { network, caps ->
            cellularNetwork = network
            capabilitiesMap[network] = caps
            refreshSnapshot("Cellular telemetry updated")
        },
        onLostAction = { network ->
            if (cellularNetwork == network) cellularNetwork = null
            capabilitiesMap.remove(network)
            refreshSnapshot("Cellular network unavailable")
        }
    )

    private val ethernetCallback = createCallback(
        onAvailableAction = { network ->
            ethernetNetwork = network
            connectivityManager.getNetworkCapabilities(network)?.let { capabilitiesMap[network] = it }
            refreshSnapshot("Ethernet available")
        },
        onCapabilitiesAction = { network, caps ->
            ethernetNetwork = network
            capabilitiesMap[network] = caps
            refreshSnapshot("Ethernet updated")
        },
        onLostAction = { network ->
            if (ethernetNetwork == network) ethernetNetwork = null
            capabilitiesMap.remove(network)
            refreshSnapshot("Ethernet disconnected")
        }
    )

    private val vpnCallback = createCallback(
        onAvailableAction = { network ->
            vpnNetwork = network
            connectivityManager.getNetworkCapabilities(network)?.let { capabilitiesMap[network] = it }
            refreshSnapshot("VPN tunnel active")
        },
        onCapabilitiesAction = { network, caps ->
            vpnNetwork = network
            capabilitiesMap[network] = caps
            refreshSnapshot("VPN updated")
        },
        onLostAction = { network ->
            if (vpnNetwork == network) vpnNetwork = null
            capabilitiesMap.remove(network)
            refreshSnapshot("VPN disconnected")
        }
    )

    fun startListening() {
        if (isRegistered) {
            refreshSnapshot("Live telemetry refreshed")
            return
        }
        try {
            connectivityManager.registerDefaultNetworkCallback(defaultCallback)
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build(),
                wifiCallback
            )
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .build(),
                cellularCallback
            )
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                    .build(),
                ethernetCallback
            )
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(),
                vpnCallback
            )
            registerSignalStrengthListener()
            isRegistered = true
        } catch (e: Exception) {
            Log.e(tag, "Failed to register network callbacks", e)
        }
        refreshSnapshot("Connectivity callbacks active")
    }

    fun stopListening() {
        if (!isRegistered) return
        listOf(defaultCallback, wifiCallback, cellularCallback, ethernetCallback, vpnCallback).forEach { cb ->
            try {
                connectivityManager.unregisterNetworkCallback(cb)
            } catch (_: Exception) {
            }
        }
        unregisterSignalStrengthListener()
        isRegistered = false
    }

    private fun registerSignalStrengthListener() {
        val tm = telephonyManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cb = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                    override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                        latestSignalStrength = signalStrength
                        refreshSnapshot("Cellular signal updated")
                    }
                }
                tm.registerTelephonyCallback(context.mainExecutor, cb)
                telephonyCallback = cb
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                        latestSignalStrength = signalStrength
                        refreshSnapshot("Cellular signal updated")
                    }
                }
                @Suppress("DEPRECATION")
                tm.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
                legacyPhoneStateListener = listener
            }
        } catch (e: SecurityException) {
            Log.w(tag, "Signal strength listener restricted by OS/permission", e)
        } catch (e: Exception) {
            Log.w(tag, "Unable to register cellular signal listener", e)
        }
    }

    private fun unregisterSignalStrengthListener() {
        val tm = telephonyManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (telephonyCallback as? TelephonyCallback)?.let { tm.unregisterTelephonyCallback(it) }
                telephonyCallback = null
            } else {
                @Suppress("DEPRECATION")
                legacyPhoneStateListener?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
                legacyPhoneStateListener = null
            }
        } catch (_: Exception) {
        }
    }

    fun refreshSnapshot(reason: String = "Manual refresh"): NetworkSnapshot {
        val previous = _snapshot.value
        val permissions = checkPermissions()

        val activeNet = defaultNetwork ?: connectivityManager.activeNetwork
        val activeCaps = activeNet?.let {
            capabilitiesMap[it] ?: connectivityManager.getNetworkCapabilities(it)
        }

        val airplaneModeOn = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        } catch (_: Exception) {
            false
        }

        val wifiDetails = inspectWifi(permissions)
        val cellularDetails = inspectCellular(permissions, airplaneModeOn)

        val vpnActive = vpnNetwork != null ||
            (activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true)
        val ethernetAvailable = ethernetNetwork != null ||
            (activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true)

        val activeType = when {
            boundNetworkType != null -> boundNetworkType!!
            activeCaps == null -> NetworkType.NONE
            activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> {
                when {
                    wifiDetails.isConnected -> NetworkType.WIFI
                    cellularDetails.isConnected -> NetworkType.CELLULAR
                    else -> NetworkType.VPN
                }
            }
            else -> NetworkType.OTHER
        }

        val hasInternet = activeCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true ||
            wifiDetails.hasInternet || cellularDetails.hasInternet
        val isValidated = activeCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true ||
            (activeType == NetworkType.WIFI && wifiDetails.isValidated) ||
            (activeType == NetworkType.CELLULAR && cellularDetails.isValidated)

        val statusSummary = when {
            airplaneModeOn && !wifiDetails.isConnected -> "Airplane Mode enabled — radios offline"
            activeType == NetworkType.NONE -> "No active network connection"
            !isValidated && hasInternet -> "${activeType.label} connected — internet not validated"
            boundNetworkType != null -> "App bound to ${boundNetworkType!!.label}"
            else -> reason
        }

        val updated = NetworkSnapshot(
            timestampMs = System.currentTimeMillis(),
            activeNetworkType = activeType,
            boundProcessNetworkType = boundNetworkType,
            hasInternet = hasInternet,
            isInternetValidated = isValidated,
            isEthernetAvailable = ethernetAvailable,
            isVpnActive = vpnActive,
            isAirplaneModeOn = airplaneModeOn,
            wifi = wifiDetails,
            cellular = cellularDetails,
            statusMessage = statusSummary
        )
        _snapshot.value = updated
        if (previous.activeNetworkType != updated.activeNetworkType ||
            previous.isInternetValidated != updated.isInternetValidated ||
            previous.wifi.isConnected != updated.wifi.isConnected ||
            previous.cellular.isConnected != updated.cellular.isConnected
        ) {
            onConnectivityChangedListener?.invoke(previous, updated)
        }
        return updated
    }

    @Suppress("DEPRECATION")
    private fun inspectWifi(permissions: PermissionSummary): WifiDetails {
        val wm = wifiManager
        val wifiEnabled = try {
            wm?.isWifiEnabled == true
        } catch (_: Exception) {
            false
        }

        val net = wifiNetwork
        val caps = net?.let { capabilitiesMap[it] ?: connectivityManager.getNetworkCapabilities(it) }
            ?: connectivityManager.activeNetwork?.let { active ->
                val activeCaps = connectivityManager.getNetworkCapabilities(active)
                if (activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) activeCaps else null
            }

        val isConnected = caps != null
        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val isValidated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val isMetered = caps?.let { !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) } ?: false

        val wifiInfo: WifiInfo? = latestWifiInfoFromCallback
            ?: (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) caps?.transportInfo as? WifiInfo else null)
            ?: try {
                wm?.connectionInfo
            } catch (_: Exception) {
                null
            }

        val rawSsid = wifiInfo?.ssid?.trim('"')
        val validSsid = rawSsid?.takeIf {
            it.isNotBlank() && it != "<unknown ssid>" && !it.equals("0x", ignoreCase = true)
        }

        val ssidNote = when {
            !isConnected -> null
            validSsid != null -> null
            !permissions.fineLocationGranted ->
                "Location permission required by Android to read Wi-Fi SSID"
            else -> "SSID hidden or restricted by Android Location Services"
        }

        val rssi = wifiInfo?.rssi?.takeIf { it in -126..-1 && isConnected }
        val signalLevel = rssi?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
                try {
                    wm.calculateSignalLevel(it).coerceIn(0, 4)
                } catch (_: Exception) {
                    WifiManager.calculateSignalLevel(it, 5)
                }
            } else {
                WifiManager.calculateSignalLevel(it, 5)
            }
        }

        val freq = wifiInfo?.frequency?.takeIf { it > 0 && isConnected }
        val band = freq?.let {
            when {
                it in 2400..2500 -> "2.4 GHz"
                it in 4900..5900 -> "5 GHz"
                it in 5925..7125 -> "6 GHz"
                else -> "${it} MHz"
            }
        }

        val linkSpeed = wifiInfo?.linkSpeed?.takeIf { it > 0 && isConnected }
        val txSpeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiInfo?.txLinkSpeedMbps?.takeIf { it > 0 && isConnected }
        } else null
        val rxSpeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiInfo?.rxLinkSpeedMbps?.takeIf { it > 0 && isConnected }
        } else null

        return WifiDetails(
            isWifiEnabled = wifiEnabled,
            isAvailable = wifiEnabled && (net != null || isConnected),
            isConnected = isConnected,
            ssid = validSsid,
            ssidRestrictionNote = ssidNote,
            rssiDbm = rssi,
            signalLevel = signalLevel,
            frequencyMhz = freq,
            frequencyBand = band,
            linkSpeedMbps = linkSpeed,
            txLinkSpeedMbps = txSpeed,
            rxLinkSpeedMbps = rxSpeed,
            hasInternet = hasInternet,
            isValidated = isValidated,
            isMetered = isMetered,
            downstreamBandwidthKbps = caps?.linkDownstreamBandwidthKbps?.takeIf { it > 0 },
            upstreamBandwidthKbps = caps?.linkUpstreamBandwidthKbps?.takeIf { it > 0 }
        )
    }

    @SuppressLint("MissingPermission")
    private fun inspectCellular(
        permissions: PermissionSummary,
        airplaneModeOn: Boolean
    ): CellularDetails {
        val tm = telephonyManager
        val simState = try {
            tm?.simState ?: TelephonyManager.SIM_STATE_UNKNOWN
        } catch (_: Exception) {
            TelephonyManager.SIM_STATE_UNKNOWN
        }

        val simReady = simState == TelephonyManager.SIM_STATE_READY
        val simLabel = when (simState) {
            TelephonyManager.SIM_STATE_READY -> "Ready"
            TelephonyManager.SIM_STATE_ABSENT -> "No SIM Inserted"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN Locked"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK Locked"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network Locked"
            TelephonyManager.SIM_STATE_NOT_READY -> "Initializing"
            else -> "Unavailable"
        }

        val carrier = try {
            tm?.networkOperatorName?.takeIf { it.isNotBlank() }
                ?: tm?.simOperatorName?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }

        val net = cellularNetwork
        val caps = net?.let { capabilitiesMap[it] ?: connectivityManager.getNetworkCapabilities(it) }
            ?: connectivityManager.activeNetwork?.let { active ->
                val activeCaps = connectivityManager.getNetworkCapabilities(active)
                if (activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) activeCaps else null
            }

        val isConnected = caps != null && !airplaneModeOn
        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val isValidated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val isMetered = caps?.let { !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) } ?: true

        var permissionNote: String? = null
        val generation: String? = if (permissions.phoneStateGranted && tm != null && !airplaneModeOn) {
            try {
                mapNetworkGeneration(tm.dataNetworkType)
            } catch (_: SecurityException) {
                permissionNote = "Phone State permission required for radio generation"
                null
            } catch (_: Exception) {
                null
            }
        } else {
            if (!permissions.phoneStateGranted) {
                permissionNote = "Grant READ_PHONE_STATE for exact 2G/3G/4G/5G generation"
            }
            null
        }

        val signalObj: SignalStrength? = latestSignalStrength ?: try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) tm?.signalStrength else null
        } catch (_: Exception) {
            null
        }

        var dbm: Int? = null
        var level: Int? = null
        var rsrp: Int? = null
        var rsrq: Int? = null

        if (signalObj != null && !airplaneModeOn && simReady) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val strengths: List<CellSignalStrength> = signalObj.cellSignalStrengths
                val primary = strengths.firstOrNull { it.dbm in -140..-40 } ?: strengths.firstOrNull()
                if (primary != null) {
                    dbm = primary.dbm.takeIf { it in -145..-30 }
                    level = primary.level.coerceIn(0, 4)
                    when (primary) {
                        is CellSignalStrengthLte -> {
                            rsrp = primary.rsrp.takeIf { it in -140..-43 }
                            rsrq = primary.rsrq.takeIf { it in -34..3 }
                        }
                        is CellSignalStrengthNr -> {
                            rsrp = primary.ssRsrp.takeIf { it in -140..-43 }
                            rsrq = primary.ssRsrq.takeIf { it in -43..20 }
                        }
                    }
                }
            } else {
                level = signalObj.level.coerceIn(0, 4)
            }
        }

        val qualityLabel = when (level) {
            4 -> "Excellent"
            3 -> "Good"
            2 -> "Fair"
            1 -> "Weak"
            0 -> "Poor / None"
            else -> null
        }

        return CellularDetails(
            isAvailable = simReady && !airplaneModeOn && (net != null || isConnected || carrier != null),
            isConnected = isConnected,
            networkGeneration = generation,
            carrierName = carrier,
            signalStrengthDbm = dbm,
            signalLevel = level,
            signalQualityLabel = qualityLabel,
            rsrpDbm = rsrp,
            rsrqDb = rsrq,
            simStateLabel = simLabel,
            isSimReady = simReady,
            hasInternet = hasInternet,
            isValidated = isValidated,
            isMetered = isMetered,
            downstreamBandwidthKbps = caps?.linkDownstreamBandwidthKbps?.takeIf { it > 0 },
            upstreamBandwidthKbps = caps?.linkUpstreamBandwidthKbps?.takeIf { it > 0 },
            permissionNote = permissionNote
        )
    }

    @Suppress("DEPRECATION")
    private fun mapNetworkGeneration(networkType: Int): String? {
        return when (networkType) {
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN,
            TelephonyManager.NETWORK_TYPE_GSM -> "2G"

            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"

            TelephonyManager.NETWORK_TYPE_LTE,
            TelephonyManager.NETWORK_TYPE_IWLAN -> "4G LTE"

            TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> null
            else -> "Cellular ($networkType)"
        }
    }
}
