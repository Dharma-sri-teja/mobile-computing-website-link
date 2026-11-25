package com.example.model

enum class NetworkType(val label: String, val code: String) {
    WIFI("Wi-Fi", "WIFI"),
    CELLULAR("Cellular", "CELLULAR"),
    ETHERNET("Ethernet", "ETHERNET"),
    VPN("VPN", "VPN"),
    NONE("Disconnected", "NONE"),
    OTHER("Other", "OTHER");

    companion object {
        fun fromCode(code: String): NetworkType =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: NONE
    }
}

enum class MonitoringState(val label: String) {
    STOPPED("STOPPED"),
    ACTIVE("LIVE"),
    PAUSED("PAUSED")
}

enum class SwitchMode(val label: String, val code: String) {
    MANUAL("Manual", "MANUAL"),
    AUTOMATIC("Automatic", "AUTOMATIC")
}

enum class PreferredNetwork(val label: String, val code: String) {
    AUTO("Automatic (Best Score)", "AUTO"),
    WIFI("Prefer Wi-Fi", "WIFI"),
    CELLULAR("Prefer Cellular", "CELLULAR");

    companion object {
        fun fromCode(code: String): PreferredNetwork =
            entries.firstOrNull { it.code == code } ?: AUTO
    }
}

enum class PredictionStatus(val label: String) {
    INSUFFICIENT_DATA("Insufficient Historical Data"),
    ACTIVE("Active Prediction"),
    DEGRADATION_WARNING("Degradation Predicted"),
    CRITICAL_ALERT("Critical Risk Predicted")
}

enum class DegradationRisk(val label: String) {
    UNKNOWN("Unavailable"),
    LOW("Low Risk"),
    MODERATE("Moderate Risk"),
    HIGH("High Risk"),
    CRITICAL("Critical Risk")
}

enum class PredictedQualityClass(val label: String) {
    GOOD("Good"),
    AVERAGE("Average"),
    POOR("Poor"),
    UNKNOWN("Unavailable")
}

enum class ReportPeriod(val label: String, val hours: Int) {
    DAILY("Last 24 Hours", 24),
    WEEKLY("Last 7 Days", 168)
}

data class WifiDetails(
    val isWifiEnabled: Boolean = false,
    val isAvailable: Boolean = false,
    val isConnected: Boolean = false,
    val ssid: String? = null,
    val ssidRestrictionNote: String? = null,
    val rssiDbm: Int? = null,
    val signalLevel: Int? = null, // 0..4
    val frequencyMhz: Int? = null,
    val frequencyBand: String? = null,
    val linkSpeedMbps: Int? = null,
    val txLinkSpeedMbps: Int? = null,
    val rxLinkSpeedMbps: Int? = null,
    val hasInternet: Boolean = false,
    val isValidated: Boolean = false,
    val isMetered: Boolean = false,
    val downstreamBandwidthKbps: Int? = null,
    val upstreamBandwidthKbps: Int? = null
)

data class CellularDetails(
    val isAvailable: Boolean = false,
    val isConnected: Boolean = false,
    val networkGeneration: String? = null, // 2G, 3G, 4G, LTE, 5G NR
    val carrierName: String? = null,
    val signalStrengthDbm: Int? = null,
    val signalLevel: Int? = null, // 0..4
    val signalQualityLabel: String? = null,
    val rsrpDbm: Int? = null,
    val rsrqDb: Int? = null,
    val simStateLabel: String = "Unknown",
    val isSimReady: Boolean = false,
    val hasInternet: Boolean = false,
    val isValidated: Boolean = false,
    val isMetered: Boolean = true,
    val downstreamBandwidthKbps: Int? = null,
    val upstreamBandwidthKbps: Int? = null,
    val permissionNote: String? = null
)

data class NetworkSnapshot(
    val timestampMs: Long = System.currentTimeMillis(),
    val activeNetworkType: NetworkType = NetworkType.NONE,
    val boundProcessNetworkType: NetworkType? = null,
    val hasInternet: Boolean = false,
    val isInternetValidated: Boolean = false,
    val isEthernetAvailable: Boolean = false,
    val isVpnActive: Boolean = false,
    val isAirplaneModeOn: Boolean = false,
    val wifi: WifiDetails = WifiDetails(),
    val cellular: CellularDetails = CellularDetails(),
    val statusMessage: String = "Waiting for connectivity updates"
)

data class PerformanceMeasurement(
    val timestampMs: Long = System.currentTimeMillis(),
    val networkType: NetworkType = NetworkType.NONE,
    val downloadSpeedMbps: Double? = null,
    val uploadSpeedMbps: Double? = null,
    val avgLatencyMs: Double? = null,
    val minLatencyMs: Double? = null,
    val maxLatencyMs: Double? = null,
    val jitterMs: Double? = null,
    val probeLossPercent: Double? = null, // HTTP probe failure rate (distinct from ICMP)
    val probesAttempted: Int = 0,
    val probesSucceeded: Int = 0,
    val responseTimeMs: Double? = null,
    val signalStrengthDbm: Int? = null,
    val stabilityScore: Double? = null, // 0..100
    val isInternetReachable: Boolean = false,
    val probeMethodology: String = "HTTPS Timing Probe (Non-ICMP)",
    val errorNote: String? = null
)

