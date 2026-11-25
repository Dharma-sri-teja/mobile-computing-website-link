# NetSwitch AI — Intelligent Mobile Application for Predictive Network Switching

Native Android application (Kotlin + Jetpack Compose + MVVM) built for a **Mobile Computing Capstone Project**. Collects genuine real-time Wi-Fi and cellular telemetry directly from Android connectivity APIs, performs controlled performance probing, evaluates interfaces with an explainable weighted scoring engine, forecasts degradation trends, and executes hysteresis-protected app-level network switching.

---

## Architecture & Tech Stack

- **Language & UI:** Kotlin, Jetpack Compose, Material Design 3 (Navy Mission-Control Dark Theme + Light Theme toggle)
- **Architecture:** MVVM + Repository Pattern (`StateFlow` + `Flow` + Kotlin Coroutines)
- **Min / Target SDK:** API 26 (Android 8.0) / API 36
- **Real-Time Android Network APIs:** `ConnectivityManager`, independent `NetworkCallback`s (Default, Wi-Fi, Cellular, Ethernet, VPN), `NetworkCapabilities`, `WifiManager`/`WifiInfo`, `TelephonyManager`/`TelephonyCallback`/`CellSignalStrength`
- **Performance Probing:** OkHttp with per-interface `Network.socketFactory` binding (HTTPS RTT latency min/avg/max, RFC-style jitter, HTTPS probe failure rate, controlled data-capped download & upload speed tests)
- **Intelligence Layer:** Explainable configurable weighted scoring (`NetworkScoringEngine`) + modular `NetworkPredictor` (`RuleBasedNetworkPredictor` with linear regression trend analysis, strict `MIN_SAMPLES = 5` guard, and `OnDeviceMlModelRunner` plug-in for TensorFlow Lite / Random Forest)
- **Background Monitoring:** Compliant `dataSync` Android Foreground Service (`NetworkMonitoringService`) with Start / Pause / Resume / Stop controls
- **Local Storage:** Offline-first Room SQLite Database (`network_metrics`, `switching_history`, `prediction_logs`, `connection_events`) + Preferences DataStore
- **Cloud Sync:** Optional Supabase PostgREST + Auth RLS integration (`SupabaseSyncRepository`) with offline queueing and safe retry
- **Export:** Shareable CSV telemetry & switching history reports via `FileProvider`

---

## Configuring Supabase (Connecting to Your Lovable Website)

Cloud sync is **disabled by default** and the app operates 100% offline using Room until you configure your own Supabase project in **Settings → Supabase Cloud Sync**.

### 1. Create the Supabase Tables & Row Level Security (RLS)

Run this SQL in your Supabase SQL Editor:

```sql
CREATE TABLE IF NOT EXISTS public.network_metrics (
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

CREATE TABLE IF NOT EXISTS public.switching_history (
    id uuid PRIMARY KEY,
    device_id text NOT NULL,
    previous_network text NOT NULL,
    new_network text NOT NULL,
    reason text NOT NULL,
    switching_mode text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.network_metrics ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.switching_history ENABLE ROW LEVEL SECURITY;

-- Example Authenticated RLS Policies (recommended for production)
CREATE POLICY "Allow authenticated insert network_metrics"
    ON public.network_metrics FOR INSERT TO authenticated WITH CHECK (true);

CREATE POLICY "Allow authenticated select network_metrics"
    ON public.network_metrics FOR SELECT TO authenticated USING (true);

CREATE POLICY "Allow authenticated insert switching_history"
    ON public.switching_history FOR INSERT TO authenticated WITH CHECK (true);

CREATE POLICY "Allow authenticated select switching_history"
    ON public.switching_history FOR SELECT TO authenticated USING (true);
```

### 2. Configure Inside the App

