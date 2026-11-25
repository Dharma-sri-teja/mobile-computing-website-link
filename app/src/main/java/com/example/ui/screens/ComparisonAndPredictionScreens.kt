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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PredictionLogEntity
import com.example.model.DegradationRisk
import com.example.model.NetworkComparisonResult
import com.example.model.NetworkSnapshot
import com.example.model.PerformanceMeasurement
import com.example.model.PredictedQualityClass
import com.example.model.PredictionResult
import com.example.model.PredictionStatus
import com.example.model.ScoringWeights
import com.example.ui.components.EmptyTelemetryPlaceholder
import com.example.ui.components.ExplainableScoringDashboardCard
import com.example.ui.components.KeyValueRow
import com.example.ui.components.QualityGaugeRing
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusChip
import com.example.ui.components.TelemetryCard
import com.example.ui.components.WifiVsCellularBarChartCard
import com.example.ui.components.formatDateTime
import com.example.ui.components.formatDbm
import com.example.ui.components.formatMbps
import com.example.ui.components.formatMs
import com.example.ui.components.formatPct
import com.example.ui.components.formatScore
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoFontFamily
import java.util.Locale

@Composable
fun NetworkComparisonScreen(
    snapshot: NetworkSnapshot,
    wifiMeasurement: PerformanceMeasurement?,
    cellularMeasurement: PerformanceMeasurement?,
    comparison: NetworkComparisonResult?,
    weights: ScoringWeights,
    isProbing: Boolean,
    onProbeNow: () -> Unit,
    onUpdateWeights: (ScoringWeights) -> Unit,
    onNavigateToSwitching: () -> Unit
) {
    val sem = LocalTelemetryColors.current
    var draftWeights by remember(weights) { mutableStateOf(weights) }

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
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            ) {
                Text("Decision Verdict", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QualityGaugeRing(
                        score = comparison?.wifiScore?.totalScore,
                        label = "WI-FI",
                        size = 108.dp
                    )
                    QualityGaugeRing(
                        score = comparison?.cellularScore?.totalScore,
                        label = "CELLULAR",
                        size = 108.dp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                KeyValueRow("Current Active Network", snapshot.activeNetworkType.label)
                KeyValueRow(
                    "Best Available Network",
                    comparison?.bestAvailableNetwork?.label ?: "NOT AVAILABLE",
                    sem.good
                )
                KeyValueRow(
                    "Recommended Network",
                    comparison?.recommendedNetwork?.label ?: "NOT AVAILABLE",
                    MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Reason: ${comparison?.reason ?: "Run a live probe to evaluate both interfaces."}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
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
                            .testTag("compare_probe_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Refresh Metrics")
                    }
                    Button(
                        onClick = onNavigateToSwitching,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("compare_switch_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Open Switching")
                    }
                }
            }
        }

        // Side-by-side 8-metric comparison matrix
        item {
            TelemetryCard(modifier = Modifier.widthIn(max = 680.dp)) {
                Text("Side-by-Side Interface Matrix", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Metric", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1.1f))
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.Wifi, contentDescription = null, tint = sem.wifiAccent, modifier = Modifier.size(15.dp))
                        Text("Wi-Fi", style = MaterialTheme.typography.labelLarge, color = sem.wifiAccent)
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.CellTower, contentDescription = null, tint = sem.cellularAccent, modifier = Modifier.size(15.dp))
                        Text("Cellular", style = MaterialTheme.typography.labelLarge, color = sem.cellularAccent)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ComparisonMatrixRow("Download", formatMbps(wifiMeasurement?.downloadSpeedMbps), formatMbps(cellularMeasurement?.downloadSpeedMbps))
                ComparisonMatrixRow("Upload", formatMbps(wifiMeasurement?.uploadSpeedMbps), formatMbps(cellularMeasurement?.uploadSpeedMbps))
                ComparisonMatrixRow("Latency", formatMs(wifiMeasurement?.avgLatencyMs), formatMs(cellularMeasurement?.avgLatencyMs))
                ComparisonMatrixRow("Jitter", formatMs(wifiMeasurement?.jitterMs), formatMs(cellularMeasurement?.jitterMs))
                ComparisonMatrixRow("Probe Loss", formatPct(wifiMeasurement?.probeLossPercent), formatPct(cellularMeasurement?.probeLossPercent))
                ComparisonMatrixRow("Signal", formatDbm(snapshot.wifi.rssiDbm), formatDbm(snapshot.cellular.signalStrengthDbm))
                ComparisonMatrixRow("Stability", formatPct(wifiMeasurement?.stabilityScore), formatPct(cellularMeasurement?.stabilityScore))
                ComparisonMatrixRow(
                    "Internet",
                    if (snapshot.wifi.isValidated) "Validated" else if (snapshot.wifi.isConnected) "Unvalidated" else "Offline",
                    if (snapshot.cellular.isValidated) "Validated" else if (snapshot.cellular.isConnected) "Unvalidated" else "Offline"
                )
                ComparisonMatrixRow(
                    "Predicted Quality",
                    comparison?.wifiScore?.predictedQualityClass?.label ?: "N/A",
                    comparison?.cellularScore?.predictedQualityClass?.label ?: "N/A"
                )
                ComparisonMatrixRow(
                    "Total Score",
                    formatScore(comparison?.wifiScore?.totalScore),
                    formatScore(comparison?.cellularScore?.totalScore)
                )
            }
        }

        item {
            WifiVsCellularBarChartCard(
                title = "Visual Score & Latency Breakdown",
                wifiScore = comparison?.wifiScore?.totalScore,
                cellularScore = comparison?.cellularScore?.totalScore,
                wifiLatencyMs = wifiMeasurement?.avgLatencyMs,
                cellularLatencyMs = cellularMeasurement?.avgLatencyMs,
                wifiDownloadMbps = wifiMeasurement?.downloadSpeedMbps,
                cellularDownloadMbps = cellularMeasurement?.downloadSpeedMbps,
                modifier = Modifier.widthIn(max = 680.dp)
            )
        }

        item {
            ExplainableScoringDashboardCard(
                snapshot = snapshot,
                wifiMeasurement = wifiMeasurement,
                cellularMeasurement = cellularMeasurement,
                comparison = comparison,
                weights = draftWeights,
                onProbeNow = onProbeNow,
                modifier = Modifier.widthIn(max = 680.dp)
            )
        }

        // Configurable Weighted Scoring Controls
        item {
            val norm = draftWeights.normalized()
            TelemetryCard(modifier = Modifier.widthIn(max = 680.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Configurable Scoring Weights", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Weights normalize automatically to 100%.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val defaults = ScoringWeights()
                            draftWeights = defaults
                            onUpdateWeights(defaults)
                        },
                        modifier = Modifier.testTag("reset_weights_button")
                    ) {
                        Text("Reset")
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                WeightSliderRow("Signal Strength", draftWeights.signalWeight, norm.signalWeight) {
                    draftWeights = draftWeights.copy(signalWeight = it)
                }
                WeightSliderRow("Latency (RTT)", draftWeights.latencyWeight, norm.latencyWeight) {
                    draftWeights = draftWeights.copy(latencyWeight = it)
                }
                WeightSliderRow("Download Speed", draftWeights.downloadWeight, norm.downloadWeight) {
                    draftWeights = draftWeights.copy(downloadWeight = it)
                }
                WeightSliderRow("Connection Stability", draftWeights.stabilityWeight, norm.stabilityWeight) {
                    draftWeights = draftWeights.copy(stabilityWeight = it)
                }
                WeightSliderRow("Probe / Packet Loss", draftWeights.packetLossWeight, norm.packetLossWeight) {
                    draftWeights = draftWeights.copy(packetLossWeight = it)
                }
                WeightSliderRow("Upload Speed", draftWeights.uploadWeight, norm.uploadWeight) {
                    draftWeights = draftWeights.copy(uploadWeight = it)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onUpdateWeights(draftWeights) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("apply_weights_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Apply Scoring Weights")
                }
            }
        }
    }
}

