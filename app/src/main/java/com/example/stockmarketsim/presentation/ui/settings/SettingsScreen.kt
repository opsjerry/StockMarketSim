package com.example.stockmarketsim.presentation.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToManageUniverse: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val apiKey by viewModel.apiKey.collectAsState()
    val indianApiKey by viewModel.indianApiKey.collectAsState()

    // Zerodha State
    val zerodhaApiKey by viewModel.zerodhaApiKey.collectAsState()
    val zerodhaAccessToken by viewModel.zerodhaAccessToken.collectAsState()

    val scrollState = rememberScrollState()
    var showAboutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            // --- API CONFIGURATION ---
            SettingsSectionHeader("API Configuration")
            
            OutlinedTextField(
                value = apiKey,
                onValueChange = { viewModel.updateApiKey(it) },
                label = { Text("Alpha Vantage API Key") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true,
                placeholder = { Text("Enter API Key") }
            )
            
            Text(
                text = "Required for Sentinel Analysis & Inflation Data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 16.dp, end = 16.dp)
            )

            OutlinedTextField(
                value = indianApiKey,
                onValueChange = { viewModel.updateIndianApiKey(it) },
                label = { Text("IndianAPI.in Key") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true,
                placeholder = { Text("Enter IndianAPI Key") }
            )
            
            Text(
                text = "Primary source for P/E, ROE, D/E, MarketCap & Sentiment. Falls back to Yahoo Finance if blank or rate-limited. 7-day Room cache.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 16.dp, end = 16.dp)
            )

            Divider()

            // --- ZERODHA LIVE TRADING ---
            SettingsSectionHeader("Zerodha Live Trading")
            
            OutlinedTextField(
                value = zerodhaApiKey,
                onValueChange = { viewModel.updateZerodhaApiKey(it) },
                label = { Text("Kite Connect API Key") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = zerodhaAccessToken,
                onValueChange = { viewModel.updateZerodhaAccessToken(it) },
                label = { Text("Access Token") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true
            )
            
            Text(
                text = "Enter manually generated Access Token from Kite Developer Console.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp)
            )
             
            Button(
                onClick = { 
                    viewModel.saveZerodhaSettings()
                    Toast.makeText(context, "Zerodha Credentials Saved", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Save Credentials")
            }

            Divider()

            // --- DATA PREFERENCES ---
            SettingsSectionHeader("Data Preferences")
            


            // Data Management Link
             Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToManageUniverse() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Manage Stock Universe", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Add, Remove, or Sync NIFTY 100 stocks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Navigate",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Divider()

            // --- ABOUT ---
            SettingsSectionHeader("About")
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAboutDialog = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "About Intelligence Engine",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Strategies, Risk Protocols & Ranking Logic",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Main Save Button (for general settings)
            Button(
                onClick = {
                    viewModel.saveSettings()
                    Toast.makeText(context, "General Settings Saved", Toast.LENGTH_SHORT).show()
                    onNavigateBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Save General Settings & Exit")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("📊 Intelligence Engine v3.0", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
            ) {
                Text("WHAT'S NEW", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(8.dp))
                
                // Live Trading
                Text("🚀 Live Trading Support (Beta)", style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(
                    "Seamless integration with Zerodha Kite Connect. Execute real trades based on strategy signals with institutional-grade security (EncryptedSharedPreferences).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Incremental Sync
                Text("⚡ High-Frequency Data Sync", style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(
                    "New incremental fetch engine reduces data usage by 99% and eliminates UI freezes. Checks only for missing candles (Delta-Fetch).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Deep Learning
                Text("🧠 Deep Learning Forecasts", style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(
                    "Native TensorFlow Keras Deep Neural Network predicts T+1 success probabilities based on 6 daily tabular features.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Phase 12: Code Review Fixes
                Text("🛡️ Stability & Safety (v2.1)", style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(
                    "Market hours guard (NSE 9:15–15:30 IST), integer share safety for live orders, SMA(200) macro regime detection, auto-refresh data tokens, and connection timeout protection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Phase 13: Quant Improvements
                Text("📈 Quant Edge (v2.3)", style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(
                    "Signal-proportional sizing (stronger signals → bigger positions), Monday-only rebalancing (80% less churn), corrected MACD signal line (true EMA-9), and 365-day data window for 52-week breakout strategy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Phase 14: P2 Quality + Fee Scoring
                Text("🔬 Quality & Fee Intelligence (v2.4)", style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(
                    "Fundamental quality filter (ROE ≥ 12%, D/E ≤ 1.0) via Yahoo Finance quoteSummary API. Zerodha paid API ready as fallback. Fee-adjusted tournament scoring penalizes high-churn strategies (0.4% per trade drag).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Phase 15: Regression Suite
                Text("✅ Robustness Upgrade (v2.5)", style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(
                    "Comprehensive 166-point regression test suite covers all domain logic. Fixed critical bugs in Backtester return calculation, aligned Benchmark data, and standardized ATR risk parameters per Expert Panel review.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Phase 16: Deep Neural Net Migration
                Text("🤖 TensorFlow Lite Native Engine (v2.6)", style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(
                    "Migrated Machine Learning architecture from XGBoost to Native Keras Deep Neural Networks for perfect zero-allocation TFLite integration and optimized 24-byte payload mapping.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Phase 17: Quant Guard
                Text("🛡️ Sticky ML Anchor & Circuit Breaker (v2.7)", style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(
                    "Implemented Quant logic to prevent strategy whiplash. The Deep Learning model acts as the portfolio anchor and requires any legacy challenger strategy to beat its Alpha by ≥ 1.5x before allowing a regime shift.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Phase 18: Multi-Factor ML + API Integration
                Text("🧬 Multi-Factor ML Architecture (v3.0)", style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(
                    "Upgraded the ML feature vector from 60 (log returns) to 64 inputs: RSI(14), SMA Ratio (50/200), ATR%(14), and Relative Volume(20) are now wired into the LSTM model alongside price data. Dynamic ByteBuffer eliminates model-shape rigidity.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text("📡 Smart Fundamentals Pipeline (v3.0)", style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(
                    "Real-time fundamentals from IndianAPI.in (P/E, ROE, D/E, MarketCap, Analyst Sentiment) with 1 req/sec throttle. Room-persisted 7-day cache. Yahoo Finance as automatic fallback. Zero mock data — stocks with no data from any source are skipped.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                
                Text("CORE SYSTEMS", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                Text("• 22+ Strategy Variants (Momentum, RSI, Bollinger, Volume, ML)", style = MaterialTheme.typography.bodySmall)
                Text("• 64-Feature Multi-Factor LSTM (Log Returns + 4 TA Indicators)", style = MaterialTheme.typography.bodySmall)
                Text("• Real Fundamentals: IndianAPI.in → Yahoo Finance → Room Cache", style = MaterialTheme.typography.bodySmall)
                Text("• Fee-Adjusted Tournament + Weekly Rebalancing", style = MaterialTheme.typography.bodySmall)
                Text("• ATR Trailing Stops + 7% Hard Stop + 30% Sector Cap", style = MaterialTheme.typography.bodySmall)
                Text("• Regime Filter (SMA-200 + Volatility + CPI) — Zero-Allocation", style = MaterialTheme.typography.bodySmall)
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("See full documentation in `app_bible.md`", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
