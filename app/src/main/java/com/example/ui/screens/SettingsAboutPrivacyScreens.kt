package com.example.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppSettings
import com.example.model.MonitoringState
import com.example.model.PreferredNetwork
import com.example.model.SupabaseConfig
import com.example.model.SyncStatus
import com.example.ui.components.KeyValueRow
import com.example.ui.components.PlatformDisclosureBanner
import com.example.ui.components.StatusChip
import com.example.ui.components.TelemetryCard
import com.example.ui.components.formatDateTime
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun SettingsScreen(
    settings: AppSettings,
    monitoringState: MonitoringState = MonitoringState.STOPPED,
    supabaseConfig: SupabaseConfig,
    syncStatus: SyncStatus,
    onStartMonitoring: () -> Unit = {},
    onStopMonitoring: () -> Unit = {},
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onSaveSupabaseConfig: (url: String, anonKey: String) -> Unit,
    onSignInSupabase: (url: String, anonKey: String, email: String, password: String) -> Unit,
    onSignOutSupabase: () -> Unit,
    onSyncNow: () -> Unit,
    onNavigateManualSwitch: () -> Unit
) {
    val context = LocalContext.current
    val sem = LocalTelemetryColors.current

    var urlInput by remember(supabaseConfig.projectUrl) { mutableStateOf(supabaseConfig.projectUrl) }
    var anonKeyInput by remember(supabaseConfig.anonKey) { mutableStateOf(supabaseConfig.anonKey) }
    var emailInput by remember(supabaseConfig.userEmail) { mutableStateOf(supabaseConfig.userEmail) }
    var passwordInput by remember { mutableStateOf("") }
    var showSqlGuide by remember { mutableStateOf(false) }

    var latencyUrlInput by remember(settings.latencyProbeUrl) { mutableStateOf(settings.latencyProbeUrl) }
    var downloadUrlInput by remember(settings.downloadTestUrl) { mutableStateOf(settings.downloadTestUrl) }
    var uploadUrlInput by remember(settings.uploadTestUrl) { mutableStateOf(settings.uploadTestUrl) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. SWITCHING & MONITORING CONTROLS
        item {
            TelemetryCard(modifier = Modifier.widthIn(max = 680.dp)) {
                Text("Intelligent Switching & Monitoring", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                SettingToggleRow(
                    title = "Real-Time Live Monitoring",
                    subtitle = "Run foreground service to continuously monitor signal, speed, and quality",
                    checked = monitoringState == MonitoringState.ACTIVE,
                    testTag = "settings_live_monitoring_toggle"
                ) { active ->
                    if (active) onStartMonitoring() else onStopMonitoring()
                }

                SettingToggleRow(
                    title = "Automatic Switching",
                    subtitle = "Automatically switch app traffic when active network degrades",
                    checked = settings.autoSwitchingEnabled,
                    testTag = "settings_auto_switch_toggle"
                ) { enabled -> onUpdateSettings { it.copy(autoSwitchingEnabled = enabled) } }

                OutlinedButton(
                    onClick = onNavigateManualSwitch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_manual_switch_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Open Manual Network Switching")
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Preferred Network", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreferredNetwork.entries.forEach { pref ->
                        FilterChip(
                            selected = settings.preferredNetwork == pref,
                            onClick = { onUpdateSettings { it.copy(preferredNetwork = pref) } },
                            label = { Text(pref.code) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                SettingSliderRow(
                    label = "Monitoring Interval",
                    valueText = "${settings.monitoringIntervalSec}s",
                    value = settings.monitoringIntervalSec.toFloat(),
                    range = 5f..60f,
                    onChange = { v -> onUpdateSettings { it.copy(monitoringIntervalSec = v.toInt()) } }
                )
                SettingSliderRow(
                    label = "Speed Test Interval (conserves data)",
                    valueText = "${settings.speedTestIntervalSec}s",
                    value = settings.speedTestIntervalSec.toFloat(),
                    range = 60f..600f,
                    onChange = { v -> onUpdateSettings { it.copy(speedTestIntervalSec = v.toInt()) } }
                )
                SettingSliderRow(
                    label = "Minimum Network Score Floor",
                    valueText = "${settings.minimumNetworkScore}/100",
                    value = settings.minimumNetworkScore.toFloat(),
                    range = 25f..85f,
                    onChange = { v -> onUpdateSettings { it.copy(minimumNetworkScore = v.toInt()) } }
                )
                SettingSliderRow(
                    label = "Switching Threshold (Hysteresis Delta)",
                    valueText = "+${settings.switchingThresholdDelta} pts",
                    value = settings.switchingThresholdDelta.toFloat(),
                    range = 5f..30f,
                    onChange = { v -> onUpdateSettings { it.copy(switchingThresholdDelta = v.toInt()) } }
                )
                SettingSliderRow(
                    label = "Stability Cooldown Duration",
                    valueText = "${settings.stabilityDurationSec}s",
                    value = settings.stabilityDurationSec.toFloat(),
                    range = 10f..180f,
                    onChange = { v -> onUpdateSettings { it.copy(stabilityDurationSec = v.toInt()) } }
                )
            }
        }

        // 2. APPEARANCE, NOTIFICATIONS, BATTERY & DATA CONTROLS
        item {
            TelemetryCard(modifier = Modifier.widthIn(max = 680.dp)) {
                Text("Display, Battery & Data Controls", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                SettingToggleRow(
                    title = "Dark Mission-Control Theme",
                    subtitle = "Navy dark aesthetic (turn off for Light theme)",
                    checked = settings.darkTheme,
                    testTag = "settings_theme_toggle"
                ) { dark -> onUpdateSettings { it.copy(darkTheme = dark) } }

                SettingToggleRow(
                    title = "Network Quality & Switching Alerts",
                    subtitle = "Notify when quality drops to Poor, a better network is available, or switching occurs",
                    checked = settings.notificationsEnabled,
                    testTag = "settings_notifications_toggle"
                ) { enabled -> onUpdateSettings { it.copy(notificationsEnabled = enabled) } }

                SettingToggleRow(
                    title = "Battery-Saving Mode",
                    subtitle = "Doubles probe intervals and suspends background speed tests",
                    checked = settings.batterySavingMode,
                    testTag = "settings_battery_toggle"
                ) { enabled -> onUpdateSettings { it.copy(batterySavingMode = enabled) } }

                SettingToggleRow(
                    title = "Allow Speed Tests on Metered Cellular",
                    subtitle = "Keep OFF to prevent automatic cellular data consumption",
                    checked = settings.meteredSpeedTestsAllowed,
                    testTag = "settings_metered_toggle"
                ) { allowed -> onUpdateSettings { it.copy(meteredSpeedTestsAllowed = allowed) } }

                SettingSliderRow(
                    label = "Download Test Payload Size Cap",
                    valueText = "${settings.downloadTestSizeKb} KB",
                    value = settings.downloadTestSizeKb.toFloat(),
                    range = 64f..1024f,
                    onChange = { kb -> onUpdateSettings { it.copy(downloadTestSizeKb = kb.toInt()) } }
                )

                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("battery_opt_settings_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.BatterySaver, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Android Battery Optimization Settings")
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Android Doze Mode and OEM battery managers can throttle background services when the screen is off. NetSwitch AI uses a compliant dataSync Foreground Service, though unrestricted execution cannot be guaranteed on all OEM ROMs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 3. SUPABASE CLOUD SYNCHRONIZATION CONFIGURATION
        item {
            TelemetryCard(
                modifier = Modifier.widthIn(max = 680.dp),
                borderColor = if (supabaseConfig.isConfigured && settings.cloudSyncEnabled) {
                    sem.good.copy(alpha = 0.6f)
                } else sem.cardBorder
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Supabase Cloud Sync (Lovable)", style = MaterialTheme.typography.titleMedium)
                    }
                    StatusChip(
                        text = if (supabaseConfig.isConfigured) "CONFIGURED" else "NOT CONFIGURED",
                        color = if (supabaseConfig.isConfigured) sem.good else sem.warning
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Connects to your Lovable website's Supabase tables (network_metrics & switching_history). Offline-first via Room; never enter a service_role key here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                SettingToggleRow(
                    title = "Enable Cloud Synchronization",
                    subtitle = if (supabaseConfig.isConfigured) "Auto-sync measurements when online" else "Save Supabase URL & Anon Key first",
                    checked = settings.cloudSyncEnabled && supabaseConfig.isConfigured,
                    testTag = "settings_cloud_sync_toggle"
                ) { enabled ->
                    if (supabaseConfig.isConfigured || !enabled) {
                        onUpdateSettings { it.copy(cloudSyncEnabled = enabled) }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Supabase Project URL") },
                    placeholder = { Text("https://your-project.supabase.co") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("supabase_url_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = anonKeyInput,
                    onValueChange = { anonKeyInput = it },
                    label = { Text("Supabase Public Anon Key (RLS Protected)") },
                    placeholder = { Text("eyJhbGciOi... (NEVER service_role)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("supabase_anon_key_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onSaveSupabaseConfig(urlInput, anonKeyInput) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_supabase_config_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Supabase Credentials")
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Optional RLS User Sign-In (Email / Password)", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("Supabase User Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onSignInSupabase(urlInput, anonKeyInput, emailInput, passwordInput)
                            passwordInput = ""
                        },
                        enabled = urlInput.startsWith("https://") && anonKeyInput.length >= 20 && emailInput.isNotBlank() && passwordInput.isNotBlank(),
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Sign In")
                    }
                    if (supabaseConfig.accessToken.isNotBlank()) {
                        OutlinedButton(
                            onClick = onSignOutSupabase,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Sign Out")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                KeyValueRow("Device ID", supabaseConfig.deviceId.ifBlank { "Auto-generated" })
                KeyValueRow("Auth Session", if (supabaseConfig.accessToken.isNotBlank()) "Authenticated (${supabaseConfig.userEmail})" else "Anon Key Mode")
                KeyValueRow("Unsynced Metrics", "${syncStatus.pendingMetricsCount}")
                KeyValueRow("Unsynced Switches", "${syncStatus.pendingSwitchesCount}")
                KeyValueRow("Last Cloud Sync", formatDateTime(syncStatus.lastSyncTimestampMs))
                if (syncStatus.lastError != null) {
                    Text(
                        text = "Sync Error: ${syncStatus.lastError}",
                        style = MaterialTheme.typography.bodySmall,
                        color = sem.danger
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSyncNow,
                        enabled = !syncStatus.isSyncing && supabaseConfig.isConfigured && settings.cloudSyncEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("supabase_sync_now_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(if (syncStatus.isSyncing) "Syncing..." else "Sync Now")
                    }
                    OutlinedButton(
                        onClick = { showSqlGuide = !showSqlGuide },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (showSqlGuide) "Hide SQL" else "View SQL Schema")
                    }
                }

                if (showSqlGuide) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = """
CREATE TABLE network_metrics (
  id uuid PRIMARY KEY,
  device_id text NOT NULL,
  network_type text NOT NULL,
  download_speed double precision,
  upload_speed double precision,
  latency double precision,
  jitter double precision,
  packet_loss double precision,
  signal_strength integer,
  is_connected boolean NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE switching_history (
  id uuid PRIMARY KEY,
  device_id text NOT NULL,
  previous_network text NOT NULL,
  new_network text NOT NULL,
  reason text NOT NULL,
  switching_mode text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
-- Enable RLS and grant authenticated insert/select policies.
                        """.trimIndent(),
                        fontFamily = MonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 4. CONFIGURABLE PROBE ENDPOINTS
        item {
            TelemetryCard(modifier = Modifier.widthIn(max = 680.dp)) {
                Text("Configurable Measurement Endpoints", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = latencyUrlInput,
                    onValueChange = { latencyUrlInput = it },
                    label = { Text("Latency Probe Endpoint") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = downloadUrlInput,
                    onValueChange = { downloadUrlInput = it },
                    label = { Text("Download Throughput Endpoint") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uploadUrlInput,
                    onValueChange = { uploadUrlInput = it },
                    label = { Text("Upload Throughput Endpoint") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        onUpdateSettings {
                            it.copy(
                                latencyProbeUrl = latencyUrlInput,
                                downloadTestUrl = downloadUrlInput,
                                uploadTestUrl = uploadUrlInput
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Measurement Endpoints")
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.size(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun SettingSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(valueText, fontFamily = MonoFontFamily, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
fun AboutProjectScreen(
    onOpenPrivacy: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            TelemetryCard(modifier = Modifier.widthIn(max = 680.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Mobile Computing Capstone", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.project_full_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "NetSwitch AI is a native Android application built with Kotlin, Jetpack Compose, MVVM, ConnectivityManager, WifiManager, TelephonyManager, OkHttp per-network sockets, Room Database, and Supabase PostgREST. It collects genuine real-time network telemetry, compares Wi-Fi and cellular links using an explainable weighted scoring engine, predicts degradation trends, and performs hysteresis-protected app-level network switching.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        item {
            TelemetryCard(modifier = Modifier.widthIn(max = 680.dp)) {
                Text("Device & Runtime Environment", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                KeyValueRow("Device Model", "${Build.MANUFACTURER} ${Build.MODEL}")
                KeyValueRow("Android Version", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                KeyValueRow("Min / Target SDK", "API 26 / API 36")
                KeyValueRow("Architecture", "MVVM + Clean Repository + Room + Flow")
                KeyValueRow("Cloud Backend", "Supabase REST + RLS (Optional)")
            }
        }

        item {
            PlatformDisclosureBanner(
                title = "Honest Engineering Scope (Implemented vs OS-Restricted)",
                body = "• Genuinely Implemented: Multi-callback ConnectivityManager monitoring, WifiInfo/CellSignalStrength reading, real HTTPS latency/jitter/probe-loss & capped download/upload measurements, explainable weighted scoring, linear-regression predictive trend engine (ML-ready feature vector), ConnectivityManager.requestNetwork() + bindProcessToNetwork() app-level switching with hysteresis & cooldown, Foreground Service monitoring, Room persistence, CSV export, and Supabase RLS sync.\n\n• Restricted by Standard Android Security: Third-party apps without root/system signature cannot toggle global Wi-Fi/Mobile Data radios or override system-wide routing for other apps. Raw ICMP ping sockets may also be blocked by OEM kernels, so probe loss is explicitly measured and labeled as HTTPS probe failure rate.",
                modifier = Modifier.widthIn(max = 680.dp)
            )
        }

        item {
            OutlinedButton(
                onClick = onOpenPrivacy,
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("about_open_privacy_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Policy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text("Read Privacy & Data Handling Policy")
            }
        }
    }
}

@Composable
fun PrivacyInfoScreen(
    onClearAllData: () -> Unit,
    onOpenPermissions: () -> Unit
) {
    val sem = LocalTelemetryColors.current
    var showConfirmDelete by remember { mutableStateOf(false) }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Delete All Local Telemetry?") },
            text = { Text("This permanently deletes all locally stored Room metrics, switching history, prediction logs, and connection events on this device.") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDelete = false
                        onClearAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = sem.danger)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            TelemetryCard(modifier = Modifier.widthIn(max = 680.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Policy, contentDescription = null, tint = sem.good)
                    Text("Privacy & Minimal Permissions", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "NetSwitch AI collects strictly technical network telemetry required for predictive network switching research. It NEVER accesses or transmits your contacts, SMS messages, call logs, personal files, photos, or GPS coordinates.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            TelemetryCard(modifier = Modifier.widthIn(max = 680.dp)) {
                Text("Why Each Permission Is Requested", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                KeyValueRow("ACCESS_NETWORK_STATE / CHANGE_NETWORK_STATE", "Monitor & request networks")
                KeyValueRow("ACCESS_WIFI_STATE", "Inspect Wi-Fi link & RSSI")
                KeyValueRow("ACCESS_FINE_LOCATION", "Android gate for Wi-Fi SSID")
                KeyValueRow("READ_PHONE_STATE", "Cellular 4G/5G generation")
                KeyValueRow("FOREGROUND_SERVICE_DATA_SYNC", "Persistent monitoring")
                KeyValueRow("POST_NOTIFICATIONS", "Live status & switch alerts")
            }
        }

        item {
            TelemetryCard(modifier = Modifier.widthIn(max = 680.dp)) {
                Text("Storage & Opt-In Cloud Sync", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• Local-First Storage: All measurements are stored privately in an on-device Room SQLite database.\n• Opt-In Cloud Sync: Synchronization to Supabase is OFF by default and only runs if you explicitly enter your own Supabase Project URL and public anon key (protected by Row Level Security). A randomly generated app-scoped UUID is used as device_id rather than hardware IMEI or MAC addresses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenPermissions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("privacy_manage_permissions_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Review Runtime Permissions")
                }

                Button(
                    onClick = { showConfirmDelete = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("privacy_clear_data_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = sem.danger),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Delete All Local Telemetry Data")
                }
            }
        }
    }
}
