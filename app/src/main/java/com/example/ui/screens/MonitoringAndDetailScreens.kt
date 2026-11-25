package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ConnectionEventEntity
import com.example.data.local.NetworkMetricEntity
import com.example.model.MonitoringState
import com.example.model.NetworkSnapshot
import com.example.model.NetworkType
import com.example.model.PerformanceMeasurement
import com.example.ui.components.ChartPoint
import com.example.ui.components.EmptyTelemetryPlaceholder
import com.example.ui.components.KeyValueRow
import com.example.ui.components.LivePulseBadge
import com.example.ui.components.PlatformDisclosureBanner
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusChip
import com.example.ui.components.TelemetryCard
import com.example.ui.components.TelemetryLineChartCard
import com.example.ui.components.formatDateTime
import com.example.ui.components.formatDbm
import com.example.ui.components.formatDurationHms
import com.example.ui.components.formatMbps
import com.example.ui.components.formatMs
import com.example.ui.components.formatPct
import com.example.ui.components.formatTimestamp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material3.CircularProgressIndicator
import com.example.data.local.KnownWifiNetworkEntity
import com.example.model.NearbyWifiNetwork
import com.example.model.WifiScanState
import com.example.network.NetworkAvailabilityState
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun NetworkMonitoringScreen(
    snapshot: NetworkSnapshot,
    networkAvailability: NetworkAvailabilityState = NetworkAvailabilityState(),
    monitoringState: MonitoringState,
    elapsedSec: Long,
    isProbing: Boolean,
    activeMeasurement: PerformanceMeasurement?,
    events: List<ConnectionEventEntity>,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onProbeNow: () -> Unit,
    onSpeedTestNow: () -> Unit
) {
    val sem = LocalTelemetryColors.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            TelemetryCard(modifier = Modifier.widthIn(max = 680.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Foreground Service Controller", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Duration: ${formatDurationHms(elapsedSec)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LivePulseBadge(state = monitoringState)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (monitoringState) {
                        MonitoringState.STOPPED -> {
                            Button(
                                onClick = onStart,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("monitoring_start_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("Start")
                            }
                        }
                        MonitoringState.ACTIVE -> {
                            FilledTonalButton(
                                onClick = onPause,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("monitoring_pause_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("Pause")
                            }
                            Button(
                                onClick = onStop,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("monitoring_stop_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = sem.danger),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("Stop")
                            }
                        }
                        MonitoringState.PAUSED -> {
                            Button(
                                onClick = onResume,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("monitoring_resume_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("Resume")
                            }
                            Button(
                                onClick = onStop,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("monitoring_stop_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = sem.danger),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("Stop")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onProbeNow,
                        enabled = !isProbing,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("monitoring_probe_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Run Latency Probe")
                    }
                    OutlinedButton(
                        onClick = onSpeedTestNow,
                        enabled = !isProbing,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("monitoring_speed_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Run Speed Test")
                    }
                }
            }
        }

        item {
            TelemetryCard(modifier = Modifier.widthIn(max = 680.dp)) {
                Text("Real-Time Callback Flow (NetworkMonitorService)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusChip(
                        text = if (networkAvailability.isWifiAvailable) {
                            if (networkAvailability.isWifiValidated) "Wi-Fi: Available (Validated)" else "Wi-Fi: Available"
                        } else "Wi-Fi: Unavailable",
                        color = if (networkAvailability.isWifiValidated) sem.good else if (networkAvailability.isWifiAvailable) sem.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    StatusChip(
                        text = if (networkAvailability.isCellularAvailable) {
                            if (networkAvailability.isCellularValidated) "Cellular: Available (Validated)" else "Cellular: Available"
                        } else "Cellular: Unavailable",
                        color = if (networkAvailability.isCellularValidated) sem.good else if (networkAvailability.isCellularAvailable) sem.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                KeyValueRow("Callback Active Transport", networkAvailability.activeTransport.label, MaterialTheme.colorScheme.primary)
                KeyValueRow("Callback Updated", formatTimestamp(networkAvailability.lastUpdatedMs))
            }
        }

        item {
            TelemetryCard(modifier = Modifier.widthIn(max = 680.dp)) {
                Text("ConnectivityManager Live State", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                KeyValueRow("Active Network Type", snapshot.activeNetworkType.label, MaterialTheme.colorScheme.primary)
                KeyValueRow(
                    "App Process Binding",
                    snapshot.boundProcessNetworkType?.label ?: "System Default"
                )
                KeyValueRow(
                    "Internet Capability",
                    if (snapshot.hasInternet) "YES" else "NO",
                    if (snapshot.hasInternet) sem.good else sem.danger
                )
                KeyValueRow(
                    "Internet Validated",
                    if (snapshot.isInternetValidated) "VALIDATED" else "NOT VALIDATED",
                    if (snapshot.isInternetValidated) sem.good else sem.warning
                )
                KeyValueRow("Wi-Fi Connected", if (snapshot.wifi.isConnected) "YES" else "NO")
                KeyValueRow("Cellular Connected", if (snapshot.cellular.isConnected) "YES" else "NO")
                KeyValueRow("Ethernet Available", if (snapshot.isEthernetAvailable) "YES" else "NO")
                KeyValueRow("VPN Active", if (snapshot.isVpnActive) "ACTIVE" else "INACTIVE")
                KeyValueRow("Airplane Mode", if (snapshot.isAirplaneModeOn) "ON" else "OFF")
                KeyValueRow("Snapshot Timestamp", formatTimestamp(snapshot.timestampMs))
            }
        }

        item {
            TelemetryCard(modifier = Modifier.widthIn(max = 680.dp)) {
                Text("Latest Real Probe Breakdown", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                KeyValueRow("Avg Latency", formatMs(activeMeasurement?.avgLatencyMs))
                KeyValueRow("Min / Max Latency", if (activeMeasurement?.minLatencyMs != null && activeMeasurement.maxLatencyMs != null) {
                    "${"%.1f".format(activeMeasurement.minLatencyMs)} / ${"%.1f".format(activeMeasurement.maxLatencyMs)} ms"
                } else "NOT AVAILABLE")
                KeyValueRow("Response Time (Last)", formatMs(activeMeasurement?.responseTimeMs))
                KeyValueRow("Jitter", formatMs(activeMeasurement?.jitterMs))
                KeyValueRow("HTTP Probe Failure Rate", formatPct(activeMeasurement?.probeLossPercent))
                KeyValueRow(
                    "Probes Succeeded",
                    activeMeasurement?.let { "${it.probesSucceeded} / ${it.probesAttempted}" } ?: "NOT AVAILABLE"
                )
                KeyValueRow("Download Throughput", formatMbps(activeMeasurement?.downloadSpeedMbps))
                KeyValueRow("Upload Throughput", formatMbps(activeMeasurement?.uploadSpeedMbps))
                KeyValueRow("Connection Stability", formatPct(activeMeasurement?.stabilityScore))
                KeyValueRow("Methodology", activeMeasurement?.probeMethodology ?: "HTTPS Timing Probe (Non-ICMP)")
                if (activeMeasurement?.errorNote != null) {
                    Text(
                        text = "Diagnostic Note: ${activeMeasurement.errorNote}",
                        style = MaterialTheme.typography.bodySmall,
                        color = sem.warning
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = "Connection Events & Interruptions",
                subtitle = "${events.size} logged real connectivity transitions",
                trailing = null
            )
        }

        if (events.isEmpty()) {
            item {
                EmptyTelemetryPlaceholder(
                    title = "No Connectivity Interruptions Logged",
                    subtitle = "Network losses, restorations, and service transitions will appear here in real time.",
                    modifier = Modifier.widthIn(max = 680.dp)
                )
            }
        } else {
            items(events.take(25)) { event ->
                TelemetryCard(modifier = Modifier.widthIn(max = 680.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(event.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        StatusChip(
                            text = event.eventType,
                            color = when (event.eventType) {
                                "DISCONNECTED", "SWITCH_FAILED", "ERROR" -> sem.danger
                                "RESTORED" -> sem.good
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(event.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = formatDateTime(event.createdAtMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun WifiDetailsScreen(
    snapshot: NetworkSnapshot,
    wifiMeasurement: PerformanceMeasurement?,
    wifiHistory: List<NetworkMetricEntity>,
    wifiScanState: WifiScanState = WifiScanState(),
    knownWifiNetworks: List<KnownWifiNetworkEntity> = emptyList(),
    isProbing: Boolean,
    onProbeWifi: (Boolean) -> Unit,
    onScanNearbyWifi: () -> Unit = {}
) {
    val sem = LocalTelemetryColors.current
    val wifi = snapshot.wifi
    val ordered = wifiHistory.sortedBy { it.createdAtMs }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            TelemetryCard(
                modifier = Modifier.widthIn(max = 680.dp),
                borderColor = sem.wifiAccent.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.Wifi, contentDescription = "Wi-Fi", tint = sem.wifiAccent, modifier = Modifier.size(28.dp))
                        Column {
                            Text(
                                text = wifi.ssid ?: if (wifi.isConnected) "Connected Wi-Fi (SSID Restricted)" else "Wi-Fi Disconnected",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = wifi.frequencyBand ?: "Band unavailable",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    StatusChip(
                        text = if (wifi.isValidated) "VALIDATED" else if (wifi.isConnected) "NO INTERNET" else "OFFLINE",
                        color = if (wifi.isValidated) sem.good else if (wifi.isConnected) sem.warning else sem.danger
                    )
                }
                if (wifi.ssidRestrictionNote != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = wifi.ssidRestrictionNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = sem.warning
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onProbeWifi(false) },
                        enabled = !isProbing && wifi.isConnected,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("wifi_probe_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Probe Wi-Fi Latency")
                    }
                    OutlinedButton(
                        onClick = { onProbeWifi(true) },
                        enabled = !isProbing && wifi.isConnected,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("wifi_speed_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Wi-Fi Speed Test")
                    }
                }
            }
        }

        item {
            TelemetryCard(modifier = Modifier.widthIn(max = 680.dp)) {
                Text("Radio & Link Diagnostics", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                KeyValueRow("Wi-Fi Radio Enabled", if (wifi.isWifiEnabled) "YES" else "NO")
                KeyValueRow("Wi-Fi Available", if (wifi.isAvailable) "YES" else "NO")
                KeyValueRow("Wi-Fi Connected", if (wifi.isConnected) "YES" else "NO")
                KeyValueRow("SSID", wifi.ssid ?: "NOT AVAILABLE")
                KeyValueRow("Signal Strength (RSSI)", formatDbm(wifi.rssiDbm), sem.wifiAccent)
                KeyValueRow("Signal Level (0-4)", wifi.signalLevel?.let { "$it / 4" } ?: "NOT AVAILABLE")
                KeyValueRow("Frequency", wifi.frequencyMhz?.let { "$it MHz (${wifi.frequencyBand})" } ?: "NOT AVAILABLE")
                KeyValueRow("Link Speed", wifi.linkSpeedMbps?.let { "$it Mbps" } ?: "NOT AVAILABLE")
                KeyValueRow("TX / RX Link Speed", if (wifi.txLinkSpeedMbps != null || wifi.rxLinkSpeedMbps != null) {
                    "${wifi.txLinkSpeedMbps ?: "--"} / ${wifi.rxLinkSpeedMbps ?: "--"} Mbps"
                } else "NOT AVAILABLE")
                KeyValueRow("Internet Capability", if (wifi.hasInternet) "YES" else "NO")
                KeyValueRow("Internet Validated", if (wifi.isValidated) "YES" else "NO")
                KeyValueRow("Metered Network", if (wifi.isMetered) "YES" else "NO")
            }
        }

        item {
            TelemetryCard(modifier = Modifier.widthIn(max = 680.dp)) {
                Text("Measured Wi-Fi Performance", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                KeyValueRow("Download Speed", formatMbps(wifiMeasurement?.downloadSpeedMbps))
                KeyValueRow("Upload Speed", formatMbps(wifiMeasurement?.uploadSpeedMbps))
                KeyValueRow("Average Latency", formatMs(wifiMeasurement?.avgLatencyMs))
                KeyValueRow("Jitter", formatMs(wifiMeasurement?.jitterMs))
                KeyValueRow("HTTP Probe Loss", formatPct(wifiMeasurement?.probeLossPercent))
                KeyValueRow("Stability Score", formatPct(wifiMeasurement?.stabilityScore))
                KeyValueRow("Measured At", formatTimestamp(wifiMeasurement?.timestampMs))
            }
        }

        item {
            TelemetryLineChartCard(
                title = "Wi-Fi Signal Strength History",
                unit = "dBm",
                points = ordered.mapNotNull { m -> m.signalStrength?.let { ChartPoint(m.createdAtMs, it.toDouble()) } }.takeLast(30),
                lineColor = sem.wifiAccent,
                modifier = Modifier.widthIn(max = 680.dp)
            )
        }

        // NEARBY AVAILABLE WI-FI SCANNER
        item {
            TelemetryCard(
                modifier = Modifier.widthIn(max = 680.dp),
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Filled.Radar,
                            contentDescription = "Nearby Wi-Fi Scanner",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text("Nearby Available Wi-Fi", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = wifiScanState.lastScanTimestampMs?.let {
                                    "Last scan: ${formatTimestamp(it)} • ${wifiScanState.networks.size} found"
                                } ?: "Tap Scan Now to discover nearby APs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Button(
                        onClick = onScanNearbyWifi,
                        enabled = !wifiScanState.isScanning,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("wifi_scan_nearby_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (wifiScanState.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Scanning")
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Scan Now")
                        }
                    }
                }

                if (wifiScanState.statusNote != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = wifiScanState.statusNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (wifiScanState.networks.isEmpty()) {
            item {
                EmptyTelemetryPlaceholder(
                    title = "No Nearby Wi-Fi Scan Results Yet",
                    subtitle = "Tap 'Scan Now' with Location permission and device Location Services enabled to list nearby access points, live RSSI, band, Wi-Fi standard, and PHY link estimates.",
                    modifier = Modifier.widthIn(max = 680.dp)
                )
            }
        } else {
            items(wifiScanState.networks, key = { "${it.ssid}_${it.bssid}" }) { ap ->
                NearbyWifiAccessPointCard(ap = ap, modifier = Modifier.widthIn(max = 680.dp))
            }
        }

        // PREVIOUSLY SEEN & CONNECTED NETWORK HISTORY (Room)
        item {
            SectionHeader(
                title = "Previously Connected & Seen Wi-Fi",
                subtitle = "Tracked locally in Room (Android 10+ restricts reading system saved Wi-Fi lists)"
            )
        }

        if (knownWifiNetworks.isEmpty()) {
            item {
                EmptyTelemetryPlaceholder(
                    title = "No Wi-Fi History Recorded Yet",
                    subtitle = "Networks you connect to or discover during scans will appear here with best RSSI, standard, and last measured speed.",
                    modifier = Modifier.widthIn(max = 680.dp)
                )
            }
        } else {
            items(knownWifiNetworks, key = { it.ssid }) { known ->
                TelemetryCard(
                    modifier = Modifier.widthIn(max = 680.dp),
                    borderColor = if (known.hasBeenConnected) sem.good.copy(alpha = 0.45f) else sem.cardBorder
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = known.ssid,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = listOfNotNull(
                                    known.frequencyBand,
                                    known.wifiStandard,
                                    known.securityLabel
                                ).joinToString(" • ").ifBlank { "Details pending scan" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusChip(
                            text = if (known.hasBeenConnected) {
                                "USED (${known.timesConnected}x)"
                            } else "SEEN",
                            color = if (known.hasBeenConnected) sem.good else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    KeyValueRow(
                        "Last / Best RSSI",
                        "${formatDbm(known.lastRssiDbm)} / ${formatDbm(known.bestRssiDbm)}",
                        sem.wifiAccent
                    )
                    KeyValueRow(
                        "Est. PHY Link",
                        known.estimatedPhySpeedMbps?.let { "~$it Mbps (PHY est.)" } ?: "NOT AVAILABLE"
                    )
                    if (known.lastMeasuredDownloadMbps != null || known.lastMeasuredLatencyMs != null) {
                        KeyValueRow(
                            "Last Measured Speed / RTT",
                            "${formatMbps(known.lastMeasuredDownloadMbps)} • ${formatMs(known.lastMeasuredLatencyMs)}",
                            sem.good
                        )
                    }
                    KeyValueRow("Last Seen", formatDateTime(known.lastSeenMs))
                }
            }
        }

        item {
            PlatformDisclosureBanner(
                title = "About Nearby Wi-Fi Speeds & Saved Networks",
                body = "• PHY Speed Estimates: Unconnected networks cannot run live download tests, so NetSwitch AI calculates a theoretical PHY link estimate from Wi-Fi standard (Wi-Fi 4/5/6/6E/7), channel width, and live RSSI.\n• Android 10+ Saved Networks: Android blocks third-party apps from reading the OS saved Wi-Fi list; instead, NetSwitch AI automatically remembers Wi-Fi networks you connect to or scan while using the app.\n• Scan Throttling: Android 9+ restricts foreground Wi-Fi scans to 4 every 2 minutes.",
                modifier = Modifier.widthIn(max = 680.dp)
            )
        }
    }
}

@Composable
private fun NearbyWifiAccessPointCard(
    ap: NearbyWifiNetwork,
    modifier: Modifier = Modifier
) {
    val sem = LocalTelemetryColors.current
    val signalColor = when {
        ap.signalLevel >= 3 -> sem.good
        ap.signalLevel == 2 -> sem.warning
        else -> sem.danger
    }

    TelemetryCard(
        modifier = modifier,
        borderColor = if (ap.isCurrentlyConnected) sem.wifiAccent else sem.cardBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.SignalWifi4Bar,
                    contentDescription = "Signal ${ap.signalQualityLabel}",
                    tint = signalColor,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = ap.ssid,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${ap.frequencyBand} (${ap.frequencyMhz} MHz) • ${ap.channelWidthMhz} MHz • ${ap.wifiStandard}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${ap.rssiDbm} dBm",
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = signalColor
                )
                Text(
                    text = ap.signalQualityLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = signalColor
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (ap.isCurrentlyConnected) {
                StatusChip(text = "CONNECTED", color = sem.good)
            } else if (ap.isPreviouslyConnected) {
                StatusChip(text = "PREVIOUSLY USED", color = MaterialTheme.colorScheme.primary)
            }
            StatusChip(
                text = "~${ap.estimatedPhySpeedMbps} Mbps PHY est.",
                color = sem.wifiAccent
            )
            StatusChip(
                text = ap.securityLabel,
                color = if (ap.securityLabel == "Open") sem.warning else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = if (ap.securityLabel == "Open") Icons.Filled.LockOpen else Icons.Filled.Lock,
                contentDescription = ap.securityLabel,
                tint = if (ap.securityLabel == "Open") sem.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
fun CellularDetailsScreen(
    snapshot: NetworkSnapshot,
    cellularMeasurement: PerformanceMeasurement?,
    cellularHistory: List<NetworkMetricEntity>,
    isProbing: Boolean,
    onProbeCellular: (Boolean) -> Unit
) {
    val sem = LocalTelemetryColors.current
    val cell = snapshot.cellular
    val ordered = cellularHistory.sortedBy { it.createdAtMs }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            TelemetryCard(
                modifier = Modifier.widthIn(max = 680.dp),
                borderColor = sem.cellularAccent.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.CellTower, contentDescription = "Cellular", tint = sem.cellularAccent, modifier = Modifier.size(28.dp))
                        Column {
                            Text(
                                text = cell.carrierName ?: "Cellular Radio",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = cell.networkGeneration ?: "Generation unavailable",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    StatusChip(
                        text = when {
                            snapshot.isAirplaneModeOn -> "AIRPLANE MODE"
                            cell.isValidated -> "VALIDATED"
                            cell.isConnected -> "NO INTERNET"
                            else -> "UNAVAILABLE"
                        },
                        color = if (cell.isValidated) sem.good else if (cell.isConnected) sem.warning else sem.danger
                    )
                }

                if (cell.permissionNote != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = cell.permissionNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = sem.warning
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onProbeCellular(false) },
                        enabled = !isProbing && cell.isConnected,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("cellular_probe_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Probe Cellular Latency")
                    }
                    OutlinedButton(
                        onClick = { onProbeCellular(true) },
                        enabled = !isProbing && cell.isConnected,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("cellular_speed_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cellular Speed Test")
                    }
                }
            }
        }

        item {
            TelemetryCard(modifier = Modifier.widthIn(max = 680.dp)) {
                Text("Telephony & Signal Diagnostics", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                KeyValueRow("Cellular Available", if (cell.isAvailable) "YES" else "NO")
                KeyValueRow("Mobile Data Connected", if (cell.isConnected) "YES" else "NO")
                KeyValueRow("Network Generation", cell.networkGeneration ?: "NOT AVAILABLE", sem.cellularAccent)
                KeyValueRow("Carrier Name", cell.carrierName ?: "NOT AVAILABLE")
                KeyValueRow("SIM Status", cell.simStateLabel)
                KeyValueRow("Signal Strength (dBm)", formatDbm(cell.signalStrengthDbm), sem.cellularAccent)
                KeyValueRow("Signal Quality", cell.signalQualityLabel ?: "NOT AVAILABLE")
                KeyValueRow("LTE/5G RSRP", cell.rsrpDbm?.let { "$it dBm" } ?: "NOT AVAILABLE")
                KeyValueRow("LTE/5G RSRQ", cell.rsrqDb?.let { "$it dB" } ?: "NOT AVAILABLE")
                KeyValueRow("Internet Capability", if (cell.hasInternet) "YES" else "NO")
                KeyValueRow("Internet Validated", if (cell.isValidated) "YES" else "NO")
                KeyValueRow("Metered Connection", if (cell.isMetered) "YES" else "NO")
            }
        }

        item {
            TelemetryCard(modifier = Modifier.widthIn(max = 680.dp)) {
                Text("Measured Cellular Performance", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                KeyValueRow("Download Speed", formatMbps(cellularMeasurement?.downloadSpeedMbps))
                KeyValueRow("Upload Speed", formatMbps(cellularMeasurement?.uploadSpeedMbps))
                KeyValueRow("Average Latency", formatMs(cellularMeasurement?.avgLatencyMs))
                KeyValueRow("Jitter", formatMs(cellularMeasurement?.jitterMs))
                KeyValueRow("HTTP Probe Loss", formatPct(cellularMeasurement?.probeLossPercent))
                KeyValueRow("Stability Score", formatPct(cellularMeasurement?.stabilityScore))
                KeyValueRow("Measured At", formatTimestamp(cellularMeasurement?.timestampMs))
            }
        }

        item {
            TelemetryLineChartCard(
                title = "Cellular Signal Strength History",
                unit = "dBm",
                points = ordered.mapNotNull { m -> m.signalStrength?.let { ChartPoint(m.createdAtMs, it.toDouble()) } }.takeLast(30),
                lineColor = sem.cellularAccent,
                modifier = Modifier.widthIn(max = 680.dp)
            )
        }

        item {
            PlatformDisclosureBanner(
                title = "Note on Simultaneous Wi-Fi + Cellular Probing",
                body = "When Wi-Fi is active, some Android devices keep the cellular data radio in standby until requested via ConnectivityManager.requestNetwork(). Use Intelligent Switching to bring up cellular for direct probing.",
                modifier = Modifier.widthIn(max = 680.dp)
            )
        }
    }
}
