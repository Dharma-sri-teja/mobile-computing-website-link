package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.NetworkMetricEntity
import com.example.model.DegradationRisk
import com.example.model.NetworkType
import com.example.model.SmaTrendDirection
import com.example.service.PredictiveAnalyticsService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PredictiveAnalyticsServiceTest {

    private lateinit var database: AppDatabase
    private lateinit var service: PredictiveAnalyticsService

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        service = PredictiveAnalyticsService(database.networkMetricDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun simpleMovingAverage_calculatesExactRollingMeans() {
        val values = listOf(-60.0, -63.0, -66.0, -69.0, -72.0)
        val sma3 = PredictiveAnalyticsService.computeSimpleMovingAverage(values, 3)

        assertEquals(5, sma3.size)
        assertNull(sma3[0])
        assertNull(sma3[1])
        assertEquals(-63.0, sma3[2]!!, 0.001)
        assertEquals(-66.0, sma3[3]!!, 0.001)
        assertEquals(-69.0, sma3[4]!!, 0.001)
    }

    @Test
    fun forecast_reportsInsufficientDataWhenRoomHistoryBelowWindow() = runBlocking {
        val dao = database.networkMetricDao()
        dao.insertMetric(
            NetworkMetricEntity(
                deviceId = "dev-1",
                networkType = NetworkType.WIFI.code,
                signalStrength = -60,
                createdAtMs = 10_000L
            )
        )
        dao.insertMetric(
            NetworkMetricEntity(
                deviceId = "dev-1",
                networkType = NetworkType.WIFI.code,
                signalStrength = -62,
                createdAtMs = 20_000L
            )
        )

        val forecast = service.observeSignalDegradationForecast(
            networkType = NetworkType.WIFI,
            shortWindow = 3,
            longWindow = 6
        ).first()

        assertFalse(forecast.hasEnoughData)
        assertEquals(2, forecast.sampleCount)
        assertEquals(SmaTrendDirection.INSUFFICIENT_DATA, forecast.trendDirection)
        assertEquals(DegradationRisk.UNKNOWN, forecast.degradationRisk)
        assertTrue(forecast.series.none { it.isForecast })
    }

    @Test
    fun forecast_detectsWifiSignalDegradationFromRoomHistory() = runBlocking {
        val dao = database.networkMetricDao()
        val degradingSignals = listOf(-56, -59, -63, -68, -73, -78, -82)
        degradingSignals.forEachIndexed { idx, rssi ->
            dao.insertMetric(
                NetworkMetricEntity(
                    deviceId = "dev-1",
                    networkType = NetworkType.WIFI.code,
                    signalStrength = rssi,
                    isConnected = true,
                    isValidated = true,
                    createdAtMs = 100_000L + idx * 10_000L
                )
            )
        }

        val forecast = service.observeSignalDegradationForecast(
            networkType = NetworkType.WIFI,
            shortWindow = 3,
            longWindow = 6,
            forecastSteps = 3
        ).first()

        assertTrue(forecast.hasEnoughData)
        assertEquals(7, forecast.sampleCount)
        assertEquals(-77.667, forecast.latestShortSmaDbm!!, 0.05) // avg(-73, -78, -82)
        assertNotNull(forecast.latestLongSmaDbm)
        assertTrue(forecast.smaCrossoverDeltaDbm!! < -3.0)
        assertTrue(forecast.driftPerStepDbm!! < -2.0)
        assertEquals(SmaTrendDirection.DEGRADING_RAPIDLY, forecast.trendDirection)
        assertTrue(
            forecast.degradationRisk == DegradationRisk.HIGH ||
                forecast.degradationRisk == DegradationRisk.CRITICAL
        )
        val forecastPts = forecast.series.filter { it.isForecast }
        assertEquals(3, forecastPts.size)
        assertTrue(forecastPts.last().shortSmaDbm!! < forecast.latestShortSmaDbm)
    }

    @Test
    fun forecast_identifiesStableCellularSignalFromRoomHistory() = runBlocking {
        val dao = database.networkMetricDao()
        val stableSignals = listOf(-86, -85, -86, -85, -86, -85)
        stableSignals.forEachIndexed { idx, dbm ->
            dao.insertMetric(
                NetworkMetricEntity(
                    deviceId = "dev-1",
                    networkType = NetworkType.CELLULAR.code,
                    signalStrength = dbm,
                    isConnected = true,
                    isValidated = true,
                    createdAtMs = 200_000L + idx * 10_000L
                )
            )
        }

        val dual = service.observeDualInterfaceForecasts(shortWindow = 3, longWindow = 6).first()
        val cell = dual.cellular

        assertTrue(cell.hasEnoughData)
        assertEquals(SmaTrendDirection.STABLE, cell.trendDirection)
        assertEquals(DegradationRisk.LOW, cell.degradationRisk)
        assertNull(cell.estimatedStepsToThreshold)
        assertFalse(dual.wifi.hasEnoughData)
    }
}
