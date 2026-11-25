package com.example.repository

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.data.local.AppDatabase
import com.example.data.local.ConnectionEventEntity
import com.example.data.local.KnownWifiNetworkEntity
import com.example.data.local.NetworkMetricEntity
import com.example.data.local.PredictionLogEntity
import com.example.data.local.SettingsDataStore
import com.example.data.local.SwitchingHistoryEntity
import com.example.data.remote.SupabaseSyncRepository
import com.example.network.AutoSwitchEvaluation
import com.example.domain.NetworkPredictor
import com.example.domain.NetworkScoringEngine
import com.example.domain.RuleBasedNetworkPredictor
import com.example.model.AppSettings
import com.example.model.MonitoringState
import com.example.model.NearbyWifiNetwork
import com.example.model.NetworkComparisonResult
import com.example.model.NetworkSnapshot
import com.example.model.NetworkType
import com.example.model.PerformanceMeasurement
import com.example.model.PermissionSummary
import com.example.model.PredictedQualityClass
import com.example.model.PredictionResult
import com.example.model.SwitchDecisionResult
import com.example.model.SwitchMode
import com.example.model.SyncStatus
import com.example.model.WifiScanState
import com.example.network.AppNetworkSwitcher
import com.example.network.NetworkDetector
import com.example.network.NetworkMeasurementEngine
import com.example.network.WifiScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NetworkTelemetryRepository(
    private val context: Context,
    val database: AppDatabase,
    val settingsDataStore: SettingsDataStore,
    val detector: NetworkDetector,
    val wifiScanner: WifiScanner,
    val measurementEngine: NetworkMeasurementEngine,
    val scoringEngine: NetworkScoringEngine,
    val predictor: NetworkPredictor = RuleBasedNetworkPredictor(),
    val switcher: AppNetworkSwitcher,
    val supabaseSync: SupabaseSyncRepository
) {
    private val tag = "NetworkTelemetryRepo"
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val metricDao = database.networkMetricDao()
    private val switchingDao = database.switchingHistoryDao()
    private val predictionDao = database.predictionLogDao()
    private val eventDao = database.connectionEventDao()
    private val knownWifiDao = database.knownWifiNetworkDao()

    val snapshot: StateFlow<NetworkSnapshot> = detector.snapshot
    val wifiScanState: StateFlow<WifiScanState> = wifiScanner.scanState
    val settingsFlow: Flow<AppSettings> = settingsDataStore.settingsFlow
    val supabaseConfigFlow = settingsDataStore.supabaseConfigFlow
    val syncStatus: StateFlow<SyncStatus> = supabaseSync.syncStatus

    private val _monitoringState = MutableStateFlow(MonitoringState.STOPPED)
    val monitoringState: StateFlow<MonitoringState> = _monitoringState.asStateFlow()

    private val _monitoringStartTimeMs = MutableStateFlow<Long?>(null)
    val monitoringStartTimeMs: StateFlow<Long?> = _monitoringStartTimeMs.asStateFlow()

    private val _isProbing = MutableStateFlow(false)
    val isProbing: StateFlow<Boolean> = _isProbing.asStateFlow()

    private val _isRunningSpeedTest = MutableStateFlow(false)
    val isRunningSpeedTest: StateFlow<Boolean> = _isRunningSpeedTest.asStateFlow()

    private val _activeMeasurement = MutableStateFlow<PerformanceMeasurement?>(null)
    val activeMeasurement: StateFlow<PerformanceMeasurement?> = _activeMeasurement.asStateFlow()

    private val _wifiMeasurement = MutableStateFlow<PerformanceMeasurement?>(null)
    val wifiMeasurement: StateFlow<PerformanceMeasurement?> = _wifiMeasurement.asStateFlow()

    private val _cellularMeasurement = MutableStateFlow<PerformanceMeasurement?>(null)
    val cellularMeasurement: StateFlow<PerformanceMeasurement?> = _cellularMeasurement.asStateFlow()

    private val _comparison = MutableStateFlow<NetworkComparisonResult?>(null)
    val comparison: StateFlow<NetworkComparisonResult?> = _comparison.asStateFlow()

    private val _prediction = MutableStateFlow(PredictionResult())
    val prediction: StateFlow<PredictionResult> = _prediction.asStateFlow()

    private val _lastSwitchDecision = MutableStateFlow<SwitchDecisionResult?>(null)
    val lastSwitchDecision: StateFlow<SwitchDecisionResult?> = _lastSwitchDecision.asStateFlow()

    private val _autoSwitchEvaluation = MutableStateFlow<AutoSwitchEvaluation?>(null)
    val autoSwitchEvaluation: StateFlow<AutoSwitchEvaluation?> = _autoSwitchEvaluation.asStateFlow()

    private val _permissions = MutableStateFlow(detector.checkPermissions())
    val permissions: StateFlow<PermissionSummary> = _permissions.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    val recentMetrics: Flow<List<NetworkMetricEntity>> = metricDao.observeRecentMetrics(120)
    val wifiMetrics: Flow<List<NetworkMetricEntity>> = metricDao.observeMetricsForNetwork(NetworkType.WIFI.code, 60)
    val cellularMetrics: Flow<List<NetworkMetricEntity>> = metricDao.observeMetricsForNetwork(NetworkType.CELLULAR.code, 60)
    val switchingHistory: Flow<List<SwitchingHistoryEntity>> = switchingDao.observeSwitchingHistory(100)
    val predictionHistory: Flow<List<PredictionLogEntity>> = predictionDao.observePredictions(60)
    val connectionEvents: Flow<List<ConnectionEventEntity>> = eventDao.observeEvents(80)
    val knownWifiNetworks: Flow<List<KnownWifiNetworkEntity>> = knownWifiDao.observeKnownNetworks(60)

    @Volatile
    private var lastSpeedTestTimestampMs: Long = 0L

    @Volatile
    private var deviceIdCache: String = ""

    @Volatile
    private var lastRecordedConnectedSsid: String? = null

    @Volatile
    private var cachedKnownConnectedSsids: Set<String> = emptySet()

    @Volatile
    private var lastPoorQualityAlertMs: Long = 0L

    @Volatile
    private var lastBetterNetworkAlertMs: Long = 0L

    var onAutoSwitchNotification: ((title: String, body: String) -> Unit)? = null

    init {
        repoScope.launch {
            deviceIdCache = settingsDataStore.getOrCreateDeviceId()
        }
        repoScope.launch {
            knownWifiDao.observeKnownNetworks(60).collect { list ->
                val connectedSet = list.filter { it.hasBeenConnected }.map { it.ssid }.toSet()
                cachedKnownConnectedSsids = connectedSet
                wifiScanner.updateContextMetadata(snapshot.value.wifi.ssid, connectedSet)
            }
        }
        wifiScanner.setOnScanCompletedListener { scanned ->
            repoScope.launch {
                recordScannedWifiNetworks(scanned)
            }
        }
        repoScope.launch {
            combine(
                metricDao.observeUnsyncedMetricCount(),
                switchingDao.observeUnsyncedSwitchCount()
            ) { mCount, sCount -> mCount to sCount }
                .collect { (mCount, sCount) ->
                    supabaseSync.updatePendingCounts(mCount, sCount)
                }
        }
        detector.setOnConnectivityChangedListener { previous, current ->
            repoScope.launch {
                handleConnectivityTransition(previous, current)
            }
        }
        detector.startListening()
        repoScope.launch {
            recordConnectedWifiIfPresent(snapshot.value, null)
            recomputeComparisonAndPrediction()
        }
    }

    fun triggerWifiScan() {
        val currentSsid = snapshot.value.wifi.ssid
        wifiScanner.triggerScan(
            connectedSsid = currentSsid,
            previouslyConnectedSsids = cachedKnownConnectedSsids
        )
    }

    private suspend fun recordConnectedWifiIfPresent(
        snap: NetworkSnapshot,
        measurement: PerformanceMeasurement?
    ) {
        val ssid = snap.wifi.ssid?.takeIf { it.isNotBlank() && snap.wifi.isConnected } ?: return
        val now = System.currentTimeMillis()
        val existing = knownWifiDao.getBySsid(ssid)
        val isNewConnectionSession = lastRecordedConnectedSsid != ssid
        lastRecordedConnectedSsid = ssid

        val rssi = snap.wifi.rssiDbm
        val bestRssi = listOfNotNull(existing?.bestRssiDbm, rssi).maxOrNull()
        knownWifiDao.upsert(
            KnownWifiNetworkEntity(
                ssid = ssid,
                bssid = existing?.bssid,
                lastRssiDbm = rssi ?: existing?.lastRssiDbm,
                bestRssiDbm = bestRssi,
                frequencyBand = snap.wifi.frequencyBand ?: existing?.frequencyBand,
                wifiStandard = existing?.wifiStandard,
                securityLabel = existing?.securityLabel,
                estimatedPhySpeedMbps = snap.wifi.linkSpeedMbps ?: existing?.estimatedPhySpeedMbps,
                lastMeasuredDownloadMbps = measurement?.downloadSpeedMbps ?: existing?.lastMeasuredDownloadMbps,
                lastMeasuredLatencyMs = measurement?.avgLatencyMs ?: existing?.lastMeasuredLatencyMs,
                hasBeenConnected = true,
                timesConnected = (existing?.timesConnected ?: 0) + if (isNewConnectionSession) 1 else 0,
                firstSeenMs = existing?.firstSeenMs ?: now,
                lastSeenMs = now
            )
        )
    }

    private suspend fun recordScannedWifiNetworks(scanned: List<NearbyWifiNetwork>) {
        val now = System.currentTimeMillis()
        scanned.forEach { item ->
            if (item.ssid == "Hidden Network") return@forEach
            val existing = knownWifiDao.getBySsid(item.ssid)
            val bestRssi = listOfNotNull(existing?.bestRssiDbm, item.rssiDbm).maxOrNull()
            val connectedNow = item.isCurrentlyConnected
            knownWifiDao.upsert(
                KnownWifiNetworkEntity(
                    ssid = item.ssid,
                    bssid = item.bssid.ifBlank { existing?.bssid },
                    lastRssiDbm = item.rssiDbm,
                    bestRssiDbm = bestRssi,
                    frequencyBand = item.frequencyBand,
                    wifiStandard = item.wifiStandard,
                    securityLabel = item.securityLabel,
                    estimatedPhySpeedMbps = item.estimatedPhySpeedMbps,
                    lastMeasuredDownloadMbps = existing?.lastMeasuredDownloadMbps,
                    lastMeasuredLatencyMs = existing?.lastMeasuredLatencyMs,
                    hasBeenConnected = (existing?.hasBeenConnected == true) || connectedNow,
                    timesConnected = existing?.timesConnected ?: if (connectedNow) 1 else 0,
                    firstSeenMs = existing?.firstSeenMs ?: now,
                    lastSeenMs = now
                )
            )
        }
    }

    fun refreshPermissionsAndSnapshot() {
        _permissions.value = detector.checkPermissions()
        detector.refreshSnapshot("Telemetry refreshed")
        repoScope.launch {
            recomputeComparisonAndPrediction()
        }
    }

    fun setMonitoringState(state: MonitoringState) {
        val prev = _monitoringState.value
        _monitoringState.value = state
        when (state) {
            MonitoringState.ACTIVE -> {
                if (_monitoringStartTimeMs.value == null) {
                    _monitoringStartTimeMs.value = System.currentTimeMillis()
                }
                detector.startListening()
            }
            MonitoringState.STOPPED -> {
                _monitoringStartTimeMs.value = null
            }
            MonitoringState.PAUSED -> Unit
        }
        if (prev != state) {
            repoScope.launch {
                logEvent(
                    eventType = "SERVICE_STATE",
                    networkType = snapshot.value.activeNetworkType.code,
                    title = "Monitoring ${state.label}",
                    details = "Background telemetry state transitioned from ${prev.label} to ${state.label}."
                )
            }
        }
    }

    fun clearLastError() {
        _lastErrorMessage.value = null
    }

    fun reportError(message: String) {
        _lastErrorMessage.value = message
    }

    private suspend fun handleConnectivityTransition(
        previous: NetworkSnapshot,
        current: NetworkSnapshot
    ) {
        when {
            previous.isInternetValidated && !current.isInternetValidated -> {
                logEvent(
                    eventType = "DISCONNECTED",
                    networkType = previous.activeNetworkType.code,
                    title = "Internet Connectivity Interrupted",
                    details = "Lost validated internet on ${previous.activeNetworkType.label}. Active state: ${current.statusMessage}"
                )
            }
            !previous.isInternetValidated && current.isInternetValidated -> {
                logEvent(
                    eventType = "RESTORED",
                    networkType = current.activeNetworkType.code,
                    title = "Internet Restored (${current.activeNetworkType.label})",
                    details = "Validated internet connectivity re-established via ${current.activeNetworkType.label}."
                )
                // Attempt offline queue flush when internet returns
                val settings = settingsFlow.first()
                if (settings.cloudSyncEnabled) {
                    supabaseSync.syncPendingRecords(maxRetries = 1)
                }
            }
            previous.wifi.isConnected && !current.wifi.isConnected -> {
                logEvent(
                    eventType = "DISCONNECTED",
                    networkType = NetworkType.WIFI.code,
                    title = "Wi-Fi Disconnected",
                    details = "Wi-Fi transport lost. Active interface is now ${current.activeNetworkType.label}."
                )
            }
            previous.cellular.isConnected && !current.cellular.isConnected -> {
                logEvent(
                    eventType = "DISCONNECTED",
                    networkType = NetworkType.CELLULAR.code,
                    title = "Cellular Interface Unavailable",
                    details = "Cellular transport lost. Active interface is now ${current.activeNetworkType.label}."
                )
            }
        }
        recomputeComparisonAndPrediction()
    }

    /**
     * Executes one real telemetry monitoring cycle:
     * 1. Refreshes OS connectivity/signal snapshot.
     * 2. Runs real HTTPS latency/jitter/probe-loss checks on active & secondary interfaces.
     * 3. Runs controlled speed test only if interval elapsed and data rules allow.
     * 4. Recomputes explainable score & trend prediction.
     * 5. Persists real timestamped record in Room.
     * 6. Evaluates automatic switching with hysteresis & stability cooldown.
     * 7. Syncs pending records to Supabase if enabled & configured.
     */
    suspend fun runMonitoringCycle(forceSpeedTest: Boolean = false) {
        if (_isProbing.value) return
        _isProbing.value = true
        try {
            val settings = settingsFlow.first()
            val snap = detector.refreshSnapshot("Live monitoring cycle")
            val deviceId = deviceIdCache.ifBlank { settingsDataStore.getOrCreateDeviceId().also { deviceIdCache = it } }

            val now = System.currentTimeMillis()
            val speedIntervalMs = (if (settings.batterySavingMode) {
                settings.speedTestIntervalSec * 2
            } else {
                settings.speedTestIntervalSec
            }) * 1000L

            val isMeteredActive = when (snap.activeNetworkType) {
                NetworkType.CELLULAR -> snap.cellular.isMetered
                NetworkType.WIFI -> snap.wifi.isMetered
                else -> false
            }

            val shouldRunSpeed = forceSpeedTest || (
                !settings.batterySavingMode &&
                    snap.isInternetValidated &&
                    (now - lastSpeedTestTimestampMs >= speedIntervalMs) &&
                    (!isMeteredActive || settings.meteredSpeedTestsAllowed)
                )

            if (shouldRunSpeed) {
                _isRunningSpeedTest.value = true
                lastSpeedTestTimestampMs = now
            }

            // Probe Wi-Fi interface if connected
            val wifiNet = detector.getWifiNetwork()
            if (snap.wifi.isConnected) {
                val runWifiSpeed = shouldRunSpeed && snap.activeNetworkType == NetworkType.WIFI
                val wifiResult = measurementEngine.measureFullPerformance(
                    networkType = NetworkType.WIFI,
                    targetNetwork = wifiNet,
                    signalDbm = snap.wifi.rssiDbm,
                    settings = settings,
                    includeSpeedTest = runWifiSpeed
                )
                // Preserve last known download/upload speed if this cycle was latency-only
                val mergedWifi = mergeWithPreviousThroughput(wifiResult, _wifiMeasurement.value)
                _wifiMeasurement.value = mergedWifi
            } else {
                _wifiMeasurement.value = null
            }

            // Probe Cellular interface if connected
            val cellNet = detector.getCellularNetwork()
            if (snap.cellular.isConnected) {
                val runCellSpeed = shouldRunSpeed && snap.activeNetworkType == NetworkType.CELLULAR
                val cellResult = measurementEngine.measureFullPerformance(
                    networkType = NetworkType.CELLULAR,
                    targetNetwork = cellNet,
                    signalDbm = snap.cellular.signalStrengthDbm,
                    settings = settings,
                    includeSpeedTest = runCellSpeed
                )
                val mergedCell = mergeWithPreviousThroughput(cellResult, _cellularMeasurement.value)
                _cellularMeasurement.value = mergedCell
            } else {
                _cellularMeasurement.value = null
            }

            val activePerf = when (snap.activeNetworkType) {
                NetworkType.WIFI -> _wifiMeasurement.value
                NetworkType.CELLULAR -> _cellularMeasurement.value
                NetworkType.NONE -> PerformanceMeasurement(
                    timestampMs = System.currentTimeMillis(),
                    networkType = NetworkType.NONE,
                    isInternetReachable = false,
                    errorNote = "No active network connection"
                )
                else -> measurementEngine.measureFullPerformance(
                    networkType = snap.activeNetworkType,
                    targetNetwork = detector.getActiveOrDefaultNetwork(),
                    signalDbm = null,
                    settings = settings,
                    includeSpeedTest = shouldRunSpeed
                )
            }
            _activeMeasurement.value = activePerf

            val comp = scoringEngine.compareNetworks(
                snapshot = snap,
                wifiMeasurement = _wifiMeasurement.value,
                cellularMeasurement = _cellularMeasurement.value,
                settings = settings
            )
            _comparison.value = comp

            val activeScore = when (snap.activeNetworkType) {
                NetworkType.WIFI -> comp.wifiScore.totalScore
                NetworkType.CELLULAR -> comp.cellularScore.totalScore
                else -> null
            }

            // Persist real measurement to Room
            val metricEntity = NetworkMetricEntity(
                deviceId = deviceId,
                networkType = snap.activeNetworkType.code,
                downloadSpeed = activePerf?.downloadSpeedMbps,
                uploadSpeed = activePerf?.uploadSpeedMbps,
                latency = activePerf?.avgLatencyMs,
                minLatency = activePerf?.minLatencyMs,
                maxLatency = activePerf?.maxLatencyMs,
                jitter = activePerf?.jitterMs,
                packetLoss = activePerf?.probeLossPercent,
                signalStrength = activePerf?.signalStrengthDbm ?: when (snap.activeNetworkType) {
                    NetworkType.WIFI -> snap.wifi.rssiDbm
                    NetworkType.CELLULAR -> snap.cellular.signalStrengthDbm
                    else -> null
                },
                stabilityScore = activePerf?.stabilityScore,
                qualityScore = activeScore,
                isConnected = snap.activeNetworkType != NetworkType.NONE,
                isValidated = snap.isInternetValidated,
                probeType = activePerf?.probeMethodology ?: "HTTPS_PROBE",
                createdAtMs = System.currentTimeMillis(),
                syncedToCloud = false
            )
            metricDao.insertMetric(metricEntity)
            recordConnectedWifiIfPresent(snap, _wifiMeasurement.value)

            // Run predictive engine on updated real history
            val recent = metricDao.getRecentMetricsOnce(30)
            val pred = predictor.predict(snap, recent)
            _prediction.value = pred
            predictionDao.insertPrediction(
                PredictionLogEntity(
                    engineName = pred.engineName,
                    networkType = snap.activeNetworkType.code,
                    status = pred.status.label,
                    predictedQuality = pred.predictedQualityScore,
                    confidencePercent = pred.confidencePercent,
                    degradationRisk = pred.degradationRisk.label,
                    expectedDegradation = pred.expectedDegradation,
                    recommendedAction = pred.recommendedAction,
                    reason = pred.reasoning
                )
            )

            // Automatic switching evaluation with hysteresis & stability cooldown
            val autoEval = switcher.evaluateAutomaticDecision(comp, settings)
            _autoSwitchEvaluation.value = autoEval
            var switchExecuted = false
            if (autoEval.shouldExecute && autoEval.targetNetwork != null) {
                val res = executeSwitch(
                    target = autoEval.targetNetwork,
                    mode = SwitchMode.AUTOMATIC,
                    reason = autoEval.explanation
                )
                switchExecuted = res.success
            }

            // Proactive cooldown-protected alerts for Poor Quality & Better Network Recommended
            if (!switchExecuted && settings.notificationsEnabled) {
                evaluateProactiveAlerts(
                    snap = snap,
                    activeScore = activeScore,
                    comp = comp,
                    pred = pred,
                    settings = settings
                )
            }

            // Optional cloud sync if configured & enabled
            if (settings.cloudSyncEnabled && snap.isInternetValidated) {
                supabaseSync.syncPendingRecords(maxRetries = 1)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error during monitoring cycle", e)
            _lastErrorMessage.value = "Monitoring cycle error: ${e.message ?: e.javaClass.simpleName}"
            logEvent(
                eventType = "ERROR",
                networkType = snapshot.value.activeNetworkType.code,
                title = "Measurement Cycle Error",
                details = e.message ?: "Unexpected failure"
            )
        } finally {
            _isProbing.value = false
            _isRunningSpeedTest.value = false
        }
    }

    suspend fun runSpecificInterfaceProbe(target: NetworkType, includeSpeed: Boolean = false) {
        if (_isProbing.value) return
        _isProbing.value = true
        if (includeSpeed) _isRunningSpeedTest.value = true
        try {
            val settings = settingsFlow.first()
            val snap = detector.refreshSnapshot("Targeted ${target.label} probe")
            val net = when (target) {
                NetworkType.WIFI -> detector.getWifiNetwork()
                NetworkType.CELLULAR -> detector.getCellularNetwork()
                else -> detector.getActiveOrDefaultNetwork()
            }
            val signal = when (target) {
                NetworkType.WIFI -> snap.wifi.rssiDbm
                NetworkType.CELLULAR -> snap.cellular.signalStrengthDbm
                else -> null
            }
            val result = measurementEngine.measureFullPerformance(
                networkType = target,
                targetNetwork = net,
                signalDbm = signal,
                settings = settings,
                includeSpeedTest = includeSpeed
            )
            when (target) {
                NetworkType.WIFI -> _wifiMeasurement.value = mergeWithPreviousThroughput(result, _wifiMeasurement.value)
                NetworkType.CELLULAR -> _cellularMeasurement.value = mergeWithPreviousThroughput(result, _cellularMeasurement.value)
                else -> Unit
            }
            if (snap.activeNetworkType == target) {
                _activeMeasurement.value = when (target) {
                    NetworkType.WIFI -> _wifiMeasurement.value
                    NetworkType.CELLULAR -> _cellularMeasurement.value
                    else -> result
                }
            }
            recomputeComparisonAndPrediction()
        } catch (e: Exception) {
            _lastErrorMessage.value = "Probe failed for ${target.label}: ${e.message}"
        } finally {
            _isProbing.value = false
            _isRunningSpeedTest.value = false
        }
    }

    private fun mergeWithPreviousThroughput(
        fresh: PerformanceMeasurement,
        previous: PerformanceMeasurement?
    ): PerformanceMeasurement {
        if (!fresh.isInternetReachable) return fresh
        return fresh.copy(
            downloadSpeedMbps = fresh.downloadSpeedMbps ?: previous?.downloadSpeedMbps,
            uploadSpeedMbps = fresh.uploadSpeedMbps ?: previous?.uploadSpeedMbps
        )
    }

    suspend fun recomputeComparisonAndPrediction() {
        val settings = settingsFlow.first()
        val snap = snapshot.value
        val comp = scoringEngine.compareNetworks(
            snapshot = snap,
            wifiMeasurement = _wifiMeasurement.value,
            cellularMeasurement = _cellularMeasurement.value,
            settings = settings
        )
        _comparison.value = comp
        _autoSwitchEvaluation.value = switcher.evaluateAutomaticDecision(comp, settings)

        val recent = metricDao.getRecentMetricsOnce(30)
        _prediction.value = predictor.predict(snap, recent)
    }

    suspend fun executeSwitch(
        target: NetworkType,
        mode: SwitchMode,
        reason: String
    ): SwitchDecisionResult {
        val comp = _comparison.value
        val beforeType = snapshot.value.activeNetworkType
        val scoreBefore = when (beforeType) {
            NetworkType.WIFI -> comp?.wifiScore?.totalScore
            NetworkType.CELLULAR -> comp?.cellularScore?.totalScore
            else -> null
        }
        val scoreAfter = when (target) {
            NetworkType.WIFI -> comp?.wifiScore?.totalScore
            NetworkType.CELLULAR -> comp?.cellularScore?.totalScore
            else -> null
        }

        val result = switcher.switchAppTrafficTo(
            target = target,
            mode = mode,
            scoreBefore = scoreBefore,
            scoreAfter = scoreAfter,
            reason = reason
        )
        _lastSwitchDecision.value = result

        val deviceId = deviceIdCache.ifBlank { settingsDataStore.getOrCreateDeviceId().also { deviceIdCache = it } }
        switchingDao.insertSwitch(
            SwitchingHistoryEntity(
                deviceId = deviceId,
                previousNetwork = result.previousNetwork.code,
                newNetwork = result.targetNetwork.code,
                reason = if (result.success) reason else "FAILED: ${result.message}",
                switchingMode = mode.code,
                scoreBefore = scoreBefore,
                scoreAfter = scoreAfter,
                wasSuccessful = result.success,
                createdAtMs = result.timestampMs,
                syncedToCloud = false
            )
        )

        if (!result.success) {
            _lastErrorMessage.value = result.message
            logEvent(
                eventType = "SWITCH_FAILED",
                networkType = target.code,
                title = "App-Level Switch to ${target.label} Failed",
                details = result.message
            )
        } else if (mode == SwitchMode.AUTOMATIC) {
            onAutoSwitchNotification?.invoke(
                "Switched App Traffic to ${target.label}",
                result.message
            )
        }

        recomputeComparisonAndPrediction()
        val settings = settingsFlow.first()
        if (settings.cloudSyncEnabled && snapshot.value.isInternetValidated) {
            supabaseSync.syncPendingRecords(maxRetries = 1)
        }
        return result
    }

    fun resetToSystemDefault(): SwitchDecisionResult {
        val result = switcher.resetToSystemDefault()
        _lastSwitchDecision.value = result
        repoScope.launch {
            recomputeComparisonAndPrediction()
        }
        return result
    }

    suspend fun getMetricsSince(sinceMs: Long): List<NetworkMetricEntity> =
        metricDao.getMetricsSinceOnce(sinceMs)

    suspend fun getSwitchesSince(sinceMs: Long): List<SwitchingHistoryEntity> =
        switchingDao.getSwitchesSinceOnce(sinceMs)

    fun observeMetricsSince(sinceMs: Long): Flow<List<NetworkMetricEntity>> =
        metricDao.observeMetricsSince(sinceMs)

    fun observeInterruptionsSince(sinceMs: Long): Flow<Int> =
        eventDao.observeInterruptionCountSince(sinceMs)

    suspend fun clearAllLocalTelemetry() {
        metricDao.clearAll()
        switchingDao.clearAll()
        predictionDao.clearAll()
        eventDao.clearAll()
        knownWifiDao.clearAll()
        _wifiMeasurement.value = null
        _cellularMeasurement.value = null
        _activeMeasurement.value = null
        recomputeComparisonAndPrediction()
    }

    private fun evaluateProactiveAlerts(
        snap: NetworkSnapshot,
        activeScore: Double?,
        comp: NetworkComparisonResult,
        pred: PredictionResult,
        settings: AppSettings
    ) {
        val now = System.currentTimeMillis()
        val cooldownMs = 60_000L

        // 1. Better network available / switching recommended alert
        if (comp.shouldSwitch &&
            comp.recommendedNetwork != NetworkType.NONE &&
            comp.recommendedNetwork != snap.activeNetworkType &&
            now - lastBetterNetworkAlertMs >= cooldownMs
        ) {
            lastBetterNetworkAlertMs = now
            dispatchAlertNotification(
                title = "Better Network Available: ${comp.recommendedNetwork.label}",
                body = comp.reason,
                notificationId = 1003
            )
            return
        }

        // 2. Poor network quality alert
        val isPoorQuality = pred.predictedQualityClass == PredictedQualityClass.POOR ||
            (activeScore != null && activeScore < settings.minimumNetworkScore.toDouble())
        if (isPoorQuality &&
            snap.activeNetworkType != NetworkType.NONE &&
            now - lastPoorQualityAlertMs >= cooldownMs
        ) {
            lastPoorQualityAlertMs = now
            val scoreStr = activeScore?.let { "${it.toInt()}/100" } ?: "Poor"
            dispatchAlertNotification(
                title = "Poor ${snap.activeNetworkType.label} Quality ($scoreStr)",
                body = pred.recommendedAction,
                notificationId = 1004
            )
        }
    }

    private fun dispatchAlertNotification(
        title: String,
        body: String,
        notificationId: Int = 1002
    ) {
        val callback = onAutoSwitchNotification
        if (callback != null) {
            callback.invoke(title, body)
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channelId = "netswitch_alerts_channel"
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        "Switching & Degradation Alerts",
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            }
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            nm.notify(notificationId, notification)
        } catch (_: Exception) {
        }
    }

    private suspend fun logEvent(
        eventType: String,
        networkType: String,
        title: String,
        details: String
    ) {
        eventDao.insertEvent(
            ConnectionEventEntity(
                eventType = eventType,
                networkType = networkType,
                title = title,
                details = details
            )
        )
    }
}
