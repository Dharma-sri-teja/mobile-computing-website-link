package com.example.domain

import com.example.model.AppSettings
import com.example.model.ExplainableScoreBreakdown
import com.example.model.NetworkComparisonResult
import com.example.model.NetworkScoreDetail
import com.example.model.NetworkSnapshot
import com.example.model.NetworkType
import com.example.model.PerformanceMeasurement
import com.example.model.PredictedQualityClass
import com.example.model.PreferredNetwork
import com.example.model.ScoreContributionItem
import com.example.model.ScoringMetricId
import com.example.model.ScoringWeights
import java.util.Locale

/**
 * Explainable weighted scoring algorithm (0..100) comparing Wi-Fi and Cellular networks.
 * Strictly prohibits recommending any interface lacking validated internet connectivity.
 */
class NetworkScoringEngine {

    fun compareNetworks(
        snapshot: NetworkSnapshot,
        wifiMeasurement: PerformanceMeasurement?,
        cellularMeasurement: PerformanceMeasurement?,
        settings: AppSettings
    ): NetworkComparisonResult {
        val weights = settings.scoringWeights.normalized()

        val wifiScore = scoreInterface(
            type = NetworkType.WIFI,
            isAvailable = snapshot.wifi.isConnected,
            isValidated = snapshot.wifi.isValidated,
            signalDbm = snapshot.wifi.rssiDbm,
            signalLevel = snapshot.wifi.signalLevel,
            measurement = wifiMeasurement,
            linkBandwidthDownKbps = snapshot.wifi.downstreamBandwidthKbps,
            linkBandwidthUpKbps = snapshot.wifi.upstreamBandwidthKbps,
            weights = weights
        )

        val cellularScore = scoreInterface(
            type = NetworkType.CELLULAR,
            isAvailable = snapshot.cellular.isConnected,
            isValidated = snapshot.cellular.isValidated,
            signalDbm = snapshot.cellular.signalStrengthDbm,
            signalLevel = snapshot.cellular.signalLevel,
            measurement = cellularMeasurement,
            linkBandwidthDownKbps = snapshot.cellular.downstreamBandwidthKbps,
            linkBandwidthUpKbps = snapshot.cellular.upstreamBandwidthKbps,
            weights = weights
        )

        val wifiEligible = wifiScore.isAvailable && wifiScore.isValidated && wifiScore.totalScore != null
        val cellEligible = cellularScore.isAvailable && cellularScore.isValidated && cellularScore.totalScore != null

        val wifiPoints = wifiScore.totalScore ?: -1.0
        val cellPoints = cellularScore.totalScore ?: -1.0

        val bestAvailable = when {
            wifiEligible && cellEligible -> if (wifiPoints >= cellPoints) NetworkType.WIFI else NetworkType.CELLULAR
            wifiEligible -> NetworkType.WIFI
            cellEligible -> NetworkType.CELLULAR
            else -> NetworkType.NONE
        }

        // Apply user preference bonus only when both are validated and healthy
        val recommended = when {
            !wifiEligible && !cellEligible -> NetworkType.NONE
            wifiEligible && !cellEligible -> NetworkType.WIFI
            cellEligible && !wifiEligible -> NetworkType.CELLULAR
            else -> {
                val wifiAdjusted = wifiPoints + if (settings.preferredNetwork == PreferredNetwork.WIFI) 6.0 else 0.0
                val cellAdjusted = cellPoints + if (settings.preferredNetwork == PreferredNetwork.CELLULAR) 6.0 else 0.0
                if (wifiAdjusted >= cellAdjusted) NetworkType.WIFI else NetworkType.CELLULAR
            }
        }

        val active = snapshot.activeNetworkType
        val activeScore = when (active) {
            NetworkType.WIFI -> wifiScore.totalScore ?: 0.0
            NetworkType.CELLULAR -> cellularScore.totalScore ?: 0.0
            else -> 0.0
        }
        val recScore = when (recommended) {
            NetworkType.WIFI -> wifiScore.totalScore ?: 0.0
            NetworkType.CELLULAR -> cellularScore.totalScore ?: 0.0
            else -> 0.0
        }

        val shouldSwitch = recommended != NetworkType.NONE &&
            recommended != active &&
            (recScore - activeScore >= settings.switchingThresholdDelta ||
                (active == NetworkType.WIFI && !wifiScore.isValidated) ||
                (active == NetworkType.CELLULAR && !cellularScore.isValidated))

        val reason = buildExplanation(
            active = active,
            recommended = recommended,
            wifiScore = wifiScore,
            cellularScore = cellularScore,
            thresholdDelta = settings.switchingThresholdDelta
        )

        return NetworkComparisonResult(
            timestampMs = System.currentTimeMillis(),
            activeNetwork = active,
            bestAvailableNetwork = bestAvailable,
            recommendedNetwork = recommended,
            wifiScore = wifiScore,
            cellularScore = cellularScore,
            reason = reason,
            shouldSwitch = shouldSwitch
        )
    }

