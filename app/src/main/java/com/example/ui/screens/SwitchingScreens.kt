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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.NetworkMetricEntity
import com.example.data.local.SwitchingHistoryEntity
import com.example.model.AppSettings
import com.example.model.NetworkComparisonResult
import com.example.model.NetworkSnapshot
import com.example.model.NetworkType
import com.example.model.SwitchDecisionResult
import com.example.network.AutoSwitchEvaluation
import com.example.ui.components.EmptyTelemetryPlaceholder
import com.example.ui.components.KeyValueRow
import com.example.ui.components.PlatformDisclosureBanner
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusChip
import com.example.ui.components.TelemetryCard
import com.example.ui.components.formatDateTime
import com.example.ui.components.formatDbm
import com.example.ui.components.formatMbps
import com.example.ui.components.formatMs
import com.example.ui.components.formatPct
import com.example.ui.components.formatScore
import com.example.ui.theme.LocalTelemetryColors

@Composable
fun IntelligentSwitchingScreen(
    snapshot: NetworkSnapshot,
    comparison: NetworkComparisonResult?,
    settings: AppSettings,
    autoEvaluation: AutoSwitchEvaluation?,
    cooldownSecondsLeft: Int,
    lastDecision: SwitchDecisionResult?,
    onToggleAutoSwitch: (Boolean) -> Unit,
    onManualSwitch: (NetworkType) -> Unit,
    onResetToDefault: () -> Unit,
    onOpenHistory: () -> Unit
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
            PlatformDisclosureBanner(
                title = "Android App-Level Switching Architecture",
                body = stringResource(R.string.platform_switching_disclosure),
                modifier = Modifier.widthIn(max = 680.dp)
            )
        }

        // AUTOMATIC MODE CARD
        item {
            TelemetryCard(
                modifier = Modifier.widthIn(max = 680.dp),
                borderColor = if (settings.autoSwitchingEnabled) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                } else sem.cardBorder
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Filled.AutoMode,
                            contentDescription = "Automatic Switching",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text("Automatic Switching Mode", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Monitors quality, evaluates predictions, applies hysteresis",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = settings.autoSwitchingEnabled,
                        onCheckedChange = onToggleAutoSwitch,
                        modifier = Modifier.testTag("auto_switch_toggle")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                KeyValueRow("Active App Routing", snapshot.boundProcessNetworkType?.label ?: "System Default (${snapshot.activeNetworkType.label})")
                KeyValueRow("Recommended Network", comparison?.recommendedNetwork?.label ?: "NOT AVAILABLE", MaterialTheme.colorScheme.primary)
                KeyValueRow("Hysteresis Threshold", "+${settings.switchingThresholdDelta} score pts")
                KeyValueRow("Minimum Quality Floor", "${settings.minimumNetworkScore} / 100")
                KeyValueRow(
                    "Stability Cooldown",
                    if (cooldownSecondsLeft > 0) "${cooldownSecondsLeft}s remaining" else "Ready (${settings.stabilityDurationSec}s policy)",
                    if (cooldownSecondsLeft > 0) sem.warning else sem.good
                )

                if (autoEvaluation != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Engine Status: ${autoEvaluation.explanation}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // MANUAL MODE CARD
        item {
            TelemetryCard(modifier = Modifier.widthIn(max = 680.dp)) {
                Text("Manual App-Level Network Selection", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Request a specific transport via ConnectivityManager.requestNetwork() and bind this app's traffic.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onManualSwitch(NetworkType.WIFI) },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("manual_switch_wifi_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Use Wi-Fi")
                    }

                    Button(
                        onClick = { onManualSwitch(NetworkType.CELLULAR) },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("manual_switch_cellular_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.CellTower, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Use Cellular")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onResetToDefault,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("reset_default_network_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Release Binding (System Default)")
                }

                Spacer(modifier = Modifier.height(8.dp))
                KeyValueRow("Wi-Fi Score", formatScore(comparison?.wifiScore?.totalScore), sem.wifiAccent)
                KeyValueRow("Cellular Score", formatScore(comparison?.cellularScore?.totalScore), sem.cellularAccent)
            }
        }

        // LAST SWITCHING RESULT
        if (lastDecision != null) {
            item {
                TelemetryCard(
                    modifier = Modifier.widthIn(max = 680.dp),
                    borderColor = if (lastDecision.success) sem.good.copy(alpha = 0.6f) else sem.danger.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Last Selection Result", style = MaterialTheme.typography.titleMedium)
                        StatusChip(
                            text = if (lastDecision.success) "SUCCESS" else "RETAINED",
                            color = if (lastDecision.success) sem.good else sem.danger
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${lastDecision.previousNetwork.label} → ${lastDecision.targetNetwork.label} (${lastDecision.mode.label})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = lastDecision.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formatDateTime(lastDecision.timestampMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onOpenHistory,
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_switching_history_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("View Full Switching History Audit Log")
            }
        }
    }
}

@Composable
fun SwitchingHistoryScreen(
    history: List<SwitchingHistoryEntity>,
    speedTestHistory: List<NetworkMetricEntity> = emptyList(),
    onExportReport: () -> Unit
) {
    val sem = LocalTelemetryColors.current
    var activeSection by remember { mutableStateOf("SWITCHING") }
    var filterMode by remember { mutableStateOf("ALL") }

    val filteredSwitches = remember(history, filterMode) {
        when (filterMode) {
            "AUTOMATIC" -> history.filter { it.switchingMode.equals("AUTOMATIC", ignoreCase = true) }
            "MANUAL" -> history.filter { it.switchingMode.equals("MANUAL", ignoreCase = true) }
            else -> history
        }
    }

    val measuredSpeedLogs = remember(speedTestHistory) {
        speedTestHistory
            .filter { it.downloadSpeed != null || it.uploadSpeed != null || it.latency != null }
            .sortedByDescending { it.createdAtMs }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(modifier = Modifier.widthIn(max = 680.dp)) {
                SectionHeader(
                    title = if (activeSection == "SWITCHING") "Switching Audit Trail" else "Speed Test & Probe History",
                    subtitle = if (activeSection == "SWITCHING") {
                        "${history.size} switching decisions stored in Room"
                    } else {
                        "${measuredSpeedLogs.size} timestamped measurements in Room"
                    },
                    trailing = {
                        OutlinedButton(
                            onClick = onExportReport,
                            modifier = Modifier.testTag("history_export_csv_btn")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Export CSV")
                        }
                    }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = activeSection == "SWITCHING",
                        onClick = { activeSection = "SWITCHING" },
                        label = { Text("Switching (${history.size})") },
                        modifier = Modifier.testTag("history_tab_switching")
                    )
                    FilterChip(
                        selected = activeSection == "SPEED_TESTS",
                        onClick = { activeSection = "SPEED_TESTS" },
                        label = { Text("Speed Tests (${measuredSpeedLogs.size})") },
                        modifier = Modifier.testTag("history_tab_speed_tests")
                    )
                }

                if (activeSection == "SWITCHING") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ALL" to "All", "AUTOMATIC" to "Automatic", "MANUAL" to "Manual").forEach { (key, label) ->
                            FilterChip(
                                selected = filterMode == key,
                                onClick = { filterMode = key },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }

        if (activeSection == "SWITCHING") {
            if (filteredSwitches.isEmpty()) {
                item {
                    EmptyTelemetryPlaceholder(
                        title = "No Switching Decisions Recorded",
                        subtitle = "Manual or automatic app-level network selections will be persisted here with timestamps and before/after scores.",
                        modifier = Modifier.widthIn(max = 680.dp)
                    )
                }
            } else {
                items(filteredSwitches, key = { it.id }) { entry ->
                    TelemetryCard(
                        modifier = Modifier.widthIn(max = 680.dp),
                        borderColor = if (entry.wasSuccessful) sem.good.copy(alpha = 0.45f) else sem.danger.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = entry.previousNetwork,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "to",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = entry.newNetwork,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StatusChip(
                                    text = entry.switchingMode,
                                    color = if (entry.switchingMode == "AUTOMATIC") MaterialTheme.colorScheme.primary else sem.cellularAccent
                                )
                                Icon(
                                    imageVector = if (entry.syncedToCloud) Icons.Filled.CloudDone else Icons.Filled.CloudQueue,
                                    contentDescription = if (entry.syncedToCloud) "Synced to Supabase" else "Pending local sync",
                                    tint = if (entry.syncedToCloud) sem.good else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = entry.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Score: ${formatScore(entry.scoreBefore)} → ${formatScore(entry.scoreAfter)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatDateTime(entry.createdAtMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        } else {
            if (measuredSpeedLogs.isEmpty()) {
                item {
                    EmptyTelemetryPlaceholder(
                        title = "No Speed Tests Recorded Yet",
                        subtitle = "Tap 'Speed Test' or 'Probe Now' on the Home screen to log real download, upload, latency, and signal history.",
                        modifier = Modifier.widthIn(max = 680.dp)
                    )
                }
            } else {
                items(measuredSpeedLogs, key = { it.id }) { metric ->
                    val isWifi = metric.networkType.equals(NetworkType.WIFI.code, ignoreCase = true)
                    val accent = if (isWifi) sem.wifiAccent else sem.cellularAccent
                    TelemetryCard(
                        modifier = Modifier.widthIn(max = 680.dp),
                        borderColor = accent.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isWifi) Icons.Filled.Wifi else Icons.Filled.CellTower,
                                    contentDescription = metric.networkType,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = NetworkType.fromCode(metric.networkType).label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            StatusChip(
                                text = formatScore(metric.qualityScore),
                                color = accent
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        KeyValueRow("Download / Upload", "${formatMbps(metric.downloadSpeed)} / ${formatMbps(metric.uploadSpeed)}")
                        KeyValueRow("Latency / Jitter", "${formatMs(metric.latency)} / ${formatMs(metric.jitter)}")
                        KeyValueRow("Signal / Probe Loss", "${formatDbm(metric.signalStrength)} • ${formatPct(metric.packetLoss)}")
                        Text(
                            text = formatDateTime(metric.createdAtMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
