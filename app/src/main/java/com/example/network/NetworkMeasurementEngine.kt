package com.example.network

import android.net.Network
import android.util.Log
import com.example.model.AppSettings
import com.example.model.NetworkType
import com.example.model.PerformanceMeasurement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Performs genuine network telemetry measurements (HTTPS latency, jitter, HTTP probe loss,
 * controlled download throughput, and controlled upload throughput).
 * Supports per-interface measurement using android.net.Network.socketFactory so Wi-Fi and
 * Cellular can be probed independently without faking values.
 */
class NetworkMeasurementEngine {

    private val tag = "NetworkMeasurementEngine"

    private fun buildClientForNetwork(network: Network?, timeoutSeconds: Long = 8L): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)

        if (network != null) {
            builder.socketFactory(network.socketFactory)
            builder.dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    return network.getAllByName(hostname).toList()
                }
            })
        }
        return builder.build()
    }

    /**
     * Measures latency (avg/min/max), jitter, HTTP probe loss %, and response time
     * using lightweight repeated HTTPS probes. Never fabricates values when offline.
     */
    suspend fun measureLatencyAndLoss(
        networkType: NetworkType,
        targetNetwork: Network?,
        signalDbm: Int?,
        settings: AppSettings,
        probeCount: Int = 5
    ): PerformanceMeasurement = withContext(Dispatchers.IO) {
        val probeUrl = settings.latencyProbeUrl.ifBlank {
            "https://connectivitycheck.gstatic.com/generate_204"
        }
        val client = buildClientForNetwork(targetNetwork, timeoutSeconds = 5L)
        val rttSamples = mutableListOf<Double>()
        var lastError: String? = null

        for (i in 0 until probeCount) {
            val request = Request.Builder()
                .url(probeUrl)
                .header("Cache-Control", "no-cache, no-store")
                .header("Connection", "close")
                .head()
                .build()

            val startNs = System.nanoTime()
            try {
                client.newCall(request).execute().use { response ->
                    val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
                    if (response.isSuccessful || response.code == 204 || response.code in 200..399) {
                        rttSamples.add(elapsedMs)
                    } else {
                        lastError = "HTTP ${response.code}"
                    }
                }
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                Log.d(tag, "Probe $i failed on ${networkType.label}: $lastError")
            }
        }

        val succeeded = rttSamples.size
        val lossPercent = ((probeCount - succeeded).toDouble() / probeCount.toDouble()) * 100.0

        if (rttSamples.isEmpty()) {
            return@withContext PerformanceMeasurement(
                timestampMs = System.currentTimeMillis(),
                networkType = networkType,
                probeLossPercent = 100.0,
                probesAttempted = probeCount,
                probesSucceeded = 0,
                signalStrengthDbm = signalDbm,
                stabilityScore = 0.0,
                isInternetReachable = false,
                probeMethodology = "HTTPS Probe Failure Rate (Non-ICMP)",
                errorNote = lastError ?: "All $probeCount probes timed out or failed"
            )
        }

        val avgLatency = rttSamples.average()
        val minLatency = rttSamples.min()
        val maxLatency = rttSamples.max()

        // RFC-style consecutive delay variation (jitter)
        val jitter = if (rttSamples.size >= 2) {
            val diffs = (1 until rttSamples.size).map { idx ->
                abs(rttSamples[idx] - rttSamples[idx - 1])
            }
            diffs.average()
        } else {
            0.0
        }

        val stability = computeStabilityScore(
            avgLatencyMs = avgLatency,
            jitterMs = jitter,
            lossPercent = lossPercent,
            signalDbm = signalDbm
        )

        PerformanceMeasurement(
            timestampMs = System.currentTimeMillis(),
            networkType = networkType,
            avgLatencyMs = avgLatency,
            minLatencyMs = minLatency,
            maxLatencyMs = maxLatency,
            jitterMs = jitter,
            probeLossPercent = lossPercent,
            probesAttempted = probeCount,
            probesSucceeded = succeeded,
            responseTimeMs = rttSamples.last(),
            signalStrengthDbm = signalDbm,
            stabilityScore = stability,
            isInternetReachable = true,
            probeMethodology = "HTTPS Probe Failure Rate (Non-ICMP)",
            errorNote = if (lossPercent > 0.0) "Partial probe loss: $lastError" else null
        )
    }

    /**
     * Performs a controlled, data-capped download & upload speed test plus latency/jitter probes.
     */
    suspend fun measureFullPerformance(
        networkType: NetworkType,
        targetNetwork: Network?,
        signalDbm: Int?,
        settings: AppSettings,
        includeSpeedTest: Boolean = true
    ): PerformanceMeasurement = withContext(Dispatchers.IO) {
        val base = measureLatencyAndLoss(
            networkType = networkType,
            targetNetwork = targetNetwork,
            signalDbm = signalDbm,
            settings = settings,
            probeCount = 5
        )
        if (!base.isInternetReachable || !includeSpeedTest) {
            return@withContext base
        }

        val downloadMbps = measureDownloadSpeed(targetNetwork, settings)
        val uploadMbps = measureUploadSpeed(targetNetwork, settings)

        val errNote = when {
            downloadMbps == null && uploadMbps == null ->
                "Speed test endpoints unreachable; latency probes succeeded"
            downloadMbps == null -> "Download throughput probe unavailable"
            uploadMbps == null -> "Upload throughput probe unavailable"
            else -> base.errorNote
        }

        base.copy(
            timestampMs = System.currentTimeMillis(),
            downloadSpeedMbps = downloadMbps,
            uploadSpeedMbps = uploadMbps,
            errorNote = errNote
        )
    }

    private fun measureDownloadSpeed(targetNetwork: Network?, settings: AppSettings): Double? {
        val sizeBytes = (settings.downloadTestSizeKb.coerceIn(64, 2048)) * 1024
        val configuredUrl = settings.downloadTestUrl.ifBlank {
            "https://speed.cloudflare.com/__down?bytes=$sizeBytes"
        }
        val candidates = listOf(
            configuredUrl,
            "https://speed.cloudflare.com/__down?bytes=$sizeBytes",
            "https://httpbin.org/bytes/${sizeBytes.coerceAtMost(102400)}"
        ).distinct()

        val client = buildClientForNetwork(targetNetwork, timeoutSeconds = 12L)
        val buffer = ByteArray(16 * 1024)

        for (url in candidates) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Cache-Control", "no-cache, no-store")
                    .get()
                    .build()

                val startNs = System.nanoTime()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) continue
                    val stream = response.body?.byteStream() ?: continue
                    var totalBytes = 0L
                    var read: Int
                    while (stream.read(buffer).also { read = it } != -1) {
                        totalBytes += read
                        if (totalBytes >= 2L * 1024L * 1024L) break // safety data cap
                    }
                    val elapsedSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.005)
                    if (totalBytes >= 4096L) {
                        return (totalBytes * 8.0) / (elapsedSec * 1_000_000.0)
                    }
                }
            } catch (e: Exception) {
                Log.d(tag, "Download speed test failed for $url: ${e.message}")
            }
        }
        return null
    }

    private fun measureUploadSpeed(targetNetwork: Network?, settings: AppSettings): Double? {
        val uploadBytesSize = (settings.downloadTestSizeKb / 2).coerceIn(32, 512) * 1024
        val payload = ByteArray(uploadBytesSize) { (it % 251).toByte() }
        val mediaType = "application/octet-stream".toMediaType()

        val configuredUrl = settings.uploadTestUrl.ifBlank { "https://speed.cloudflare.com/__up" }
        val candidates = listOf(
            configuredUrl,
            "https://speed.cloudflare.com/__up",
            "https://httpbin.org/post"
        ).distinct()

        val client = buildClientForNetwork(targetNetwork, timeoutSeconds = 12L)

        for (url in candidates) {
            try {
                val body = payload.toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(url)
                    .header("Cache-Control", "no-cache, no-store")
                    .post(body)
                    .build()

                val startNs = System.nanoTime()
                client.newCall(request).execute().use { response ->
                    val elapsedSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.005)
                    if (response.isSuccessful) {
                        return (uploadBytesSize * 8.0) / (elapsedSec * 1_000_000.0)
                    }
                }
            } catch (e: Exception) {
                Log.d(tag, "Upload speed test failed for $url: ${e.message}")
            }
        }
        return null
    }

    fun computeStabilityScore(
        avgLatencyMs: Double,
        jitterMs: Double,
        lossPercent: Double,
        signalDbm: Int?
    ): Double {
        val jitterPenalty = (jitterMs * 1.1).coerceIn(0.0, 40.0)
        val lossPenalty = (lossPercent * 0.8).coerceIn(0.0, 50.0)
        val latencyPenalty = ((avgLatencyMs - 40.0).coerceAtLeast(0.0) * 0.08).coerceIn(0.0, 20.0)
        val signalPenalty = when {
            signalDbm == null -> 0.0
            signalDbm <= -105 -> 20.0
            signalDbm <= -90 -> 10.0
            signalDbm <= -80 -> 4.0
            else -> 0.0
        }
        return (100.0 - jitterPenalty - lossPenalty - latencyPenalty - signalPenalty).coerceIn(0.0, 100.0)
    }
}
