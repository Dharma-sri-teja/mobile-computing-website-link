package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.remote.SupabaseSyncRepository
import com.example.domain.NetworkScoringEngine
import com.example.domain.RuleBasedNetworkPredictor
import com.example.network.AppNetworkSwitcher
import com.example.network.NetworkDetector
import com.example.network.NetworkMeasurementEngine
import com.example.network.NetworkMonitorService
import com.example.network.WifiScanner
import com.example.repository.NetworkTelemetryRepository
import com.example.service.PredictiveAnalyticsService

class NetSwitchApplication : Application() {

    lateinit var telemetryRepository: NetworkTelemetryRepository
        private set

    lateinit var networkMonitorService: NetworkMonitorService
        private set

    lateinit var predictiveAnalyticsService: PredictiveAnalyticsService
        private set

    override fun onCreate() {
        super.onCreate()
        networkMonitorService = NetworkMonitorService(this)
        val database = AppDatabase.getInstance(this)
        predictiveAnalyticsService = PredictiveAnalyticsService(database.networkMetricDao())
        val settingsDataStore = SettingsDataStore(this)
        val detector = NetworkDetector(this)
        val wifiScanner = WifiScanner(this)
        val measurementEngine = NetworkMeasurementEngine()
        val scoringEngine = NetworkScoringEngine()
        val predictor = RuleBasedNetworkPredictor()
        val switcher = AppNetworkSwitcher(this, detector)
        val supabaseSync = SupabaseSyncRepository(
            metricDao = database.networkMetricDao(),
            switchingDao = database.switchingHistoryDao(),
            settingsDataStore = settingsDataStore
        )

        telemetryRepository = NetworkTelemetryRepository(
            context = this,
            database = database,
            settingsDataStore = settingsDataStore,
            detector = detector,
            wifiScanner = wifiScanner,
            measurementEngine = measurementEngine,
            scoringEngine = scoringEngine,
            predictor = predictor,
            switcher = switcher,
            supabaseSync = supabaseSync
        )
    }
}
