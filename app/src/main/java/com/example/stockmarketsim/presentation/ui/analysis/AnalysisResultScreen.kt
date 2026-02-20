package com.example.stockmarketsim.presentation.ui.analysis

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisResultScreen(
    simulationId: Int,
    onNavigateBack: () -> Unit,
    onSimulationStarted: () -> Unit,
    onNavigateToLogs: () -> Unit,
    viewModel: AnalysisViewModel = hiltViewModel()
) {
    LaunchedEffect(simulationId) {
        viewModel.loadAnalysis(simulationId)
    }
    
    val state by viewModel.state.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Analysis Results") })
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is AnalysisState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is AnalysisState.Error -> Text(text = s.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
                is AnalysisState.Success -> {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                        Text("Analysis Complete for: ${s.simulationName}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Initial: ${java.text.NumberFormat.getCurrencyInstance(java.util.Locale("en", "IN")).format(s.amount)} | Duration: ${s.duration} Months", 
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Recommended Strategies:", style = MaterialTheme.typography.labelLarge)
                        
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(s.strategies) { strategy ->
                                StrategyCard(strategy, enabled = !s.isActivating) {
                                    viewModel.selectStrategy(simulationId, strategy.id, onSimulationStarted)
                                }
                            }
                        }
                        
                        if (s.isActivating) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                            Text("Activating Intelligence Engine...", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                        
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Button(
                            onClick = { 
                                viewModel.generatePdf(simulationId) { file ->
                                    if (file != null) {
                                        android.widget.Toast.makeText(context, "PDF Saved: ${file.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                                    } else {
                                        android.widget.Toast.makeText(context, "Failed to generate PDF", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }, 
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !s.isActivating
                        ) {
                            Text("Download Analysis Report (PDF)")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedButton(onClick = onNavigateToLogs, modifier = Modifier.fillMaxWidth()) {
                            Text("View Intelligence Logs (Live)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StrategyCard(strategy: StrategyRecommendation, enabled: Boolean, onSelect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(strategy.name, style = MaterialTheme.typography.titleMedium, color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            Text(strategy.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Final Value: ${java.text.NumberFormat.getCurrencyInstance(java.util.Locale("en", "IN")).format(strategy.finalValue)}",
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val alphaColor = if (!enabled) MaterialTheme.colorScheme.outline else if (strategy.alpha >= 0) androidx.compose.ui.graphics.Color(0xFF4CAF50) else androidx.compose.ui.graphics.Color.Red
                Text("Alpha: ${"%.2f".format(strategy.alpha)}%", color = alphaColor, style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("Sharpe: ${"%.2f".format(strategy.sharpe)}", style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("Max DD: ${"%.1f".format(strategy.drawdown)}%", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Text("Score: ${(strategy.score * 100).toInt()}/100", style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onSelect, enabled = enabled) {
                Text(if (enabled) "Select & Start" else "Starting...")
            }
        }
    }
}
