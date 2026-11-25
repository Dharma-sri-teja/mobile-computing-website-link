package com.example

import com.example.data.local.NetworkMetricEntity
import com.example.domain.FeatureVectorExtractor
import com.example.domain.NetworkScoringEngine
import com.example.domain.OnDeviceQualityClassifier
import com.example.domain.RuleBasedNetworkPredictor
import com.example.model.AppSettings
import com.example.model.CellularDetails
import com.example.model.DegradationRisk
import com.example.model.NetworkSnapshot
import com.example.model.NetworkType
import com.example.model.PerformanceMeasurement
import com.example.model.PredictedQualityClass
import com.example.model.PredictionStatus
import com.example.model.WifiDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkIntelligenceUnitTest {

    private val scoringEngine = NetworkScoringEngine()
    private val predictor = RuleBasedNetworkPredictor()

    @Test
    fun scoring_neverRecommendsUnvalidatedWifiOverValidatedCellular() {
        val snapshot = NetworkSnapshot(
            activeNetworkType = NetworkType.WIFI,
            hasInternet = true,
            isInternetValidated = false,
            wifi = WifiDetails(
                isWifiEnabled = true,
                isAvailable = true,
                isConnected = true,
                rssiDbm = -45,
                hasInternet = true,
                isValidated = false // Captive portal / no actual internet
            ),
            cellular = CellularDetails(
                isAvailable = true,
                isConnected = true,
                signalStrengthDbm = -92,
                isSimReady = true,
                hasInternet = true,
                isValidated = true
            )
        )

        val wifiPerf = PerformanceMeasurement(
            networkType = NetworkType.WIFI,
            avgLatencyMs = 20.0,
            jitterMs = 2.0,
            probeLossPercent = 0.0,
            downloadSpeedMbps = 80.0,
            stabilityScore = 95.0,
            isInternetReachable = false
        )
        val cellPerf = PerformanceMeasurement(
            networkType = NetworkType.CELLULAR,
            avgLatencyMs = 45.0,
            jitterMs = 6.0,
            probeLossPercent = 0.0,
            downloadSpeedMbps = 25.0,
            stabilityScore = 88.0,
            isInternetReachable = true
        )

        val result = scoringEngine.compareNetworks(snapshot, wifiPerf, cellPerf, AppSettings())

        assertEquals(NetworkType.CELLULAR, result.recommendedNetwork)
        assertEquals(NetworkType.CELLULAR, result.bestAvailableNetwork)
        assertTrue(result.shouldSwitch)
        assertTrue((result.wifiScore.totalScore ?: 100.0) <= 15.0)
    }

    @Test
    fun scoring_selectsHigherPerformingValidatedInterface() {
        val snapshot = NetworkSnapshot(
            activeNetworkType = NetworkType.WIFI,
            hasInternet = true,
            isInternetValidated = true,
            wifi = WifiDetails(
                isWifiEnabled = true,
                isAvailable = true,
                isConnected = true,
                rssiDbm = -86, // Weak Wi-Fi
                hasInternet = true,
                isValidated = true
            ),
            cellular = CellularDetails(
                isAvailable = true,
                isConnected = true,
                signalStrengthDbm = -78, // Strong 5G/LTE
                isSimReady = true,
                hasInternet = true,
                isValidated = true
            )
        )

        val wifiPerf = PerformanceMeasurement(
            networkType = NetworkType.WIFI,
            avgLatencyMs = 240.0,
            jitterMs = 48.0,
            probeLossPercent = 20.0,
            downloadSpeedMbps = 3.0,
            stabilityScore = 35.0,
            isInternetReachable = true
        )
        val cellPerf = PerformanceMeasurement(
            networkType = NetworkType.CELLULAR,
            avgLatencyMs = 28.0,
            jitterMs = 4.0,
            probeLossPercent = 0.0,
            downloadSpeedMbps = 55.0,
            stabilityScore = 94.0,
            isInternetReachable = true
        )

        val result = scoringEngine.compareNetworks(snapshot, wifiPerf, cellPerf, AppSettings())

        assertEquals(NetworkType.CELLULAR, result.recommendedNetwork)
        assertTrue(result.shouldSwitch)
        assertTrue((result.cellularScore.totalScore ?: 0.0) > (result.wifiScore.totalScore ?: 0.0) + 20.0)
    }

    @Test
    fun prediction_reportsInsufficientDataWhenFewerThanFiveSamples() {
        val snapshot = NetworkSnapshot(
            activeNetworkType = NetworkType.WIFI,
            wifi = WifiDetails(isConnected = true, isValidated = true, rssiDbm = -60)
        )
        val fewMetrics = List(3) { idx ->
            NetworkMetricEntity(
                deviceId = "test-device",
                networkType = NetworkType.WIFI.code,
                latency = 30.0 + idx,
                signalStrength = -60,
                isConnected = true,
                isValidated = true,
                createdAtMs = 1000L * (idx + 1)
            )
        }

        val prediction = predictor.predict(snapshot, fewMetrics)

        assertEquals(PredictionStatus.INSUFFICIENT_DATA, prediction.status)
        assertNull(prediction.predictedQualityScore)
        assertEquals(0.0, prediction.confidencePercent, 0.001)
    }

    @Test
    fun prediction_detectsWifiDisconnectionAndRisingLatencyTrends() {
        val snapshot = NetworkSnapshot(
            activeNetworkType = NetworkType.WIFI,
            wifi = WifiDetails(isConnected = true, isValidated = true, rssiDbm = -84),
            cellular = CellularDetails(isConnected = true, isValidated = true, signalStrengthDbm = -82)
        )

        // 6 deteriorating samples: RSSI dropping from -64 to -84, latency rising from 40 to 240ms
        val degradingMetrics = List(6) { idx ->
            NetworkMetricEntity(
                deviceId = "test-device",
                networkType = NetworkType.WIFI.code,
                latency = 40.0 + idx * 40.0,
                jitter = 10.0 + idx * 10.0,
                packetLoss = if (idx >= 4) 20.0 else 0.0,
                signalStrength = -64 - idx * 4,
                stabilityScore = 80.0 - idx * 10.0,
                qualityScore = 82.0 - idx * 10.0,
                isConnected = true,
                isValidated = true,
                createdAtMs = 10_000L * (idx + 1)
            )
        }

        val prediction = predictor.predict(snapshot, degradingMetrics)

        assertNotNull(prediction.predictedQualityScore)
        assertTrue(prediction.degradationRisk == DegradationRisk.HIGH || prediction.degradationRisk == DegradationRisk.CRITICAL)
        assertTrue(prediction.predictedEvents.any { it.category.contains("Wi-Fi Disconnection") })
        assertTrue(prediction.predictedEvents.any { it.category.contains("Increasing Latency") })
        assertTrue(prediction.confidencePercent > 40.0)
    }

    @Test
    fun featureExtractor_computesAccurateLinearSlope() {
        val slope = FeatureVectorExtractor.linearSlope(listOf(10.0, 20.0, 30.0, 40.0))
        assertNotNull(slope)
        assertEquals(10.0, slope!!, 0.001)
    }

    @Test
    fun explainableBreakdown_earnedPointsSumMatchesValidatedTotalScore() {
        val snapshot = NetworkSnapshot(
            activeNetworkType = NetworkType.WIFI,
            hasInternet = true,
            isInternetValidated = true,
            wifi = WifiDetails(
                isWifiEnabled = true,
                isAvailable = true,
                isConnected = true,
                rssiDbm = -55,
                hasInternet = true,
                isValidated = true
            )
        )
        val wifiPerf = PerformanceMeasurement(
            networkType = NetworkType.WIFI,
            avgLatencyMs = 42.0,
            jitterMs = 5.0,
            probeLossPercent = 10.0,
            downloadSpeedMbps = 35.0,
            uploadSpeedMbps = 12.0,
            stabilityScore = 82.0,
            isInternetReachable = true
        )
        val settings = AppSettings()

        val breakdown = scoringEngine.buildExplainableBreakdown(
            type = NetworkType.WIFI,
            snapshot = snapshot,
            measurement = wifiPerf,
            weights = settings.scoringWeights
        )
        val comparison = scoringEngine.compareNetworks(snapshot, wifiPerf, null, settings)

        assertNotNull(breakdown.finalScore)
        assertNotNull(breakdown.rawWeightedScore)
        assertEquals(0.0, breakdown.validationCapDeduction, 0.001)

        val sumEarned = breakdown.contributions.filter { it.isAvailable }.sumOf { it.earnedPoints }
        val sumWeights = breakdown.contributions.filter { it.isAvailable }.sumOf { it.effectiveWeight.toDouble() }
        assertEquals(1.0, sumWeights, 0.001)
        assertEquals(breakdown.rawWeightedScore!!, sumEarned, 0.05)
        assertEquals(comparison.wifiScore.totalScore!!, breakdown.finalScore!!, 0.05)
        assertNotNull(breakdown.topContributor)
        assertNotNull(breakdown.largestDrag)
    }

    @Test
    fun explainableBreakdown_reportsValidationCapDeductionWhenUnvalidated() {
        val snapshot = NetworkSnapshot(
            activeNetworkType = NetworkType.WIFI,
            hasInternet = true,
            isInternetValidated = false,
            wifi = WifiDetails(
                isWifiEnabled = true,
                isAvailable = true,
                isConnected = true,
                rssiDbm = -42,
                hasInternet = true,
                isValidated = false
            )
        )
        val wifiPerf = PerformanceMeasurement(
            networkType = NetworkType.WIFI,
            avgLatencyMs = 18.0,
            jitterMs = 2.0,
            probeLossPercent = 0.0,
            downloadSpeedMbps = 75.0,
            uploadSpeedMbps = 30.0,
            stabilityScore = 96.0,
            isInternetReachable = false
        )

        val breakdown = scoringEngine.buildExplainableBreakdown(
            type = NetworkType.WIFI,
            snapshot = snapshot,
            measurement = wifiPerf,
            weights = AppSettings().scoringWeights
        )

        assertNotNull(breakdown.rawWeightedScore)
        assertNotNull(breakdown.finalScore)
        assertTrue(breakdown.rawWeightedScore!! > 80.0)
        assertEquals(15.0, breakdown.finalScore!!, 0.001)
        assertEquals(breakdown.rawWeightedScore - 15.0, breakdown.validationCapDeduction, 0.05)
    }

    @Test
    fun qualityClassifier_mapsTelemetryToGoodAverageAndPoor() {
        assertEquals(
            PredictedQualityClass.GOOD,
            OnDeviceQualityClassifier.classifyInterface(
                isAvailable = true,
                isValidated = true,
                totalScore = 86.0,
                latencyMs = 28.0,
                probeLossPercent = 0.0
            )
        )
        assertEquals(
            PredictedQualityClass.AVERAGE,
            OnDeviceQualityClassifier.classifyInterface(
                isAvailable = true,
                isValidated = true,
                totalScore = 64.0,
                latencyMs = 95.0,
                probeLossPercent = 4.0
            )
        )
        assertEquals(
            PredictedQualityClass.POOR,
            OnDeviceQualityClassifier.classifyInterface(
                isAvailable = true,
                isValidated = true,
                totalScore = 38.0,
                latencyMs = 260.0,
                probeLossPercent = 20.0
            )
        )
        assertEquals(
            PredictedQualityClass.POOR,
            OnDeviceQualityClassifier.classifyInterface(
                isAvailable = true,
                isValidated = false,
                totalScore = 15.0,
                latencyMs = 20.0,
                probeLossPercent = 0.0
            )
        )
    }
}
