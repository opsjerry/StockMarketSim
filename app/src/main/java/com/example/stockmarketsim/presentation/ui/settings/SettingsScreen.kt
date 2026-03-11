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
                text = "Primary source for P/E, ROE, D/E, MarketCap. Falls back to Yahoo Finance if blank or rate-limited. 7-day Room cache. India CPI sourced from World Bank (no key needed).",
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
                Text("📊 Auto-Pilot Simulator v3.5", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
            ) {

                // ── HOW IT WORKS ─────────────────────────────────────────────
                Text("HOW IT WORKS", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Every weekday the app runs 5 steps automatically — you don't need to do anything.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "1️⃣  Health Check",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    "Scans 100 NSE stocks. Companies with weak profitability (ROE < 12%) or too much debt (D/E > 1.0) are removed. Only healthy companies can be bought.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                Text("2️⃣  Strategy Battle", style = MaterialTheme.typography.labelLarge)
                Text(
                    "23+ trading strategies (momentum, RSI, Bollinger Bands, AI deep learning, etc.) compete by backtesting against recent price history. The one that would have made the most money — matched to your target return — becomes the Auto-Pilot.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                Text("3️⃣  Portfolio Build", style = MaterialTheme.typography.labelLarge)
                Text(
                    "The winning strategy picks up to 20 stocks. Stocks that move less (lower volatility) get larger positions. Maximum 10% of your portfolio in any single stock to stay diversified.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                Text("4️⃣  Risk Guard (Stop-Loss)", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Every stock gets an automatic exit price. If a stock falls too far from its recent peak, it is sold automatically to protect your capital. New positions get a wider buffer for their first 3 days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                Text("5️⃣  Intra-Day Watch", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Every 30 minutes during market hours (9:15 AM – 3:30 PM IST), live prices are checked. If a stop is breached continuously for ~30 minutes it sells immediately — not waiting until end of day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // ── READING YOUR LOGS ─────────────────────────────────────────
                Text("READING YOUR LOGS", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(8.dp))

                LogEntry("🌅 Starting Daily Market Analysis…",
                    "The daily run has begun for all your simulations.")
                LogEntry("📑 Fetching fundamentals for 100 symbols…",
                    "Downloading financial data (profit, debt) for every stock in the universe.")
                LogEntry("🧹 Filtered out X low-quality stocks",
                    "X companies were dropped because their financials were too weak. The remaining stocks are eligible for selection.")
                LogEntry("🏎️ Auto-Pilot: Running Strategy Tournament…",
                    "All strategies are being tested against historical data to find the best one right now.")
                LogEntry("📊 Tournament Phase 1/2 & 2/2",
                    "Phase 1 trains on older data; Phase 2 tests on recent data. Both must pass to avoid picking a 'lucky' strategy.")
                LogEntry("🚀 FAST START: Immediate deployment triggered",
                    "Your portfolio was empty (new simulation or reset). The app invests right away instead of waiting for Monday.")
                LogEntry("✅ Strategy selected X stocks. Top: [A, B, C]",
                    "The winning strategy chose X stocks to buy. A, B, C are the top picks by allocation.")
                LogEntry("📊 Analysis Complete: Target X stocks. Stops triggered: N",
                    "The daily run finished. N sell orders were triggered by stop-losses today.")
                LogEntry("⏳ Mid-week: holding current positions",
                    "Today is not Monday. No new buys — the app only sells if a stop-loss fires.")
                LogEntry("🟢 BUY STOCK @ ₹X | Qty: N | Value: ₹Y",
                    "A stock was purchased. Your simulated cash decreased by ₹Y.")
                LogEntry("🔴 SELL STOCK @ ₹X | … (Stop-Loss)",
                    "A stock fell below its protection price and was sold to limit the loss.")
                LogEntry("⚡ INTRADAY STOP: STOCK breached ATR stop",
                    "During live market hours, a stock stayed below its safety price for 30+ minutes. It was sold without waiting for end of day.")
                LogEntry("⚡ First breach detected for STOCK — Awaiting confirmation",
                    "A stock just crossed its stop price for the first time today. The app waits one more 30-min check before selling, to avoid reacting to a brief spike.")
                LogEntry("⚡ Fast-Bear triggered: Nifty −X% in 20 days",
                    "The Nifty index dropped sharply in a short period — a crash signal. The app shifts to defensive mode immediately without waiting weeks for confirmation.")
                LogEntry("🐻 Bear Market — switching to defensive mode",
                    "The broad market is in a sustained downtrend. The app reduces exposure to 50% cash and favours stable stocks.")
                LogEntry("🌍 India CPI (YEAR): X% (World Bank)",
                    "Inflation data was downloaded successfully. If it exceeds 6% (RBI's upper limit), the app treats it as a headwind for equities.")
                LogEntry("⚠️ World Bank CPI fetch failed — using fallback 4.5%",
                    "Inflation data couldn't be downloaded (network issue). A safe mid-range default is used. No action needed from you.")
                LogEntry("📈 Market data ready: X/100 symbols (X% coverage)",
                    "Price history loaded for X stocks. Stocks with no history are skipped for safety.")
                LogEntry("⚠️ No fundamentals for X symbols: A, B, C…",
                    "Financial data for these specific stocks couldn't be fetched. They're still eligible — the app gives them the benefit of the doubt.")
                LogEntry("⚠️ No live price for STOCK — stop check deferred",
                    "The real-time price feed couldn't reach this stock. Its stop-loss will be checked at end of day instead.")
                LogEntry("⚠️ Market Data might be stale (> 4 days old)",
                    "Price history looks older than expected — possibly a prolonged holiday. The app continues, but results may lag slightly.")

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // ── WHY THINGS HAPPEN ─────────────────────────────────────────
                Text("WHY THINGS HAPPEN", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "• Buys only happen on Mondays. Mid-week you'll only see sells (stop-losses).",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "• Stop-loss width is based on the stock's normal daily movement (ATR). A volatile stock gets a wider stop so it isn't sold on normal swings.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "• A stop only fires after two consecutive checks (≈60 min) below the level — preventing panic sells on a brief intra-day spike.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "• The strategy changes every Monday. This is intentional — the market changes and the best approach changes with it.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "• 'Equity' = your cash + the current market value of everything you hold.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "• If fewer than 20 stocks appear in the portfolio, some were skipped because their allocated amount wasn't enough to buy even 1 full share at that day's price.",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Full technical documentation: app_bible.md",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

/**
 * A compact two-line row explaining a simulation log entry.
 * @param emoji The log line prefix (emoji + starting text).
 * @param meaning Plain-English explanation for a non-technical user.
 */
@Composable
private fun LogEntry(emoji: String, meaning: String) {
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = meaning,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