1. Open **Settings → Supabase Cloud Sync**.
2. Paste your **Supabase Project URL** (`https://<ref>.supabase.co`).
3. Paste your **public `anon` key** (never paste `service_role`; the app actively blocks `service_role` JWTs).
4. Optionally sign in with a Supabase Auth email & password so requests use your authenticated JWT for RLS.
5. Toggle **Enable Cloud Synchronization** ON and tap **Sync Now**.

---

## Running on a Physical Android Smartphone

1. Export the project or open it in **Android Studio** (Ladybug or newer).
2. Enable **Developer Options** and **USB Debugging** on your Android smartphone (Android 8.0 / API 26 or higher).
3. Connect the phone via USB and accept the RSA debugging prompt.
4. Select your physical device in Android Studio's device dropdown and click **Run (`app`)**.
5. Grant **Location** (for Wi-Fi SSID), **Phone State** (for 4G/5G generation), and **Notifications** (for Foreground Service) on the Onboarding screen.
6. For live faculty demonstrations, open **Presentation Mode** and tap **START LIVE DEMONSTRATION**.

### Generating a Signed Release APK / AAB

1. In Android Studio: **Build → Generate Signed App Bundle / APK**.
2. Choose **APK** (for direct sideloading on your demo phone) or **Android App Bundle (AAB)** (for Play Store).
3. Create or select your upload keystore (`.jks`), enter passwords, select the `release` build variant, and click **Create**.
4. Or from the command line with keystore env vars set:
   ```bash
   KEYSTORE_PATH=/path/to/upload.jks STORE_PASSWORD=... KEY_PASSWORD=... ./gradlew assembleRelease
   ```

---

## Honest Engineering Capability Matrix

| Feature | Status | Notes / Android Platform Constraints |
|---|---|---|
| Real-time Wi-Fi & Cellular Detection (`ConnectivityManager`, `WifiInfo`, `TelephonyManager`) | **Genuinely Implemented** | SSID requires Location permission + Location Services ON; 4G/5G generation requires `READ_PHONE_STATE`. |
| Latency (min/avg/max), Jitter & Probe Loss | **Genuinely Implemented** | Uses timed HTTPS probes; explicitly labeled as HTTPS Probe Failure Rate because raw ICMP sockets are restricted on many unrooted devices. |
| Download & Upload Throughput | **Genuinely Implemented** | Controlled data-capped HTTP payloads to configurable endpoints (`speed.cloudflare.com` default); never runs continuous heavy transfers. |
| Explainable Weighted Scoring & Comparison | **Genuinely Implemented** | Strictly disqualifies interfaces lacking `NET_CAPABILITY_VALIDATED`. Weights configurable in UI. |
| Predictive Trend Analysis | **Genuinely Implemented** | Linear-regression slope engine requiring ≥5 real samples before making claims. Exposes `OnDeviceMlModelRunner` interface + CSV export for plugging in a trained `.tflite` / Random Forest model. |
| App-Level Network Switching (`requestNetwork` + `bindProcessToNetwork`) | **Genuinely Implemented** | Routes **this app's process/socket traffic** with hysteresis and stability cooldown. Standard Android security prevents non-system apps from toggling global Wi-Fi/Mobile Data or rerouting other apps. |
| Foreground Service Background Monitoring | **Genuinely Implemented** | Uses `foregroundServiceType="dataSync"` with Pause/Resume/Stop notification actions. OEM Doze/battery savers may still restrict background work unless exempted by the user. |
| Room Local Database & CSV Report Export | **Genuinely Implemented** | 4 Room tables + shareable CSV export via `FileProvider`. |
| Supabase Cloud Synchronization | **Genuinely Implemented** | Requires user-supplied URL + public `anon` key + table/RLS setup on their Supabase project. |
| Physical-Device RF Scenarios (Weak Wi-Fi, Captive Portal, 5G Handover) | **Requires Physical Smartphone Testing** | Must be verified on a real handset with SIM + Wi-Fi AP; unit & Robolectric tests validate scoring, prediction, hysteresis, and Room persistence on JVM. |