    fun scoreInterface(
        type: NetworkType,
        isAvailable: Boolean,
        isValidated: Boolean,
        signalDbm: Int?,
        signalLevel: Int?,
        measurement: PerformanceMeasurement?,
        linkBandwidthDownKbps: Int?,
        linkBandwidthUpKbps: Int?,
        weights: ScoringWeights
    ): NetworkScoreDetail {
        if (!isAvailable) {
            return NetworkScoreDetail(
                networkType = type,
                isAvailable = false,
                isValidated = false,
                totalScore = null,
                signalComponent = null,
                latencyComponent = null,
                downloadComponent = null,
                uploadComponent = null,
                stabilityComponent = null,
                packetLossComponent = null,
                summaryNote = "${type.label} is not currently connected."
            )
        }

        val signalScore = scoreSignal(type, signalDbm, signalLevel)
        val latencyScore = measurement?.avgLatencyMs?.let { scoreLatency(it) }
        val downloadScore = (measurement?.downloadSpeedMbps
            ?: linkBandwidthDownKbps?.let { (it / 1000.0).coerceAtMost(150.0) * 0.5 })
            ?.let { scoreDownload(it) }
        val uploadScore = (measurement?.uploadSpeedMbps
            ?: linkBandwidthUpKbps?.let { (it / 1000.0).coerceAtMost(80.0) * 0.5 })
            ?.let { scoreUpload(it) }
        val stabilityScore = measurement?.stabilityScore
        val lossScore = measurement?.probeLossPercent?.let { (100.0 - it * 2.5).coerceIn(0.0, 100.0) }

        // Only aggregate components that have genuine measurements/capabilities
        var weightedSum = 0.0
        var activeWeight = 0.0

        fun accumulate(component: Double?, weight: Float) {
            if (component != null) {
                weightedSum += component * weight
                activeWeight += weight
            }
        }

        accumulate(signalScore, weights.signalWeight)
        accumulate(latencyScore, weights.latencyWeight)
        accumulate(downloadScore, weights.downloadWeight)
        accumulate(stabilityScore, weights.stabilityWeight)
        accumulate(lossScore, weights.packetLossWeight)
        accumulate(uploadScore, weights.uploadWeight)

        val rawScore = if (activeWeight > 0.0) {
            (weightedSum / activeWeight).coerceIn(0.0, 100.0)
        } else {
            null
        }

        // Hard rule: never give a passing recommendation score to a non-validated interface
        val finalScore = if (!isValidated && rawScore != null) {
            rawScore.coerceAtMost(15.0)
        } else {
            rawScore
        }

        val summaryNote = when {
            !isValidated -> "Connected without validated internet access — excluded from recommendations."
            finalScore == null -> "Connected, awaiting telemetry measurement."
            finalScore >= 80 -> "Strong, validated ${type.label} quality."
            finalScore >= 60 -> "Moderate validated ${type.label} performance."
            else -> "Degraded ${type.label} performance detected."
        }

        val predictedClass = OnDeviceQualityClassifier.classifyInterface(
            isAvailable = true,
            isValidated = isValidated,
            totalScore = finalScore,
            latencyMs = measurement?.avgLatencyMs,
            probeLossPercent = measurement?.probeLossPercent
        )

        return NetworkScoreDetail(
            networkType = type,
            isAvailable = true,
            isValidated = isValidated,
            totalScore = finalScore,
            signalComponent = signalScore,
            latencyComponent = latencyScore,
            downloadComponent = downloadScore,
            uploadComponent = uploadScore,
            stabilityComponent = stabilityScore,
            packetLossComponent = lossScore,
            predictedQualityClass = predictedClass,
            summaryNote = summaryNote
        )
    }

