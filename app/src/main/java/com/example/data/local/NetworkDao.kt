package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkMetricDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetric(metric: NetworkMetricEntity)

    @Query("SELECT * FROM network_metrics ORDER BY createdAtMs DESC LIMIT :limit")
    fun observeRecentMetrics(limit: Int = 100): Flow<List<NetworkMetricEntity>>

    @Query("SELECT * FROM network_metrics WHERE createdAtMs >= :sinceMs ORDER BY createdAtMs ASC")
    fun observeMetricsSince(sinceMs: Long): Flow<List<NetworkMetricEntity>>

    @Query("SELECT * FROM network_metrics WHERE networkType = :networkType ORDER BY createdAtMs DESC LIMIT :limit")
    fun observeMetricsForNetwork(networkType: String, limit: Int = 60): Flow<List<NetworkMetricEntity>>

    @Query("SELECT * FROM network_metrics ORDER BY createdAtMs DESC LIMIT :limit")
    suspend fun getRecentMetricsOnce(limit: Int = 30): List<NetworkMetricEntity>

    @Query("SELECT * FROM network_metrics WHERE createdAtMs >= :sinceMs ORDER BY createdAtMs ASC")
    suspend fun getMetricsSinceOnce(sinceMs: Long): List<NetworkMetricEntity>

    @Query("SELECT * FROM network_metrics WHERE syncedToCloud = 0 ORDER BY createdAtMs ASC LIMIT :batchSize")
    suspend fun getUnsyncedMetrics(batchSize: Int = 50): List<NetworkMetricEntity>

    @Query("SELECT COUNT(*) FROM network_metrics WHERE syncedToCloud = 0")
    fun observeUnsyncedMetricCount(): Flow<Int>

    @Query("UPDATE network_metrics SET syncedToCloud = 1 WHERE id IN (:ids)")
    suspend fun markMetricsSynced(ids: List<String>)

    @Query("DELETE FROM network_metrics")
    suspend fun clearAll()
}

@Dao
interface SwitchingHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSwitch(entry: SwitchingHistoryEntity)

    @Query("SELECT * FROM switching_history ORDER BY createdAtMs DESC LIMIT :limit")
    fun observeSwitchingHistory(limit: Int = 100): Flow<List<SwitchingHistoryEntity>>

    @Query("SELECT * FROM switching_history WHERE createdAtMs >= :sinceMs ORDER BY createdAtMs DESC")
    suspend fun getSwitchesSinceOnce(sinceMs: Long): List<SwitchingHistoryEntity>

    @Query("SELECT * FROM switching_history WHERE syncedToCloud = 0 ORDER BY createdAtMs ASC LIMIT :batchSize")
    suspend fun getUnsyncedSwitches(batchSize: Int = 50): List<SwitchingHistoryEntity>

    @Query("SELECT COUNT(*) FROM switching_history WHERE syncedToCloud = 0")
    fun observeUnsyncedSwitchCount(): Flow<Int>

    @Query("UPDATE switching_history SET syncedToCloud = 1 WHERE id IN (:ids)")
    suspend fun markSwitchesSynced(ids: List<String>)

    @Query("DELETE FROM switching_history")
    suspend fun clearAll()
}

@Dao
interface PredictionLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrediction(entry: PredictionLogEntity)

    @Query("SELECT * FROM prediction_logs ORDER BY createdAtMs DESC LIMIT :limit")
    fun observePredictions(limit: Int = 60): Flow<List<PredictionLogEntity>>

    @Query("DELETE FROM prediction_logs")
    suspend fun clearAll()
}

@Dao
interface ConnectionEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(entry: ConnectionEventEntity)

    @Query("SELECT * FROM connection_events ORDER BY createdAtMs DESC LIMIT :limit")
    fun observeEvents(limit: Int = 80): Flow<List<ConnectionEventEntity>>

    @Query("SELECT COUNT(*) FROM connection_events WHERE eventType = 'DISCONNECTED' AND createdAtMs >= :sinceMs")
    fun observeInterruptionCountSince(sinceMs: Long): Flow<Int>

    @Query("DELETE FROM connection_events")
    suspend fun clearAll()
}

@Dao
interface KnownWifiNetworkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(network: KnownWifiNetworkEntity)

    @Query("SELECT * FROM known_wifi_networks WHERE ssid = :ssid LIMIT 1")
    suspend fun getBySsid(ssid: String): KnownWifiNetworkEntity?

    @Query("SELECT * FROM known_wifi_networks ORDER BY hasBeenConnected DESC, lastSeenMs DESC LIMIT :limit")
    fun observeKnownNetworks(limit: Int = 60): Flow<List<KnownWifiNetworkEntity>>

    @Query("DELETE FROM known_wifi_networks")
    suspend fun clearAll()
}

