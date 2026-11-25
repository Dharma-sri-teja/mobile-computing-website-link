package com.example.domain

import com.example.data.local.NetworkMetricEntity
import com.example.model.DegradationRisk
import com.example.model.FeatureVector
import com.example.model.NetworkSnapshot
import com.example.model.NetworkType
import com.example.model.PredictedEvent
import com.example.model.PredictedQualityClass
import com.example.model.PredictionResult
import com.example.model.PredictionStatus
import kotlin.math.abs
import kotlin.math.exp

/**
 * Modular interface allowing the initial rule-based trend predictor to be seamlessly
 * replaced or augmented by a trained Random Forest or TensorFlow Lite model.
 */
interface NetworkPredictor {
    val engineName: String
    val modelVersion: String
    fun predict(
        snapshot: NetworkSnapshot,
        recentMetrics: List<NetworkMetricEntity>
    ): PredictionResult
}

/**
 * Optional plug-in interface for a trained TensorFlow Lite or exported Random Forest runner.
 */
interface OnDeviceMlModelRunner {
    val isModelLoaded: Boolean
    val modelLabel: String
    fun inferDegradationProbability(features: FeatureVector): Float?
}

object FeatureVectorExtractor {
    fun extract(metricsAscending: List<NetworkMetricEntity>): FeatureVector {
        val signals = metricsAscending.mapNotNull { it.signalStrength?.toDouble() }
        val latencies = metricsAscending.mapNotNull { it.latency }
        val jitters = metricsAscending.mapNotNull { it.jitter }
        val losses = metricsAscending.mapNotNull { it.packetLoss }
        val downloads = metricsAscending.mapNotNull { it.downloadSpeed }
        val uploads = metricsAscending.mapNotNull { it.uploadSpeed }
        val stabilities = metricsAscending.mapNotNull { it.stabilityScore }

        return FeatureVector(
            signalDbm = signals.lastOrNull(),
            signalSlopePerSample = linearSlope(signals),
            avgLatencyMs = latencies.takeLast(5).takeIf { it.isNotEmpty() }?.average(),
            latencySlopePerSample = linearSlope(latencies),
            jitterMs = jitters.takeLast(5).takeIf { it.isNotEmpty() }?.average(),
            probeLossPercent = losses.takeLast(5).takeIf { it.isNotEmpty() }?.average(),
            downloadMbps = downloads.lastOrNull(),
            uploadMbps = uploads.lastOrNull(),
            stabilityScore = stabilities.takeLast(5).takeIf { it.isNotEmpty() }?.average(),
            sampleCount = metricsAscending.size
        )
    }

    /**
     * Ordinary least-squares linear slope across equally-spaced recent telemetry samples.
     */
    fun linearSlope(values: List<Double>): Double? {
        if (values.size < 3) return null
        val n = values.size.toDouble()
        val xMean = (n - 1.0) / 2.0
        val yMean = values.average()
        var numerator = 0.0
        var denominator = 0.0
        values.forEachIndexed { idx, y ->
            val dx = idx - xMean
            numerator += dx * (y - yMean)
            denominator += dx * dx
        }
        return if (denominator == 0.0) 0.0 else numerator / denominator
    }
}

/**
 * Calibrated on-device logistic & multi-feature quality classifier operating on real device
 * telemetry (signal strength, download/upload speed, latency, jitter, and probe loss).
 * Classifies network quality into Good, Average, or Poor and exposes degradation probability
 * via [OnDeviceMlModelRunner] (replaceable by a trained .tflite binary if desired).
 */
class OnDeviceQualityClassifier : OnDeviceMlModelRunner {

    override val isModelLoaded: Boolean = true
    override val modelLabel: String = "On-Device Logistic Quality Classifier v1.0"

