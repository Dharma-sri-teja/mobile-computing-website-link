package com.example.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NetSwitchApplication
import com.example.data.local.ConnectionEventEntity
import com.example.data.local.KnownWifiNetworkEntity
import com.example.data.local.NetworkMetricEntity
import com.example.data.local.PredictionLogEntity
import com.example.data.local.SwitchingHistoryEntity
import com.example.network.AutoSwitchEvaluation
import com.example.network.NetworkAvailabilityState
import com.example.domain.ReportExporter
import com.example.model.AppSettings
import com.example.model.DualInterfaceSmaForecast
import com.example.model.MonitoringState
import com.example.model.NetworkComparisonResult
import com.example.model.NetworkSnapshot
import com.example.model.NetworkType
import com.example.model.PerformanceMeasurement
import com.example.model.PermissionSummary
import com.example.model.PredictionResult
import com.example.model.ReportPeriod
import com.example.model.ScoringWeights
import com.example.model.SupabaseConfig
import com.example.model.SwitchDecisionResult
import com.example.model.SwitchMode
import com.example.model.SyncStatus
import com.example.model.WifiScanState
import com.example.service.NetworkMonitoringService
import com.example.service.PredictiveAnalyticsService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NetSwitchViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val app = application as NetSwitchApplication
    private val repo = app.telemetryRepository

    val networkAvailabilityFlow: Flow<NetworkAvailabilityState> =
        app.networkMonitorService.observeNetworkAvailability()

    val networkAvailability: StateFlow<NetworkAvailabilityState> =
        networkAvailabilityFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = NetworkAvailabilityState()
        )

    val snapshot: StateFlow<NetworkSnapshot> = repo.snapshot
    val wifiScanState: StateFlow<WifiScanState> = repo.wifiScanState
    val monitoringState: StateFlow<MonitoringState> = repo.monitoringState
    val monitoringStartTimeMs: StateFlow<Long?> = repo.monitoringStartTimeMs
    val isProbing: StateFlow<Boolean> = repo.isProbing
    val isRunningSpeedTest: StateFlow<Boolean> = repo.isRunningSpeedTest
    val activeMeasurement: StateFlow<PerformanceMeasurement?> = repo.activeMeasurement
    val wifiMeasurement: StateFlow<PerformanceMeasurement?> = repo.wifiMeasurement
    val cellularMeasurement: StateFlow<PerformanceMeasurement?> = repo.cellularMeasurement
    val comparison: StateFlow<NetworkComparisonResult?> = repo.comparison
    val prediction: StateFlow<PredictionResult> = repo.prediction
    val lastSwitchDecision: StateFlow<SwitchDecisionResult?> = repo.lastSwitchDecision
    val autoSwitchEvaluation: StateFlow<AutoSwitchEvaluation?> = repo.autoSwitchEvaluation
    val permissions: StateFlow<PermissionSummary> = repo.permissions
    val lastErrorMessage: StateFlow<String?> = repo.lastErrorMessage
    val syncStatus: StateFlow<SyncStatus> = repo.syncStatus

    val settings: StateFlow<AppSettings> = repo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    val supabaseConfig: StateFlow<SupabaseConfig> = repo.supabaseConfigFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SupabaseConfig()
    )

    val recentMetrics: StateFlow<List<NetworkMetricEntity>> = repo.recentMetrics.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val wifiMetrics: StateFlow<List<NetworkMetricEntity>> = repo.wifiMetrics.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val cellularMetrics: StateFlow<List<NetworkMetricEntity>> = repo.cellularMetrics.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val switchingHistory: StateFlow<List<SwitchingHistoryEntity>> = repo.switchingHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val predictionHistory: StateFlow<List<PredictionLogEntity>> = repo.predictionHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val connectionEvents: StateFlow<List<ConnectionEventEntity>> = repo.connectionEvents.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val knownWifiNetworks: StateFlow<List<KnownWifiNetworkEntity>> = repo.knownWifiNetworks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _smaWindowConfig = MutableStateFlow(
        PredictiveAnalyticsService.DEFAULT_SHORT_WINDOW to PredictiveAnalyticsService.DEFAULT_LONG_WINDOW
    )
    val smaWindowConfig: StateFlow<Pair<Int, Int>> = _smaWindowConfig.asStateFlow()

    val smaSignalForecast: StateFlow<DualInterfaceSmaForecast> = _smaWindowConfig
        .flatMapLatest { (shortW, longW) ->
            app.predictiveAnalyticsService.observeDualInterfaceForecasts(
                shortWindow = shortW,
                longWindow = longW
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DualInterfaceSmaForecast()
        )

    private val _selectedAnalyticsPeriod = MutableStateFlow(ReportPeriod.DAILY)
    val selectedAnalyticsPeriod: StateFlow<ReportPeriod> = _selectedAnalyticsPeriod.asStateFlow()

    val analyticsMetrics: StateFlow<List<NetworkMetricEntity>> = _selectedAnalyticsPeriod
        .flatMapLatest { period ->
            val sinceMs = System.currentTimeMillis() - period.hours * 3600_000L
            repo.observeMetricsSince(sinceMs)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val interruptionCountForPeriod: StateFlow<Int> = _selectedAnalyticsPeriod
        .flatMapLatest { period ->
            val sinceMs = System.currentTimeMillis() - period.hours * 3600_000L
            repo.observeInterruptionsSince(sinceMs)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _monitoringElapsedSec = MutableStateFlow(0L)
    val monitoringElapsedSec: StateFlow<Long> = _monitoringElapsedSec.asStateFlow()

    private val _cooldownSecondsLeft = MutableStateFlow(0)
    val cooldownSecondsLeft: StateFlow<Int> = _cooldownSecondsLeft.asStateFlow()

    private val _uiBannerMessage = MutableStateFlow<String?>(null)
    val uiBannerMessage: StateFlow<String?> = _uiBannerMessage.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                val start = monitoringStartTimeMs.value
                if (monitoringState.value == MonitoringState.ACTIVE && start != null) {
                    _monitoringElapsedSec.value = ((System.currentTimeMillis() - start) / 1000L).coerceAtLeast(0L)
                } else if (monitoringState.value == MonitoringState.STOPPED) {
                    _monitoringElapsedSec.value = 0L
                }
                _cooldownSecondsLeft.value = repo.switcher.remainingCooldownSeconds(settings.value)
                delay(1000L)
            }
        }
    }

    fun startMonitoring() {
        NetworkMonitoringService.start(appContext)
    }

    fun pauseMonitoring() {
        NetworkMonitoringService.pause(appContext)
    }

    fun resumeMonitoring() {
        NetworkMonitoringService.resume(appContext)
    }

    fun stopMonitoring() {
        NetworkMonitoringService.stop(appContext)
    }

    fun refreshNow() {
        repo.refreshPermissionsAndSnapshot()
        viewModelScope.launch {
            repo.runMonitoringCycle(forceSpeedTest = false)
        }
    }

    fun runFullSpeedTestNow() {
        viewModelScope.launch {
            repo.runMonitoringCycle(forceSpeedTest = true)
        }
    }

    fun probeSpecificNetwork(target: NetworkType, includeSpeed: Boolean = false) {
        viewModelScope.launch {
            repo.runSpecificInterfaceProbe(target, includeSpeed)
        }
    }

    fun scanNearbyWifi() {
        repo.triggerWifiScan()
    }

    fun manualSwitchTo(target: NetworkType) {
        viewModelScope.launch {
            val res = repo.executeSwitch(
                target = target,
                mode = SwitchMode.MANUAL,
                reason = "User initiated manual app-level switch to ${target.label}"
            )
            _uiBannerMessage.value = res.message
        }
    }

    fun resetToSystemDefault() {
        val res = repo.resetToSystemDefault()
        _uiBannerMessage.value = res.message
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            repo.settingsDataStore.updateSettings(transform)
            repo.recomputeComparisonAndPrediction()
        }
    }

    fun updateScoringWeights(weights: ScoringWeights) {
        updateSettings { it.copy(scoringWeights = weights) }
    }

    fun completeOnboarding() {
        repo.refreshPermissionsAndSnapshot()
        updateSettings { it.copy(onboardingCompleted = true) }
    }

    fun onPermissionsUpdated() {
        repo.refreshPermissionsAndSnapshot()
    }

    fun selectAnalyticsPeriod(period: ReportPeriod) {
        _selectedAnalyticsPeriod.value = period
    }

    fun setSmaWindow(shortWindow: Int, longWindow: Int) {
        val safeShort = shortWindow.coerceAtLeast(2)
        val safeLong = longWindow.coerceAtLeast(safeShort + 1)
        _smaWindowConfig.value = safeShort to safeLong
    }

    fun saveSupabaseConfig(projectUrl: String, anonKey: String) {
        if (anonKey.isNotBlank() && repo.supabaseSync.isLikelyServiceRoleKey(anonKey)) {
            _uiBannerMessage.value =
                "Security Block: service_role key detected! Only paste the public anon key paired with RLS."
            return
        }
        viewModelScope.launch {
            repo.settingsDataStore.updateSupabaseConfig(projectUrl = projectUrl, anonKey = anonKey)
            _uiBannerMessage.value = "Supabase endpoint configuration saved."
        }
    }

    fun signInSupabase(projectUrl: String, anonKey: String, email: String, password: String) {
        viewModelScope.launch {
            val res = repo.supabaseSync.signInWithEmailPassword(projectUrl, anonKey, email, password)
            _uiBannerMessage.value = res.fold(
                onSuccess = { it },
                onFailure = { "Supabase sign-in failed: ${it.message}" }
            )
        }
    }

    fun signOutSupabase() {
        viewModelScope.launch {
            repo.settingsDataStore.clearSupabaseAuth()
            _uiBannerMessage.value = "Cleared Supabase session token."
        }
    }

    fun syncSupabaseNow() {
        viewModelScope.launch {
            val res = repo.supabaseSync.syncPendingRecords(maxRetries = 2)
            _uiBannerMessage.value = res.fold(
                onSuccess = { it },
                onFailure = { it.message ?: "Supabase sync failed" }
            )
        }
    }

    fun exportReport(onShareIntentReady: (Intent) -> Unit) {
        viewModelScope.launch {
            val period = _selectedAnalyticsPeriod.value
            val sinceMs = System.currentTimeMillis() - period.hours * 3600_000L
            val metrics = repo.getMetricsSince(sinceMs)
            val switches = repo.getSwitchesSince(sinceMs)
            val result = ReportExporter.exportCsvReport(appContext, period, metrics, switches)
            result.fold(
                onSuccess = { intent -> onShareIntentReady(intent) },
                onFailure = { _uiBannerMessage.value = "Export failed: ${it.message}" }
            )
        }
    }

    fun clearAllLocalData() {
        viewModelScope.launch {
            repo.clearAllLocalTelemetry()
            _uiBannerMessage.value = "Cleared all local Room telemetry & history records."
        }
    }

    fun dismissBanner() {
        _uiBannerMessage.value = null
        repo.clearLastError()
    }
}
