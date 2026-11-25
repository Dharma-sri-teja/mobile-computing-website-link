package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

@JsonClass(generateAdapter = true)
data class SupabaseNetworkMetricDto(
    @Json(name = "id") val id: String,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "network_type") val networkType: String,
    @Json(name = "download_speed") val downloadSpeed: Double?,
    @Json(name = "upload_speed") val uploadSpeed: Double?,
    @Json(name = "latency") val latency: Double?,
    @Json(name = "jitter") val jitter: Double?,
    @Json(name = "packet_loss") val packetLoss: Double?,
    @Json(name = "signal_strength") val signalStrength: Int?,
    @Json(name = "is_connected") val isConnected: Boolean,
    @Json(name = "created_at") val createdAt: String
)

@JsonClass(generateAdapter = true)
data class SupabaseSwitchingHistoryDto(
    @Json(name = "id") val id: String,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "previous_network") val previousNetwork: String,
    @Json(name = "new_network") val newNetwork: String,
    @Json(name = "reason") val reason: String,
    @Json(name = "switching_mode") val switchingMode: String,
    @Json(name = "created_at") val createdAt: String
)

@JsonClass(generateAdapter = true)
data class SupabasePasswordSignInRequest(
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class SupabaseAuthResponse(
    @Json(name = "access_token") val accessToken: String?,
    @Json(name = "token_type") val tokenType: String?
)

interface SupabaseRestApi {
    @POST("rest/v1/network_metrics")
    suspend fun insertNetworkMetrics(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=minimal",
        @Body payload: List<SupabaseNetworkMetricDto>
    ): Response<Unit>

    @POST("rest/v1/switching_history")
    suspend fun insertSwitchingHistory(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=minimal",
        @Body payload: List<SupabaseSwitchingHistoryDto>
    ): Response<Unit>

    @POST("auth/v1/token?grant_type=password")
    suspend fun signInWithPassword(
        @Header("apikey") apiKey: String,
        @Body body: SupabasePasswordSignInRequest
    ): Response<SupabaseAuthResponse>
}