data class ScoringWeights(
    val signalWeight: Float = 0.25f,
    val latencyWeight: Float = 0.25f,
    val downloadWeight: Float = 0.20f,
    val stabilityWeight: Float = 0.15f,
    val packetLossWeight: Float = 0.10f,
    val uploadWeight: Float = 0.05f
) {
    fun normalized(): ScoringWeights {
        val total = (signalWeight + latencyWeight + downloadWeight +
            stabilityWeight + packetLossWeight + uploadWeight).coerceAtLeast(0.01f)
        return ScoringWeights(
            signalWeight = signalWeight / total,
            latencyWeight = latencyWeight / total,
            downloadWeight = downloadWeight / total,
            stabilityWeight = stabilityWeight / total,
            packetLossWeight = packetLossWeight / total,
            uploadWeight = uploadWeight / total
        )
    }
}

data class NetworkScoreDetail(
    val networkType: NetworkType,
    val isAvailable: Boolean,
    val isValidated: Boolean,
    val totalScore: Double?, // null if unavailable
    val signalComponent: Double?,
    val latencyComponent: Double?,
    val downloadComponent: Double?,
    val uploadComponent: Double?,
    val stabilityComponent: Double?,
    val packetLossComponent: Double?,
    val summaryNote: String,
    val predictedQualityClass: PredictedQualityClass = PredictedQualityClass.UNKNOWN
)

data class NetworkComparisonResult(
    val timestampMs: Long = System.currentTimeMillis(),
    val activeNetwork: NetworkType = NetworkType.NONE,
    val bestAvailableNetwork: NetworkType = NetworkType.NONE,
    val recommendedNetwork: NetworkType = NetworkType.NONE,
    val wifiScore: NetworkScoreDetail,
    val cellularScore: NetworkScoreDetail,
    val reason: String,
    val shouldSwitch: Boolean
)

enum class ScoringMetricId(val label: String, val shortLabel: String) {
    SIGNAL("Signal Strength", "Signal"),
    LATENCY("Latency (RTT)", "Latency"),
    DOWNLOAD("Download Speed", "Download"),
    STABILITY("Connection Stability", "Stability"),
    PACKET_LOSS("Probe / Packet Loss", "Loss"),
    UPLOAD("Upload Speed", "Upload")
}

data class ScoreContributionItem(
    val id: ScoringMetricId,
    val rawDisplayValue: String,
    val subScore: Double?, // 0..100 or null when metric not yet measured
    val configuredWeight: Float, // normalized configured weight (0..1)
    val effectiveWeight: Float, // redistributed active weight among available metrics (0..1)
    val maxPoints: Double, // effectiveWeight * 100
    val earnedPoints: Double, // (subScore ?: 0) * effectiveWeight
    val lostPoints: Double, // maxPoints - earnedPoints
    val isAvailable: Boolean,
    val formulaNote: String
)

data class ExplainableScoreBreakdown(
    val networkType: NetworkType,
    val isConnected: Boolean,
    val isValidated: Boolean,
    val rawWeightedScore: Double?,
    val finalScore: Double?,
    val validationCapDeduction: Double,
    val contributions: List<ScoreContributionItem>,
    val topContributor: ScoreContributionItem?,
    val largestDrag: ScoreContributionItem?
)

data class FeatureVector(
    val signalDbm: Double?,
    val signalSlopePerSample: Double?,
    val avgLatencyMs: Double?,
    val latencySlopePerSample: Double?,
    val jitterMs: Double?,
    val probeLossPercent: Double?,
    val downloadMbps: Double?,
    val uploadMbps: Double?,
    val stabilityScore: Double?,
    val sampleCount: Int
)

data class PredictedEvent(
    val category: String,
    val severity: DegradationRisk,
    val description: String
)

data class PredictionResult(
    val timestampMs: Long = System.currentTimeMillis(),
    val engineName: String = "Rule-Based Trend Engine v1.0",
    val status: PredictionStatus = PredictionStatus.INSUFFICIENT_DATA,
    val predictedQualityScore: Double? = null,
    val predictedQualityClass: PredictedQualityClass = PredictedQualityClass.UNKNOWN,
    val confidencePercent: Double = 0.0,
    val degradationRisk: DegradationRisk = DegradationRisk.UNKNOWN,
    val expectedDegradation: String = "Insufficient data",
    val recommendedAction: String = "Continue live monitoring to collect baseline samples.",
    val reasoning: String = "At least 5 real timestamped telemetry samples are required before making trend predictions.",
    val predictedEvents: List<PredictedEvent> = emptyList(),
    val featureVector: FeatureVector? = null,
    val samplesCollected: Int = 0,
    val minSamplesRequired: Int = 5
)

enum class SmaTrendDirection(val label: String) {
    INSUFFICIENT_DATA("Collecting Baseline"),
    IMPROVING("Signal Improving"),
    STABLE("Signal Stable"),
    DEGRADING("Degradation Forecast"),
    DEGRADING_RAPIDLY("Rapid Signal Fade")
}

