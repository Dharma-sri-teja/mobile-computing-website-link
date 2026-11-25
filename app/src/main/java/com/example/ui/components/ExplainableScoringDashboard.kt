package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.NetworkScoringEngine
import com.example.model.ExplainableScoreBreakdown
import com.example.model.NetworkComparisonResult
import com.example.model.NetworkSnapshot
import com.example.model.NetworkType
import com.example.model.PerformanceMeasurement
import com.example.model.ScoreContributionItem
import com.example.model.ScoringMetricId
import com.example.model.ScoringWeights
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoFontFamily
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private data class ArcSegmentBounds(
    val id: ScoringMetricId,
    val startAngleDeg: Float,
    val sweepAngleDeg: Float
)

@Composable
private fun metricAccentColor(id: ScoringMetricId): Color {
    val sem = LocalTelemetryColors.current
    return when (id) {
        ScoringMetricId.SIGNAL -> MaterialTheme.colorScheme.primary
        ScoringMetricId.LATENCY -> sem.good
        ScoringMetricId.DOWNLOAD -> MaterialTheme.colorScheme.secondary
        ScoringMetricId.STABILITY -> sem.cellularAccent
        ScoringMetricId.PACKET_LOSS -> sem.danger
        ScoringMetricId.UPLOAD -> sem.warning
    }
}

/**
 * Interactive native Jetpack Compose Canvas visualization for the Explainable Scoring system.
 * Combines a D3-inspired dual-ring radial contribution donut (tap-to-inspect slices),
 * a calibrated 0-100 stacked horizontal waterfall bar, and per-metric attribution rows.
 */
