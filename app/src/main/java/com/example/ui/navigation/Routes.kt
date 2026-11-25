package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PresentToAll
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val MONITORING = "monitoring"
    const val WIFI_DETAILS = "wifi_details"
    const val CELLULAR_DETAILS = "cellular_details"
    const val COMPARISON = "comparison"
    const val PREDICTIVE = "predictive"
    const val SWITCHING = "switching"
    const val HISTORY = "history"
    const val ANALYTICS = "analytics"
    const val PRESENTATION = "presentation"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val PRIVACY = "privacy"
}

data class NavDestinationItem(
    val route: String,
    val label: String,
    val shortLabel: String,
    val icon: ImageVector,
    val section: String
)

val PrimaryBottomNavItems = listOf(
    NavDestinationItem(Routes.DASHBOARD, "Home", "Home", Icons.Filled.Dashboard, "Core"),
    NavDestinationItem(Routes.COMPARISON, "Network Comparison", "Compare", Icons.Filled.CompareArrows, "Core"),
    NavDestinationItem(Routes.ANALYTICS, "Performance Analytics", "Analytics", Icons.Filled.Analytics, "Core"),
    NavDestinationItem(Routes.HISTORY, "History", "History", Icons.Filled.History, "Core"),
    NavDestinationItem(Routes.SETTINGS, "Settings", "Settings", Icons.Filled.Settings, "Core")
)

val AllDrawerNavItems = listOf(
    NavDestinationItem(Routes.DASHBOARD, "Main Dashboard", "Dashboard", Icons.Filled.Dashboard, "Telemetry"),
    NavDestinationItem(Routes.MONITORING, "Network Monitoring", "Monitoring", Icons.Filled.MonitorHeart, "Telemetry"),
    NavDestinationItem(Routes.WIFI_DETAILS, "Wi-Fi Details", "Wi-Fi", Icons.Filled.Wifi, "Telemetry"),
    NavDestinationItem(Routes.CELLULAR_DETAILS, "Cellular Network Details", "Cellular", Icons.Filled.CellTower, "Telemetry"),
    NavDestinationItem(Routes.COMPARISON, "Network Comparison", "Compare", Icons.Filled.CompareArrows, "Intelligence"),
    NavDestinationItem(Routes.PREDICTIVE, "Predictive Analytics", "Predict", Icons.Filled.Psychology, "Intelligence"),
    NavDestinationItem(Routes.SWITCHING, "Intelligent Switching", "Switch", Icons.Filled.SwapHoriz, "Intelligence"),
    NavDestinationItem(Routes.HISTORY, "Switching History", "History", Icons.Filled.History, "Reports"),
    NavDestinationItem(Routes.ANALYTICS, "Performance Analytics", "Analytics", Icons.Filled.Analytics, "Reports"),
    NavDestinationItem(Routes.PRESENTATION, "Presentation Mode", "Present", Icons.Filled.PresentToAll, "Capstone"),
    NavDestinationItem(Routes.SETTINGS, "Settings & Supabase", "Settings", Icons.Filled.Settings, "System"),
    NavDestinationItem(Routes.ONBOARDING, "Permissions Setup", "Permissions", Icons.Filled.Security, "System"),
    NavDestinationItem(Routes.ABOUT, "About Project", "About", Icons.Filled.Info, "System"),
    NavDestinationItem(Routes.PRIVACY, "Privacy Information", "Privacy", Icons.Filled.Policy, "System")
)

fun titleForRoute(route: String?): String = when (route) {
    Routes.DASHBOARD -> "NetSwitch AI"
    Routes.MONITORING -> "Network Monitoring"
    Routes.WIFI_DETAILS -> "Wi-Fi Details"
    Routes.CELLULAR_DETAILS -> "Cellular Network Details"
    Routes.COMPARISON -> "Network Comparison"
    Routes.PREDICTIVE -> "Predictive Analytics"
    Routes.SWITCHING -> "Intelligent Switching"
    Routes.HISTORY -> "Switching History"
    Routes.ANALYTICS -> "Performance Analytics"
    Routes.PRESENTATION -> "Presentation Mode"
    Routes.SETTINGS -> "Settings"
    Routes.ONBOARDING -> "Onboarding & Permissions"
    Routes.ABOUT -> "About Project"
    Routes.PRIVACY -> "Privacy Information"
    else -> "NetSwitch AI"
}
