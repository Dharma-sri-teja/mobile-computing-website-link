package com.example.service

import com.example.data.local.NetworkMetricDao
import com.example.data.local.NetworkMetricEntity
import com.example.model.DegradationRisk
import com.example.model.DualInterfaceSmaForecast
import com.example.model.NetworkType
import com.example.model.SmaSignalForecast
import com.example.model.SmaTrendDirection
import com.example.model.SmaTrendPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Reactive predictive analytics service that observes historical network telemetry stored in Room
 * and applies a dual-window Simple Moving Average (SMA) algorithm to detect and forecast
 * network signal degradation trends without fabricating measurements.
 */
class PredictiveAnalyticsService(
    private val networkMetricDao: NetworkMetricDao
) {

    companion object {
        const val DEFAULT_SHORT_WINDOW = 3
        const val DEFAULT_LONG_WINDOW = 6
        const val DEFAULT_FORECAST_STEPS = 3
        const val DEFAULT_HISTORY_LIMIT = 30

        const val WIFI_WARNING_DBM = -75.0
        const val WIFI_CRITICAL_DBM = -82.0
        const val CELLULAR_WARNING_DBM = -105.0
        const val CELLULAR_CRITICAL_DBM = -115.0

        private const val MIN_VALID_DBM = -135.0
        private const val MAX_VALID_DBM = -25.0
        private const val DEFAULT_STEP_MS = 10_000L

        /**
         * Computes the trailing Simple Moving Average series for [values] with the given [window].
         * Indices prior to `window - 1` return null because a full moving window is not yet filled.
         */
        fun computeSimpleMovingAverage(values: List<Double>, window: Int): List<Double?> {
            require(window >= 1) { "SMA window must be >= 1" }
            if (values.isEmpty()) return emptyList()
            val result = ArrayList<Double?>(values.size)
            var runningSum = 0.0
            for (i in values.indices) {
                runningSum += values[i]
                if (i >= window) {
                    runningSum -= values[i - window]
                }
                if (i >= window - 1) {
                    result.add(runningSum / window.toDouble())
                } else {
                    result.add(null)
                }
            }
            return result
        }
    }

    /**
     * Observes Room telemetry history for [networkType] and emits an updated [SmaSignalForecast]
     * whenever new real measurements are persisted.
     */
    fun observeSignalDegradationForecast(
        networkType: NetworkType,
        shortWindow: Int = DEFAULT_SHORT_WINDOW,
        longWindow: Int = DEFAULT_LONG_WINDOW,
        forecastSteps: Int = DEFAULT_FORECAST_STEPS,
        historyLimit: Int = DEFAULT_HISTORY_LIMIT
    ): Flow<SmaSignalForecast> {
        return networkMetricDao
            .observeMetricsForNetwork(networkType = networkType.code, limit = historyLimit)
            .map { records ->
                computeForecast(
                    networkType = networkType,
                    metrics = records,
                    shortWindow = shortWindow,
                    longWindow = longWindow,
                    forecastSteps = forecastSteps
                )
            }
    }

    /**
     * Observes both Wi-Fi and Cellular Room signal histories in parallel for dashboard trend display.
     */
    fun observeDualInterfaceForecasts(
        shortWindow: Int = DEFAULT_SHORT_WINDOW,
        longWindow: Int = DEFAULT_LONG_WINDOW,
        forecastSteps: Int = DEFAULT_FORECAST_STEPS,
        historyLimit: Int = DEFAULT_HISTORY_LIMIT
    ): Flow<DualInterfaceSmaForecast> {
        val wifiFlow = observeSignalDegradationForecast(
            networkType = NetworkType.WIFI,
            shortWindow = shortWindow,
            longWindow = longWindow,
            forecastSteps = forecastSteps,
            historyLimit = historyLimit
        )
        val cellularFlow = observeSignalDegradationForecast(
            networkType = NetworkType.CELLULAR,
            shortWindow = shortWindow,
            longWindow = longWindow,
            forecastSteps = forecastSteps,
            historyLimit = historyLimit
        )
        return combine(wifiFlow, cellularFlow) { wifi, cellular ->
            DualInterfaceSmaForecast(wifi = wifi, cellular = cellular)
        }
    }

    /**
     * Pure deterministic SMA forecast calculation operating on persisted Room [NetworkMetricEntity] rows.
     */
    fun computeForecast(
        networkType: NetworkType,
        metrics: List<NetworkMetricEntity>,
        shortWindow: Int = DEFAULT_SHORT_WINDOW,
        longWindow: Int = DEFAULT_LONG_WINDOW,
        forecastSteps: Int = DEFAULT_FORECAST_STEPS
    ): SmaSignalForecast {
        val safeShort = shortWindow.coerceAtLeast(2)
        val safeLong = longWindow.coerceAtLeast(safeShort + 1)
        val safeSteps = forecastSteps.coerceIn(1, 6)

        val warningThreshold = if (networkType == NetworkType.CELLULAR) CELLULAR_WARNING_DBM else WIFI_WARNING_DBM
        val criticalThreshold = if (networkType == NetworkType.CELLULAR) CELLULAR_CRITICAL_DBM else WIFI_CRITICAL_DBM

        val validSamples = metrics
            .filter { it.networkType.equals(networkType.code, ignoreCase = true) && it.signalStrength != null }
            .sortedBy { it.createdAtMs }
            .takeLast(DEFAULT_HISTORY_LIMIT)

        val signalValues = validSamples.map { it.signalStrength!!.toDouble() }
        val shortSmaList = computeSimpleMovingAverage(signalValues, safeShort)
        val longSmaList = computeSimpleMovingAverage(signalValues, safeLong)

        val historicalPoints = validSamples.mapIndexed { idx, entity ->
            SmaTrendPoint(
                timestampMs = entity.createdAtMs,
                rawSignalDbm = entity.signalStrength!!.toDouble(),
                shortSmaDbm = shortSmaList[idx],
                longSmaDbm = longSmaList[idx],
                isForecast = false
            )
        }

        if (validSamples.size < safeShort) {
            val remaining = safeShort - validSamples.size
            return SmaSignalForecast(
                networkType = networkType,
                shortWindow = safeShort,
                longWindow = safeLong,
                forecastSteps = safeSteps,
                sampleCount = validSamples.size,
                minSamplesRequired = safeShort,
                hasEnoughData = false,
                latestRawDbm = signalValues.lastOrNull(),
                warningThresholdDbm = warningThreshold,
                criticalThresholdDbm = criticalThreshold,
                trendDirection = SmaTrendDirection.INSUFFICIENT_DATA,
                degradationRisk = DegradationRisk.UNKNOWN,
                series = historicalPoints,
                summary = "Collected ${validSamples.size}/$safeShort real ${networkType.label} signal samples ($remaining more needed for SMA-$safeShort).",
                recommendation = "Keep live monitoring active or tap Probe Now to populate Room signal history."
            )
        }

        val filledShortSmas = shortSmaList.filterNotNull()
        val latestRaw = signalValues.last()
        val latestShortSma = filledShortSmas.last()
        val latestLongSma = longSmaList.lastOrNull { it != null }

        // Crossover delta: fast SMA minus baseline SMA (negative => downward momentum)
        val crossoverDelta = if (latestLongSma != null) {
            latestShortSma - latestLongSma
        } else if (filledShortSmas.size >= 2) {
            latestShortSma - filledShortSmas.first()
        } else {
            0.0
        }

        // Per-step SMA drift computed over the trailing short-SMA steps (smooth & noise-resistant)
        val trailingSmas = filledShortSmas.takeLast(safeShort + 1)
        val stepDriftDbm = if (trailingSmas.size >= 2) {
            val span = (trailingSmas.size - 1).toDouble()
            ((trailingSmas.last() - trailingSmas.first()) / span).coerceIn(-8.0, 8.0)
        } else {
            // Single filled SMA window available: compare latest raw reading gently against SMA
            ((latestRaw - latestShortSma) * 0.5).coerceIn(-6.0, 6.0)
        }

        // Estimate average sampling interval from Room timestamps
        val avgStepMs = if (validSamples.size >= 2) {
            val deltas = validSamples.zipWithNext { a, b -> (b.createdAtMs - a.createdAtMs).coerceAtLeast(1_000L) }
            deltas.average().toLong().coerceIn(2_000L, 600_000L)
        } else {
            DEFAULT_STEP_MS
        }

        val lastTimestampMs = validSamples.last().createdAtMs
        val forecastPoints = (1..safeSteps).map { step ->
            val projected = (latestShortSma + stepDriftDbm * step).coerceIn(MIN_VALID_DBM, MAX_VALID_DBM)
            SmaTrendPoint(
                timestampMs = lastTimestampMs + avgStepMs * step,
                rawSignalDbm = null,
                shortSmaDbm = projected,
                longSmaDbm = null,
                isForecast = true
            )
        }

        val projectedNext = forecastPoints.firstOrNull()?.shortSmaDbm
        val projectedHorizon = forecastPoints.lastOrNull()?.shortSmaDbm

        val estimatedStepsToThreshold = when {
            latestShortSma <= warningThreshold -> 0
            stepDriftDbm <= -0.25 -> ceil((latestShortSma - warningThreshold) / abs(stepDriftDbm)).toInt().coerceIn(1, 99)
            else -> null
        }

        val crossingCritical = (projectedHorizon != null && projectedHorizon <= criticalThreshold) ||
            latestShortSma <= criticalThreshold
        val crossingWarning = (projectedHorizon != null && projectedHorizon <= warningThreshold) ||
            latestShortSma <= warningThreshold

        val trendDirection = when {
            crossingCritical && (stepDriftDbm <= -0.4 || crossoverDelta <= -1.5 || latestShortSma <= criticalThreshold) ->
                SmaTrendDirection.DEGRADING_RAPIDLY
            stepDriftDbm <= -2.0 || crossoverDelta <= -4.5 ->
                SmaTrendDirection.DEGRADING_RAPIDLY
            stepDriftDbm <= -0.6 || crossoverDelta <= -2.0 || (crossingWarning && stepDriftDbm < -0.2) ->
                SmaTrendDirection.DEGRADING
            stepDriftDbm >= 0.6 && crossoverDelta >= 1.2 ->
                SmaTrendDirection.IMPROVING
            else ->
                SmaTrendDirection.STABLE
        }

        val risk = when {
            crossingCritical || trendDirection == SmaTrendDirection.DEGRADING_RAPIDLY -> {
                if (latestShortSma <= criticalThreshold || (projectedNext != null && projectedNext <= criticalThreshold)) {
                    DegradationRisk.CRITICAL
                } else {
                    DegradationRisk.HIGH
                }
            }
            trendDirection == SmaTrendDirection.DEGRADING || crossingWarning -> {
                if (crossingWarning) DegradationRisk.HIGH else DegradationRisk.MODERATE
            }
            else -> DegradationRisk.LOW
        }

        val summary = buildString {
            append("${networkType.label} SMA-$safeShort is ${String.format(Locale.US, "%.1f", latestShortSma)} dBm")
            if (latestLongSma != null) {
                append(" vs SMA-$safeLong ${String.format(Locale.US, "%.1f", latestLongSma)} dBm (Δ ${String.format(Locale.US, "%+.1f", crossoverDelta)} dBm)")
            }
            if (projectedHorizon != null) {
                append(". Forecast (+$safeSteps): ${String.format(Locale.US, "%.1f", projectedHorizon)} dBm (${String.format(Locale.US, "%+.1f", stepDriftDbm)} dBm/step).")
            }
        }

        val alternativeLabel = if (networkType == NetworkType.WIFI) "Cellular" else "Wi-Fi"
        val recommendation = when (trendDirection) {
            SmaTrendDirection.DEGRADING_RAPIDLY ->
                "Signal fading rapidly toward ${criticalThreshold.toInt()} dBm threshold — prepare immediate handover to validated $alternativeLabel."
            SmaTrendDirection.DEGRADING -> {
                val stepsNote = estimatedStepsToThreshold?.let {
                    if (it == 0) "already below ${warningThreshold.toInt()} dBm" else "est. ~$it intervals to ${warningThreshold.toInt()} dBm"
                } ?: "downward SMA momentum"
                "Downward SMA drift detected ($stepsNote). Monitor closely or enable Auto-Switch fallback to $alternativeLabel."
            }
            SmaTrendDirection.IMPROVING ->
                "${networkType.label} signal moving average is strengthening; retain current interface."
            SmaTrendDirection.STABLE ->
                if (crossingWarning) {
                    "Signal is steady but currently weak (${latestShortSma.toInt()} dBm); keep $alternativeLabel ready as backup."
                } else {
                    "${networkType.label} signal moving average is stable above degradation threshold."
                }
            SmaTrendDirection.INSUFFICIENT_DATA ->
                "Continue collecting Room samples."
        }

        return SmaSignalForecast(
            networkType = networkType,
            shortWindow = safeShort,
            longWindow = safeLong,
            forecastSteps = safeSteps,
            sampleCount = validSamples.size,
            minSamplesRequired = safeShort,
            hasEnoughData = true,
            latestRawDbm = latestRaw,
            latestShortSmaDbm = latestShortSma,
            latestLongSmaDbm = latestLongSma,
            smaCrossoverDeltaDbm = crossoverDelta,
            driftPerStepDbm = stepDriftDbm,
            projectedDbmNextStep = projectedNext,
            projectedDbmHorizon = projectedHorizon,
            warningThresholdDbm = warningThreshold,
            criticalThresholdDbm = criticalThreshold,
            estimatedStepsToThreshold = estimatedStepsToThreshold,
            trendDirection = trendDirection,
            degradationRisk = risk,
            series = historicalPoints + forecastPoints,
            summary = summary,
            recommendation = recommendation
        )
    }
}