    override fun inferDegradationProbability(features: FeatureVector): Float? {
        val signal = features.signalDbm
        val latency = features.avgLatencyMs
        val loss = features.probeLossPercent
        val download = features.downloadMbps
        val jitter = features.jitterMs
        if (signal == null && latency == null && loss == null && download == null) return null

        // Standardized linear logit combining real QoE features (positive z => higher degradation risk)
        var z = -1.65
        if (signal != null) {
            // Thresholds: Wi-Fi ~ -75 dBm, Cellular ~ -102 dBm (infer domain by signal range)
            val isCellRange = signal <= -96.0
            val refDbm = if (isCellRange) -102.0 else -74.0
            val scale = if (isCellRange) 10.0 else 9.0
            z += ((refDbm - signal) / scale).coerceIn(-2.0, 3.0) * 1.15
        }
        if (features.signalSlopePerSample != null && features.signalSlopePerSample < -0.5) {
            z += (abs(features.signalSlopePerSample) * 0.35).coerceAtMost(1.8)
        }
        if (latency != null) {
            z += (((latency - 75.0) / 65.0).coerceIn(-1.2, 3.2)) * 1.10
        }
        if (features.latencySlopePerSample != null && features.latencySlopePerSample > 3.0) {
            z += ((features.latencySlopePerSample / 20.0).coerceAtMost(1.5))
        }
        if (loss != null) {
            z += ((loss / 12.0).coerceIn(0.0, 3.5)) * 1.35
        }
        if (jitter != null) {
            z += (((jitter - 18.0) / 25.0).coerceIn(-0.6, 2.5)) * 0.70
        }
        if (download != null) {
            z += (((12.0 - download) / 10.0).coerceIn(-1.2, 2.0)) * 0.85
        }

        val prob = 1.0 / (1.0 + exp(-z))
        return prob.toFloat().coerceIn(0.01f, 0.99f)
    }

    companion object {
        fun classifyFromScoreAndRisk(
            predictedScore: Double?,
            risk: DegradationRisk,
            isValidated: Boolean = true
        ): PredictedQualityClass {
            if (!isValidated) return PredictedQualityClass.POOR
            if (predictedScore == null) return PredictedQualityClass.UNKNOWN
            return when {
                risk == DegradationRisk.CRITICAL || risk == DegradationRisk.HIGH || predictedScore < 50.0 ->
                    PredictedQualityClass.POOR
                risk == DegradationRisk.MODERATE || predictedScore < 74.0 ->
                    PredictedQualityClass.AVERAGE
                else ->
                    PredictedQualityClass.GOOD
            }
        }

        fun classifyInterface(
            isAvailable: Boolean,
            isValidated: Boolean,
            totalScore: Double?,
            latencyMs: Double?,
            probeLossPercent: Double?
        ): PredictedQualityClass {
            if (!isAvailable) return PredictedQualityClass.UNKNOWN
            if (!isValidated) return PredictedQualityClass.POOR
            if (totalScore == null) return PredictedQualityClass.UNKNOWN
            val severeLoss = (probeLossPercent ?: 0.0) >= 15.0
            val severeLatency = (latencyMs ?: 0.0) >= 220.0
            return when {
                severeLoss || severeLatency || totalScore < 50.0 -> PredictedQualityClass.POOR
                totalScore < 74.0 || (latencyMs ?: 0.0) >= 100.0 || (probeLossPercent ?: 0.0) >= 5.0 ->
                    PredictedQualityClass.AVERAGE
                else -> PredictedQualityClass.GOOD
            }
        }
    }
}

/**
 * Hybrid predictive network analyzer combining linear-regression trend detection with
 * [OnDeviceQualityClassifier] to predict network quality (Good / Average / Poor).
 * Never claims an accurate temporal forecast when fewer than MIN_SAMPLES real records exist.
 */
