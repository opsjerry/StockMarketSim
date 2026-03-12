package com.example.stockmarketsim.presentation.ui.creation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stockmarketsim.presentation.ui.components.GlassCard
import com.example.stockmarketsim.presentation.ui.components.GradientProgressBar
import com.example.stockmarketsim.presentation.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSimulationScreen(
    onNavigateBack: () -> Unit,
    viewModel: CreateSimulationViewModel = hiltViewModel()
) {
    var step   by remember { mutableIntStateOf(0) }   // 0=Info  1=Capital  2=Review
    var name   by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("100000") }
    var duration by remember { mutableStateOf(12) }
    var target by remember { mutableStateOf(15f) }
    var error  by remember { mutableStateOf<String?>(null) }

    val targetLabel = when {
        target <= 12f -> "Conservative 🛡️"
        target <= 25f -> "Balanced ⚖️"
        target <= 40f -> "Aggressive 🚀"
        else          -> "High Risk ⚡"
    }

    Scaffold(
        containerColor = Navy900,
        topBar = {
            TopAppBar(
                title = { Text("New Simulation", style = MaterialTheme.typography.titleLarge, color = Color.White) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ── Step progress ─────────────────────────────────────────────────
            GradientProgressBar(
                progress = (step + 1) / 3f,
                startColor = ElectricBlue,
                endColor = CyanAccent,
                height = 6.dp,
                label = listOf("① Name", "② Capital", "③ Confirm")[step],
                trailingLabel = "${step + 1}/3"
            )

            // ── Step 0: Name ──────────────────────────────────────────────────
            if (step == 0) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("📝 Name your simulation", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(
                            "Give it a memorable name — e.g. \"Nifty 50 Bull Run\" or \"Safe 12M Test\"",
                            style = MaterialTheme.typography.bodySmall, color = NeutralSlate
                        )
                        AppTextField(
                            value = name,
                            onValueChange = { name = it; error = null },
                            label = "Simulation Name",
                            placeholder = "e.g. Nifty Bull Run 2026"
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (name.isBlank()) error = "Please enter a name"
                        else { error = null; step = 1 }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Next →", fontWeight = FontWeight.SemiBold)
                }
            }

            // ── Step 1: Capital & Duration ────────────────────────────────────
            if (step == 1) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("💰 Capital & Duration", style = MaterialTheme.typography.titleMedium, color = Color.White)

                        AppTextField(
                            value = amount,
                            onValueChange = { amount = it; error = null },
                            label = "Initial Capital (₹)",
                            keyboardType = KeyboardType.Number,
                            prefix = "₹ "
                        )

                        // Duration segmented buttons
                        Column {
                            Text("Duration", style = MaterialTheme.typography.labelMedium, color = NeutralSlate)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(3, 6, 12, 24).forEach { months ->
                                    val selected = duration == months
                                    Surface(
                                        onClick = { duration = months },
                                        color = if (selected) ElectricBlue else Navy700,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = if (months < 12) "${months}M" else "${months / 12}Y",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (selected) Color.White else NeutralSlate,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.padding(vertical = 10.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        // Target return slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Target Return", style = MaterialTheme.typography.labelMedium, color = NeutralSlate)
                                Text(
                                    "${"%.0f".format(target)}%  ·  $targetLabel",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ElectricBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = target,
                                onValueChange = { target = it },
                                valueRange = 5f..60f,
                                steps = 10,
                                colors = SliderDefaults.colors(
                                    thumbColor = ElectricBlue,
                                    activeTrackColor = ElectricBlue,
                                    inactiveTrackColor = Navy600
                                )
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("5% Conservative", style = MaterialTheme.typography.labelSmall, color = NeutralSlate)
                                Text("60% High Risk", style = MaterialTheme.typography.labelSmall, color = NeutralSlate)
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { step = 0 },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("← Back") }
                    Button(
                        onClick = {
                            if (amount.toDoubleOrNull() == null || amount.toDouble() < 1000) {
                                error = "Enter a valid amount (min ₹1,000)"
                            } else { error = null; step = 2 }
                        },
                        modifier = Modifier.weight(2f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                    ) { Text("Review →", fontWeight = FontWeight.SemiBold) }
                }
            }

            // ── Step 2: Review & Confirm ──────────────────────────────────────
            if (step == 2) {
                GlassCard(modifier = Modifier.fillMaxWidth(), glowColor = BullGreen) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("✅ Review & Confirm", style = MaterialTheme.typography.titleMedium, color = Color.White)

                        Divider(color = Navy600)

                        ReviewRow("Simulation Name",  name)
                        ReviewRow("Initial Capital",  "₹${"%,.0f".format(amount.toDoubleOrNull() ?: 0.0)}")
                        ReviewRow("Duration",         if (duration >= 12) "${duration / 12} Year(s)" else "$duration Months")
                        ReviewRow("Target Return",    "${"%.0f".format(target)}% ($targetLabel)")

                        Divider(color = Navy600)

                        Text(
                            "The Strategy Tournament will run immediately and the best strategy for your risk profile will be selected automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeutralSlate
                        )
                    }
                }

                error?.let { Text(it, color = BearRed, style = MaterialTheme.typography.bodySmall) }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { step = 1 },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("← Edit") }

                    Button(
                        onClick = {
                            val amountVal = amount.toDoubleOrNull()
                            if (amountVal != null) {
                                viewModel.createSimulation(
                                    name, amountVal, duration, target.toDouble(),
                                    onSuccess = onNavigateBack,
                                    onError = { error = it }
                                )
                            }
                        },
                        modifier = Modifier.weight(2f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BullGreen, contentColor = Color.White)
                    ) {
                        Text("🚀 Start Analysis", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = NeutralSlate)
        Text(value, style = MaterialTheme.typography.bodySmall, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    prefix: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        placeholder = { Text(placeholder, color = NeutralSlate.copy(alpha = 0.5f)) },
        prefix = if (prefix.isNotEmpty()) ({ Text(prefix, color = NeutralSlate) }) else null,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ElectricBlue,
            unfocusedBorderColor = Navy600,
            focusedLabelColor = ElectricBlue,
            unfocusedLabelColor = NeutralSlate,
            cursorColor = ElectricBlue,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = Navy700,
            unfocusedContainerColor = Navy700
        ),
        shape = RoundedCornerShape(12.dp),
        singleLine = true
    )
}