    private fun scoreSignal(type: NetworkType, dbm: Int?, level: Int?): Double? {
        if (dbm != null) {
            return if (type == NetworkType.WIFI) {
                // -35 dBm = 100, -95 dBm = 0
                (((dbm + 95.0) / 60.0) * 100.0).coerceIn(0.0, 100.0)
            } else {
                // Cellular LTE/5G RSRP: -70 dBm = 100, -125 dBm = 0
                (((dbm + 125.0) / 55.0) * 100.0).coerceIn(0.0, 100.0)
            }
        }
        return level?.let { (it.coerceIn(0, 4) * 25.0) }
    }

    private fun scoreLatency(avgMs: Double): Double {
        return when {
            avgMs <= 25.0 -> 100.0
            avgMs <= 60.0 -> 100.0 - ((avgMs - 25.0) / 35.0) * 15.0
            avgMs <= 150.0 -> 85.0 - ((avgMs - 60.0) / 90.0) * 35.0
            avgMs <= 400.0 -> 50.0 - ((avgMs - 150.0) / 250.0) * 40.0
            else -> 5.0
        }.coerceIn(0.0, 100.0)
    }

    private fun scoreDownload(mbps: Double): Double {
        return when {
            mbps >= 50.0 -> 100.0
            mbps >= 20.0 -> 80.0 + ((mbps - 20.0) / 30.0) * 20.0
            mbps >= 5.0 -> 50.0 + ((mbps - 5.0) / 15.0) * 30.0
            else -> (mbps / 5.0) * 50.0
        }.coerceIn(0.0, 100.0)
    }

    private fun scoreUpload(mbps: Double): Double {
        return when {
            mbps >= 25.0 -> 100.0
            mbps >= 10.0 -> 75.0 + ((mbps - 10.0) / 15.0) * 25.0
            mbps >= 2.0 -> 40.0 + ((mbps - 2.0) / 8.0) * 35.0
            else -> (mbps / 2.0) * 40.0
        }.coerceIn(0.0, 100.0)
    }

    private fun buildExplanation(
        active: NetworkType,
        recommended: NetworkType,
        wifiScore: NetworkScoreDetail,
        cellularScore: NetworkScoreDetail,
        thresholdDelta: Int
    ): String {
        if (recommended == NetworkType.NONE) {
            return when {
                wifiScore.isAvailable && !wifiScore.isValidated ->
                    "Wi-Fi is connected without validated internet and no validated cellular alternative is available."
                cellularScore.isAvailable && !cellularScore.isValidated ->
                    "Cellular is connected without validated internet and no validated Wi-Fi alternative is available."
                else -> "No network interface currently has validated internet connectivity."
            }
        }
        val wifiVal = wifiScore.totalScore?.let { "${"%.0f".format(it)}/100" } ?: "N/A"
        val cellVal = cellularScore.totalScore?.let { "${"%.0f".format(it)}/100" } ?: "N/A"

        return when {
            recommended == NetworkType.WIFI && !cellularScore.isValidated ->
                "Wi-Fi ($wifiVal) is the only interface with validated internet access."
            recommended == NetworkType.CELLULAR && !wifiScore.isValidated ->
                if (wifiScore.isAvailable) {
                    "Wi-Fi lacks validated internet; Cellular ($cellVal) is validated and recommended."
                } else {
                    "Cellular ($cellVal) is the only active validated interface."
                }
            recommended == active ->
                "Retain ${active.label}: Wi-Fi ($wifiVal) vs Cellular ($cellVal). Active interface meets quality targets."
            else -> {
                val delta = kotlin.math.abs((wifiScore.totalScore ?: 0.0) - (cellularScore.totalScore ?: 0.0))
                if (delta >= thresholdDelta) {
                    "Switch to ${recommended.label} recommended: outperforms ${active.label} by ${"%.1f".format(delta)} pts (Wi-Fi $wifiVal vs Cellular $cellVal)."
                } else {
                    "${recommended.label} scores slightly higher (Wi-Fi $wifiVal vs Cellular $cellVal), within ${thresholdDelta}-pt hysteresis band."
                }
            }
        }
    }

