package com.example.stockmarketsim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.example.stockmarketsim.presentation.ui.theme.StockMarketSimTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission Granted: Proceed
        } else {
            // Permission Denied: Handle appropriately (e.g. show dialog)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Check & Request Notification Permission (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 2. Check & Request Battery Optimization Exemption
        val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }

        setContent {
            StockMarketSimTheme {
                val navController = rememberNavController()
                var currentSimulationId by remember { mutableStateOf<Int?>(null) }
                
                // Track current route to update bottom nav state and intercept simulationId
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                
                // Try to glean current simulationId from arguments if available
                LaunchedEffect(navBackStackEntry) {
                    val id = navBackStackEntry?.arguments?.getInt("simulationId")
                    if (id != null && id != 0) {
                        currentSimulationId = id
                    }
                }

                // EXPERT REVIEW FIX: Compliance Disclaimer
                LaunchedEffect(Unit) {
                     android.widget.Toast.makeText(
                         this@MainActivity, 
                         "⚠️ SIMULATION ONLY - NOT FINANCIAL ADVICE ⚠️", 
                         android.widget.Toast.LENGTH_LONG
                     ).show()
                }

                Scaffold(
                    bottomBar = {
                        val isBottomBarVisible = currentRoute in listOf(
                            com.example.stockmarketsim.presentation.navigation.Screen.Dashboard.route,
                            com.example.stockmarketsim.presentation.navigation.Screen.Settings.route
                        ) || currentRoute?.startsWith("simulation_detail") == true
                          || currentRoute?.startsWith("log_viewer") == true
                          || currentRoute?.startsWith("analysis_result") == true

                        if (isBottomBarVisible) {
                            NavigationBar(
                                containerColor = com.example.stockmarketsim.presentation.ui.theme.Navy950,
                                contentColor = com.example.stockmarketsim.presentation.ui.theme.NeutralSlate
                            ) {
                                val itemColors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = com.example.stockmarketsim.presentation.ui.theme.ElectricBlue, 
                                    unselectedIconColor = com.example.stockmarketsim.presentation.ui.theme.NeutralSlate, 
                                    indicatorColor = com.example.stockmarketsim.presentation.ui.theme.Navy800
                                )
                                
                                val isDashboard = currentRoute == com.example.stockmarketsim.presentation.navigation.Screen.Dashboard.route
                                NavigationBarItem(
                                    selected = isDashboard,
                                    onClick = {
                                        navController.navigate(com.example.stockmarketsim.presentation.navigation.Screen.Dashboard.route) {
                                            popUpTo(com.example.stockmarketsim.presentation.navigation.Screen.Dashboard.route) { inclusive = true }
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Home, "Portfolio") },
                                    label = { Text("Portfolio", style = com.example.stockmarketsim.presentation.ui.theme.AppTypography.labelSmall) },
                                    colors = itemColors
                                )

                                val isDetail = currentRoute?.startsWith("simulation_detail") == true || currentRoute?.startsWith("analysis_result") == true
                                val detailEnabled = currentSimulationId != null
                                NavigationBarItem(
                                    selected = isDetail,
                                    enabled = detailEnabled,
                                    onClick = {
                                        currentSimulationId?.let { id ->
                                            navController.navigate(com.example.stockmarketsim.presentation.navigation.Screen.SimulationDetail.createRoute(id)) {
                                                popUpTo(com.example.stockmarketsim.presentation.navigation.Screen.Dashboard.route)
                                            }
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Info, "Detail") },
                                    label = { Text("Detail", style = com.example.stockmarketsim.presentation.ui.theme.AppTypography.labelSmall) },
                                    colors = itemColors
                                )

                                val isLogs = currentRoute?.startsWith("log_viewer") == true
                                val logsEnabled = currentSimulationId != null
                                NavigationBarItem(
                                    selected = isLogs,
                                    enabled = logsEnabled,
                                    onClick = {
                                        currentSimulationId?.let { id ->
                                            navController.navigate(com.example.stockmarketsim.presentation.navigation.Screen.LogViewer.createRoute(id)) {
                                                popUpTo(com.example.stockmarketsim.presentation.navigation.Screen.Dashboard.route)
                                            }
                                        }
                                    },
                                    icon = { Icon(Icons.Default.List, "Logs") },
                                    label = { Text("Logs", style = com.example.stockmarketsim.presentation.ui.theme.AppTypography.labelSmall) },
                                    colors = itemColors
                                )

                                val isSettings = currentRoute == com.example.stockmarketsim.presentation.navigation.Screen.Settings.route
                                NavigationBarItem(
                                    selected = isSettings,
                                    onClick = {
                                        navController.navigate(com.example.stockmarketsim.presentation.navigation.Screen.Settings.route) {
                                            popUpTo(com.example.stockmarketsim.presentation.navigation.Screen.Dashboard.route)
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Settings, "Settings") },
                                    label = { Text("Settings", style = com.example.stockmarketsim.presentation.ui.theme.AppTypography.labelSmall) },
                                    colors = itemColors
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = com.example.stockmarketsim.presentation.navigation.Screen.Dashboard.route,
                        modifier = Modifier.fillMaxSize().padding(innerPadding).background(MaterialTheme.colorScheme.background)
                    ) {
                        composable(com.example.stockmarketsim.presentation.navigation.Screen.Dashboard.route) {
                            com.example.stockmarketsim.presentation.ui.dashboard.DashboardScreen(
                                onNavigateToCreate = {
                                    navController.navigate(com.example.stockmarketsim.presentation.navigation.Screen.CreateSimulation.route)
                                },
                                onNavigateToDetail = { id ->
                                    navController.navigate(com.example.stockmarketsim.presentation.navigation.Screen.SimulationDetail.createRoute(id))
                                },
                                onNavigateToAnalysis = { id ->
                                    navController.navigate(com.example.stockmarketsim.presentation.navigation.Screen.AnalysisResult.createRoute(id))
                                },
                                onNavigateToSettings = {
                                    navController.navigate(com.example.stockmarketsim.presentation.navigation.Screen.Settings.route)
                                }
                            )
                        }
                        
                        composable(com.example.stockmarketsim.presentation.navigation.Screen.CreateSimulation.route) {
                            com.example.stockmarketsim.presentation.ui.creation.CreateSimulationScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        
                        composable(
                            route = com.example.stockmarketsim.presentation.navigation.Screen.SimulationDetail.route,
                            arguments = listOf(navArgument("simulationId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val simulationId = backStackEntry.arguments?.getInt("simulationId") ?: 0
                            com.example.stockmarketsim.presentation.ui.detail.SimulationDetailScreen(
                                simulationId = simulationId,
                                onNavigateBack = { navController.navigateUp() },
                                onNavigateToLogs = { id ->
                                    navController.navigate(com.example.stockmarketsim.presentation.navigation.Screen.LogViewer.createRoute(id))
                                }
                            )
                        }
                        
                        composable(
                            route = com.example.stockmarketsim.presentation.navigation.Screen.AnalysisResult.route,
                            arguments = listOf(navArgument("simulationId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val simulationId = backStackEntry.arguments?.getInt("simulationId") ?: 0
                            com.example.stockmarketsim.presentation.ui.analysis.AnalysisResultScreen(
                                simulationId = simulationId,
                                onNavigateBack = { navController.navigateUp() },
                                onSimulationStarted = {
                                    navController.navigate(com.example.stockmarketsim.presentation.navigation.Screen.SimulationDetail.createRoute(simulationId)) {
                                        popUpTo(com.example.stockmarketsim.presentation.navigation.Screen.Dashboard.route)
                                    }
                                },
                                onNavigateToLogs = {
                                    navController.navigate(com.example.stockmarketsim.presentation.navigation.Screen.LogViewer.createRoute(simulationId))
                                }
                            )
                        }
                        composable(com.example.stockmarketsim.presentation.navigation.Screen.Settings.route) {
                            com.example.stockmarketsim.presentation.ui.settings.SettingsScreen(
                                onNavigateBack = {
                                    navController.navigateUp()
                                },
                                onNavigateToManageUniverse = {
                                    navController.navigate(com.example.stockmarketsim.presentation.navigation.Screen.ManageUniverse.route)
                                }
                            )
                        }

                        composable(com.example.stockmarketsim.presentation.navigation.Screen.ManageUniverse.route) {
                            com.example.stockmarketsim.presentation.ui.settings.ManageUniverseScreen(
                                onNavigateBack = {
                                    navController.navigateUp()
                                }
                            )
                        }
                        
                        composable(
                            route = com.example.stockmarketsim.presentation.navigation.Screen.LogViewer.route,
                            arguments = listOf(navArgument("simulationId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val simulationId = backStackEntry.arguments?.getInt("simulationId") ?: 0
                            com.example.stockmarketsim.presentation.ui.logs.LogViewerScreen(
                                simulationId = simulationId,
                                onNavigateBack = { navController.navigateUp() }
                            )
                        }
                    }
                }
            }
        }
    }
}