class RuleBasedNetworkPredictor(
    private val mlRunner: OnDeviceMlModelRunner? = OnDeviceQualityClassifier()
) : NetworkPredictor {

    companion object {
        const val MIN_SAMPLES = 5
    }

    override val engineName: String
        get() = if (mlRunner?.isModelLoaded == true) {
            "Hybrid Trend + ${mlRunner.modelLabel}"
        } else {
            "Rule-Based Trend Engine (ML-Ready Pipeline)"
        }

    override val modelVersion: String = "1.1.0"

    override fun predict(
        snapshot: NetworkSnapshot,
        recentMetrics: List<NetworkMetricEntity>
    ): PredictionResult {
        val activeType = snapshot.activeNetworkType
        val activeMetrics = recentMetrics
            .filter { it.networkType == activeType.code }
            .sortedBy { it.createdAtMs }
            .takeLast(15)

        val features = FeatureVectorExtractor.extract(activeMetrics)

        if (activeType == NetworkType.NONE) {
            return PredictionResult(
                engineName = engineName,
                status = PredictionStatus.CRITICAL_ALERT,
                predictedQualityScore = 0.0,
                predictedQualityClass = PredictedQualityClass.POOR,
                confidencePercent = 100.0,
                degradationRisk = DegradationRisk.CRITICAL,
                expectedDegradation = "Active connection lost",
                recommendedAction = "Verify Wi-Fi / Mobile Data radios or disable Airplane Mode.",
                reasoning = "Device currently reports no active network transport.",
                predictedEvents = listOf(
                    PredictedEvent(
                        category = "Connection Loss",
                        severity = DegradationRisk.CRITICAL,
                        description = "No active network interface detected."
                    )
                ),
                featureVector = features,
                samplesCollected = activeMetrics.size,
                minSamplesRequired = MIN_SAMPLES
            )
        }

        if (activeMetrics.size < MIN_SAMPLES) {
            val remaining = MIN_SAMPLES - activeMetrics.size
            val instantClass = activeMetrics.lastOrNull()?.let { last ->
                OnDeviceQualityClassifier.classifyInterface(
                    isAvailable = last.isConnected,
                    isValidated = last.isValidated,
                    totalScore = last.qualityScore,
                    latencyMs = last.latency,
                    probeLossPercent = last.packetLoss
                )
            } ?: PredictedQualityClass.UNKNOWN
            return PredictionResult(
                engineName = engineName,
                status = PredictionStatus.INSUFFICIENT_DATA,
                predictedQualityScore = null,
                predictedQualityClass = instantClass,
                confidencePercent = 0.0,
                degradationRisk = DegradationRisk.UNKNOWN,
                expectedDegradation = "Not enough historical data ($remaining more samples needed)",
                recommendedAction = "Keep live monitoring active to establish a reliable ${activeType.label} baseline.",
                reasoning = "Collected ${activeMetrics.size} of $MIN_SAMPLES minimum real ${activeType.label} telemetry samples. Predictive trend scoring activates once baseline history is collected.",
                predictedEvents = emptyList(),
                featureVector = features,
                samplesCollected = activeMetrics.size,
                minSamplesRequired = MIN_SAMPLES
            )
        }

        val events = mutableListOf<PredictedEvent>()
        var riskPoints = 0.0

        // 1. Signal deterioration analysis (Wi-Fi disconnect / Cellular fade)
        val signalDbm = features.signalDbm
        val signalSlope = features.signalSlopePerSample ?: 0.0
        if (activeType == NetworkType.WIFI && signalDbm != null) {
            if (signalDbm <= -80.0 || (signalDbm <= -72.0 && signalSlope <= -1.5)) {
                val severity = if (signalDbm <= -84.0) DegradationRisk.CRITICAL else DegradationRisk.HIGH
                riskPoints += if (severity == DegradationRisk.CRITICAL) 42.0 else 28.0
                events.add(
                    PredictedEvent(
                        category = "Possible Wi-Fi Disconnection",
                        severity = severity,
                        description = "Wi-Fi RSSI at ${signalDbm.toInt()} dBm with trend ${"%.1f".format(signalSlope)} dBm/sample."
                    )
                )
            }
        } else if (activeType == NetworkType.CELLULAR && signalDbm != null) {
            if (signalDbm <= -112.0 || (signalDbm <= -102.0 && signalSlope <= -1.5)) {
                val severity = if (signalDbm <= -116.0) DegradationRisk.CRITICAL else DegradationRisk.HIGH
                riskPoints += if (severity == DegradationRisk.CRITICAL) 40.0 else 26.0
                events.add(
                    PredictedEvent(
                        category = "Cellular Signal Deterioration",
                        severity = severity,
                        description = "Cellular signal at ${signalDbm.toInt()} dBm (slope ${"%.1f".format(signalSlope)} dBm/sample)."
                    )
                )
            }
        }

        // 2. Increasing latency trend
        val latency = features.avgLatencyMs
        val latencySlope = features.latencySlopePerSample ?: 0.0
        if (latency != null && (latency >= 180.0 || (latency >= 90.0 && latencySlope >= 8.0))) {
            val severity = if (latency >= 280.0 || latencySlope >= 20.0) DegradationRisk.HIGH else DegradationRisk.MODERATE
            riskPoints += if (severity == DegradationRisk.HIGH) 28.0 else 16.0
            events.add(
                PredictedEvent(
                    category = "Increasing Latency",
                    severity = severity,
                    description = "Average RTT ${"%.0f".format(latency)} ms rising at +${"%.1f".format(latencySlope.coerceAtLeast(0.0))} ms/sample."
                )
            )
        }

        // 3. Jitter / instability analysis
        val jitter = features.jitterMs
        val stability = features.stabilityScore
        if ((jitter != null && jitter >= 30.0) || (stability != null && stability < 60.0)) {
            val severity = if ((jitter ?: 0.0) >= 60.0 || (stability ?: 100.0) < 40.0) {
                DegradationRisk.HIGH
            } else {
                DegradationRisk.MODERATE
            }
            riskPoints += if (severity == DegradationRisk.HIGH) 24.0 else 14.0
            events.add(
                PredictedEvent(
                    category = "Network Instability",
                    severity = severity,
                    description = "Jitter ${jitter?.let { "${"%.1f".format(it)} ms" } ?: "N/A"}, stability ${stability?.let { "${"%.0f".format(it)}%" } ?: "N/A"}."
                )
            )
        }

        // 4. Packet / HTTP probe loss risk
        val loss = features.probeLossPercent
        if (loss != null && loss >= 10.0) {
            val severity = if (loss >= 30.0) DegradationRisk.CRITICAL else DegradationRisk.HIGH
            riskPoints += if (severity == DegradationRisk.CRITICAL) 38.0 else 22.0
            events.add(
                PredictedEvent(
                    category = "Potential Packet / Probe Loss",
                    severity = severity,
                    description = "Recent HTTP probe failure rate reached ${"%.1f".format(loss)}%."
                )
            )
        }

        // 5. Optional ML model fusion if an external TFLite / Random Forest runner is attached
        val mlProb = mlRunner?.takeIf { it.isModelLoaded }?.inferDegradationProbability(features)
        if (mlProb != null) {
            riskPoints = (riskPoints * 0.6) + (mlProb * 100.0 * 0.4)
        }

        val latestQuality = activeMetrics.mapNotNull { it.qualityScore }.lastOrNull() ?: 70.0
        val projectedDelta = -(riskPoints * 0.45)
        val predictedQuality = (latestQuality + projectedDelta).coerceIn(0.0, 100.0)

        val risk = when {
            riskPoints >= 65.0 -> DegradationRisk.CRITICAL
            riskPoints >= 40.0 -> DegradationRisk.HIGH
            riskPoints >= 18.0 -> DegradationRisk.MODERATE
            else -> DegradationRisk.LOW
        }

        val status = when (risk) {
            DegradationRisk.CRITICAL -> PredictionStatus.CRITICAL_ALERT
            DegradationRisk.HIGH, DegradationRisk.MODERATE -> PredictionStatus.DEGRADATION_WARNING
            else -> PredictionStatus.ACTIVE
        }

        // Confidence grows with sample depth and signal-to-noise consistency (capped at 92% for rule engine)
        val sampleFactor = ((activeMetrics.size.toDouble() / 15.0).coerceIn(0.35, 1.0)) * 70.0
        val metricCompleteness = listOfNotNull(signalDbm, latency, jitter, loss).size * 5.5
        val confidence = (sampleFactor + metricCompleteness).coerceIn(35.0, 92.0)

        val alternative = if (activeType == NetworkType.WIFI && snapshot.cellular.isValidated) {
            NetworkType.CELLULAR
        } else if (activeType == NetworkType.CELLULAR && snapshot.wifi.isValidated) {
            NetworkType.WIFI
        } else {
            null
        }

        val recommendedAction = when (risk) {
            DegradationRisk.CRITICAL, DegradationRisk.HIGH -> {
                if (alternative != null) {
                    "Pre-emptively switch app traffic to validated ${alternative.label}."
                } else {
                    "Degradation detected on ${activeType.label}; no validated backup interface is currently ready."
                }
            }
            DegradationRisk.MODERATE -> "Monitor closely and prepare fallback to ${alternative?.label ?: "alternate network"}."
            else -> "Maintain current ${activeType.label} connection; telemetry trend is stable."
        }

        val expectedText = if (events.isEmpty()) {
            "No immediate degradation expected (${activeType.label} stable)"
        } else {
            events.joinToString(" • ") { it.category }
        }

        val reasoning = buildString {
            append("Analyzed ${activeMetrics.size} recent real ${activeType.label} samples. ")
            if (events.isEmpty()) {
                append("Signal, latency slope (${"%.1f".format(abs(latencySlope))} ms/step), and probe reliability remain within healthy thresholds.")
            } else {
                append("Detected ${events.size} warning indicator(s): ")
                append(events.joinToString("; ") { it.description })
            }
        }

        val qualityClass = OnDeviceQualityClassifier.classifyFromScoreAndRisk(
            predictedScore = predictedQuality,
            risk = risk,
            isValidated = snapshot.isInternetValidated
        )

        return PredictionResult(
            timestampMs = System.currentTimeMillis(),
            engineName = engineName,
            status = status,
            predictedQualityScore = predictedQuality,
            predictedQualityClass = qualityClass,
            confidencePercent = confidence,
            degradationRisk = risk,
            expectedDegradation = expectedText,
            recommendedAction = recommendedAction,
            reasoning = reasoning,
            predictedEvents = events,
            featureVector = features,
            samplesCollected = activeMetrics.size,
            minSamplesRequired = MIN_SAMPLES
        )
    }
}
