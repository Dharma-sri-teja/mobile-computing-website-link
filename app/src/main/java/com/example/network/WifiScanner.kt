package com.example.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.model.NearbyWifiNetwork
import com.example.model.WifiScanState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

/**
 * Real-time nearby Wi-Fi network scanner using Android's WifiManager.scanResults
 * and SCAN_RESULTS_AVAILABLE_ACTION BroadcastReceiver.
 * Computes honest, theoretical PHY link-rate estimates from Wi-Fi standard,
 * channel width, and live RSSI (never fabricated speed tests).
 */
class WifiScanner(private val context: Context) {

    private val tag = "WifiScanner"
    private val appContext = context.applicationContext
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _scanState = MutableStateFlow(WifiScanState())
    val scanState: StateFlow<WifiScanState> = _scanState.asStateFlow()

    @Volatile
    private var receiverRegistered = false

    @Volatile
    private var currentConnectedSsid: String? = null

    @Volatile
    private var knownConnectedSsids: Set<String> = emptySet()

    private var onScanCompletedListener: ((List<NearbyWifiNetwork>) -> Unit)? = null

    fun setOnScanCompletedListener(listener: (List<NearbyWifiNetwork>) -> Unit) {
        onScanCompletedListener = listener
    }

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val success = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
            readAndPublishResults(
                noteOverride = if (success) {
                    null
                } else {
                    "Showing cached OS scan results (Android throttles foreground scans to 4 per 2 min)."
                }
            )
        }
    }

    fun registerReceiver() {
        if (receiverRegistered) return
        try {
            ContextCompat.registerReceiver(
                appContext,
                scanReceiver,
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        } catch (e: Exception) {
            Log.w(tag, "Unable to register Wi-Fi scan receiver", e)
        }
    }

    fun unregisterReceiver() {
        if (!receiverRegistered) return
        runCatching { appContext.unregisterReceiver(scanReceiver) }
        receiverRegistered = false
    }

    fun updateContextMetadata(connectedSsid: String?, previouslyConnectedSsids: Set<String>) {
        currentConnectedSsid = connectedSsid
        knownConnectedSsids = previouslyConnectedSsids
        // Refresh flags on already-displayed networks without triggering an extra scan
        _scanState.update { state ->
            if (state.networks.isEmpty()) state
            else state.copy(
                networks = state.networks.map { item ->
                    item.copy(
                        isCurrentlyConnected = connectedSsid != null &&
                            item.ssid.equals(connectedSsid, ignoreCase = false),
                        isPreviouslyConnected = knownConnectedSsids.contains(item.ssid)
                    )
                }
            )
        }
    }

    @Suppress("DEPRECATION")
    fun triggerScan(connectedSsid: String?, previouslyConnectedSsids: Set<String>) {
        currentConnectedSsid = connectedSsid
        knownConnectedSsids = previouslyConnectedSsids
        registerReceiver()

        val wm = wifiManager
        if (wm == null) {
            _scanState.update {
                it.copy(isScanning = false, statusNote = "Wi-Fi hardware service unavailable on this device.")
            }
            return
        }

        if (!hasLocationPermission()) {
            _scanState.update {
                it.copy(
                    isScanning = false,
                    statusNote = "Grant Fine Location permission (Onboarding / Permissions) to scan nearby Wi-Fi networks."
                )
            }
            return
        }

        if (!wm.isWifiEnabled) {
            _scanState.update {
                it.copy(
                    isScanning = false,
                    networks = emptyList(),
                    statusNote = "Wi-Fi radio is turned OFF. Enable Wi-Fi to scan nearby access points."
                )
            }
            return
        }

        val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { locationManager?.isLocationEnabled == true }.getOrDefault(true)
        } else true

        if (!locationEnabled) {
            _scanState.update {
                it.copy(
                    isScanning = false,
                    statusNote = "Android requires device Location Services to be enabled to return nearby Wi-Fi scan results."
                )
            }
            return
        }

        // First surface any immediately available OS-cached results while fresh scan starts
        readAndPublishResults(noteOverride = "Scanning nearby access points...", keepScanningFlag = true)

        val scanInitiated = try {
            wm.startScan()
        } catch (e: SecurityException) {
            Log.w(tag, "Wi-Fi scan permission denied", e)
            _scanState.update {
                it.copy(isScanning = false, statusNote = "Wi-Fi scan blocked by OS permission.")
            }
            return
        } catch (e: Exception) {
            Log.w(tag, "startScan failed", e)
            false
        }

        if (!scanInitiated) {
            readAndPublishResults(
                noteOverride = "Showing latest OS-cached scan results (Android limits active scans to 4 every 2 minutes)."
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun readAndPublishResults(noteOverride: String? = null, keepScanningFlag: Boolean = false) {
        val wm = wifiManager ?: return
        if (!hasLocationPermission()) return

        val rawResults: List<ScanResult> = try {
            wm.scanResults.orEmpty()
        } catch (e: SecurityException) {
            Log.w(tag, "Cannot read scanResults without location permission", e)
            emptyList()
        } catch (e: Exception) {
            Log.w(tag, "Error reading scanResults", e)
            emptyList()
        }

        val parsed = parseAndDeduplicate(
            results = rawResults,
            wm = wm,
            connectedSsid = currentConnectedSsid,
            knownConnectedSsids = knownConnectedSsids
        )

        val note = noteOverride ?: if (parsed.isEmpty()) {
            "No nearby Wi-Fi access points returned. Ensure Wi-Fi and device Location Services are ON."
        } else {
            "Found ${parsed.size} nearby Wi-Fi network(s). Speeds shown are theoretical PHY link estimates."
        }

        _scanState.value = WifiScanState(
            isScanning = keepScanningFlag,
            networks = parsed,
            lastScanTimestampMs = System.currentTimeMillis(),
            statusNote = note
        )

        if (!keepScanningFlag && parsed.isNotEmpty()) {
            onScanCompletedListener?.invoke(parsed)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        @Suppress("DEPRECATION")
        fun parseAndDeduplicate(
            results: List<ScanResult>,
            wm: WifiManager?,
            connectedSsid: String?,
            knownConnectedSsids: Set<String>
        ): List<NearbyWifiNetwork> {
            val now = System.currentTimeMillis()
            val mapped = results.mapNotNull { sr ->
                val rawSsid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    sr.wifiSsid?.toString()?.trim('"') ?: sr.SSID
                } else {
                    sr.SSID
                }?.trim().orEmpty()

                val displaySsid = rawSsid.ifBlank { "Hidden Network" }
                val bssid = sr.BSSID.orEmpty()
                val rssi = sr.level
                if (rssi !in -127..-1) return@mapNotNull null

                val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
                    runCatching { wm.calculateSignalLevel(rssi).coerceIn(0, 4) }
                        .getOrElse { WifiManager.calculateSignalLevel(rssi, 5) }
                } else {
                    WifiManager.calculateSignalLevel(rssi, 5)
                }

                val band = frequencyToBand(sr.frequency)
                val channelWidth = extractChannelWidthMhz(sr)
                val standard = extractWifiStandard(sr, band)
                val security = parseSecurityLabel(sr.capabilities.orEmpty())
                val estPhy = estimatePhySpeedMbps(
                    wifiStandard = standard,
                    channelWidthMhz = channelWidth,
                    rssiDbm = rssi,
                    frequencyBand = band
                )

                NearbyWifiNetwork(
                    ssid = displaySsid,
                    bssid = bssid,
                    rssiDbm = rssi,
                    signalLevel = level,
                    signalQualityLabel = signalLevelToLabel(level),
                    frequencyMhz = sr.frequency,
                    frequencyBand = band,
                    channelWidthMhz = channelWidth,
                    wifiStandard = standard,
                    securityLabel = security,
                    estimatedPhySpeedMbps = estPhy,
                    isCurrentlyConnected = connectedSsid != null &&
                        rawSsid.isNotBlank() &&
                        rawSsid == connectedSsid,
                    isPreviouslyConnected = rawSsid.isNotBlank() && knownConnectedSsids.contains(rawSsid),
                    lastSeenMs = now
                )
            }

            // Deduplicate multi-AP mesh networks by SSID (keeping strongest RSSI AP),
            // while keeping distinct Hidden Networks separated by BSSID.
            val grouped = mapped.groupBy { net ->
                if (net.ssid == "Hidden Network") "hidden_${net.bssid}" else net.ssid
            }

            return grouped.values
                .map { group -> group.maxBy { it.rssiDbm } }
                .sortedWith(
                    compareByDescending<NearbyWifiNetwork> { it.isCurrentlyConnected }
                        .thenByDescending { it.rssiDbm }
                )
        }

        fun frequencyToBand(freqMhz: Int): String = when {
            freqMhz in 2400..2500 -> "2.4 GHz"
            freqMhz in 4900..5900 -> "5 GHz"
            freqMhz in 5925..7125 -> "6 GHz"
            else -> "$freqMhz MHz"
        }

        fun signalLevelToLabel(level: Int): String = when (level) {
            4 -> "Excellent"
            3 -> "Good"
            2 -> "Fair"
            1 -> "Weak"
            else -> "Poor"
        }

        fun extractChannelWidthMhz(sr: ScanResult): Int {
            return when (sr.channelWidth) {
                ScanResult.CHANNEL_WIDTH_20MHZ -> 20
                ScanResult.CHANNEL_WIDTH_40MHZ -> 40
                ScanResult.CHANNEL_WIDTH_80MHZ -> 80
                ScanResult.CHANNEL_WIDTH_160MHZ,
                ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        sr.channelWidth == ScanResult.CHANNEL_WIDTH_320MHZ
                    ) {
                        320
                    } else {
                        20
                    }
                }
            }
        }

        fun extractWifiStandard(sr: ScanResult, band: String): String {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                when (sr.wifiStandard) {
                    ScanResult.WIFI_STANDARD_11BE -> return "Wi-Fi 7 (802.11be)"
                    ScanResult.WIFI_STANDARD_11AX -> return if (band == "6 GHz") "Wi-Fi 6E (802.11ax)" else "Wi-Fi 6 (802.11ax)"
                    ScanResult.WIFI_STANDARD_11AC -> return "Wi-Fi 5 (802.11ac)"
                    ScanResult.WIFI_STANDARD_11N -> return "Wi-Fi 4 (802.11n)"
                    ScanResult.WIFI_STANDARD_LEGACY -> return "Legacy (802.11a/b/g)"
                }
            }
            return when {
                band == "6 GHz" -> "Wi-Fi 6E (802.11ax)"
                band == "5 GHz" && sr.channelWidth >= ScanResult.CHANNEL_WIDTH_80MHZ -> "Wi-Fi 5 (802.11ac)"
                else -> "Wi-Fi 4 (802.11n)"
            }
        }

        fun parseSecurityLabel(capabilities: String): String {
            val upper = capabilities.uppercase()
            val hasWpa3 = upper.contains("SAE") || upper.contains("WPA3")
            val hasWpa2 = upper.contains("WPA2") ||
                (upper.contains("RSN") && (upper.contains("PSK") || upper.contains("EAP") || !hasWpa3))
            val hasWpa = upper.contains("WPA-") || upper.contains("[WPA]")
            val hasWep = upper.contains("WEP")
            val hasOwe = upper.contains("OWE")
            return when {
                hasWpa3 && hasWpa2 -> "WPA2/WPA3"
                hasWpa3 -> "WPA3"
                hasWpa2 -> "WPA2"
                hasWpa -> "WPA"
                hasWep -> "WEP"
                hasOwe -> "Enhanced Open"
                else -> "Open"
            }
        }

        /**
         * Estimates realistic single/dual-stream PHY link speed (Mbps) based on
         * Wi-Fi generation, channel width, and live RSSI modulation attenuation.
         * Explicitly presented in UI as a PHY estimate rather than a measured speed test.
         */
        fun estimatePhySpeedMbps(
            wifiStandard: String,
            channelWidthMhz: Int,
            rssiDbm: Int,
            frequencyBand: String
        ): Int {
            // Typical smartphone 2x2 MIMO peak PHY rate (Mbps) by standard & width
            val peakPhyMbps = when {
                wifiStandard.contains("Wi-Fi 7") -> when (channelWidthMhz) {
                    320 -> 2880.0
                    160 -> 1440.0
                    80 -> 720.0
                    40 -> 360.0
                    else -> 180.0
                }
                wifiStandard.contains("Wi-Fi 6") -> when (channelWidthMhz) {
                    160 -> 1201.0
                    80 -> 600.0
                    40 -> 286.0
                    else -> 143.0
                }
                wifiStandard.contains("Wi-Fi 5") -> when (channelWidthMhz) {
                    160 -> 866.0
                    80 -> 433.0
                    40 -> 200.0
                    else -> 86.0
                }
                wifiStandard.contains("Wi-Fi 4") -> when {
                    channelWidthMhz >= 40 && frequencyBand != "2.4 GHz" -> 150.0
                    channelWidthMhz >= 40 -> 130.0
                    else -> 72.0
                }
                else -> 54.0
            }

            // Signal-to-MCS attenuation factor based on live RSSI in dBm
            val rssiFactor = when {
                rssiDbm >= -55 -> 0.85
                rssiDbm >= -65 -> 0.68
                rssiDbm >= -72 -> 0.48
                rssiDbm >= -80 -> 0.28
                rssiDbm >= -88 -> 0.14
                else -> 0.06
            }

            return (peakPhyMbps * rssiFactor).roundToInt().coerceAtLeast(1)
        }
    }
}
