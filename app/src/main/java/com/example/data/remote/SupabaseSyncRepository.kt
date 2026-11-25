package com.example.data.remote

import android.util.Base64
import android.util.Log
import com.example.data.local.NetworkMetricDao
import com.example.data.local.SettingsDataStore
import com.example.data.local.SwitchingHistoryDao
import com.example.model.SupabaseConfig
import com.example.model.SyncStatus
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.Instant
import java.util.concurrent.TimeUnit

class SupabaseSyncRepository(
    private val metricDao: NetworkMetricDao,
    private val switchingDao: SwitchingHistoryDao,
    private val settingsDataStore: SettingsDataStore
) {
    private val tag = "SupabaseSyncRepository"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _syncStatus = MutableStateFlow(SyncStatus())
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    /**
     * Rejects accidental pasting of a Supabase service_role key in the client app.
     * Only public anon keys (paired with Row Level Security) are permitted on mobile devices.
     */
    fun isLikelyServiceRoleKey(key: String): Boolean {
        val trimmed = key.trim()
        if (trimmed.startsWith("sb_secret_")) return true
        val parts = trimmed.split(".")
        if (parts.size == 3) {
            return try {
                val payloadJson = String(
                    Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                )
                payloadJson.contains("\"role\":\"service_role\"") ||
                    payloadJson.contains("\"role\": \"service_role\"")
            } catch (_: Exception) {
                false
            }
        }
        return false
    }

    private fun buildApi(projectUrl: String): SupabaseRestApi {
        val normalized = projectUrl.trim().trimEnd('/') + "/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SupabaseRestApi::class.java)
    }

    suspend fun signInWithEmailPassword(
        projectUrl: String,
        anonKey: String,
        email: String,
        password: String
    ): Result<String> {
        if (!projectUrl.startsWith("https://")) {
            return Result.failure(IllegalArgumentException("Supabase URL must start with https://"))
        }
        if (isLikelyServiceRoleKey(anonKey)) {
            return Result.failure(
                SecurityException("Refused: Never use a service_role key in the Android APK. Use the public anon key with RLS.")
            )
        }
        return try {
            val api = buildApi(projectUrl)
            val response = api.signInWithPassword(
                apiKey = anonKey.trim(),
                body = SupabasePasswordSignInRequest(email = email.trim(), password = password)
            )
            if (response.isSuccessful) {
                val token = response.body()?.accessToken
                if (!token.isNullOrBlank()) {
                    settingsDataStore.updateSupabaseConfig(
                        projectUrl = projectUrl,
                        anonKey = anonKey,
                        userEmail = email,
                        accessToken = token
                    )
                    Result.success("Authenticated as ${email.trim()} for Row Level Security.")
                } else {
                    Result.failure(IllegalStateException("Supabase returned an empty access token."))
                }
            } else {
                Result.failure(
                    IllegalStateException("Auth failed (HTTP ${response.code()}): check email/password and Supabase Auth settings.")
                )
            }
        } catch (e: Exception) {
            Log.w(tag, "Supabase auth error", e)
            Result.failure(e)
        }
    }

    suspend fun syncPendingRecords(maxRetries: Int = 2): Result<String> {
        val settings = settingsDataStore.settingsFlow.first()
        val config = settingsDataStore.supabaseConfigFlow.first()

        if (!settings.cloudSyncEnabled) {
            return Result.failure(IllegalStateException("Cloud synchronization is disabled in Settings."))
        }
        if (!config.isConfigured) {
            return Result.failure(IllegalStateException("Supabase URL and anon key are not configured."))
        }
        if (isLikelyServiceRoleKey(config.anonKey)) {
            return Result.failure(
                SecurityException("Blocked sync: service_role key detected. Replace with public anon key.")
            )
        }

        _syncStatus.update { it.copy(isSyncing = true, lastError = null) }

        var attempt = 0
        var lastException: Exception? = null
        while (attempt <= maxRetries) {
            try {
                val resultMessage = performSingleSyncPass(config)
                _syncStatus.update {
                    it.copy(
                        isSyncing = false,
                        lastSyncTimestampMs = System.currentTimeMillis(),
                        lastError = null,
                        lastSuccessMessage = resultMessage
                    )
                }
                return Result.success(resultMessage)
            } catch (e: Exception) {
                lastException = e
                Log.w(tag, "Sync attempt ${attempt + 1} failed: ${e.message}")
                if (attempt < maxRetries) {
                    delay(1500L * (attempt + 1))
                }
            }
            attempt++
        }

        val errMsg = lastException?.message ?: "Supabase synchronization failed."
        _syncStatus.update { it.copy(isSyncing = false, lastError = errMsg) }
        return Result.failure(lastException ?: IllegalStateException(errMsg))
    }

    private suspend fun performSingleSyncPass(config: SupabaseConfig): String {
        val api = buildApi(config.projectUrl)
        val bearerToken = if (config.accessToken.isNotBlank()) config.accessToken else config.anonKey
        val authHeader = "Bearer $bearerToken"

        val unsyncedMetrics = metricDao.getUnsyncedMetrics(batchSize = 50)
        val unsyncedSwitches = switchingDao.getUnsyncedSwitches(batchSize = 50)

        if (unsyncedMetrics.isEmpty() && unsyncedSwitches.isEmpty()) {
            return "All local records are already synchronized."
        }

        var syncedMetricCount = 0
        var syncedSwitchCount = 0

        if (unsyncedMetrics.isNotEmpty()) {
            val dtos = unsyncedMetrics.map { entity ->
                SupabaseNetworkMetricDto(
                    id = entity.id,
                    deviceId = entity.deviceId,
                    networkType = entity.networkType,
                    downloadSpeed = entity.downloadSpeed,
                    uploadSpeed = entity.uploadSpeed,
                    latency = entity.latency,
                    jitter = entity.jitter,
                    packetLoss = entity.packetLoss,
                    signalStrength = entity.signalStrength,
                    isConnected = entity.isConnected,
                    createdAt = Instant.ofEpochMilli(entity.createdAtMs).toString()
                )
            }
            val resp = api.insertNetworkMetrics(
                apiKey = config.anonKey,
                authorization = authHeader,
                payload = dtos
            )
            if (!resp.isSuccessful) {
                throw IllegalStateException(
                    "network_metrics sync failed (HTTP ${resp.code()}): verify table schema and RLS policies."
                )
            }
            metricDao.markMetricsSynced(unsyncedMetrics.map { it.id })
            syncedMetricCount = unsyncedMetrics.size
        }

        if (unsyncedSwitches.isNotEmpty()) {
            val dtos = unsyncedSwitches.map { entity ->
                SupabaseSwitchingHistoryDto(
                    id = entity.id,
                    deviceId = entity.deviceId,
                    previousNetwork = entity.previousNetwork,
                    newNetwork = entity.newNetwork,
                    reason = entity.reason,
                    switchingMode = entity.switchingMode,
                    createdAt = Instant.ofEpochMilli(entity.createdAtMs).toString()
                )
            }
            val resp = api.insertSwitchingHistory(
                apiKey = config.anonKey,
                authorization = authHeader,
                payload = dtos
            )
            if (!resp.isSuccessful) {
                throw IllegalStateException(
                    "switching_history sync failed (HTTP ${resp.code()}): verify table schema and RLS policies."
                )
            }
            switchingDao.markSwitchesSynced(unsyncedSwitches.map { it.id })
            syncedSwitchCount = unsyncedSwitches.size
        }

        return "Synced $syncedMetricCount metrics & $syncedSwitchCount switch logs to Supabase."
    }

    fun updatePendingCounts(metricsPending: Int, switchesPending: Int) {
        _syncStatus.update {
            it.copy(
                pendingMetricsCount = metricsPending,
                pendingSwitchesCount = switchesPending
            )
        }
    }
}
