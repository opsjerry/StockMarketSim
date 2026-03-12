package com.example.stockmarketsim.presentation.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stockmarketsim.presentation.ui.components.GlassCard
import com.example.stockmarketsim.presentation.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToManageUniverse: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val indianApiKey     by viewModel.indianApiKey.collectAsState()
    val zerodhaApiKey    by viewModel.zerodhaApiKey.collectAsState()
    val zerodhaAccessToken by viewModel.zerodhaAccessToken.collectAsState()

    var showAboutDialog  by remember { mutableStateOf(false) }
    var apiKeyVisible    by remember { mutableStateOf(false) }
    var zerodhaKeyVisible by remember { mutableStateOf(false) }
    var tokenVisible     by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        containerColor = Navy900,
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy950)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Section: Market Data ──────────────────────────────────────────
            SectionLabel("📡 Market Data API")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "IndianAPI.in provides fundamentals (P/E, ROE, D/E, Market Cap).\n" +
                        "Yahoo Finance is the automatic fallback. India CPI uses World Bank (no key needed).",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeutralSlate
                    )
                    SettingsTextField(
                        value = indianApiKey,
                        onValueChange = { viewModel.updateIndianApiKey(it) },
                        label = "IndianAPI.in Key",
                        placeholder = "Paste your API key here",
                        hidden = !apiKeyVisible,
                        onToggleVisibility = { apiKeyVisible = !apiKeyVisible }
                    )
                    Button(
                        onClick = {
                            viewModel.saveSettings()
                            Toast.makeText(context, "API key saved", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save API Key", fontWeight = FontWeight.SemiBold) }
                }
            }

            // ── Section: Zerodha ─────────────────────────────────────────────
            SectionLabel("🔗 Zerodha Live Trading")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enables real-time intra-day stop-loss monitoring. Access token must be manually refreshed daily from Kite Developer Console.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeutralSlate
                    )
                    // Zerodha connection status chip
                    val isConnected = zerodhaApiKey.isNotBlank() && zerodhaAccessToken.isNotBlank()
                    Surface(
                        color = if (isConnected) BullGreenDim else BearRedDim,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            if (isConnected) "🟢  Credentials Configured" else "🔴  Not Configured",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isConnected) BullGreen else BearRed,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    SettingsTextField(
                        value = zerodhaApiKey,
                        onValueChange = { viewModel.updateZerodhaApiKey(it) },
                        label = "Kite Connect API Key",
                        hidden = !zerodhaKeyVisible,
                        onToggleVisibility = { zerodhaKeyVisible = !zerodhaKeyVisible }
                    )
                    SettingsTextField(
                        value = zerodhaAccessToken,
                        onValueChange = { viewModel.updateZerodhaAccessToken(it) },
                        label = "Access Token (daily)",
                        hidden = !tokenVisible,
                        onToggleVisibility = { tokenVisible = !tokenVisible }
                    )
                    Button(
                        onClick = {
                            viewModel.saveZerodhaSettings()
                            Toast.makeText(context, "Zerodha credentials saved", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save Zerodha Credentials", fontWeight = FontWeight.SemiBold) }
                }
            }

            // ── Section: Data Management ──────────────────────────────────────
            SectionLabel("📊 Data Management")
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToManageUniverse() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Manage Stock Universe", style = MaterialTheme.typography.titleSmall, color = Color.White)
                        Text("Add, remove or sync NIFTY 100 stocks.", style = MaterialTheme.typography.bodySmall, color = NeutralSlate)
                    }
                    Icon(Icons.Default.ArrowForward, null, tint = NeutralSlate)
                }
            }

            // ── Section: About ────────────────────────────────────────────────
            SectionLabel("ℹ️ About")
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAboutDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Intelligence Engine Guide", style = MaterialTheme.typography.titleSmall, color = Color.White)
                        Text("How the simulation works — for beginners.", style = MaterialTheme.typography.bodySmall, color = NeutralSlate)
                    }
                    Icon(Icons.Default.Info, null, tint = ElectricBlue)
                }
            }

            // App version footer
            Text(
                "Auto-Pilot Simulator  v3.5  ·  Midnight Quant Edition",
                style = MaterialTheme.typography.labelSmall,
                color = NeutralSlate.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = NeutralSlate,
        modifier = Modifier.padding(top = 4.dp, start = 2.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    hidden: Boolean = false,
    onToggleVisibility: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder, color = NeutralSlate.copy(alpha = 0.5f)) }) else null,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (onToggleVisibility != null) ({
            TextButton(onClick = onToggleVisibility, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(if (hidden) "Show" else "Hide", style = MaterialTheme.typography.labelSmall, color = ElectricBlue)
            }
        }) else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = ElectricBlue,
            unfocusedBorderColor = Navy600,
            focusedLabelColor    = ElectricBlue,
            unfocusedLabelColor  = NeutralSlate,
            cursorColor          = ElectricBlue,
            focusedTextColor     = Color.White,
            unfocusedTextColor   = Color.White,
            focusedContainerColor   = Navy700,
            unfocusedContainerColor = Navy700
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

