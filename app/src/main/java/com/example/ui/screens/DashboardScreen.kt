package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PresentToAll
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.NetworkMetricEntity
import com.example.model.AppSettings
import com.example.model.DegradationRisk
import com.example.model.DualInterfaceSmaForecast
import com.example.model.MonitoringState
import com.example.model.NetworkComparisonResult
import com.example.model.NetworkSnapshot
import com.example.model.NetworkType
import com.example.model.PerformanceMeasurement
import com.example.model.PredictedQualityClass
import com.example.model.PredictionResult
import com.example.ui.components.ChartPoint
import com.example.ui.components.ExplainableScoringDashboardCard
import com.example.ui.components.KeyValueRow
import com.example.ui.components.LivePulseBadge
import com.example.ui.components.MetricKpiTile
import com.example.ui.components.QualityGaugeRing
import com.example.ui.components.SectionHeader
import com.example.ui.components.SmaSignalForecastCard
import com.example.ui.components.StatusChip
import com.example.ui.components.TelemetryCard
import com.example.ui.components.TelemetryLineChartCard
import com.example.ui.components.WifiVsCellularBarChartCard
import com.example.ui.components.formatDbm
import com.example.ui.components.formatDurationHms
import com.example.ui.components.formatMbps
import com.example.ui.components.formatMs
import com.example.ui.components.formatPct
import com.example.ui.components.formatScore
import com.example.ui.components.formatTimestamp
import com.example.ui.navigation.Routes
import com.example.ui.theme.LocalTelemetryColors

