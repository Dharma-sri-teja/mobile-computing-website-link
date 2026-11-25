package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.NetworkMetricEntity
import com.example.data.local.SwitchingHistoryEntity
import com.example.model.AppSettings
import com.example.model.MonitoringState
import com.example.model.NetworkComparisonResult
import com.example.model.NetworkSnapshot
import com.example.model.NetworkType
import com.example.model.PerformanceMeasurement
import com.example.model.PredictionResult
import com.example.model.ReportPeriod
import com.example.ui.components.ChartPoint
import com.example.ui.components.EmptyTelemetryPlaceholder
import com.example.ui.components.KeyValueRow
import com.example.ui.components.LivePulseBadge
import com.example.ui.components.MetricKpiTile
import com.example.ui.components.QualityGaugeRing
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusChip
import com.example.ui.components.TelemetryCard
import com.example.ui.components.TelemetryLineChartCard
import com.example.ui.components.WifiVsCellularBarChartCard
import com.example.ui.components.formatDateTime
import com.example.ui.components.formatDbm
import com.example.ui.components.formatDurationHms
import com.example.ui.components.formatMbps
import com.example.ui.components.formatMs
import com.example.ui.components.formatPct
import com.example.ui.components.formatScore
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun PerformanceAnalyticsScreen(
    period: ReportPeriod,
    metrics: List<NetworkMetricEntity>,
    interruptionCount: Int,
    switchingCount: Int,
    onSelectPeriod: (ReportPeriod) -> Unit,
    onExportReport: () -> Unit
) {
    val sem = LocalTelemetryColors.current
    val ordered = metrics.sortedBy { it.createdAtMs }

    val avgDownload = ordered.mapNotNull { it.downloadSpeed }.takeIf { it.isNotEmpty() }?.average()
    val avgUpload = ordered.mapNotNull { it.uploadSpeed }.takeIf { it.isNotEmpty() }?.average()
    val avgLatency = ordered.mapNotNull { it.latency }.takeIf { it.isNotEmpty() }?.average()
    val avgJitter = ordered.mapNotNull { it.jitter }.takeIf { it.isNotEmpty() }?.average()
    val avgLoss = ordered.mapNotNull { it.packetLoss }.takeIf { it.isNotEmpty() }?.average()
    val avgQuality = ordered.mapNotNull { it.qualityScore }.takeIf { it.isNotEmpty() }?.average()

    val wifiRows = ordered.filter { it.networkType == NetworkType.WIFI.code }
    val cellRows = ordered.filter { it.networkType == NetworkType.CELLULAR.code }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(modifier = Modifier.widthIn(max = 680.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReportPeriod.entries.forEach { p ->
                            FilterChip(
                                selected = period == p,
                                onClick = { onSelectPeriod(p) },
                                label = { Text(p.label) },
                                modifier = Modifier.testTag("period_${p.name.lowercase()}")
                            )
                        }
                    }
                    Button(
                        onClick = onExportReport,
                        modifier = Modifier.testTag("analytics_export_report_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Report")
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier.widthIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricKpiTile(
                        label = "Avg Download",
                        value = formatMbps(avgDownload),
                        icon = Icons.Filled.ArrowDownward,
                        accentColor = sem.wifiAccent,
                        modifier = Modifier.weight(1f)
                    )
                    MetricKpiTile(
                        label = "Avg Upload",
                        value = formatMbps(avgUpload),
                        icon = Icons.Filled.ArrowUpward,
                        accentColor = sem.cellularAccent,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricKpiTile(
                        label = "Avg Latency",
                        value = formatMs(avgLatency),
                        icon = Icons.Filled.Timer,
                        accentColor = sem.good,
                        subtext = "Jitter: ${formatMs(avgJitter)}",
                        modifier = Modifier.weight(1f)
                    )
                    MetricKpiTile(
                        label = "Avg Probe Loss",
                        value = formatPct(avgLoss),
                        icon = Icons.Filled.ErrorOutline,
                        accentColor = sem.danger,
                        subtext = "$interruptionCount interruptions",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            TelemetryCard(modifier = Modifier.widthIn(max = 680.dp)) {
                Text("Period Summary (${period.label})", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                KeyValueRow("Total Real Samples", "${ordered.size}")
                KeyValueRow("Average Quality Score", formatScore(avgQuality), MaterialTheme.colorScheme.primary)
                KeyValueRow("Wi-Fi Samples", "${wifiRows.size}")
                KeyValueRow("Cellular Samples", "${cellRows.size}")
                KeyValueRow("Connection Interruptions", "$interruptionCount", if (interruptionCount > 0) sem.warning else sem.good)
                KeyValueRow("Total Switching Events", "$switchingCount")
            }
        }

        item {
            WifiVsCellularBarChartCard(
                title = "Historical Wi-Fi vs Cellular Averages",
                wifiScore = wifiRows.mapNotNull { it.qualityScore }.takeIf { it.isNotEmpty() }?.average(),
                cellularScore = cellRows.mapNotNull { it.qualityScore }.takeIf { it.isNotEmpty() }?.average(),
                wifiLatencyMs = wifiRows.mapNotNull { it.latency }.takeIf { it.isNotEmpty() }?.average(),
                cellularLatencyMs = cellRows.mapNotNull { it.latency }.takeIf { it.isNotEmpty() }?.average(),
                wifiDownloadMbps = wifiRows.mapNotNull { it.downloadSpeed }.takeIf { it.isNotEmpty() }?.average(),
                cellularDownloadMbps = cellRows.mapNotNull { it.downloadSpeed }.takeIf { it.isNotEmpty() }?.average(),
                modifier = Modifier.widthIn(max = 680.dp)
            )
        }

        item {
            Column(
                modifier = Modifier.widthIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TelemetryLineChartCard(
                    title = "Download Speed Trend",
                    unit = "Mbps",
                    points = ordered.mapNotNull { m -> m.downloadSpeed?.let { ChartPoint(m.createdAtMs, it) } }.takeLast(40),
                    lineColor = sem.wifiAccent,
                    emptyHint = "Run a Speed Test on Home to record real download throughput history."
                )
                TelemetryLineChartCard(
                    title = "Upload Speed Trend",
                    unit = "Mbps",
                    points = ordered.mapNotNull { m -> m.uploadSpeed?.let { ChartPoint(m.createdAtMs, it) } }.takeLast(40),
                    lineColor = sem.cellularAccent,
                    emptyHint = "Run a Speed Test on Home to record real upload throughput history."
                )
                TelemetryLineChartCard(
                    title = "Quality Score Trend",
                    unit = "pts",
                    points = ordered.mapNotNull { m -> m.qualityScore?.let { ChartPoint(m.createdAtMs, it) } }.takeLast(40),
                    lineColor = MaterialTheme.colorScheme.primary
                )
                TelemetryLineChartCard(
                    title = "Latency Trend",
                    unit = "ms",
                    points = ordered.mapNotNull { m -> m.latency?.let { ChartPoint(m.createdAtMs, it) } }.takeLast(40),
                    lineColor = sem.good
                )
                TelemetryLineChartCard(
                    title = "Signal Strength Trend",
                    unit = "dBm",
                    points = ordered.mapNotNull { m -> m.signalStrength?.let { ChartPoint(m.createdAtMs, it.toDouble()) } }.takeLast(40),
                    lineColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun PresentationModeScreen(
    snapshot: NetworkSnapshot,
    monitoringState: MonitoringState,
    elapsedSec: Long,
    activeMeasurement: PerformanceMeasurement?,
    comparison: NetworkComparisonResult?,
    prediction: PredictionResult,
    settings: AppSettings,
    switchingHistory: List<SwitchingHistoryEntity>,
    onStartDemo: () -> Unit,
    onStopDemo: () -> Unit
) {
    val sem = LocalTelemetryColors.current
    val isLive = monitoringState == MonitoringState.ACTIVE
    val activeScore = when (snapshot.activeNetworkType) {
        NetworkType.WIFI -> comparison?.wifiScore?.totalScore
        NetworkType.CELLULAR -> comparison?.cellularScore?.totalScore
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
        // CAPSTONE LIVE DEMO HEADER
        item {
            TelemetryCard(
                modifier = Modifier.widthIn(max = 720.dp),
                borderColor = if (isLive) sem.good else MaterialTheme.colorScheme.primary
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusChip(
                        text = "CAPSTONE PRESENTATION MODE",
                        color = MaterialTheme.colorScheme.primary
                    )
                    LivePulseBadge(state = monitoringState)
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.project_full_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                KeyValueRow("Device", "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
                KeyValueRow("Monitoring Duration", formatDurationHms(elapsedSec), MaterialTheme.colorScheme.primary)
                KeyValueRow("Last Measurement Timestamp", formatTimestamp(activeMeasurement?.timestampMs ?: snapshot.timestampMs))
                KeyValueRow("Data Authenticity", "100% Real Device Telemetry (Zero Simulation)", sem.good)

                Spacer(modifier = Modifier.height(14.dp))
                if (!isLive) {
                    Button(
                        onClick = onStartDemo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("start_live_demonstration_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = stringResource(R.string.action_start_demo),
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = onStopDemo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("stop_demonstration_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = sem.danger),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = stringResource(R.string.action_stop_demo),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // LIVE INTERFACE STATUS ROW
        item {
            Row(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryCard(
                    modifier = Modifier.weight(1f),
                    borderColor = sem.wifiAccent.copy(alpha = 0.6f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Wifi, contentDescription = null, tint = sem.wifiAccent)
                        Text("Wi-Fi", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    KeyValueRow("Connected", if (snapshot.wifi.isConnected) "YES" else "NO")
                    KeyValueRow("Validated", if (snapshot.wifi.isValidated) "YES" else "NO")
                    KeyValueRow("RSSI", formatDbm(snapshot.wifi.rssiDbm))
                    KeyValueRow("Score", formatScore(comparison?.wifiScore?.totalScore))
                }

                TelemetryCard(
                    modifier = Modifier.weight(1f),
                    borderColor = sem.cellularAccent.copy(alpha = 0.6f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.CellTower, contentDescription = null, tint = sem.cellularAccent)
                        Text("Cellular", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    KeyValueRow("Connected", if (snapshot.cellular.isConnected) "YES" else "NO")
                    KeyValueRow("Validated", if (snapshot.cellular.isValidated) "YES" else "NO")
                    KeyValueRow("Signal", formatDbm(snapshot.cellular.signalStrengthDbm))
                    KeyValueRow("Score", formatScore(comparison?.cellularScore?.totalScore))
                }
            }
        }

        // REAL-TIME MEASUREMENTS & SCORE
        item {
            TelemetryCard(modifier = Modifier.widthIn(max = 720.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    QualityGaugeRing(score = activeScore, label = "ACTIVE", size = 104.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        KeyValueRow("Active Network", snapshot.activeNetworkType.label, MaterialTheme.colorScheme.primary)
                        KeyValueRow("Download", formatMbps(activeMeasurement?.downloadSpeedMbps))
                        KeyValueRow("Upload", formatMbps(activeMeasurement?.uploadSpeedMbps))
                        KeyValueRow("Latency / Jitter", "${formatMs(activeMeasurement?.avgLatencyMs)} / ${formatMs(activeMeasurement?.jitterMs)}")
                        KeyValueRow("HTTP Probe Loss", formatPct(activeMeasurement?.probeLossPercent))
                    }
                }
            }
        }

        // PREDICTION & AUTOMATIC SWITCHING STATUS
        item {
            TelemetryCard(modifier = Modifier.widthIn(max = 720.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Live Prediction & Auto-Switch Decision", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                KeyValueRow("Auto Switching", if (settings.autoSwitchingEnabled) "ENABLED" else "DISABLED")
                KeyValueRow("Recommended Interface", comparison?.recommendedNetwork?.label ?: "NOT AVAILABLE", sem.good)
                KeyValueRow("Predicted Quality", formatScore(prediction.predictedQualityScore))
                KeyValueRow("Prediction Confidence", if (prediction.confidencePercent > 0) "${prediction.confidencePercent.toInt()}%" else "NOT AVAILABLE")
                KeyValueRow("Degradation Forecast", prediction.expectedDegradation)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = prediction.recommendedAction,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // LIVE SWITCHING HISTORY FEED
        item {
            SectionHeader(
                title = "Live Switching Decisions Feed",
                subtitle = "${switchingHistory.size} recorded switching events"
            )
        }

        if (switchingHistory.isEmpty()) {
            item {
                EmptyTelemetryPlaceholder(
                    title = "No Switching Events Yet",
                    subtitle = "Toggle Wi-Fi or degrade signal during live demo to observe real app-level switching events.",
                    modifier = Modifier.widthIn(max = 720.dp)
                )
            }
        } else {
            items(switchingHistory.take(8)) { item ->
                Surface(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, sem.cardBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${item.previousNetwork} → ${item.newNetwork} (${item.switchingMode})",
                                fontFamily = MonoFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = formatDateTime(item.createdAtMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = item.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