// ── About Dialog (unchanged content, styled to theme) ────────────────────────
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Navy800,
        titleContentColor = Color.White,
        textContentColor = NeutralSlate,
        title = {
            Text("📊 Auto-Pilot Simulator v3.5", color = ElectricBlue, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
            ) {
                AboutSection("HOW IT WORKS")
                Text(
                    "Every weekday the app runs 5 steps automatically — you don't need to do anything.",
                    style = MaterialTheme.typography.bodySmall, color = NeutralSlate
                )
                Spacer(Modifier.height(8.dp))
                AboutStep("1️⃣  Health Check", "Scans 100 NSE stocks. Companies with weak profitability (ROE < 12%) or too much debt (D/E > 1.0) are removed.")
                AboutStep("2️⃣  Strategy Battle", "23+ strategies compete by backtesting. The best one matched to your target return becomes the Auto-Pilot.")
                AboutStep("3️⃣  Portfolio Build", "Picks up to 20 stocks. Lower-volatility stocks get larger positions. Max 10% per stock.")
                AboutStep("4️⃣  Risk Guard", "Every stock gets an auto exit price (stop-loss). New positions get a wider buffer for their first 3 days.")
                AboutStep("5️⃣  Intra-Day Watch", "Every 30 min during market hours (9:15–15:30 IST), prices are checked. A sustained breach triggers an immediate sell.")

                Divider(modifier = Modifier.padding(vertical = 12.dp), color = Navy600)

                AboutSection("READING YOUR LOGS")
                Spacer(Modifier.height(6.dp))
                LogEntry("🌅 Starting Daily Market Analysis…", "The daily run has begun.")
                LogEntry("🏎️ Running Strategy Tournament…", "All strategies are being tested against historical data.")
                LogEntry("🟢 BUY STOCK @ ₹X", "A stock was purchased. Your simulated cash decreased.")
                LogEntry("🔴 SELL STOCK (Stop-Loss)", "A stock fell below its protection price and was sold.")
                LogEntry("⚡ INTRADAY STOP", "A stock stayed below its safety price for 30+ min and was sold immediately.")
                LogEntry("⚡ Fast-Bear triggered", "Sharp Nifty drop detected. App shifts to defensive mode immediately.")
                LogEntry("🐻 Bear Market", "Sustained downtrend. App reduces exposure to 50% cash.")
                LogEntry("⚠️ World Bank CPI failed — fallback 4.5%", "Inflation data unavailable. Safe default used. No action needed.")
                LogEntry("⚠️ Market data might be stale", "Price history looks older than expected — possibly a holiday period.")

                Divider(modifier = Modifier.padding(vertical = 12.dp), color = Navy600)

                AboutSection("WHY THINGS HAPPEN")
                Spacer(Modifier.height(6.dp))
                AboutPoint("Buys only happen on Mondays. Mid-week you'll only see stop-loss sells.")
                AboutPoint("Stop-loss width is based on normal daily movement (ATR). Volatile stocks get a wider stop.")
                AboutPoint("A stop fires only after 2 consecutive checks (~60 min) below the level — no panic sells on brief spikes.")
                AboutPoint("The strategy changes every Monday. The market changes, and the best approach changes with it.")
                AboutPoint("'Equity' = cash + current market value of all your holdings.")
                AboutPoint("Fewer than 20 stocks? Some were skipped because the allocated amount couldn't buy even 1 full share.")

                Spacer(Modifier.height(12.dp))
                Text("Full technical documentation: app_bible.md", style = MaterialTheme.typography.labelSmall, color = ElectricBlue)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = ElectricBlue) }
        }
    )
}

@Composable
private fun AboutSection(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = CyanAccent)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun AboutStep(step: String, desc: String) {
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Text(step, style = MaterialTheme.typography.labelMedium, color = Color.White)
        Text(desc, style = MaterialTheme.typography.bodySmall, color = NeutralSlate, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun AboutPoint(text: String) {
    Row(modifier = Modifier.padding(bottom = 5.dp)) {
        Text("•  ", style = MaterialTheme.typography.bodySmall, color = ElectricBlue)
        Text(text, style = MaterialTheme.typography.bodySmall, color = NeutralSlate)
    }
}

@Composable
private fun LogEntry(emoji: String, meaning: String) {
    Column(modifier = Modifier.padding(bottom = 5.dp)) {
        Text(emoji, style = MaterialTheme.typography.labelMedium, color = Color.White)
        Text(meaning, style = MaterialTheme.typography.bodySmall, color = NeutralSlate, modifier = Modifier.padding(start = 8.dp))
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
