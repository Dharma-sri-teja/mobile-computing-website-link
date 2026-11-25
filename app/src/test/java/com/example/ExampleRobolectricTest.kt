package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.NetworkMetricEntity
import com.example.data.local.SwitchingHistoryEntity
import com.example.model.AppSettings
import com.example.model.NetworkComparisonResult
import com.example.model.NetworkScoreDetail
import com.example.model.NetworkType
import com.example.network.AppNetworkSwitcher
import com.example.network.NetworkDetector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun appNameMatchesNetSwitchAi() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("NetSwitch AI", context.getString(R.string.app_name))
    }

    @Test
    fun roomDatabasePersistsMetricsAndTracksUnsyncedQueue() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val metric = NetworkMetricEntity(
            deviceId = "robo-device-1",
            networkType = NetworkType.WIFI.code,
            downloadSpeed = 42.5,
            uploadSpeed = 18.2,
            latency = 24.0,
            jitter = 3.1,
            packetLoss = 0.0,
            signalStrength = -58,
            stabilityScore = 94.0,
            qualityScore = 88.0,
            isConnected = true,
            isValidated = true,
            syncedToCloud = false
        )
        db.networkMetricDao().insertMetric(metric)

        val switchEntry = SwitchingHistoryEntity(
            deviceId = "robo-device-1",
            previousNetwork = NetworkType.WIFI.code,
            newNetwork = NetworkType.CELLULAR.code,
            reason = "Wi-Fi degraded below threshold",
            switchingMode = "AUTOMATIC",
            scoreBefore = 32.0,
            scoreAfter = 84.0,
            wasSuccessful = true,
            syncedToCloud = false
        )
        db.switchingHistoryDao().insertSwitch(switchEntry)

        assertEquals(1, db.networkMetricDao().observeUnsyncedMetricCount().first())
        assertEquals(1, db.switchingHistoryDao().observeUnsyncedSwitchCount().first())

        db.networkMetricDao().markMetricsSynced(listOf(metric.id))
        assertEquals(0, db.networkMetricDao().observeUnsyncedMetricCount().first())

        db.close()
    }

    @Test
    fun automaticSwitcherAppliesHysteresisAndConfirmationBeforeSwitching() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val detector = NetworkDetector(context)
        val switcher = AppNetworkSwitcher(context, detector)

        val settings = AppSettings(
            autoSwitchingEnabled = true,
            switchingThresholdDelta = 15,
            minimumNetworkScore = 50,
            stabilityDurationSec = 30
        )

        val smallLeadComparison = NetworkComparisonResult(
            activeNetwork = NetworkType.WIFI,
            bestAvailableNetwork = NetworkType.CELLULAR,
            recommendedNetwork = NetworkType.CELLULAR,
            wifiScore = NetworkScoreDetail(NetworkType.WIFI, true, true, 75.0, null, null, null, null, null, null, ""),
            cellularScore = NetworkScoreDetail(NetworkType.CELLULAR, true, true, 82.0, null, null, null, null, null, null, ""),
            reason = "Small 7-point lead",
            shouldSwitch = false
        )
        val blockedByHysteresis = switcher.evaluateAutomaticDecision(smallLeadComparison, settings)
        assertFalse(blockedByHysteresis.shouldExecute)

        val strongLeadComparison = smallLeadComparison.copy(
            wifiScore = smallLeadComparison.wifiScore.copy(totalScore = 45.0),
            cellularScore = smallLeadComparison.cellularScore.copy(totalScore = 85.0),
            shouldSwitch = true
        )
        // First cycle confirms stability (1/2)
        val firstConfirm = switcher.evaluateAutomaticDecision(strongLeadComparison, settings)
        assertFalse(firstConfirm.shouldExecute)
        // Second consecutive cycle approves execution (2/2)
        val secondConfirm = switcher.evaluateAutomaticDecision(strongLeadComparison, settings)
        assertTrue(secondConfirm.shouldExecute)
        assertEquals(NetworkType.CELLULAR, secondConfirm.targetNetwork)
    }
}