@Composable
private fun ComparisonMatrixRow(label: String, wifiText: String, cellularText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.1f)
        )
        Text(
            text = wifiText,
            fontFamily = MonoFontFamily,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = cellularText,
            fontFamily = MonoFontFamily,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun WeightSliderRow(
    label: String,
    rawValue: Float,
    normalizedFraction: Float,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(
                text = "${(normalizedFraction * 100).toInt()}%",
                fontFamily = MonoFontFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = rawValue,
            onValueChange = onChange,
            valueRange = 0.05f..0.60f
        )
    }
}

@Composable
fun PredictiveAnalyticsScreen(
    prediction: PredictionResult,
    predictionHistory: List<PredictionLogEntity>,
    isProbing: Boolean,
    onRunProbe: () -> Unit
) {
    val sem = LocalTelemetryColors.current
    val riskColor = when (prediction.degradationRisk) {
        DegradationRisk.CRITICAL, DegradationRisk.HIGH -> sem.danger
        DegradationRisk.MODERATE -> sem.warning
        DegradationRisk.LOW -> sem.good
        DegradationRisk.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }

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
                borderColor = riskColor.copy(alpha = 0.6f)
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
                        Icon(Icons.Filled.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Predictive Analysis Verdict", style = MaterialTheme.typography.titleMedium)
                    }
                    StatusChip(text = prediction.degradationRisk.label, color = riskColor)
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (prediction.status == PredictionStatus.INSUFFICIENT_DATA) {
                    val progress = (prediction.samplesCollected.toFloat() / prediction.minSamplesRequired.toFloat())
                        .coerceIn(0f, 1f)
                    Text(
                        text = "Collecting Baseline Data (${prediction.samplesCollected} / ${prediction.minSamplesRequired} real samples)",
                        style = MaterialTheme.typography.titleSmall,
                        color = sem.warning
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                        color = sem.warning
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        QualityGaugeRing(
                            score = prediction.predictedQualityScore,
                            label = "PREDICTED",
                            size = 104.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            val clsColor = when (prediction.predictedQualityClass) {
                                PredictedQualityClass.GOOD -> sem.good
                                PredictedQualityClass.AVERAGE -> sem.warning
                                PredictedQualityClass.POOR -> sem.danger
                                PredictedQualityClass.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            KeyValueRow(
                                "ML Quality Class",
                                prediction.predictedQualityClass.label,
                                clsColor
                            )
                            KeyValueRow(
                                "Predicted Quality",
                                formatScore(prediction.predictedQualityScore),
                                riskColor
                            )
                            KeyValueRow(
                                "Confidence",
                                String.format(Locale.US, "%.0f%%", prediction.confidencePercent)
                            )
                            KeyValueRow("Engine", "Hybrid ML v1.1")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                KeyValueRow("Expected Degradation", prediction.expectedDegradation, riskColor)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Recommended Action: ${prediction.recommendedAction}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reasoning: ${prediction.reasoning}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRunProbe,
                    enabled = !isProbing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("prediction_collect_sample_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Collect Fresh Telemetry Sample")
                }
            }
        }

        if (prediction.predictedEvents.isNotEmpty()) {
            item {
                SectionHeader(title = "Predicted Risk Signals", subtitle = "Detected from real telemetry slopes")
            }
            items(prediction.predictedEvents) { evt ->
                TelemetryCard(
                    modifier = Modifier.widthIn(max = 680.dp),
                    borderColor = sem.warning.copy(alpha = 0.6f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = evt.category, tint = sem.warning)
                        Text(evt.category, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(evt.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Feature Vector & ML Pipeline Readiness Card
        item {
            val fv = prediction.featureVector
            TelemetryCard(modifier = Modifier.widthIn(max = 680.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Modular ML Pipeline & Feature Vector", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = prediction.engineName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Architecture implements NetworkPredictor + OnDeviceMlModelRunner so a trained TensorFlow Lite (.tflite) or Random Forest classifier can be plugged directly into this extracted feature vector once sufficient Room CSV training data is collected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                KeyValueRow("Samples Analyzed", "${fv?.sampleCount ?: 0}")
                KeyValueRow("Latest Signal", fv?.signalDbm?.let { "${it.toInt()} dBm" } ?: "NOT AVAILABLE")
                KeyValueRow("Signal Slope", fv?.signalSlopePerSample?.let { String.format(Locale.US, "%+.2f dBm/step", it) } ?: "NOT AVAILABLE")
                KeyValueRow("Recent Avg Latency", formatMs(fv?.avgLatencyMs))
                KeyValueRow("Latency Slope", fv?.latencySlopePerSample?.let { String.format(Locale.US, "%+.2f ms/step", it) } ?: "NOT AVAILABLE")
                KeyValueRow("Recent Avg Jitter", formatMs(fv?.jitterMs))
                KeyValueRow("Recent Probe Loss", formatPct(fv?.probeLossPercent))
            }
        }

        item {
            SectionHeader(title = "Prediction History", subtitle = "${predictionHistory.size} logged analyses")
        }

        if (predictionHistory.isEmpty()) {
            item {
                EmptyTelemetryPlaceholder(
                    title = "No Prediction Logs Yet",
                    subtitle = "Start live monitoring or collect probes to build real prediction history.",
                    modifier = Modifier.widthIn(max = 680.dp)
                )
            }
        } else {
            items(predictionHistory.take(20)) { log ->
                TelemetryCard(modifier = Modifier.widthIn(max = 680.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(log.expectedDegradation, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        StatusChip(
                            text = log.degradationRisk,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(log.recommendedAction, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = "${log.networkType} • Conf ${log.confidencePercent.toInt()}% • ${formatDateTime(log.createdAtMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
