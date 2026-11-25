package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.example.model.NetworkType
import com.example.network.NetworkMonitorService
import com.example.viewmodel.NetworkMonitorViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NetworkMonitorServiceTest {

    @Test
    fun networkMonitorServiceEmitsWifiAndCellularAvailabilityViaViewModelFlow() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadowCm = shadowOf(cm)

        val activeNetwork = cm.activeNetwork
        val wifiCaps = ShadowNetworkCapabilities.newInstance()
        shadowOf(wifiCaps).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowOf(wifiCaps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(wifiCaps).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadowCm.setNetworkCapabilities(activeNetwork, wifiCaps)

        val service = NetworkMonitorService(context)
        val viewModel = NetworkMonitorViewModel(service)

        val initialState = viewModel.networkAvailabilityFlow.first()
        assertTrue(initialState.isWifiAvailable)
        assertTrue(initialState.isWifiValidated)
        assertFalse(initialState.isCellularAvailable)
        assertEquals(NetworkType.WIFI, initialState.activeTransport)

        // Verify callback registration and real-time cellular transition via registered NetworkCallbacks
        val emitted = mutableListOf<com.example.network.NetworkAvailabilityState>()
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.networkAvailabilityFlow.collect { emitted.add(it) }
        }

        val cellCaps = ShadowNetworkCapabilities.newInstance()
        shadowOf(cellCaps).addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        shadowOf(cellCaps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(cellCaps).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadowCm.setNetworkCapabilities(activeNetwork, cellCaps)

        // Trigger all registered callbacks to simulate OS transition to cellular
        shadowCm.networkCallbacks.forEach { callback ->
            if (activeNetwork != null) {
                callback.onCapabilitiesChanged(activeNetwork, cellCaps)
            }
        }

        assertTrue(emitted.any { it.isCellularAvailable && it.activeTransport == NetworkType.CELLULAR })
        collectJob.cancel()
    }
}
