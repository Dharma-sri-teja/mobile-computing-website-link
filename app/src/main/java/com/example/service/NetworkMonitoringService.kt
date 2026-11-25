package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.NetSwitchApplication
import com.example.R
import com.example.model.MonitoringState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground Service keeping real-time network monitoring, predictive analysis,
 * and automatic app-level switching alive when the user leaves the dashboard.
 */
class NetworkMonitoringService : Service() {

    companion object {
        private const val TAG = "NetworkMonitoringSvc"
        const val CHANNEL_MONITORING = "netswitch_monitoring_channel"
        const val CHANNEL_ALERTS = "netswitch_alerts_channel"
        const val NOTIFICATION_ID_FOREGROUND = 1001
        const val NOTIFICATION_ID_ALERT = 1002

        const val ACTION_START = "com.example.action.START_MONITORING"
        const val ACTION_PAUSE = "com.example.action.PAUSE_MONITORING"
        const val ACTION_RESUME = "com.example.action.RESUME_MONITORING"
        const val ACTION_STOP = "com.example.action.STOP_MONITORING"

        fun start(context: Context) {
            val intent = Intent(context, NetworkMonitoringService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun pause(context: Context) {
            val intent = Intent(context, NetworkMonitoringService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, NetworkMonitoringService::class.java).apply {
                action = ACTION_RESUME
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, NetworkMonitoringService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitoringJob: Job? = null
    private lateinit var notificationManager: NotificationManager

    private val repository by lazy {
        (application as NetSwitchApplication).telemetryRepository
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
        repository.onAutoSwitchNotification = { title, body ->
            serviceScope.launch {
                val settings = repository.settingsFlow.first()
                if (settings.notificationsEnabled) {
                    postAlertNotification(title, body)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                pauseMonitoring()
                return START_STICKY
            }
            ACTION_RESUME, ACTION_START, null -> {
                startForegroundSafe(buildStatusNotification(MonitoringState.ACTIVE))
                startMonitoringLoop()
                return START_STICKY
            }
            else -> return START_STICKY
        }
    }

    private fun startForegroundSafe(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID_FOREGROUND,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID_FOREGROUND, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to promote to foreground service", e)
            repository.reportError("Foreground service restriction: ${e.message}")
        }
    }

    private fun startMonitoringLoop() {
        repository.setMonitoringState(MonitoringState.ACTIVE)
        monitoringJob?.cancel()
        monitoringJob = serviceScope.launch {
            while (isActive && repository.monitoringState.value == MonitoringState.ACTIVE) {
                repository.runMonitoringCycle(forceSpeedTest = false)
                updateNotification(MonitoringState.ACTIVE)

                val settings = repository.settingsFlow.first()
                val baseInterval = settings.monitoringIntervalSec.coerceIn(3, 120)
                val effectiveInterval = if (settings.batterySavingMode) {
                    (baseInterval * 2).coerceAtMost(180)
                } else {
                    baseInterval
                }
                delay(effectiveInterval * 1000L)
            }
        }
    }

    private fun pauseMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        repository.setMonitoringState(MonitoringState.PAUSED)
        updateNotification(MonitoringState.PAUSED)
    }

    private fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        repository.setMonitoringState(MonitoringState.STOPPED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(state: MonitoringState) {
        try {
            notificationManager.notify(NOTIFICATION_ID_FOREGROUND, buildStatusNotification(state))
        } catch (_: Exception) {
        }
    }

    private fun buildStatusNotification(state: MonitoringState): Notification {
        val snap = repository.snapshot.value
        val perf = repository.activeMeasurement.value
        val comp = repository.comparison.value
        val score = when (snap.activeNetworkType.code) {
            "WIFI" -> comp?.wifiScore?.totalScore
            "CELLULAR" -> comp?.cellularScore?.totalScore
            else -> null
        }

        val title = when (state) {
            MonitoringState.ACTIVE -> "NetSwitch AI • LIVE (${snap.activeNetworkType.label})"
            MonitoringState.PAUSED -> "NetSwitch AI • Monitoring Paused"
            MonitoringState.STOPPED -> "NetSwitch AI • Stopped"
        }

        val latencyText = perf?.avgLatencyMs?.let { "${"%.0f".format(it)} ms" } ?: "N/A"
        val scoreText = score?.let { "${"%.0f".format(it)}/100" } ?: "N/A"
        val body = "Score: $scoreText • Latency: $latencyText • ${snap.statusMessage}"

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleAction = if (state == MonitoringState.ACTIVE) {
            val pausePi = PendingIntent.getService(
                this,
                1,
                Intent(this, NetworkMonitoringService::class.java).setAction(ACTION_PAUSE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action(0, "Pause", pausePi)
        } else {
            val resumePi = PendingIntent.getService(
                this,
                2,
                Intent(this, NetworkMonitoringService::class.java).setAction(ACTION_RESUME),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action(0, "Resume", resumePi)
        }

        val stopPi = PendingIntent.getService(
            this,
            3,
            Intent(this, NetworkMonitoringService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_MONITORING)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOngoing(state != MonitoringState.STOPPED)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(toggleAction)
            .addAction(0, "Stop", stopPi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun postAlertNotification(title: String, body: String) {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            notificationManager.notify(NOTIFICATION_ID_ALERT, notification)
        } catch (_: Exception) {
        }
    }

    private fun createNotificationChannels() {
        val monitoringChannel = NotificationChannel(
            CHANNEL_MONITORING,
            getString(R.string.notification_channel_monitoring),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_monitoring_desc)
        }

        val alertsChannel = NotificationChannel(
            CHANNEL_ALERTS,
            getString(R.string.notification_channel_alerts),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_channel_alerts_desc)
        }

        notificationManager.createNotificationChannel(monitoringChannel)
        notificationManager.createNotificationChannel(alertsChannel)
    }

    override fun onDestroy() {
        monitoringJob?.cancel()
        repository.onAutoSwitchNotification = null
        if (repository.monitoringState.value != MonitoringState.STOPPED) {
            repository.setMonitoringState(MonitoringState.STOPPED)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
