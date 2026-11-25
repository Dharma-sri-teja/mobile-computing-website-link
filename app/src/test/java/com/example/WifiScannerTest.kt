package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.KnownWifiNetworkEntity
import com.example.network.WifiScanner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WifiScannerTest {

    @Test
    fun securityParserIdentifiesWpa3Wpa2AndOpenNetworks() {
        assertEquals("WPA2/WPA3", WifiScanner.parseSecurityLabel("[WPA2-PSK-CCMP][RSN-PSK+SAE-CCMP][ESS]"))
        assertEquals("WPA3", WifiScanner.parseSecurityLabel("[RSN-SAE-CCMP][ESS]"))
        assertEquals("WPA2", WifiScanner.parseSecurityLabel("[WPA2-PSK-CCMP][ESS]"))
        assertEquals("Open", WifiScanner.parseSecurityLabel("[ESS]"))
    }

    @Test
    fun phySpeedEstimateScalesWithStandardChannelWidthAndRssi() {
        val strongWifi6 = WifiScanner.estimatePhySpeedMbps(
            wifiStandard = "Wi-Fi 6 (802.11ax)",
            channelWidthMhz = 80,
            rssiDbm = -50,
            frequencyBand = "5 GHz"
        )
        val weakWifi6 = WifiScanner.estimatePhySpeedMbps(
            wifiStandard = "Wi-Fi 6 (802.11ax)",
            channelWidthMhz = 80,
            rssiDbm = -86,
            frequencyBand = "5 GHz"
        )
        val legacy24 = WifiScanner.estimatePhySpeedMbps(
            wifiStandard = "Wi-Fi 4 (802.11n)",
            channelWidthMhz = 20,
            rssiDbm = -50,
            frequencyBand = "2.4 GHz"
        )

        assertTrue(strongWifi6 > weakWifi6)
        assertTrue(strongWifi6 > legacy24)
        assertTrue(weakWifi6 >= 1)
    }

    @Test
    fun frequencyBandMappingCovers24GHz5GHzAnd6GHz() {
        assertEquals("2.4 GHz", WifiScanner.frequencyToBand(2437))
        assertEquals("5 GHz", WifiScanner.frequencyToBand(5180))
        assertEquals("6 GHz", WifiScanner.frequencyToBand(6115))
    }

    @Test
    fun roomPersistsPreviouslyConnectedAndSeenWifiNetworks() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val dao = db.knownWifiNetworkDao()
        dao.upsert(
            KnownWifiNetworkEntity(
                ssid = "Campus_WiFi_5G",
                lastRssiDbm = -58,
                bestRssiDbm = -52,
                frequencyBand = "5 GHz",
                wifiStandard = "Wi-Fi 6 (802.11ax)",
                securityLabel = "WPA3",
                estimatedPhySpeedMbps = 408,
                lastMeasuredDownloadMbps = 142.5,
                lastMeasuredLatencyMs = 14.2,
                hasBeenConnected = true,
                timesConnected = 3
            )
        )
        dao.upsert(
            KnownWifiNetworkEntity(
                ssid = "Cafe_Guest",
                lastRssiDbm = -74,
                bestRssiDbm = -74,
                frequencyBand = "2.4 GHz",
                wifiStandard = "Wi-Fi 4 (802.11n)",
                securityLabel = "Open",
                estimatedPhySpeedMbps = 20,
                hasBeenConnected = false,
                timesConnected = 0
            )
        )

        val stored = dao.observeKnownNetworks().first()
        assertEquals(2, stored.size)
        // Previously connected networks sort ahead of merely seen networks
        assertEquals("Campus_WiFi_5G", stored.first().ssid)
        assertEquals(3, stored.first().timesConnected)

        db.close()
    }
}
