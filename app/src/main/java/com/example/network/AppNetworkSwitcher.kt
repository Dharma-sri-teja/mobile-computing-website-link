package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.model.AppSettings
import com.example.model.NetworkComparisonResult
import com.example.model.NetworkType
import com.example.model.SwitchDecisionResult
import com.example.model.SwitchMode
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Performs genuine Android app-level network selection using ConnectivityManager.requestNetwork()
 * and bindProcessToNetwork(). Enforces hysteresis & stability cooldown for automatic switching
 * to avoid ping-pong oscillation.
 */
class AppNetworkSwitcher(
    context: Context,
    private val detector: NetworkDetector
) {
    private val tag = "AppNetworkSwitcher"
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var activeRequestCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    var lastSwitchTimestampMs: Long = 0L
        private set

    @Volatile
    private var consecutiveSwitchRecommendations: Int = 0

    @Volatile
    private var pendingRecommendedTarget: NetworkType? = null

    fun remainingCooldownSeconds(settings: AppSettings, nowMs: Long = System.currentTimeMillis()): Int {
        if (lastSwitchTimestampMs == 0L) return 0
        val elapsedSec = ((nowMs - lastSwitchTimestampMs) / 1000L).toInt()
        return (settings.stabilityDurationSec - elapsedSec).coerceAtLeast(0)
    }

    /**
     * Releases any custom app-level network binding so the OS controls default routing again.
     */
    fun resetToSystemDefault(): SwitchDecisionResult {
        val previous = detector.snapshot.value.activeNetworkType
        releaseActiveRequest()
        val unbound = try {
            connectivityManager.bindProcessToNetwork(null)
        } catch (e: Exception) {
            Log.w(tag, "Failed to unbind process network", e)
            false
        }
        detector.setBoundNetworkType(null)
        return SwitchDecisionResult(
            previousNetwork = previous,
            targetNetwork = detector.snapshot.value.activeNetworkType,
            mode = SwitchMode.MANUAL,
            success = unbound,
            message = if (unbound) {
                "Released app-level binding. App traffic now follows Android system default routing."
            } else {
                "Unable to reset process network binding."
            },
            scoreBefore = null,
            scoreAfter = null
        )
    }

    /**
     * Requests a specific transport (WIFI or CELLULAR) via ConnectivityManager.requestNetwork()
     * and binds this application process's sockets to it via bindProcessToNetwork().
     */
    suspend fun switchAppTrafficTo(
        target: NetworkType,
        mode: SwitchMode,
        scoreBefore: Double?,
        scoreAfter: Double?,
        reason: String
    ): SwitchDecisionResult {
        val previous = detector.snapshot.value.activeNetworkType

        if (target != NetworkType.WIFI && target != NetworkType.CELLULAR) {
            return SwitchDecisionResult(
                previousNetwork = previous,
                targetNetwork = target,
                mode = mode,
                success = false,
                message = "Only Wi-Fi or Cellular interfaces can be requested at the app level.",
                scoreBefore = scoreBefore,
                scoreAfter = scoreAfter
            )
        }

        val transport = when (target) {
            NetworkType.WIFI -> NetworkCapabilities.TRANSPORT_WIFI
            NetworkType.CELLULAR -> NetworkCapabilities.TRANSPORT_CELLULAR
            else -> return SwitchDecisionResult(
                previousNetwork = previous,
                targetNetwork = target,
                mode = mode,
                success = false,
                message = "Unsupported target",
                scoreBefore = scoreBefore,
                scoreAfter = scoreAfter
            )
        }

        val request = NetworkRequest.Builder()
            .addTransportType(transport)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val acquired = requestNetworkWithTimeout(request, timeoutMs = 6000L)
        if (acquired == null) {
            return SwitchDecisionResult(
                previousNetwork = previous,
                targetNetwork = target,
                mode = mode,
                success = false,
                message = "${target.label} is unavailable or lacks internet capability. Retained current connection (${previous.label}).",
                scoreBefore = scoreBefore,
                scoreAfter = scoreAfter
            )
        }

        val (network, callback) = acquired
        val caps = connectivityManager.getNetworkCapabilities(network)
        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        if (!hasInternet) {
            safeUnregister(callback)
            return SwitchDecisionResult(
                previousNetwork = previous,
                targetNetwork = target,
                mode = mode,
                success = false,
                message = "${target.label} does not provide internet connectivity. Retained ${previous.label}.",
                scoreBefore = scoreBefore,
                scoreAfter = scoreAfter
            )
        }

        val bound = try {
            connectivityManager.bindProcessToNetwork(network)
        } catch (e: Exception) {
            Log.e(tag, "bindProcessToNetwork failed for ${target.label}", e)
            false
        }

        if (bound) {
            releaseActiveRequest()
            activeRequestCallback = callback
            lastSwitchTimestampMs = System.currentTimeMillis()
            consecutiveSwitchRecommendations = 0
            pendingRecommendedTarget = null
            detector.setBoundNetworkType(target)
            return SwitchDecisionResult(
                previousNetwork = previous,
                targetNetwork = target,
                mode = mode,
                success = true,
                message = "App traffic successfully bound to ${target.label} via ConnectivityManager.requestNetwork() + bindProcessToNetwork(). ($reason)",
                scoreBefore = scoreBefore,
                scoreAfter = scoreAfter
            )
        } else {
            safeUnregister(callback)
            return SwitchDecisionResult(
                previousNetwork = previous,
                targetNetwork = target,
                mode = mode,
                success = false,
                message = "OS declined bindProcessToNetwork() for ${target.label}. Retained ${previous.label}.",
                scoreBefore = scoreBefore,
                scoreAfter = scoreAfter
            )
        }
    }

    /**
     * Evaluates whether automatic switching should execute now, applying hysteresis (threshold delta)
     * and stability cooldown to prevent rapid ping-pong switching.
     */
    fun evaluateAutomaticDecision(
        comparison: NetworkComparisonResult,
        settings: AppSettings,
        nowMs: Long = System.currentTimeMillis()
    ): AutoSwitchEvaluation {
        if (!settings.autoSwitchingEnabled) {
            return AutoSwitchEvaluation(false, null, "Automatic switching is turned OFF.")
        }

        val recommended = comparison.recommendedNetwork
        val active = comparison.activeNetwork

        if (recommended == NetworkType.NONE || recommended == active) {
            consecutiveSwitchRecommendations = 0
            pendingRecommendedTarget = null
            return AutoSwitchEvaluation(false, null, "Current network (${active.label}) remains optimal.")
        }

        val cooldownLeft = remainingCooldownSeconds(settings, nowMs)
        if (cooldownLeft > 0) {
            return AutoSwitchEvaluation(
                shouldExecute = false,
                targetNetwork = recommended,
                explanation = "Stability cooldown active (${cooldownLeft}s remaining) to prevent ping-pong switching."
            )
        }

        val activeScore = when (active) {
            NetworkType.WIFI -> comparison.wifiScore.totalScore ?: 0.0
            NetworkType.CELLULAR -> comparison.cellularScore.totalScore ?: 0.0
            else -> 0.0
        }
        val targetScore = when (recommended) {
            NetworkType.WIFI -> comparison.wifiScore.totalScore ?: 0.0
            NetworkType.CELLULAR -> comparison.cellularScore.totalScore ?: 0.0
            else -> 0.0
        }

        val targetValidated = when (recommended) {
            NetworkType.WIFI -> comparison.wifiScore.isValidated
            NetworkType.CELLULAR -> comparison.cellularScore.isValidated
            else -> false
        }

        if (!targetValidated) {
            return AutoSwitchEvaluation(
                shouldExecute = false,
                targetNetwork = null,
                explanation = "Blocked: ${recommended.label} does not have validated internet connectivity."
            )
        }

        val activeValidated = when (active) {
            NetworkType.WIFI -> comparison.wifiScore.isValidated
            NetworkType.CELLULAR -> comparison.cellularScore.isValidated
            else -> false
        }

        val scoreDelta = targetScore - activeScore
        val belowMinimum = activeScore < settings.minimumNetworkScore || !activeValidated
        val exceedsHysteresis = scoreDelta >= settings.switchingThresholdDelta || !activeValidated

        if (!exceedsHysteresis && !belowMinimum) {
            consecutiveSwitchRecommendations = 0
            pendingRecommendedTarget = null
            return AutoSwitchEvaluation(
                shouldExecute = false,
                targetNetwork = recommended,
                explanation = "Hysteresis guard: ${recommended.label} leads by ${"%.1f".format(scoreDelta)} pts (requires >= ${settings.switchingThresholdDelta} pts)."
            )
        }

        if (pendingRecommendedTarget == recommended) {
            consecutiveSwitchRecommendations++
        } else {
            pendingRecommendedTarget = recommended
            consecutiveSwitchRecommendations = 1
        }

        // Immediate switch if current network lost internet validation; otherwise require 2 consecutive confirmations
        val requiredConfirmations = if (!activeValidated) 1 else 2
        return if (consecutiveSwitchRecommendations >= requiredConfirmations) {
            AutoSwitchEvaluation(
                shouldExecute = true,
                targetNetwork = recommended,
                explanation = "Approved: ${recommended.label} (${"%.0f".format(targetScore)}/100) outperforms ${active.label} (${"%.0f".format(activeScore)}/100) by ${"%.1f".format(scoreDelta)} pts."
            )
        } else {
            AutoSwitchEvaluation(
                shouldExecute = false,
                targetNetwork = recommended,
                explanation = "Confirming stability ($consecutiveSwitchRecommendations/$requiredConfirmations cycles) before switching to ${recommended.label}."
            )
        }
    }

    private suspend fun requestNetworkWithTimeout(
        request: NetworkRequest,
        timeoutMs: Long
    ): Pair<Network, ConnectivityManager.NetworkCallback>? {
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                var settled = false
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (!settled && cont.isActive) {
                            settled = true
                            cont.resume(network to this)
                        }
                    }

                    override fun onUnavailable() {
                        if (!settled && cont.isActive) {
                            settled = true
                            safeUnregister(this)
                            cont.resume(null)
                        }
                    }
                }
                cont.invokeOnCancellation {
                    if (!settled) safeUnregister(callback)
                }
                try {
                    connectivityManager.requestNetwork(request, callback, timeoutMs.toInt())
                } catch (e: SecurityException) {
                    Log.e(tag, "CHANGE_NETWORK_STATE permission missing", e)
                    if (!settled && cont.isActive) {
                        settled = true
                        cont.resume(null)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "requestNetwork failed", e)
                    if (!settled && cont.isActive) {
                        settled = true
                        cont.resume(null)
                    }
                }
            }
        }
    }

    private fun releaseActiveRequest() {
        activeRequestCallback?.let { safeUnregister(it) }
        activeRequestCallback = null
    }

    private fun safeUnregister(callback: ConnectivityManager.NetworkCallback) {
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }
    }

    fun cleanup() {
        releaseActiveRequest()
        try {
            connectivityManager.bindProcessToNetwork(null)
        } catch (_: Exception) {
        }
    }
}

data class AutoSwitchEvaluation(
    val shouldExecute: Boolean,
    val targetNetwork: NetworkType?,
    val explanation: String
)