    /**
     * Produces a transparent per-metric attribution breakdown matching [scoreInterface] 1:1 so UI
     * visualizations display exact earned points, lost headroom, active weight redistribution,
     * and any unvalidated-internet score cap deduction.
     */
    fun buildExplainableBreakdown(
        type: NetworkType,
        snapshot: NetworkSnapshot,
        measurement: PerformanceMeasurement?,
        weights: ScoringWeights
    ): ExplainableScoreBreakdown {
        val normWeights = weights.normalized()
        val isConnected = when (type) {
            NetworkType.WIFI -> snapshot.wifi.isConnected
            NetworkType.CELLULAR -> snapshot.cellular.isConnected
            else -> false
        }
        val isValidated = when (type) {
            NetworkType.WIFI -> snapshot.wifi.isValidated
            NetworkType.CELLULAR -> snapshot.cellular.isValidated
            else -> false
        }
        val signalDbm = when (type) {
            NetworkType.WIFI -> snapshot.wifi.rssiDbm
            NetworkType.CELLULAR -> snapshot.cellular.signalStrengthDbm
            else -> null
        }
        val signalLevel = when (type) {
            NetworkType.WIFI -> snapshot.wifi.signalLevel
            NetworkType.CELLULAR -> snapshot.cellular.signalLevel
            else -> null
        }
        val downCapKbps = when (type) {
            NetworkType.WIFI -> snapshot.wifi.downstreamBandwidthKbps
            NetworkType.CELLULAR -> snapshot.cellular.downstreamBandwidthKbps
            else -> null
        }
        val upCapKbps = when (type) {
            NetworkType.WIFI -> snapshot.wifi.upstreamBandwidthKbps
            NetworkType.CELLULAR -> snapshot.cellular.upstreamBandwidthKbps
            else -> null
        }

        val scoreDetail = scoreInterface(
            type = type,
            isAvailable = isConnected,
            isValidated = isValidated,
            signalDbm = signalDbm,
            signalLevel = signalLevel,
            measurement = measurement,
            linkBandwidthDownKbps = downCapKbps,
            linkBandwidthUpKbps = upCapKbps,
            weights = normWeights
        )

        val signalDisplay = when {
            signalDbm != null -> "$signalDbm dBm"
            signalLevel != null -> "Level $signalLevel/4"
            else -> "N/A"
        }
        val latencyDisplay = measurement?.avgLatencyMs?.let {
            String.format(Locale.US, "%.1f ms", it)
        } ?: "N/A"
        val downloadDisplay = when {
            measurement?.downloadSpeedMbps != null ->
                String.format(Locale.US, "%.1f Mbps", measurement.downloadSpeedMbps)
            downCapKbps != null ->
                "~${String.format(Locale.US, "%.0f", (downCapKbps / 1000.0).coerceAtMost(150.0) * 0.5)} Mbps cap"
            else -> "N/A"
        }
        val stabilityDisplay = measurement?.stabilityScore?.let {
            String.format(Locale.US, "%.0f%%", it)
        } ?: "N/A"
        val lossDisplay = measurement?.probeLossPercent?.let {
            String.format(Locale.US, "%.1f%%", it)
        } ?: "N/A"
        val uploadDisplay = when {
            measurement?.uploadSpeedMbps != null ->
                String.format(Locale.US, "%.1f Mbps", measurement.uploadSpeedMbps)
            upCapKbps != null ->
                "~${String.format(Locale.US, "%.0f", (upCapKbps / 1000.0).coerceAtMost(80.0) * 0.5)} Mbps cap"
            else -> "N/A"
        }

        val signalFormula = if (type == NetworkType.WIFI) {
            "Wi-Fi RSSI: -35 dBm = 100 pts, -95 dBm = 0 pts"
        } else {
            "Cellular RSRP: -70 dBm = 100 pts, -125 dBm = 0 pts"
        }

        data class RawEntry(
            val id: ScoringMetricId,
            val display: String,
            val subScore: Double?,
            val cfgWeight: Float,
            val formula: String
        )

        val entries = listOf(
            RawEntry(
                id = ScoringMetricId.SIGNAL,
                display = signalDisplay,
                subScore = scoreDetail.signalComponent,
                cfgWeight = normWeights.signalWeight,
                formula = signalFormula
            ),
            RawEntry(
                id = ScoringMetricId.LATENCY,
                display = latencyDisplay,
                subScore = scoreDetail.latencyComponent,
                cfgWeight = normWeights.latencyWeight,
                formula = "≤ 25 ms = 100 pts; 60 ms = 85; 150 ms = 50; ≥ 400 ms = 5"
            ),
            RawEntry(
                id = ScoringMetricId.DOWNLOAD,
                display = downloadDisplay,
                subScore = scoreDetail.downloadComponent,
                cfgWeight = normWeights.downloadWeight,
                formula = "≥ 50 Mbps = 100 pts; 20 Mbps = 80; 5 Mbps = 50"
            ),
            RawEntry(
                id = ScoringMetricId.STABILITY,
                display = stabilityDisplay,
                subScore = scoreDetail.stabilityComponent,
                cfgWeight = normWeights.stabilityWeight,
                formula = "Derived from jitter variance & probe consistency (0–100)"
            ),
            RawEntry(
                id = ScoringMetricId.PACKET_LOSS,
                display = lossDisplay,
                subScore = scoreDetail.packetLossComponent,
                cfgWeight = normWeights.packetLossWeight,
                formula = "100 − (HTTPS probe loss % × 2.5); 0% loss = 100 pts"
            ),
            RawEntry(
                id = ScoringMetricId.UPLOAD,
                display = uploadDisplay,
                subScore = scoreDetail.uploadComponent,
                cfgWeight = normWeights.uploadWeight,
                formula = "≥ 25 Mbps = 100 pts; 10 Mbps = 75; 2 Mbps = 40"
            )
        )

        val activeWeightSum = entries.sumOf {
            if (it.subScore != null) it.cfgWeight.toDouble() else 0.0
        }.toFloat()

        val contributions = entries.map { entry ->
            val available = entry.subScore != null && activeWeightSum > 0f
            val effWeight = if (available) entry.cfgWeight / activeWeightSum else 0f
            val maxPts = effWeight * 100.0
            val earnedPts = if (available) (entry.subScore ?: 0.0) * effWeight else 0.0
            val lostPts = (maxPts - earnedPts).coerceAtLeast(0.0)

            ScoreContributionItem(
                id = entry.id,
                rawDisplayValue = entry.display,
                subScore = entry.subScore,
                configuredWeight = entry.cfgWeight,
                effectiveWeight = effWeight,
                maxPoints = maxPts,
                earnedPoints = earnedPts,
                lostPoints = lostPts,
                isAvailable = available,
                formulaNote = entry.formula
            )
        }

        val activeContributions = contributions.filter { it.isAvailable }
        val rawWeightedScore = if (activeContributions.isNotEmpty()) {
            activeContributions.sumOf { it.earnedPoints }.coerceIn(0.0, 100.0)
        } else {
            null
        }
        val finalScore = scoreDetail.totalScore
        val capDeduction = if (rawWeightedScore != null && finalScore != null && !isValidated) {
            (rawWeightedScore - finalScore).coerceAtLeast(0.0)
        } else {
            0.0
        }

        return ExplainableScoreBreakdown(
            networkType = type,
            isConnected = isConnected,
            isValidated = isValidated,
            rawWeightedScore = rawWeightedScore,
            finalScore = finalScore,
            validationCapDeduction = capDeduction,
            contributions = contributions,
            topContributor = activeContributions.maxByOrNull { it.earnedPoints },
            largestDrag = activeContributions.maxByOrNull { it.lostPoints }
        )
    }
}