@Composable
fun ExplainableScoringDashboardCard(
    snapshot: NetworkSnapshot,
    wifiMeasurement: PerformanceMeasurement?,
    cellularMeasurement: PerformanceMeasurement?,
    comparison: NetworkComparisonResult?,
    weights: ScoringWeights,
    onProbeNow: (() -> Unit)? = null,
    onTuneWeights: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val sem = LocalTelemetryColors.current
    val scoringEngine = remember { NetworkScoringEngine() }

    val defaultInterface = remember(snapshot.activeNetworkType, snapshot.wifi.isConnected, snapshot.cellular.isConnected) {
        when {
            snapshot.activeNetworkType == NetworkType.CELLULAR -> NetworkType.CELLULAR
            snapshot.activeNetworkType == NetworkType.WIFI -> NetworkType.WIFI
            snapshot.wifi.isConnected -> NetworkType.WIFI
            snapshot.cellular.isConnected -> NetworkType.CELLULAR
            else -> NetworkType.WIFI
        }
    }
    var selectedInterface by remember(defaultInterface) { mutableStateOf(defaultInterface) }
    var selectedMetricId by remember { mutableStateOf<ScoringMetricId?>(null) }

    val breakdown = remember(
        selectedInterface,
        snapshot,
        wifiMeasurement,
        cellularMeasurement,
        comparison,
        weights
    ) {
        val measurement = if (selectedInterface == NetworkType.WIFI) wifiMeasurement else cellularMeasurement
        scoringEngine.buildExplainableBreakdown(
            type = selectedInterface,
            snapshot = snapshot,
            measurement = measurement,
            weights = weights
        )
    }

    val selectedItem = breakdown.contributions.firstOrNull { it.id == selectedMetricId }

    TelemetryCard(
        modifier = modifier.testTag("explainable_scoring_card"),
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    ) {
        // Header + Wi-Fi / Cellular Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.DonutLarge,
                        contentDescription = "Explainable Scoring Visualizer",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Explainable Scoring",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "How each metric builds the 0–100 health score",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selectedInterface == NetworkType.WIFI,
                onClick = { selectedInterface = NetworkType.WIFI },
                label = {
                    val wifiScoreTxt = comparison?.wifiScore?.totalScore?.let { " (${it.toInt()})" } ?: ""
                    Text("Wi-Fi$wifiScoreTxt", maxLines = 1)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Wifi,
                        contentDescription = "Inspect Wi-Fi score",
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = sem.wifiAccent.copy(alpha = 0.18f),
                    selectedLabelColor = sem.wifiAccent,
                    selectedLeadingIconColor = sem.wifiAccent
                ),
                modifier = Modifier.testTag("explainable_select_wifi")
            )

            FilterChip(
                selected = selectedInterface == NetworkType.CELLULAR,
                onClick = { selectedInterface = NetworkType.CELLULAR },
                label = {
                    val cellScoreTxt = comparison?.cellularScore?.totalScore?.let { " (${it.toInt()})" } ?: ""
                    Text("Cellular$cellScoreTxt", maxLines = 1)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.CellTower,
                        contentDescription = "Inspect Cellular score",
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = sem.cellularAccent.copy(alpha = 0.18f),
                    selectedLabelColor = sem.cellularAccent,
                    selectedLeadingIconColor = sem.cellularAccent
                ),
                modifier = Modifier.testTag("explainable_select_cellular")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!breakdown.isConnected || breakdown.finalScore == null) {
            ExplainableEmptyState(
                networkType = selectedInterface,
                isConnected = breakdown.isConnected,
                onProbeNow = onProbeNow
            )
            return@TelemetryCard
        }

        // Responsive Radial Attribution Donut + Insights / Stacked Waterfall
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isWide = maxWidth >= 500.dp
            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadialContributionDonut(
                        breakdown = breakdown,
                        selectedMetricId = selectedMetricId,
                        onSelectMetric = { clicked ->
                            selectedMetricId = if (selectedMetricId == clicked) null else clicked
                        },
                        modifier = Modifier.size(216.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DriverAndBottleneckStrip(breakdown = breakdown)
                        StackedContributionWaterfall(
                            breakdown = breakdown,
                            selectedMetricId = selectedMetricId,
                            onSelectMetric = { clicked ->
                                selectedMetricId = if (selectedMetricId == clicked) null else clicked
                            }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadialContributionDonut(
                        breakdown = breakdown,
                        selectedMetricId = selectedMetricId,
                        onSelectMetric = { clicked ->
                            selectedMetricId = if (selectedMetricId == clicked) null else clicked
                        },
                        modifier = Modifier.size(210.dp)
                    )
                    DriverAndBottleneckStrip(breakdown = breakdown)
                    StackedContributionWaterfall(
                        breakdown = breakdown,
                        selectedMetricId = selectedMetricId,
                        onSelectMetric = { clicked ->
                            selectedMetricId = if (selectedMetricId == clicked) null else clicked
                        }
                    )
                }
            }
        }

        // Validation Cap Warning Banner if interface lacks validated internet
        if (breakdown.validationCapDeduction > 0.05) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = sem.dangerContainer.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, sem.danger.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Validation cap active",
                        tint = sem.danger,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Unvalidated internet cap applied: raw weighted score ${String.format(Locale.US, "%.1f", breakdown.rawWeightedScore ?: 0.0)} capped to ${String.format(Locale.US, "%.1f", breakdown.finalScore)} (-${String.format(Locale.US, "%.1f", breakdown.validationCapDeduction)} pts).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Interactive Inspector Drawer for tapped metric
        AnimatedVisibility(
            visible = selectedItem != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            selectedItem?.let { item ->
                Spacer(modifier = Modifier.height(10.dp))
                SelectedMetricInspector(
                    item = item,
                    accent = metricAccentColor(item.id)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Metric Contributions (tap any slice or row)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Earned / Max",
                fontFamily = MonoFontFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            breakdown.contributions.forEach { item ->
                MetricContributionRow(
                    item = item,
                    accent = metricAccentColor(item.id),
                    isSelected = selectedMetricId == item.id,
                    onClick = {
                        selectedMetricId = if (selectedMetricId == item.id) null else item.id
                    }
                )
            }
        }

        if (onTuneWeights != null) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onTuneWeights,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("explainable_tune_weights_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tune Scoring Weights")
            }
        }
    }
}

