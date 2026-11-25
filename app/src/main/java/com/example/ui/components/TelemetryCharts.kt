package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoFontFamily
import java.util.Locale

data class ChartPoint(
    val timestampMs: Long,
    val value: Double
)

@Composable
fun TelemetryLineChartCard(
    title: String,
    unit: String,
    points: List<ChartPoint>,
    lineColor: Color,
    emptyHint: String = "Start live monitoring to plot real measurements.",
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    TelemetryCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val latest = points.lastOrNull()?.value
            Text(
                text = latest?.let { String.format(Locale.US, "%.1f %s", it, unit) } ?: "NOT AVAILABLE",
                fontFamily = MonoFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (latest != null) lineColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        if (points.size < 2) {
            Text(
                text = if (points.size == 1) {
                    "1 real sample recorded (${String.format(Locale.US, "%.1f %s", points.first().value, unit)}). Awaiting additional samples for trend curve."
                } else {
                    emptyHint
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 18.dp)
            )
            return@TelemetryCard
        }

        val minVal = points.minOf { it.value }
        val maxVal = points.maxOf { it.value }
        val span = (maxVal - minVal).takeIf { it > 0.01 } ?: 1.0

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Max: ${String.format(Locale.US, "%.1f %s", maxVal, unit)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Min: ${String.format(Locale.US, "%.1f %s", minVal, unit)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            val w = size.width
            val h = size.height
            val padTop = 8.dp.toPx()
            val padBottom = 8.dp.toPx()
            val usableH = (h - padTop - padBottom).coerceAtLeast(1f)

            // Horizontal reference lines
            for (i in 0..3) {
                val y = padTop + usableH * (i / 3f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val coords = points.mapIndexed { idx, pt ->
                val x = (idx.toFloat() / (points.size - 1).toFloat()) * w
                val normY = ((pt.value - minVal) / span).toFloat().coerceIn(0f, 1f)
                val y = padTop + usableH * (1f - normY)
                Offset(x, y)
            }

            val linePath = Path().apply {
                moveTo(coords.first().x, coords.first().y)
                for (i in 1 until coords.size) {
                    lineTo(coords[i].x, coords[i].y)
                }
            }

            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(coords.last().x, h)
                lineTo(coords.first().x, h)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)
                )
            )
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Draw real sample nodes
            coords.forEach { pt ->
                drawCircle(
                    color = lineColor,
                    radius = 3.dp.toPx(),
                    center = pt
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTimestamp(points.first().timestampMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${points.size} real samples",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatTimestamp(points.last().timestampMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun WifiVsCellularBarChartCard(
    title: String,
    wifiScore: Double?,
    cellularScore: Double?,
    wifiLatencyMs: Double?,
    cellularLatencyMs: Double?,
    wifiDownloadMbps: Double?,
    cellularDownloadMbps: Double?,
    modifier: Modifier = Modifier
) {
    val sem = LocalTelemetryColors.current
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    TelemetryCard(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatusChip(text = "Wi-Fi", color = sem.wifiAccent)
            StatusChip(text = "Cellular", color = sem.cellularAccent)
        }
        Spacer(modifier = Modifier.height(14.dp))

        DualMetricBarRow(
            label = "Quality Score (0-100)",
            wifiVal = wifiScore,
            cellVal = cellularScore,
            maxScale = 100.0,
            formatter = { "${it.toInt()}" },
            wifiColor = sem.wifiAccent,
            cellColor = sem.cellularAccent,
            trackColor = trackColor
        )
        Spacer(modifier = Modifier.height(12.dp))
        DualMetricBarRow(
            label = "Download Speed (Mbps)",
            wifiVal = wifiDownloadMbps,
            cellVal = cellularDownloadMbps,
            maxScale = maxOf(wifiDownloadMbps ?: 0.0, cellularDownloadMbps ?: 0.0, 20.0),
            formatter = { String.format(Locale.US, "%.1f", it) },
            wifiColor = sem.wifiAccent,
            cellColor = sem.cellularAccent,
            trackColor = trackColor
        )
        Spacer(modifier = Modifier.height(12.dp))
        DualMetricBarRow(
            label = "Latency RTT (ms — lower is better)",
            wifiVal = wifiLatencyMs,
            cellVal = cellularLatencyMs,
            maxScale = maxOf(wifiLatencyMs ?: 0.0, cellularLatencyMs ?: 0.0, 100.0),
            formatter = { String.format(Locale.US, "%.0f ms", it) },
            wifiColor = sem.wifiAccent,
            cellColor = sem.cellularAccent,
            trackColor = trackColor
        )
    }
}

@Composable
private fun DualMetricBarRow(
    label: String,
    wifiVal: Double?,
    cellVal: Double?,
    maxScale: Double,
    formatter: (Double) -> String,
    wifiColor: Color,
    cellColor: Color,
    trackColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SingleBarLine(
            tag = "Wi-Fi",
            value = wifiVal,
            maxScale = maxScale,
            display = wifiVal?.let(formatter) ?: "N/A",
            barColor = wifiColor,
            trackColor = trackColor
        )
        SingleBarLine(
            tag = "Cell",
            value = cellVal,
            maxScale = maxScale,
            display = cellVal?.let(formatter) ?: "N/A",
            barColor = cellColor,
            trackColor = trackColor
        )
    }
}

@Composable
private fun SingleBarLine(
    tag: String,
    value: Double?,
    maxScale: Double,
    display: String,
    barColor: Color,
    trackColor: Color
) {
    val fraction = if (value != null && maxScale > 0.0) {
        (value / maxScale).toFloat().coerceIn(0.03f, 1f)
    } else {
        0f
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = tag,
            fontFamily = MonoFontFamily,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(0.14f)
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
        ) {
            drawRoundRect(
                color = trackColor,
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
            if (fraction > 0f) {
                drawRoundRect(
                    color = barColor,
                    size = Size(size.width * fraction, size.height),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }
        }
        Text(
            text = display,
            fontFamily = MonoFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(0.24f)
        )
    }
}
