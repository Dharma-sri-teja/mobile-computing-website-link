package com.example.domain

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.example.data.local.NetworkMetricEntity
import com.example.data.local.SwitchingHistoryEntity
import com.example.model.ReportPeriod
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    private val fileStampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun exportCsvReport(
        context: Context,
        period: ReportPeriod,
        metrics: List<NetworkMetricEntity>,
        switches: List<SwitchingHistoryEntity>
    ): Result<Intent> {
        return try {
            val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val stamp = fileStampFormat.format(Date())
            val file = File(reportsDir, "netswitch_report_${period.name.lowercase()}_$stamp.csv")

            val csv = buildString {
                appendLine("# NetSwitch AI — Predictive Network Switching Telemetry Report")
                appendLine("# Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT})")
                appendLine("# Period: ${period.label} | Generated: ${isoFormat.format(Date())}")
                appendLine()
                appendLine("## NETWORK METRICS (${metrics.size} rows)")
                appendLine("timestamp,network_type,download_mbps,upload_mbps,avg_latency_ms,jitter_ms,probe_loss_pct,signal_dbm,stability_score,quality_score,is_connected,is_validated")
                metrics.forEach { m ->
                    appendLine(
                        listOf(
                            isoFormat.format(Date(m.createdAtMs)),
                            m.networkType,
                            m.downloadSpeed?.let { "%.2f".format(Locale.US, it) } ?: "",
                            m.uploadSpeed?.let { "%.2f".format(Locale.US, it) } ?: "",
                            m.latency?.let { "%.1f".format(Locale.US, it) } ?: "",
                            m.jitter?.let { "%.1f".format(Locale.US, it) } ?: "",
                            m.packetLoss?.let { "%.1f".format(Locale.US, it) } ?: "",
                            m.signalStrength?.toString() ?: "",
                            m.stabilityScore?.let { "%.1f".format(Locale.US, it) } ?: "",
                            m.qualityScore?.let { "%.1f".format(Locale.US, it) } ?: "",
                            m.isConnected.toString(),
                            m.isValidated.toString()
                        ).joinToString(",")
                    )
                }
                appendLine()
                appendLine("## SWITCHING HISTORY (${switches.size} rows)")
                appendLine("timestamp,previous_network,new_network,mode,score_before,score_after,success,reason")
                switches.forEach { s ->
                    val safeReason = s.reason.replace("\"", "'").replace(",", ";")
                    appendLine(
                        listOf(
                            isoFormat.format(Date(s.createdAtMs)),
                            s.previousNetwork,
                            s.newNetwork,
                            s.switchingMode,
                            s.scoreBefore?.let { "%.1f".format(Locale.US, it) } ?: "",
                            s.scoreAfter?.let { "%.1f".format(Locale.US, it) } ?: "",
                            s.wasSuccessful.toString(),
                            "\"$safeReason\""
                        ).joinToString(",")
                    )
                }
            }

            file.writeText(csv)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "NetSwitch AI Telemetry Report (${period.label})")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Result.success(Intent.createChooser(shareIntent, "Export Telemetry CSV Report"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
