package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "network_metrics")
data class NetworkMetricEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val deviceId: String,
    val networkType: String, // WIFI, CELLULAR, ETHERNET, NONE
    val downloadSpeed: Double? = null,
    val uploadSpeed: Double? = null,
    val latency: Double? = null,
    val minLatency: Double? = null,
    val maxLatency: Double? = null,
    val jitter: Double? = null,
    val packetLoss: Double? = null,
    val signalStrength: Int? = null,
    val stabilityScore: Double? = null,
    val qualityScore: Double? = null,
    val isConnected: Boolean = false,
    val isValidated: Boolean = false,
    val probeType: String = "HTTPS_PROBE",
    val createdAtMs: Long = System.currentTimeMillis(),
    val syncedToCloud: Boolean = false
)

@Entity(tableName = "switching_history")
data class SwitchingHistoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val deviceId: String,
    val previousNetwork: String,
    val newNetwork: String,
    val reason: String,
    val switchingMode: String, // MANUAL or AUTOMATIC
    val scoreBefore: Double? = null,
    val scoreAfter: Double? = null,
    val wasSuccessful: Boolean = true,
    val createdAtMs: Long = System.currentTimeMillis(),
    val syncedToCloud: Boolean = false
)

@Entity(tableName = "prediction_logs")
data class PredictionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val engineName: String,
    val networkType: String,
    val status: String,
    val predictedQuality: Double? = null,
    val confidencePercent: Double = 0.0,
    val degradationRisk: String,
    val expectedDegradation: String,
    val recommendedAction: String,
    val reason: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "connection_events")
data class ConnectionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: String, // DISCONNECTED, RESTORED, SWITCH_FAILED, ERROR, SERVICE_STATE
    val networkType: String,
    val title: String,
    val details: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "known_wifi_networks")
data class KnownWifiNetworkEntity(
    @PrimaryKey val ssid: String,
    val bssid: String? = null,
    val lastRssiDbm: Int? = null,
    val bestRssiDbm: Int? = null,
    val frequencyBand: String? = null,
    val wifiStandard: String? = null,
    val securityLabel: String? = null,
    val estimatedPhySpeedMbps: Int? = null,
    val lastMeasuredDownloadMbps: Double? = null,
    val lastMeasuredLatencyMs: Double? = null,
    val hasBeenConnected: Boolean = false,
    val timesConnected: Int = 0,
    val firstSeenMs: Long = System.currentTimeMillis(),
    val lastSeenMs: Long = System.currentTimeMillis()
)