data class SmaTrendPoint(
    val timestampMs: Long,
    val rawSignalDbm: Double? = null,
    val shortSmaDbm: Double? = null,
    val longSmaDbm: Double? = null,
    val isForecast: Boolean = false
)

data class SmaSignalForecast(
    val networkType: NetworkType = NetworkType.WIFI,
    val shortWindow: Int = 3,
    val longWindow: Int = 6,
    val forecastSteps: Int = 3,
    val sampleCount: Int = 0,
    val minSamplesRequired: Int = 3,
    val hasEnoughData: Boolean = false,
    val latestRawDbm: Double? = null,
    val latestShortSmaDbm: Double? = null,
    val latestLongSmaDbm: Double? = null,
    val smaCrossoverDeltaDbm: Double? = null,
    val driftPerStepDbm: Double? = null,
    val projectedDbmNextStep: Double? = null,
    val projectedDbmHorizon: Double? = null,
    val warningThresholdDbm: Double = -75.0,
    val criticalThresholdDbm: Double = -82.0,
    val estimatedStepsToThreshold: Int? = null,
    val trendDirection: SmaTrendDirection = SmaTrendDirection.INSUFFICIENT_DATA,
    val degradationRisk: DegradationRisk = DegradationRisk.UNKNOWN,
    val series: List<SmaTrendPoint> = emptyList(),
    val summary: String = "Awaiting historical Room signal samples for SMA forecasting.",
    val recommendation: String = "Run live monitoring or tap Probe Now to build signal history."
)

data class DualInterfaceSmaForecast(
    val wifi: SmaSignalForecast = SmaSignalForecast(networkType = NetworkType.WIFI),
    val cellular: SmaSignalForecast = SmaSignalForecast(
        networkType = NetworkType.CELLULAR,
        warningThresholdDbm = -105.0,
        criticalThresholdDbm = -115.0
    )
)

data class SwitchDecisionResult(
    val timestampMs: Long = System.currentTimeMillis(),
    val previousNetwork: NetworkType,
    val targetNetwork: NetworkType,
    val mode: SwitchMode,
    val success: Boolean,
    val message: String,
    val scoreBefore: Double?,
    val scoreAfter: Double?
)

data class AppSettings(
    val autoSwitchingEnabled: Boolean = false,
    val monitoringIntervalSec: Int = 10,
    val speedTestIntervalSec: Int = 180,
    val preferredNetwork: PreferredNetwork = PreferredNetwork.AUTO,
    val minimumNetworkScore: Int = 55,
    val switchingThresholdDelta: Int = 12,
    val stabilityDurationSec: Int = 30,
    val batterySavingMode: Boolean = false,
    val cloudSyncEnabled: Boolean = false,
    val darkTheme: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val meteredSpeedTestsAllowed: Boolean = false,
    val downloadTestSizeKb: Int = 256,
    val latencyProbeUrl: String = "https://connectivitycheck.gstatic.com/generate_204",
    val downloadTestUrl: String = "https://speed.cloudflare.com/__down?bytes=262144",
    val uploadTestUrl: String = "https://speed.cloudflare.com/__up",
    val onboardingCompleted: Boolean = false,
    val scoringWeights: ScoringWeights = ScoringWeights()
)

data class SupabaseConfig(
    val projectUrl: String = "",
    val anonKey: String = "",
    val userEmail: String = "",
    val accessToken: String = "",
    val deviceId: String = ""
) {
    val isConfigured: Boolean
        get() = projectUrl.startsWith("https://") && anonKey.length >= 20
}

data class SyncStatus(
    val isSyncing: Boolean = false,
    val pendingMetricsCount: Int = 0,
    val pendingSwitchesCount: Int = 0,
    val lastSyncTimestampMs: Long? = null,
    val lastError: String? = null,
    val lastSuccessMessage: String? = null
)

data class PermissionSummary(
    val fineLocationGranted: Boolean = false,
    val coarseLocationGranted: Boolean = false,
    val phoneStateGranted: Boolean = false,
    val notificationsGranted: Boolean = true
)

data class NearbyWifiNetwork(
    val ssid: String,
    val bssid: String,
    val rssiDbm: Int,
    val signalLevel: Int, // 0..4
    val signalQualityLabel: String,
    val frequencyMhz: Int,
    val frequencyBand: String, // 2.4 GHz, 5 GHz, 6 GHz
    val channelWidthMhz: Int,
    val wifiStandard: String, // Wi-Fi 4/5/6/6E/7
    val securityLabel: String, // WPA3, WPA2/WPA3, WPA2, Open
    val estimatedPhySpeedMbps: Int,
    val isCurrentlyConnected: Boolean = false,
    val isPreviouslyConnected: Boolean = false,
    val lastSeenMs: Long = System.currentTimeMillis()
)

data class WifiScanState(
    val isScanning: Boolean = false,
    val networks: List<NearbyWifiNetwork> = emptyList(),
    val lastScanTimestampMs: Long? = null,
    val statusNote: String? = null
)

