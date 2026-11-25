package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DegradationRisk
import com.example.model.DualInterfaceSmaForecast
import com.example.model.NetworkType
import com.example.model.SmaSignalForecast
import com.example.model.SmaTrendDirection
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoFontFamily
import java.util.Locale

/**
 * Dashboard component displaying Simple Moving Average (SMA) signal degradation trends
 * and forward forecasts computed from Room historical telemetry by [com.example.service.PredictiveAnalyticsService].
 */
@Composable
fun SmaSignalForecastCard(
    dualForecast: DualInterfaceSmaForecast,
    activeNetworkType: NetworkType,
    shortWindow: Int,
    longWindow: Int,
    onSelectWindow: (shortWindow: Int, longWindow: Int) -> Unit,
    onProbeNow: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val sem = LocalTelemetryColors.current
    val defaultInterface = remember(activeNetworkType, dualForecast.wifi.sampleCount, dualForecast.cellular.sampleCount) {
        when {
            activeNetworkType == NetworkType.CELLULAR -> NetworkType.CELLULAR
            activeNetworkType == NetworkType.WIFI -> NetworkType.WIFI
            dualForecast.wifi.sampleCount >= dualForecast.cellular.sampleCount -> NetworkType.WIFI
            else -> NetworkType.CELLULAR
        }
    }
    var selectedInterface by remember(defaultInterface) { mutableStateOf(defaultInterface) }
    val forecast = if (selectedInterface == NetworkType.CELLULAR) dualForecast.cellular else dualForecast.wifi

    val trendColor = when (forecast.degradationRisk) {
        DegradationRisk.CRITICAL, DegradationRisk.HIGH -> sem.danger
        DegradationRisk.MODERATE -> sem.warning
        DegradationRisk.LOW -> sem.good
        DegradationRisk.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val trendIcon = when (forecast.trendDirection) {
        SmaTrendDirection.DEGRADING, SmaTrendDirection.DEGRADING_RAPIDLY -> Icons.AutoMirrored.Filled.TrendingDown
        SmaTrendDirection.IMPROVING -> Icons.AutoMirrored.Filled.TrendingUp
        else -> Icons.AutoMirrored.Filled.TrendingFlat
    }

    TelemetryCard(
        modifier = modifier.testTag("sma_signal_forecast_card"),
        borderColor = trendColor.copy(alpha = 0.5f)
    ) {
        // Header row
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
                        .background(trendColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.ShowChart,
                        contentDescription = "SMA Signal Forecast",
                        tint = trendColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "SMA Signal Degradation Forecast",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Moving average trend from Room history (${forecast.sampleCount} samples)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            StatusChip(
                text = forecast.trendDirection.label,
                color = trendColor
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Interface & SMA window selectors
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedInterface == NetworkType.WIFI,
                    onClick = { selectedInterface = NetworkType.WIFI },
                    label = { Text("Wi-Fi (${dualForecast.wifi.sampleCount})", maxLines = 1) },
                    leadingIcon = {
                        Icon(Icons.Filled.Wifi, contentDescription = "Wi-Fi SMA", modifier = Modifier.size(15.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = sem.wifiAccent.copy(alpha = 0.18f),
                        selectedLabelColor = sem.wifiAccent,
                        selectedLeadingIconColor = sem.wifiAccent
                    ),
                    modifier = Modifier.testTag("sma_select_wifi")
                )
                FilterChip(
                    selected = selectedInterface == NetworkType.CELLULAR,
                    onClick = { selectedInterface = NetworkType.CELLULAR },
                    label = { Text("Cell (${dualForecast.cellular.sampleCount})", maxLines = 1) },
                    leadingIcon = {
                        Icon(Icons.Filled.CellTower, contentDescription = "Cellular SMA", modifier = Modifier.size(15.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = sem.cellularAccent.copy(alpha = 0.18f),
                        selectedLabelColor = sem.cellularAccent,
                        selectedLeadingIconColor = sem.cellularAccent
                    ),
                    modifier = Modifier.testTag("sma_select_cellular")
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = shortWindow == 3 && longWindow == 6,
                    onClick = { onSelectWindow(3, 6) },
                    label = { Text("3/6", fontFamily = MonoFontFamily, fontSize = 11.sp) },
                    modifier = Modifier.testTag("sma_window_3_6")
                )
                FilterChip(
                    selected = shortWindow == 5 && longWindow == 10,
                    onClick = { onSelectWindow(5, 10) },
                    label = { Text("5/10", fontFamily = MonoFontFamily, fontSize = 11.sp) },
                    modifier = Modifier.testTag("sma_window_5_10")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (!forecast.hasEnoughData) {
            val progress = (forecast.sampleCount.toFloat() / forecast.minSamplesRequired.coerceAtLeast(1).toFloat())
                .coerceIn(0f, 1f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = forecast.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = forecast.recommendation,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onProbeNow != null) {
                    OutlinedButton(
                        onClick = onProbeNow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("sma_collect_sample_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Collect Signal Sample")
                    }
                }
            }
            return@TelemetryCard
        }

        // KPI Readout Strip: Fast SMA, Baseline SMA, Projected Horizon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmaMetricPill(
                label = "SMA-${forecast.shortWindow}",
                value = forecast.latestShortSmaDbm?.let { String.format(Locale.US, "%.1f dBm", it) } ?: "N/A",
                subtext = "Latest raw: ${forecast.latestRawDbm?.toInt() ?: "--"} dBm",
                accent = trendColor,
                modifier = Modifier.weight(1f)
            )
            SmaMetricPill(
                label = "SMA-${forecast.longWindow}",
                value = forecast.latestLongSmaDbm?.let { String.format(Locale.US, "%.1f dBm", it) } ?: "Building",
                subtext = forecast.smaCrossoverDeltaDbm?.let {
                    String.format(Locale.US, "Δ %+.1f dBm", it)
                } ?: "Crossover",
                accent = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
            SmaMetricPill(
                label = "Forecast +${forecast.forecastSteps}",
                value = forecast.projectedDbmHorizon?.let { String.format(Locale.US, "%.1f dBm", it) } ?: "N/A",
                subtext = forecast.driftPerStepDbm?.let {
                    String.format(Locale.US, "%+.1f / step", it)
                } ?: "Drift",
                accent = if ((forecast.driftPerStepDbm ?: 0.0) <= -0.6) sem.danger else sem.good,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Multi-Layer SMA + Forecast Canvas Chart
        SmaForecastChartCanvas(
            forecast = forecast,
            fastSmaColor = trendColor,
            baselineSmaColor = MaterialTheme.colorScheme.secondary,
            rawColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            thresholdColor = sem.warning
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Compact Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendDot("Raw dBm", MaterialTheme.colorScheme.onSurfaceVariant)
            LegendDot("SMA-${forecast.shortWindow}", trendColor)
            LegendDot("SMA-${forecast.longWindow}", MaterialTheme.colorScheme.secondary)
            LegendDot("Warn ${forecast.warningThresholdDbm.toInt()}", sem.warning)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Trend Verdict & Actionable Recommendation
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = trendColor.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, trendColor.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = trendIcon,
                    contentDescription = forecast.trendDirection.label,
                    tint = trendColor,
                    modifier = Modifier.size(18.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = forecast.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = forecast.recommendation,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SmaForecastChartCanvas(
    forecast: SmaSignalForecast,
    fastSmaColor: Color,
    baselineSmaColor: Color,
    rawColor: Color,
    thresholdColor: Color
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val forecastBg = fastSmaColor.copy(alpha = 0.08f)
    val points = forecast.series
    if (points.size < 2) return

    val allValues = buildList {
        points.forEach { pt ->
            pt.rawSignalDbm?.let { add(it) }
            pt.shortSmaDbm?.let { add(it) }
            pt.longSmaDbm?.let { add(it) }
        }
        add(forecast.warningThresholdDbm)
    }
    val minVal = (allValues.minOrNull() ?: -100.0) - 4.0
    val maxVal = (allValues.maxOrNull() ?: -40.0) + 4.0
    val span = (maxVal - minVal).coerceAtLeast(8.0)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Max: ${String.format(Locale.US, "%.0f dBm", maxVal)}",
                fontFamily = MonoFontFamily,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Threshold: ${forecast.warningThresholdDbm.toInt()} dBm • Forecast Horizon →",
                fontFamily = MonoFontFamily,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp)
        ) {
            val w = size.width
            val h = size.height
            val padTop = 10.dp.toPx()
            val padBottom = 10.dp.toPx()
            val usableH = (h - padTop - padBottom).coerceAtLeast(1f)
            val stepX = w / (points.size - 1).coerceAtLeast(1).toFloat()

            fun yFor(dbm: Double): Float {
                val norm = ((dbm - minVal) / span).toFloat().coerceIn(0f, 1f)
                return padTop + usableH * (1f - norm)
            }

            // Shaded forecast horizon zone
            val firstForecastIdx = points.indexOfFirst { it.isForecast }
            if (firstForecastIdx > 0) {
                val splitX = (firstForecastIdx - 1) * stepX
                drawRect(
                    color = forecastBg,
                    topLeft = Offset(splitX, 0f),
                    size = Size(w - splitX, h)
                )
                drawLine(
                    color = fastSmaColor.copy(alpha = 0.35f),
                    start = Offset(splitX, 0f),
                    end = Offset(splitX, h),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                )
            }

            // Horizontal reference grid lines
            for (i in 0..3) {
                val y = padTop + usableH * (i / 3f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Dashed degradation warning threshold line
            val warnY = yFor(forecast.warningThresholdDbm)
            drawLine(
                color = thresholdColor.copy(alpha = 0.75f),
                start = Offset(0f, warnY),
                end = Offset(w, warnY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
            )

            // 1. Raw historical signal samples polyline & dots
            val rawCoords = points.mapIndexedNotNull { idx, pt ->
                pt.rawSignalDbm?.let { Offset(idx * stepX, yFor(it)) }
            }
            if (rawCoords.size >= 2) {
                val rawPath = Path().apply {
                    moveTo(rawCoords.first().x, rawCoords.first().y)
                    for (i in 1 until rawCoords.size) lineTo(rawCoords[i].x, rawCoords[i].y)
                }
                drawPath(
                    path = rawPath,
                    color = rawColor,
                    style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            rawCoords.forEach { offset ->
                drawCircle(color = rawColor, radius = 2.4.dp.toPx(), center = offset)
            }

            // 2. Baseline Long SMA curve
            val longCoords = points.mapIndexedNotNull { idx, pt ->
                if (!pt.isForecast) pt.longSmaDbm?.let { Offset(idx * stepX, yFor(it)) } else null
            }
            if (longCoords.size >= 2) {
                val longPath = Path().apply {
                    moveTo(longCoords.first().x, longCoords.first().y)
                    for (i in 1 until longCoords.size) lineTo(longCoords[i].x, longCoords[i].y)
                }
                drawPath(
                    path = longPath,
                    color = baselineSmaColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // 3. Fast Short SMA historical curve + soft area fill
            val fastHistCoords = points.mapIndexedNotNull { idx, pt ->
                if (!pt.isForecast) pt.shortSmaDbm?.let { Offset(idx * stepX, yFor(it)) } else null
            }
            if (fastHistCoords.size >= 2) {
                val fastPath = Path().apply {
                    moveTo(fastHistCoords.first().x, fastHistCoords.first().y)
                    for (i in 1 until fastHistCoords.size) lineTo(fastHistCoords[i].x, fastHistCoords[i].y)
                }
                val areaPath = Path().apply {
                    addPath(fastPath)
                    lineTo(fastHistCoords.last().x, h)
                    lineTo(fastHistCoords.first().x, h)
                    close()
                }
                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(listOf(fastSmaColor.copy(alpha = 0.22f), Color.Transparent))
                )
                drawPath(
                    path = fastPath,
                    color = fastSmaColor,
                    style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // 4. Projected dashed SMA forecast curve
            val forecastCoords = buildList {
                fastHistCoords.lastOrNull()?.let { add(it) }
                points.forEachIndexed { idx, pt ->
                    if (pt.isForecast) {
                        pt.shortSmaDbm?.let { add(Offset(idx * stepX, yFor(it))) }
                    }
                }
            }
            if (forecastCoords.size >= 2) {
                val projPath = Path().apply {
                    moveTo(forecastCoords.first().x, forecastCoords.first().y)
                    for (i in 1 until forecastCoords.size) lineTo(forecastCoords[i].x, forecastCoords[i].y)
                }
                drawPath(
                    path = projPath,
                    color = fastSmaColor,
                    style = Stroke(
                        width = 2.8.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )
                )
                forecastCoords.drop(1).forEachIndexed { idx, pt ->
                    val isFinal = idx == forecastCoords.size - 2
                    drawCircle(
                        color = fastSmaColor,
                        radius = if (isFinal) 4.5.dp.toPx() else 3.dp.toPx(),
                        center = pt
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val firstReal = points.firstOrNull { !it.isForecast }
            val lastReal = points.lastOrNull { !it.isForecast }
            Text(
                text = formatTimestamp(firstReal?.timestampMs),
                fontFamily = MonoFontFamily,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Now (${formatTimestamp(lastReal?.timestampMs)})",
                fontFamily = MonoFontFamily,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "+${forecast.forecastSteps} steps",
                fontFamily = MonoFontFamily,
                fontSize = 10.sp,
                color = fastSmaColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SmaMetricPill(
    label: String,
    value: String,
    subtext: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                fontFamily = MonoFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtext,
                fontFamily = MonoFontFamily,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