@Composable
fun DashboardScreen(
    snapshot: NetworkSnapshot,
    monitoringState: MonitoringState,
    elapsedSec: Long,
    isProbing: Boolean,
    isSpeedTesting: Boolean,
    activeMeasurement: PerformanceMeasurement?,
    wifiMeasurement: PerformanceMeasurement?,
    cellularMeasurement: PerformanceMeasurement?,
    comparison: NetworkComparisonResult?,
    prediction: PredictionResult,
    smaSignalForecast: DualInterfaceSmaForecast,
    smaShortWindow: Int,
    smaLongWindow: Int,
    settings: AppSettings,
    recentMetrics: List<NetworkMetricEntity>,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onProbeNow: () -> Unit,
    onRunSpeedTest: () -> Unit,
    onSelectSmaWindow: (Int, Int) -> Unit,
    onNavigate: (String) -> Unit
) {
    val sem = LocalTelemetryColors.current
    val orderedMetrics = recentMetrics.sortedBy { it.createdAtMs }
    val activeScore = when (snapshot.activeNetworkType) {
        NetworkType.WIFI -> comparison?.wifiScore?.totalScore
        NetworkType.CELLULAR -> comparison?.cellularScore?.totalScore
        else -> null
    }
    val activeSignalDbm = when (snapshot.activeNetworkType) {
        NetworkType.WIFI -> snapshot.wifi.rssiDbm
        NetworkType.CELLULAR -> snapshot.cellular.signalStrengthDbm
        else -> null
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. TOP SECTION: Project header, device status, monitoring & auto-switch status
        item {
            TelemetryCard(
                modifier = Modifier.widthIn(max = 680.dp),
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.04f)
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Predictive Network Switching",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LivePulseBadge(state = monitoringState)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusChip(
                                text = "Active: ${snapshot.activeNetworkType.label}",
                                color = if (snapshot.isInternetValidated) sem.good else sem.warning
                            )
                            StatusChip(
                                text = if (settings.autoSwitchingEnabled) "Auto-Switch: ON" else "Auto-Switch: OFF",
                                color = if (settings.autoSwitchingEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (monitoringState == MonitoringState.ACTIVE) {
                                StatusChip(
                                    text = formatDurationHms(elapsedSec),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Text(
                            text = "Device Status: ${snapshot.statusMessage} • Updated ${formatTimestamp(snapshot.timestampMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (monitoringState == MonitoringState.ACTIVE) {
                        Button(
                            onClick = onStopMonitoring,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dashboard_stop_monitoring"),
                            colors = ButtonDefaults.buttonColors(containerColor = sem.danger),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Stop Live")
                        }
                    } else {
                        Button(
                            onClick = onStartMonitoring,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dashboard_start_monitoring"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Start Live")
                        }
                    }

                    FilledTonalButton(
                        onClick = { onNavigate(Routes.PRESENTATION) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dashboard_presentation_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.PresentToAll, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Presentation")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onProbeNow,
                        enabled = !isProbing,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dashboard_probe_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isProbing && !isSpeedTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Probe Now")
                    }

                    OutlinedButton(
                        onClick = onRunSpeedTest,
                        enabled = !isProbing,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dashboard_speed_test_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSpeedTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Speed Test")
                    }
                }
            }
        }

        // 2. NETWORK CARDS: Wi-Fi, Cellular, Active Network, Signal Strength
        item {
            Column(
                modifier = Modifier.widthIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SectionHeader(
                    title = "Live Interfaces",
                    subtitle = "Tap Wi-Fi or Cellular for radio diagnostics"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TelemetryCard(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("wifi_summary_card"),
                        borderColor = if (snapshot.wifi.isConnected) sem.wifiAccent.copy(alpha = 0.6f) else sem.cardBorder,
                        onClick = { onNavigate(Routes.WIFI_DETAILS) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.Wifi, contentDescription = "Wi-Fi", tint = sem.wifiAccent)
                            Text("Wi-Fi", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        StatusChip(
                            text = when {
                                snapshot.wifi.isConnected && snapshot.wifi.isValidated -> "Validated"
                                snapshot.wifi.isConnected -> "No Internet"
                                snapshot.wifi.isWifiEnabled -> "Disconnected"
                                else -> "Radio Off"
                            },
                            color = when {
                                snapshot.wifi.isValidated -> sem.good
                                snapshot.wifi.isConnected -> sem.warning
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = snapshot.wifi.ssid ?: if (snapshot.wifi.isConnected) "SSID Restricted" else "NOT AVAILABLE",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "RSSI: ${formatDbm(snapshot.wifi.rssiDbm)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = sem.wifiAccent
                        )
                    }

                    TelemetryCard(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cellular_summary_card"),
                        borderColor = if (snapshot.cellular.isConnected) sem.cellularAccent.copy(alpha = 0.6f) else sem.cardBorder,
                        onClick = { onNavigate(Routes.CELLULAR_DETAILS) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.CellTower, contentDescription = "Cellular", tint = sem.cellularAccent)
                            Text("Cellular", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        StatusChip(
                            text = when {
                                snapshot.isAirplaneModeOn -> "Airplane Mode"
                                !snapshot.cellular.isSimReady -> snapshot.cellular.simStateLabel
                                snapshot.cellular.isConnected && snapshot.cellular.isValidated -> "Validated"
                                snapshot.cellular.isConnected -> "No Internet"
                                else -> "Standby / Off"
                            },
                            color = when {
                                snapshot.cellular.isValidated -> sem.good
                                snapshot.cellular.isConnected -> sem.warning
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = listOfNotNull(
                                snapshot.cellular.carrierName,
                                snapshot.cellular.networkGeneration
                            ).joinToString(" • ").ifBlank { "NOT AVAILABLE" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Signal: ${formatDbm(snapshot.cellular.signalStrengthDbm)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = sem.cellularAccent
                        )
                    }
                }
            }
        }

        // 3. PERFORMANCE CARDS: Download, Upload, Latency, Jitter, Packet Loss, Signal
        item {
            Column(
                modifier = Modifier.widthIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SectionHeader(
                    title = "Performance Telemetry",
                    subtitle = activeMeasurement?.let { "Last probe: ${formatTimestamp(it.timestampMs)}" }
                        ?: "Run Probe or Start Live to collect real measurements"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricKpiTile(
                        label = "Download",
                        value = formatMbps(activeMeasurement?.downloadSpeedMbps),
                        icon = Icons.Filled.ArrowDownward,
                        accentColor = sem.wifiAccent,
                        subtext = "Controlled HTTP payload",
                        modifier = Modifier.weight(1f)
                    )
                    MetricKpiTile(
                        label = "Upload",
                        value = formatMbps(activeMeasurement?.uploadSpeedMbps),
                        icon = Icons.Filled.ArrowUpward,
                        accentColor = sem.cellularAccent,
                        subtext = "Controlled HTTP POST",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricKpiTile(
                        label = "Latency",
                        value = formatMs(activeMeasurement?.avgLatencyMs),
                        icon = Icons.Filled.Timer,
                        accentColor = sem.good,
                        subtext = activeMeasurement?.let {
                            if (it.minLatencyMs != null && it.maxLatencyMs != null) {
                                "Min ${"%.0f".format(it.minLatencyMs)} / Max ${"%.0f".format(it.maxLatencyMs)} ms"
                            } else "HTTPS RTT"
                        } ?: "HTTPS RTT",
                        modifier = Modifier.weight(1f)
                    )
                    MetricKpiTile(
                        label = "Jitter",
                        value = formatMs(activeMeasurement?.jitterMs),
                        icon = Icons.Filled.GraphicEq,
                        accentColor = sem.warning,
                        subtext = "Consecutive RTT delta",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricKpiTile(
                        label = "Probe Loss",
                        value = formatPct(activeMeasurement?.probeLossPercent),
                        icon = Icons.Filled.ErrorOutline,
                        accentColor = sem.danger,
                        subtext = "HTTPS probe failure rate",
                        modifier = Modifier.weight(1f)
                    )
                    MetricKpiTile(
                        label = "Signal",
                        value = formatDbm(activeSignalDbm),
                        icon = Icons.Filled.SignalWifi4Bar,
                        accentColor = MaterialTheme.colorScheme.primary,
                        subtext = "Active ${snapshot.activeNetworkType.label}",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4. ANALYTICS SUMMARY: Quality Score, Best Network, Prediction, Stability
        item {
            TelemetryCard(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .testTag("dashboard_analytics_card"),
                onClick = { onNavigate(Routes.PREDICTIVE) }
            ) {
                SectionHeader(
                    title = "Intelligence & Prediction",
                    subtitle = "Explainable score & degradation forecast"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    QualityGaugeRing(
                        score = activeScore,
                        label = "QUALITY",
                        size = 104.dp
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val activeQualityClass = when {
                            prediction.predictedQualityClass != PredictedQualityClass.UNKNOWN -> prediction.predictedQualityClass
                            snapshot.activeNetworkType == NetworkType.WIFI -> comparison?.wifiScore?.predictedQualityClass ?: PredictedQualityClass.UNKNOWN
                            snapshot.activeNetworkType == NetworkType.CELLULAR -> comparison?.cellularScore?.predictedQualityClass ?: PredictedQualityClass.UNKNOWN
                            else -> PredictedQualityClass.UNKNOWN
                        }
                        val qualityClassColor = when (activeQualityClass) {
                            PredictedQualityClass.GOOD -> sem.good
                            PredictedQualityClass.AVERAGE -> sem.warning
                            PredictedQualityClass.POOR -> sem.danger
                            PredictedQualityClass.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        KeyValueRow(
                            label = "ML Predicted Quality",
                            value = activeQualityClass.label,
                            valueColor = qualityClassColor
                        )
                        KeyValueRow(
                            label = "Best Available",
                            value = comparison?.bestAvailableNetwork?.label ?: "NOT AVAILABLE",
                            valueColor = sem.good
                        )
                        KeyValueRow(
                            label = "Recommended",
                            value = comparison?.recommendedNetwork?.label ?: "NOT AVAILABLE",
                            valueColor = MaterialTheme.colorScheme.primary
                        )
                        KeyValueRow(
                            label = "Stability",
                            value = formatPct(activeMeasurement?.stabilityScore)
                        )
                        KeyValueRow(
                            label = "Risk Forecast",
                            value = prediction.degradationRisk.label,
                            valueColor = when (prediction.degradationRisk) {
                                DegradationRisk.CRITICAL, DegradationRisk.HIGH -> sem.danger
                                DegradationRisk.MODERATE -> sem.warning
                                DegradationRisk.LOW -> sem.good
                                DegradationRisk.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Psychology,
                        contentDescription = "Prediction",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = prediction.expectedDegradation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = comparison?.reason ?: "Awaiting connectivity evaluation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // 4B. EXPLAINABLE SCORING VISUALIZER: Interactive metric contribution dashboard
        item {
            ExplainableScoringDashboardCard(
                snapshot = snapshot,
                wifiMeasurement = wifiMeasurement,
                cellularMeasurement = cellularMeasurement,
                comparison = comparison,
                weights = settings.scoringWeights,
                onProbeNow = onProbeNow,
                onTuneWeights = { onNavigate(Routes.COMPARISON) },
                modifier = Modifier.widthIn(max = 680.dp)
            )
        }

        // 4C. SMA SIGNAL DEGRADATION FORECAST: Room-backed Simple Moving Average trends
        item {
            SmaSignalForecastCard(
                dualForecast = smaSignalForecast,
                activeNetworkType = snapshot.activeNetworkType,
                shortWindow = smaShortWindow,
                longWindow = smaLongWindow,
                onSelectWindow = onSelectSmaWindow,
                onProbeNow = onProbeNow,
                modifier = Modifier.widthIn(max = 680.dp)
            )
        }

        // 5. CHARTS: Download, Upload, Latency, Wi-Fi vs Cellular, Signal Strength History
        item {
            Column(
                modifier = Modifier.widthIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionHeader(
                    title = "Real-Time Telemetry Charts",
                    subtitle = "Plotted exclusively from timestamped device measurements"
                )

                TelemetryLineChartCard(
                    title = "Latency History",
                    unit = "ms",
                    points = orderedMetrics.mapNotNull { m -> m.latency?.let { ChartPoint(m.createdAtMs, it) } }.takeLast(30),
                    lineColor = sem.good
                )

                TelemetryLineChartCard(
                    title = "Download Speed History",
                    unit = "Mbps",
                    points = orderedMetrics.mapNotNull { m -> m.downloadSpeed?.let { ChartPoint(m.createdAtMs, it) } }.takeLast(30),
                    lineColor = sem.wifiAccent,
                    emptyHint = "Tap 'Speed Test' or wait for the configured speed test interval to plot real download speeds."
                )

                TelemetryLineChartCard(
                    title = "Upload Speed History",
                    unit = "Mbps",
                    points = orderedMetrics.mapNotNull { m -> m.uploadSpeed?.let { ChartPoint(m.createdAtMs, it) } }.takeLast(30),
                    lineColor = sem.cellularAccent,
                    emptyHint = "Tap 'Speed Test' or wait for the configured speed test interval to plot real upload speeds."
                )

                WifiVsCellularBarChartCard(
                    title = "Wi-Fi vs Cellular Comparison",
                    wifiScore = comparison?.wifiScore?.totalScore,
                    cellularScore = comparison?.cellularScore?.totalScore,
                    wifiLatencyMs = wifiMeasurement?.avgLatencyMs,
                    cellularLatencyMs = cellularMeasurement?.avgLatencyMs,
                    wifiDownloadMbps = wifiMeasurement?.downloadSpeedMbps,
                    cellularDownloadMbps = cellularMeasurement?.downloadSpeedMbps
                )

                TelemetryLineChartCard(
                    title = "Signal Strength History",
                    unit = "dBm",
                    points = orderedMetrics.mapNotNull { m -> m.signalStrength?.let { ChartPoint(m.createdAtMs, it.toDouble()) } }.takeLast(30),
                    lineColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
