package com.example.stockmarketsim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    
                    // EXPERT REVIEW FIX: Compliance Disclaimer
                    // Show on every cold launch to ensuring user understands this is a simulation.
                    LaunchedEffect(Unit) {
                         android.widget.Toast.makeText(
                             this@MainActivity, 
                             "⚠️ SIMULATION ONLY - NOT FINANCIAL ADVICE ⚠️", 
                             android.widget.Toast.LENGTH_LONG
                         ).show()
                    }
                    
                    NavHost(
                        navController = navController,
                        startDestination = com.example.stockmarketsim.presentation.navigation.Screen.Dashboard.route
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
                                onNavigateBack = { navController.popBackStack() },
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
                                onNavigateBack = { navController.popBackStack() },
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
                                    navController.popBackStack()
                                },
                                onNavigateToManageUniverse = {
                                    navController.navigate(com.example.stockmarketsim.presentation.navigation.Screen.ManageUniverse.route)
                                }
                            )
                        }

                        composable(com.example.stockmarketsim.presentation.navigation.Screen.ManageUniverse.route) {
                            com.example.stockmarketsim.presentation.ui.settings.ManageUniverseScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
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
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
