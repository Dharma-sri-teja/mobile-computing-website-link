package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.network.NetworkAvailabilityState
import com.example.network.NetworkMonitorService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Dedicated ViewModel that exposes real-time Wi-Fi and cellular availability
 * tracked by [NetworkMonitorService] as both a cold [Flow] and lifecycle-safe [StateFlow].
 */
class NetworkMonitorViewModel(
    networkMonitorService: NetworkMonitorService
) : ViewModel() {

    val networkAvailabilityFlow: Flow<NetworkAvailabilityState> =
        networkMonitorService.observeNetworkAvailability()

    val networkAvailability: StateFlow<NetworkAvailabilityState> =
        networkAvailabilityFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = NetworkAvailabilityState()
        )
}
