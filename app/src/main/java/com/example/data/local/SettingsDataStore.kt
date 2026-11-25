package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppSettings
import com.example.model.PreferredNetwork
import com.example.model.ScoringWeights
import com.example.model.SupabaseConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.netSwitchDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "netswitch_preferences"
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val AUTO_SWITCHING = booleanPreferencesKey("auto_switching_enabled")
        val MONITORING_INTERVAL = intPreferencesKey("monitoring_interval_sec")
        val SPEED_TEST_INTERVAL = intPreferencesKey("speed_test_interval_sec")
        val PREFERRED_NETWORK = stringPreferencesKey("preferred_network")
        val MIN_NETWORK_SCORE = intPreferencesKey("minimum_network_score")
        val SWITCH_THRESHOLD_DELTA = intPreferencesKey("switching_threshold_delta")
        val STABILITY_DURATION = intPreferencesKey("stability_duration_sec")
        val BATTERY_SAVING = booleanPreferencesKey("battery_saving_mode")
        val CLOUD_SYNC_ENABLED = booleanPreferencesKey("cloud_sync_enabled")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val METERED_SPEED_TESTS = booleanPreferencesKey("metered_speed_tests_allowed")
        val DOWNLOAD_SIZE_KB = intPreferencesKey("download_test_size_kb")
        val LATENCY_PROBE_URL = stringPreferencesKey("latency_probe_url")
        val DOWNLOAD_TEST_URL = stringPreferencesKey("download_test_url")
        val UPLOAD_TEST_URL = stringPreferencesKey("upload_test_url")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

        // Scoring weights
        val W_SIGNAL = floatPreferencesKey("weight_signal")
        val W_LATENCY = floatPreferencesKey("weight_latency")
        val W_DOWNLOAD = floatPreferencesKey("weight_download")
        val W_STABILITY = floatPreferencesKey("weight_stability")
        val W_PACKET_LOSS = floatPreferencesKey("weight_packet_loss")
        val W_UPLOAD = floatPreferencesKey("weight_upload")

        // Supabase config (user-supplied runtime credentials; never service_role)
        val SUPABASE_URL = stringPreferencesKey("supabase_url")
        val SUPABASE_ANON_KEY = stringPreferencesKey("supabase_anon_key")
        val SUPABASE_EMAIL = stringPreferencesKey("supabase_email")
        val SUPABASE_ACCESS_TOKEN = stringPreferencesKey("supabase_access_token")
        val DEVICE_ID = stringPreferencesKey("device_uuid")
    }

    val settingsFlow: Flow<AppSettings> = context.netSwitchDataStore.data.map { prefs ->
        AppSettings(
            autoSwitchingEnabled = prefs[Keys.AUTO_SWITCHING] ?: false,
            monitoringIntervalSec = prefs[Keys.MONITORING_INTERVAL] ?: 10,
            speedTestIntervalSec = prefs[Keys.SPEED_TEST_INTERVAL] ?: 180,
            preferredNetwork = PreferredNetwork.fromCode(prefs[Keys.PREFERRED_NETWORK] ?: "AUTO"),
            minimumNetworkScore = prefs[Keys.MIN_NETWORK_SCORE] ?: 55,
            switchingThresholdDelta = prefs[Keys.SWITCH_THRESHOLD_DELTA] ?: 12,
            stabilityDurationSec = prefs[Keys.STABILITY_DURATION] ?: 30,
            batterySavingMode = prefs[Keys.BATTERY_SAVING] ?: false,
            cloudSyncEnabled = prefs[Keys.CLOUD_SYNC_ENABLED] ?: false,
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            meteredSpeedTestsAllowed = prefs[Keys.METERED_SPEED_TESTS] ?: false,
            downloadTestSizeKb = prefs[Keys.DOWNLOAD_SIZE_KB] ?: 256,
            latencyProbeUrl = prefs[Keys.LATENCY_PROBE_URL]
                ?: "https://connectivitycheck.gstatic.com/generate_204",
            downloadTestUrl = prefs[Keys.DOWNLOAD_TEST_URL]
                ?: "https://speed.cloudflare.com/__down?bytes=262144",
            uploadTestUrl = prefs[Keys.UPLOAD_TEST_URL]
                ?: "https://speed.cloudflare.com/__up",
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            scoringWeights = ScoringWeights(
                signalWeight = prefs[Keys.W_SIGNAL] ?: 0.25f,
                latencyWeight = prefs[Keys.W_LATENCY] ?: 0.25f,
                downloadWeight = prefs[Keys.W_DOWNLOAD] ?: 0.20f,
                stabilityWeight = prefs[Keys.W_STABILITY] ?: 0.15f,
                packetLossWeight = prefs[Keys.W_PACKET_LOSS] ?: 0.10f,
                uploadWeight = prefs[Keys.W_UPLOAD] ?: 0.05f
            )
        )
    }

    val supabaseConfigFlow: Flow<SupabaseConfig> = context.netSwitchDataStore.data.map { prefs ->
        SupabaseConfig(
            projectUrl = prefs[Keys.SUPABASE_URL] ?: "",
            anonKey = prefs[Keys.SUPABASE_ANON_KEY] ?: "",
            userEmail = prefs[Keys.SUPABASE_EMAIL] ?: "",
            accessToken = prefs[Keys.SUPABASE_ACCESS_TOKEN] ?: "",
            deviceId = prefs[Keys.DEVICE_ID] ?: ""
        )
    }

    suspend fun getOrCreateDeviceId(): String {
        val existing = context.netSwitchDataStore.data.first()[Keys.DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing
        val generated = "android-${UUID.randomUUID().toString().take(12)}"
        context.netSwitchDataStore.edit { it[Keys.DEVICE_ID] = generated }
        return generated
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val current = settingsFlow.first()
        val updated = transform(current)
        context.netSwitchDataStore.edit { prefs ->
            prefs[Keys.AUTO_SWITCHING] = updated.autoSwitchingEnabled
            prefs[Keys.MONITORING_INTERVAL] = updated.monitoringIntervalSec.coerceIn(3, 120)
            prefs[Keys.SPEED_TEST_INTERVAL] = updated.speedTestIntervalSec.coerceIn(30, 1800)
            prefs[Keys.PREFERRED_NETWORK] = updated.preferredNetwork.code
            prefs[Keys.MIN_NETWORK_SCORE] = updated.minimumNetworkScore.coerceIn(10, 95)
            prefs[Keys.SWITCH_THRESHOLD_DELTA] = updated.switchingThresholdDelta.coerceIn(3, 40)
            prefs[Keys.STABILITY_DURATION] = updated.stabilityDurationSec.coerceIn(5, 300)
            prefs[Keys.BATTERY_SAVING] = updated.batterySavingMode
            prefs[Keys.CLOUD_SYNC_ENABLED] = updated.cloudSyncEnabled
            prefs[Keys.DARK_THEME] = updated.darkTheme
            prefs[Keys.NOTIFICATIONS_ENABLED] = updated.notificationsEnabled
            prefs[Keys.METERED_SPEED_TESTS] = updated.meteredSpeedTestsAllowed
            prefs[Keys.DOWNLOAD_SIZE_KB] = updated.downloadTestSizeKb.coerceIn(64, 2048)
            prefs[Keys.LATENCY_PROBE_URL] = updated.latencyProbeUrl.trim()
            prefs[Keys.DOWNLOAD_TEST_URL] = updated.downloadTestUrl.trim()
            prefs[Keys.UPLOAD_TEST_URL] = updated.uploadTestUrl.trim()
            prefs[Keys.ONBOARDING_COMPLETED] = updated.onboardingCompleted
            prefs[Keys.W_SIGNAL] = updated.scoringWeights.signalWeight
            prefs[Keys.W_LATENCY] = updated.scoringWeights.latencyWeight
            prefs[Keys.W_DOWNLOAD] = updated.scoringWeights.downloadWeight
            prefs[Keys.W_STABILITY] = updated.scoringWeights.stabilityWeight
            prefs[Keys.W_PACKET_LOSS] = updated.scoringWeights.packetLossWeight
            prefs[Keys.W_UPLOAD] = updated.scoringWeights.uploadWeight
        }
    }

    suspend fun updateSupabaseConfig(
        projectUrl: String,
        anonKey: String,
        userEmail: String? = null,
        accessToken: String? = null
    ) {
        val deviceId = getOrCreateDeviceId()
        context.netSwitchDataStore.edit { prefs ->
            prefs[Keys.SUPABASE_URL] = projectUrl.trim().trimEnd('/')
            prefs[Keys.SUPABASE_ANON_KEY] = anonKey.trim()
            if (userEmail != null) prefs[Keys.SUPABASE_EMAIL] = userEmail.trim()
            if (accessToken != null) prefs[Keys.SUPABASE_ACCESS_TOKEN] = accessToken.trim()
            prefs[Keys.DEVICE_ID] = deviceId
        }
    }

    suspend fun clearSupabaseAuth() {
        context.netSwitchDataStore.edit { prefs ->
            prefs[Keys.SUPABASE_ACCESS_TOKEN] = ""
            prefs[Keys.SUPABASE_EMAIL] = ""
        }
    }
}
