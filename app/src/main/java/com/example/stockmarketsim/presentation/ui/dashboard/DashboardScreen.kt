package com.example.stockmarketsim.presentation.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stockmarketsim.domain.model.Simulation
import com.example.stockmarketsim.domain.model.SimulationStatus
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun DashboardScreen(
    onNavigateToCreate: () -> Unit,
    onNavigateToDetail: (Int) -> Unit,
    onNavigateToAnalysis: (Int) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val simulations by viewModel.simulations.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stock Market Sim") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                actions = {

                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCreate,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Simulation")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Active Simulations",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // COMPLIANCE BANNER (EXPERT REVIEW)
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "SIMULATION MODE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Not Financial Advice. No Real Money.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (simulations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No active simulations.\nTap + to start.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(simulations) { simulation ->
                        SimulationCard(
                            simulation = simulation,
                            onClick = { 
                                if (simulation.status == SimulationStatus.ANALYZING || 
                                    simulation.status == SimulationStatus.CREATED ||
                                    simulation.status == SimulationStatus.ANALYSIS_COMPLETE) {
                                    onNavigateToAnalysis(simulation.id)
                                } else {
                                    onNavigateToDetail(simulation.id)
                                }
                            },
                            onDelete = {
                                viewModel.deleteSimulation(simulation)
                            },
                            onRetry = {
                                viewModel.retrySimulation(simulation.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulationCard(
    simulation: Simulation,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit
) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    
    // Nice Strategy Name
    val strategyName = if (simulation.strategyId.isNotEmpty()) {
        simulation.strategyId.replace("_", " ")
            .lowercase()
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString() } }
            .replace("Bb", "BB").replace("Ema", "EMA").replace("Rsi", "RSI") // Fix acronyms
    } else {
        "Pending Selection"
    }
    
    // Metrics calculation
    val targetEquity = simulation.initialAmount * (1 + (simulation.targetReturnPercentage / 100))
    val elapsedDays = ((System.currentTimeMillis() - simulation.startDate) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
    val totalDays = simulation.durationMonths * 30
    
    // ROI Calculation
    val roi = if (simulation.initialAmount > 0) {
        ((simulation.totalEquity - simulation.initialAmount) / simulation.initialAmount) * 100
    } else 0.0
    val roiColor = if (roi >= 0) androidx.compose.ui.graphics.Color(0xFF4CAF50) else MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier, // Let parent handle padding/width
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = simulation.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (simulation.status == SimulationStatus.ACTIVE || simulation.status == SimulationStatus.COMPLETED) {
                         Text(
                            text = strategyName, 
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(
                        containerColor = when (simulation.status) {
                                SimulationStatus.ACTIVE -> MaterialTheme.colorScheme.tertiaryContainer
                                SimulationStatus.ANALYZING -> MaterialTheme.colorScheme.secondaryContainer
                                SimulationStatus.ANALYSIS_COMPLETE -> MaterialTheme.colorScheme.primaryContainer
                                SimulationStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = MaterialTheme.colorScheme.onSurface 
                    ) {
                        Text(
                            text = if (simulation.status == SimulationStatus.ANALYSIS_COMPLETE) "READY" else simulation.status.name,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp).padding(start = 8.dp)) {
                         Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Current Equity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = currencyFormat.format(simulation.totalEquity),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (simulation.status == SimulationStatus.ACTIVE || simulation.status == SimulationStatus.COMPLETED) {
                        Text(
                            text = "Return: ${if (roi > 0) "+" else ""}${"%.2f".format(roi)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = roiColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                if (simulation.status == SimulationStatus.ACTIVE || simulation.status == SimulationStatus.COMPLETED) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "Target Equity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = currencyFormat.format(targetEquity),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Goal: ${"%.2f".format(simulation.targetReturnPercentage)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else if (simulation.status == SimulationStatus.ANALYSIS_COMPLETE) {
                     Button(onClick = onClick, modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)) {
                         Text("View Results", fontSize = 12.sp)
                     }
                } else if (simulation.status == SimulationStatus.FAILED) {
                     Button(
                         onClick = onRetry, 
                         modifier = Modifier.height(36.dp), 
                         colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                         contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                     ) {
                         Text("Retry Analysis", fontSize = 12.sp)
                     }
                }
            }
            
            if (simulation.status == SimulationStatus.ACTIVE) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = (elapsedDays.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Elapsed: $elapsedDays days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "Goal: $totalDays days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
