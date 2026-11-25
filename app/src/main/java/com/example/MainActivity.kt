package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.model.NetworkType
import com.example.ui.components.LivePulseBadge
import com.example.ui.navigation.AllDrawerNavItems
import com.example.ui.navigation.PrimaryBottomNavItems
import com.example.ui.navigation.Routes
import com.example.ui.navigation.titleForRoute
import com.example.ui.screens.AboutProjectScreen
import com.example.ui.screens.CellularDetailsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.IntelligentSwitchingScreen
import com.example.ui.screens.NetworkComparisonScreen
import com.example.ui.screens.NetworkMonitoringScreen
import com.example.ui.screens.OnboardingPermissionsScreen
import com.example.ui.screens.PerformanceAnalyticsScreen
import com.example.ui.screens.PredictiveAnalyticsScreen
import com.example.ui.screens.PresentationModeScreen
import com.example.ui.screens.PrivacyInfoScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.SwitchingHistoryScreen
import com.example.ui.screens.WifiDetailsScreen
import com.example.ui.theme.NetSwitchTheme
import com.example.viewmodel.NetSwitchViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: NetSwitchViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()

            NetSwitchTheme(darkTheme = settings.darkTheme) {
                NetSwitchAppShell(viewModel = vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetSwitchAppShell(viewModel: NetSwitchViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val networkAvailability by viewModel.networkAvailability.collectAsStateWithLifecycle()
    val wifiScanState by viewModel.wifiScanState.collectAsStateWithLifecycle()
    val knownWifiNetworks by viewModel.knownWifiNetworks.collectAsStateWithLifecycle()
    val monitoringState by viewModel.monitoringState.collectAsStateWithLifecycle()
    val elapsedSec by viewModel.monitoringElapsedSec.collectAsStateWithLifecycle()
    val isProbing by viewModel.isProbing.collectAsStateWithLifecycle()
    val isSpeedTesting by viewModel.isRunningSpeedTest.collectAsStateWithLifecycle()
    val activeMeasurement by viewModel.activeMeasurement.collectAsStateWithLifecycle()
    val wifiMeasurement by viewModel.wifiMeasurement.collectAsStateWithLifecycle()
    val cellularMeasurement by viewModel.cellularMeasurement.collectAsStateWithLifecycle()
    val comparison by viewModel.comparison.collectAsStateWithLifecycle()
    val prediction by viewModel.prediction.collectAsStateWithLifecycle()
    val smaSignalForecast by viewModel.smaSignalForecast.collectAsStateWithLifecycle()
    val smaWindowConfig by viewModel.smaWindowConfig.collectAsStateWithLifecycle()
    val lastSwitchDecision by viewModel.lastSwitchDecision.collectAsStateWithLifecycle()
    val autoSwitchEvaluation by viewModel.autoSwitchEvaluation.collectAsStateWithLifecycle()
    val cooldownSecondsLeft by viewModel.cooldownSecondsLeft.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val supabaseConfig by viewModel.supabaseConfig.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val recentMetrics by viewModel.recentMetrics.collectAsStateWithLifecycle()
    val wifiMetrics by viewModel.wifiMetrics.collectAsStateWithLifecycle()
    val cellularMetrics by viewModel.cellularMetrics.collectAsStateWithLifecycle()
    val switchingHistory by viewModel.switchingHistory.collectAsStateWithLifecycle()
    val predictionHistory by viewModel.predictionHistory.collectAsStateWithLifecycle()
    val connectionEvents by viewModel.connectionEvents.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.selectedAnalyticsPeriod.collectAsStateWithLifecycle()
    val analyticsMetrics by viewModel.analyticsMetrics.collectAsStateWithLifecycle()
    val interruptionCount by viewModel.interruptionCountForPeriod.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.uiBannerMessage.collectAsStateWithLifecycle()
    val errorMessage by viewModel.lastErrorMessage.collectAsStateWithLifecycle()

    LaunchedEffect(bannerMessage, errorMessage) {
        val msg = bannerMessage ?: errorMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissBanner()
        }
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val isFullScreenRoute = currentRoute == Routes.SPLASH

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isFullScreenRoute,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .width(296.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Text(
                            text = "NetSwitch AI",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Predictive Network Switching",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    val grouped = AllDrawerNavItems.groupBy { it.section }
                    grouped.forEach { (section, items) ->
                        Text(
                            text = section.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                        )
                        items.forEach { item ->
                            NavigationDrawerItem(
                                label = { Text(item.label) },
                                selected = currentRoute == item.route,
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navigateTo(item.route)
                                },
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 2.dp)
                                    .testTag("drawer_${item.route}")
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    if (!isFullScreenRoute) {
                        TopAppBar(
                            title = {
                                Text(
                                    text = titleForRoute(currentRoute),
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.testTag("open_drawer_button")
                                ) {
                                    Icon(Icons.Filled.Menu, contentDescription = "Open navigation menu")
                                }
                            },
                            actions = {
                                LivePulseBadge(
                                    state = monitoringState,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                IconButton(
                                    onClick = { navigateTo(Routes.SETTINGS) },
                                    modifier = Modifier.testTag("topbar_settings_button")
                                ) {
                                    Icon(Icons.Filled.Settings, contentDescription = "Open Settings")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                },
                bottomBar = {
                    if (!isFullScreenRoute && !useNavigationRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            PrimaryBottomNavItems.forEach { item ->
                                NavigationBarItem(
                                    selected = currentRoute == item.route,
                                    onClick = { navigateTo(item.route) },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.shortLabel, maxLines = 1) },
                                    modifier = Modifier.testTag("bottom_${item.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (!isFullScreenRoute && useNavigationRail) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            PrimaryBottomNavItems.forEach { item ->
                                NavigationRailItem(
                                    selected = currentRoute == item.route,
                                    onClick = { navigateTo(item.route) },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.shortLabel) }
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = Routes.SPLASH,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(Routes.SPLASH) {
                            SplashScreen(
                                onboardingCompleted = settings.onboardingCompleted,
                                onNavigateNext = { done ->
                                    val dest = if (done) Routes.DASHBOARD else Routes.ONBOARDING
                                    navController.navigate(dest) {
                                        popUpTo(Routes.SPLASH) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(Routes.ONBOARDING) {
                            OnboardingPermissionsScreen(
                                permissions = permissions,
                                onPermissionsResult = { viewModel.onPermissionsUpdated() },
                                onContinueToDashboard = {
                                    viewModel.completeOnboarding()
                                    navController.navigate(Routes.DASHBOARD) {
                                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                                    }
                                },
                                onOpenPrivacyInfo = { navigateTo(Routes.PRIVACY) }
                            )
                        }

                        composable(Routes.DASHBOARD) {
                            DashboardScreen(
                                snapshot = snapshot,
                                monitoringState = monitoringState,
                                elapsedSec = elapsedSec,
                                isProbing = isProbing,
                                isSpeedTesting = isSpeedTesting,
                                activeMeasurement = activeMeasurement,
                                wifiMeasurement = wifiMeasurement,
                                cellularMeasurement = cellularMeasurement,
                                comparison = comparison,
                                prediction = prediction,
                                smaSignalForecast = smaSignalForecast,
                                smaShortWindow = smaWindowConfig.first,
                                smaLongWindow = smaWindowConfig.second,
                                settings = settings,
                                recentMetrics = recentMetrics,
                                onStartMonitoring = { viewModel.startMonitoring() },
                                onStopMonitoring = { viewModel.stopMonitoring() },
                                onProbeNow = { viewModel.refreshNow() },
                                onRunSpeedTest = { viewModel.runFullSpeedTestNow() },
                                onSelectSmaWindow = { s, l -> viewModel.setSmaWindow(s, l) },
                                onNavigate = { navigateTo(it) }
                            )
                        }

                        composable(Routes.MONITORING) {
                            NetworkMonitoringScreen(
                                snapshot = snapshot,
                                networkAvailability = networkAvailability,
                                monitoringState = monitoringState,
                                elapsedSec = elapsedSec,
                                isProbing = isProbing,
                                activeMeasurement = activeMeasurement,
                                events = connectionEvents,
                                onStart = { viewModel.startMonitoring() },
                                onPause = { viewModel.pauseMonitoring() },
                                onResume = { viewModel.resumeMonitoring() },
                                onStop = { viewModel.stopMonitoring() },
                                onProbeNow = { viewModel.refreshNow() },
                                onSpeedTestNow = { viewModel.runFullSpeedTestNow() }
                            )
                        }

                        composable(Routes.WIFI_DETAILS) {
                            WifiDetailsScreen(
                                snapshot = snapshot,
                                wifiMeasurement = wifiMeasurement,
                                wifiHistory = wifiMetrics,
                                wifiScanState = wifiScanState,
                                knownWifiNetworks = knownWifiNetworks,
                                isProbing = isProbing,
                                onProbeWifi = { includeSpeed ->
                                    viewModel.probeSpecificNetwork(NetworkType.WIFI, includeSpeed)
                                },
                                onScanNearbyWifi = { viewModel.scanNearbyWifi() }
                            )
                        }

                        composable(Routes.CELLULAR_DETAILS) {
                            CellularDetailsScreen(
                                snapshot = snapshot,
                                cellularMeasurement = cellularMeasurement,
                                cellularHistory = cellularMetrics,
                                isProbing = isProbing,
                                onProbeCellular = { includeSpeed ->
                                    viewModel.probeSpecificNetwork(NetworkType.CELLULAR, includeSpeed)
                                }
                            )
                        }

                        composable(Routes.COMPARISON) {
                            NetworkComparisonScreen(
                                snapshot = snapshot,
                                wifiMeasurement = wifiMeasurement,
                                cellularMeasurement = cellularMeasurement,
                                comparison = comparison,
                                weights = settings.scoringWeights,
                                isProbing = isProbing,
                                onProbeNow = { viewModel.refreshNow() },
                                onUpdateWeights = { viewModel.updateScoringWeights(it) },
                                onNavigateToSwitching = { navigateTo(Routes.SWITCHING) }
                            )
                        }

                        composable(Routes.PREDICTIVE) {
                            PredictiveAnalyticsScreen(
                                prediction = prediction,
                                predictionHistory = predictionHistory,
                                isProbing = isProbing,
                                onRunProbe = { viewModel.refreshNow() }
                            )
                        }

                        composable(Routes.SWITCHING) {
                            IntelligentSwitchingScreen(
                                snapshot = snapshot,
                                comparison = comparison,
                                settings = settings,
                                autoEvaluation = autoSwitchEvaluation,
                                cooldownSecondsLeft = cooldownSecondsLeft,
                                lastDecision = lastSwitchDecision,
                                onToggleAutoSwitch = { enabled ->
                                    viewModel.updateSettings { it.copy(autoSwitchingEnabled = enabled) }
                                },
                                onManualSwitch = { target -> viewModel.manualSwitchTo(target) },
                                onResetToDefault = { viewModel.resetToSystemDefault() },
                                onOpenHistory = { navigateTo(Routes.HISTORY) }
                            )
                        }

                        composable(Routes.HISTORY) {
                            SwitchingHistoryScreen(
                                history = switchingHistory,
                                speedTestHistory = recentMetrics,
                                onExportReport = {
                                    viewModel.exportReport { shareIntent ->
                                        context.startActivity(shareIntent)
                                    }
                                }
                            )
                        }

                        composable(Routes.ANALYTICS) {
                            PerformanceAnalyticsScreen(
                                period = selectedPeriod,
                                metrics = analyticsMetrics,
                                interruptionCount = interruptionCount,
                                switchingCount = switchingHistory.size,
                                onSelectPeriod = { viewModel.selectAnalyticsPeriod(it) },
                                onExportReport = {
                                    viewModel.exportReport { shareIntent ->
                                        context.startActivity(shareIntent)
                                    }
                                }
                            )
                        }

                        composable(Routes.PRESENTATION) {
                            PresentationModeScreen(
                                snapshot = snapshot,
                                monitoringState = monitoringState,
                                elapsedSec = elapsedSec,
                                activeMeasurement = activeMeasurement,
                                comparison = comparison,
                                prediction = prediction,
                                settings = settings,
                                switchingHistory = switchingHistory,
                                onStartDemo = { viewModel.startMonitoring() },
                                onStopDemo = { viewModel.stopMonitoring() }
                            )
                        }

                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                settings = settings,
                                monitoringState = monitoringState,
                                supabaseConfig = supabaseConfig,
                                syncStatus = syncStatus,
                                onStartMonitoring = { viewModel.startMonitoring() },
                                onStopMonitoring = { viewModel.stopMonitoring() },
                                onUpdateSettings = { viewModel.updateSettings(it) },
                                onSaveSupabaseConfig = { url, key -> viewModel.saveSupabaseConfig(url, key) },
                                onSignInSupabase = { url, key, email, pass ->
                                    viewModel.signInSupabase(url, key, email, pass)
                                },
                                onSignOutSupabase = { viewModel.signOutSupabase() },
                                onSyncNow = { viewModel.syncSupabaseNow() },
                                onNavigateManualSwitch = { navigateTo(Routes.SWITCHING) }
                            )
                        }

                        composable(Routes.ABOUT) {
                            AboutProjectScreen(
                                onOpenPrivacy = { navigateTo(Routes.PRIVACY) }
                            )
                        }

                        composable(Routes.PRIVACY) {
                            PrivacyInfoScreen(
                                onClearAllData = { viewModel.clearAllLocalData() },
                                onOpenPermissions = { navigateTo(Routes.ONBOARDING) }
                            )
                        }
                    }
                }
            }
        }
    }
}