@Composable
private fun RadialContributionDonut(
    breakdown: ExplainableScoreBreakdown,
    selectedMetricId: ScoringMetricId?,
    onSelectMetric: (ScoringMetricId) -> Unit,
    modifier: Modifier = Modifier
) {
    val sem = LocalTelemetryColors.current
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val activeItems = remember(breakdown) { breakdown.contributions.filter { it.isAvailable } }
    val finalScore = breakdown.finalScore ?: 0.0

    val gradeColor = when {
        !breakdown.isValidated -> sem.danger
        finalScore >= 80.0 -> sem.good
        finalScore >= 60.0 -> sem.warning
        else -> sem.danger
    }
    val gradeLabel = when {
        !breakdown.isValidated -> "CAPPED"
        finalScore >= 80.0 -> "HEALTHY"
        finalScore >= 60.0 -> "MODERATE"
        else -> "DEGRADED"
    }

    // Animate total reveal cleanly using spring physics
    val animatedFillProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.85f),
        label = "donut_reveal"
    )

    // Precompute segment angular bounds for both rendering and tap hit-testing
    val padAngleDeg = 3.2f
    val segments = remember(activeItems) {
        var cursor = -90f
        activeItems.map { item ->
            val rawSweep = ((item.earnedPoints / 100.0) * 360.0).toFloat().coerceAtLeast(0f)
            val effectiveSweep = (rawSweep - padAngleDeg).coerceAtLeast(if (rawSweep > 1f) 1.5f else 0f)
            val bounds = ArcSegmentBounds(
                id = item.id,
                startAngleDeg = cursor + padAngleDeg / 2f,
                sweepAngleDeg = effectiveSweep
            )
            cursor += rawSweep
            bounds
        }
    }

    val colorsByMetric = activeItems.associate { it.id to metricAccentColor(it.id) }
    val selectedItem = activeItems.firstOrNull { it.id == selectedMetricId }

    Box(
        modifier = modifier.semantics {
            contentDescription = "${breakdown.networkType.label} health score ${finalScore.toInt()} out of 100"
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(segments) {
                    detectTapGestures { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = offset.x - center.x
                        val dy = offset.y - center.y
                        val radius = hypot(dx, dy)
                        val outerR = minOf(size.width, size.height) / 2f
                        if (radius in (outerR * 0.42f)..outerR) {
                            var angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            if (angleDeg < -90f) angleDeg += 360f
                            val hit = segments.firstOrNull { seg ->
                                angleDeg >= seg.startAngleDeg && angleDeg <= seg.startAngleDeg + seg.sweepAngleDeg
                            }
                            if (hit != null) {
                                onSelectMetric(hit.id)
                            }
                        }
                    }
                }
        ) {
            val minDim = minOf(size.width, size.height)
            val outerRingStroke = 5.dp.toPx()
            val mainRingStroke = 20.dp.toPx()
            val selectedExtraStroke = 6.dp.toPx()

            // Outer subtle weight-allocation ring (represents 100% effective weight budget)
            val outerInset = outerRingStroke / 2f + 3.dp.toPx()
            val outerSize = Size(minDim - outerInset * 2f, minDim - outerInset * 2f)
            val outerTopLeft = Offset((size.width - outerSize.width) / 2f, (size.height - outerSize.height) / 2f)

            var weightCursor = -90f
            activeItems.forEach { item ->
                val weightSweep = (item.effectiveWeight * 360f) * animatedFillProgress
                val drawSweep = (weightSweep - 2.4f).coerceAtLeast(0f)
                val accent = colorsByMetric[item.id] ?: outlineColor
                val alpha = if (selectedMetricId == null || selectedMetricId == item.id) 0.42f else 0.14f
                drawArc(
                    color = accent.copy(alpha = alpha),
                    startAngle = weightCursor + 1.2f,
                    sweepAngle = drawSweep,
                    useCenter = false,
                    topLeft = outerTopLeft,
                    size = outerSize,
                    style = Stroke(width = outerRingStroke, cap = StrokeCap.Butt)
                )
                weightCursor += weightSweep
            }

            // Inner main ring track (represents lost headroom out of 100)
            val mainInset = outerInset + outerRingStroke + 12.dp.toPx() + mainRingStroke / 2f
            val mainSize = Size(minDim - mainInset * 2f, minDim - mainInset * 2f)
            val mainTopLeft = Offset((size.width - mainSize.width) / 2f, (size.height - mainSize.height) / 2f)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = mainTopLeft,
                size = mainSize,
                style = Stroke(width = mainRingStroke)
            )

            // Draw earned contribution slices
            segments.forEach { seg ->
                val isSelected = selectedMetricId == seg.id
                val baseColor = colorsByMetric[seg.id] ?: gradeColor
                val drawColor = when {
                    selectedMetricId == null -> baseColor
                    isSelected -> baseColor
                    else -> baseColor.copy(alpha = 0.28f)
                }
                val strokeWidth = if (isSelected) mainRingStroke + selectedExtraStroke else mainRingStroke
                val midAngleRad = ((seg.startAngleDeg + seg.sweepAngleDeg / 2f) * PI / 180.0)
                val popOffset = if (isSelected) 4.dp.toPx() else 0f
                val shiftedTopLeft = Offset(
                    mainTopLeft.x + (cos(midAngleRad) * popOffset).toFloat(),
                    mainTopLeft.y + (sin(midAngleRad) * popOffset).toFloat()
                )

                drawArc(
                    color = drawColor,
                    startAngle = seg.startAngleDeg,
                    sweepAngle = seg.sweepAngleDeg * animatedFillProgress,
                    useCenter = false,
                    topLeft = shiftedTopLeft,
                    size = mainSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
            }

            // Subtle calibrated compass ticks at 0, 25, 50, 75
            val center = Offset(size.width / 2f, size.height / 2f)
            val tickInner = (mainSize.width / 2f) - mainRingStroke / 2f - 6.dp.toPx()
            val tickOuter = (mainSize.width / 2f) - mainRingStroke / 2f - 2.dp.toPx()
            listOf(-90.0, 0.0, 90.0, 180.0).forEach { deg ->
                val rad = deg * PI / 180.0
                drawLine(
                    color = outlineColor,
                    start = Offset(
                        center.x + (cos(rad) * tickInner).toFloat(),
                        center.y + (sin(rad) * tickInner).toFloat()
                    ),
                    end = Offset(
                        center.x + (cos(rad) * tickOuter).toFloat(),
                        center.y + (sin(rad) * tickOuter).toFloat()
                    ),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }

        // Center Score Callout
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (selectedItem != null) {
                val accent = metricAccentColor(selectedItem.id)
                Text(
                    text = selectedItem.id.shortLabel.uppercase(Locale.US),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "+${String.format(Locale.US, "%.1f", selectedItem.earnedPoints)}",
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = accent
                )
                Text(
                    text = "of ${String.format(Locale.US, "%.0f", selectedItem.maxPoints)} max pts",
                    fontFamily = MonoFontFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = String.format(Locale.US, "%.0f", finalScore),
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    color = gradeColor
                )
                Text(
                    text = "/ 100 PTS",
                    fontFamily = MonoFontFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                StatusChip(text = gradeLabel, color = gradeColor)
            }
        }
    }
}

@Composable
private fun StackedContributionWaterfall(
    breakdown: ExplainableScoreBreakdown,
    selectedMetricId: ScoringMetricId?,
    onSelectMetric: (ScoringMetricId) -> Unit
) {
    val sem = LocalTelemetryColors.current
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val activeItems = breakdown.contributions.filter { it.isAvailable && it.earnedPoints > 0.0 }
    val colorsByMetric = activeItems.associate { it.id to metricAccentColor(it.id) }
    val rawScore = breakdown.rawWeightedScore ?: 0.0

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Score Composition Waterfall",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${String.format(Locale.US, "%.1f", rawScore)} / 100 earned",
                fontFamily = MonoFontFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .clip(RoundedCornerShape(8.dp))
                .pointerInput(activeItems) {
                    detectTapGestures { offset ->
                        val tappedPct = (offset.x / size.width.coerceAtLeast(1)) * 100.0
                        var acc = 0.0
                        for (item in activeItems) {
                            val next = acc + item.earnedPoints
                            if (tappedPct in acc..next) {
                                onSelectMetric(item.id)
                                break
                            }
                            acc = next
                        }
                    }
                }
        ) {
            // Track background (lost headroom)
            drawRoundRect(
                color = trackColor,
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            )

            var cursorX = 0f
            val gapPx = 2.dp.toPx()
            activeItems.forEach { item ->
                val segW = ((item.earnedPoints / 100.0) * size.width).toFloat()
                if (segW > 0.5f) {
                    val baseColor = colorsByMetric[item.id] ?: sem.good
                    val isDimmed = selectedMetricId != null && selectedMetricId != item.id
                    drawRect(
                        color = if (isDimmed) baseColor.copy(alpha = 0.30f) else baseColor,
                        topLeft = Offset(cursorX, 0f),
                        size = Size((segW - gapPx).coerceAtLeast(1f), size.height)
                    )
                    cursorX += segW
                }
            }

            // Validation cap line if active
            if (breakdown.validationCapDeduction > 0.05 && breakdown.finalScore != null) {
                val capX = ((breakdown.finalScore / 100.0) * size.width).toFloat()
                drawLine(
                    color = sem.danger,
                    start = Offset(capX, 0f),
                    end = Offset(capX, size.height),
                    strokeWidth = 2.5.dp.toPx()
                )
            }
        }

        // Calibrated 0..100 ruler ticks
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("0", "25", "50", "75", "100").forEach { tick ->
                Text(
                    text = tick,
                    fontFamily = MonoFontFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DriverAndBottleneckStrip(breakdown: ExplainableScoreBreakdown) {
    val sem = LocalTelemetryColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            color = sem.goodContainer.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, sem.good.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = "TOP DRIVER",
                    style = MaterialTheme.typography.labelSmall,
                    color = sem.good,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = breakdown.topContributor?.let {
                        "${it.id.shortLabel} (+${String.format(Locale.US, "%.1f", it.earnedPoints)})"
                    } ?: "N/A",
                    fontFamily = MonoFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            color = sem.warningContainer.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, sem.warning.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = "BIGGEST DRAG",
                    style = MaterialTheme.typography.labelSmall,
                    color = sem.warning,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = breakdown.largestDrag?.let {
                        if (it.lostPoints >= 0.1) {
                            "${it.id.shortLabel} (-${String.format(Locale.US, "%.1f", it.lostPoints)})"
                        } else {
                            "No Drag"
                        }
                    } ?: "None",
                    fontFamily = MonoFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SelectedMetricInspector(
    item: ScoreContributionItem,
    accent: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("explainable_metric_inspector"),
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.id.label} Attribution",
                    style = MaterialTheme.typography.titleSmall,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.rawDisplayValue,
                    fontFamily = MonoFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (item.isAvailable && item.subScore != null) {
                Text(
                    text = "Sub-score ${String.format(Locale.US, "%.0f", item.subScore)}/100 × ${(item.effectiveWeight * 100f).toInt()}% active weight = +${String.format(Locale.US, "%.1f", item.earnedPoints)} pts (-${String.format(Locale.US, "%.1f", item.lostPoints)} pts vs max)",
                    fontFamily = MonoFontFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    text = "Awaiting real measurement — its ${(item.configuredWeight * 100f).toInt()}% weight is redistributed proportionally across active metrics.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Scale: ${item.formulaNote}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetricContributionRow(
    item: ScoreContributionItem,
    accent: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val containerColor = if (isSelected) {
        accent.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
    }
    val borderColor = if (isSelected) accent.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("explainable_metric_${item.id.name.lowercase(Locale.US)}"),
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (item.isAvailable) accent else MaterialTheme.colorScheme.outline)
                    )
                    Text(
                        text = item.id.label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "• ${item.rawDisplayValue}",
                        fontFamily = MonoFontFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = if (item.isAvailable) {
                        "+${String.format(Locale.US, "%.1f", item.earnedPoints)} / ${String.format(Locale.US, "%.0f", item.maxPoints)}"
                    } else {
                        "N/A"
                    },
                    fontFamily = MonoFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isAvailable) accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Sub-score horizontal micro-bar with weight tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val subFraction = ((item.subScore ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                ) {
                    drawRoundRect(
                        color = trackColor,
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                    if (item.isAvailable && subFraction > 0f) {
                        drawRoundRect(
                            color = accent,
                            size = Size(size.width * subFraction, size.height),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }
                Text(
                    text = if (item.isAvailable) {
                        "w ${(item.effectiveWeight * 100f).toInt()}%"
                    } else {
                        "cfg ${(item.configuredWeight * 100f).toInt()}%"
                    },
                    fontFamily = MonoFontFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ExplainableEmptyState(
    networkType: NetworkType,
    isConnected: Boolean,
    onProbeNow: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (isConnected) {
                "${networkType.label} is connected — awaiting live telemetry probe"
            } else {
                "${networkType.label} is currently disconnected"
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Only real device measurements are plotted. Run a live probe to visualize exact signal, latency, packet loss, and throughput contributions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (onProbeNow != null && isConnected) {
            OutlinedButton(
                onClick = onProbeNow,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Run Probe Now")
            }
        }
    }
}
