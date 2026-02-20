package com.example.stockmarketsim.presentation.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.entryModelOf
import androidx.compose.ui.graphics.Color
import com.patrykandpatrick.vico.compose.component.shape.shader.verticalGradient

@Composable
fun PortfolioTrendChart(
    history: List<Pair<Long, Double>>,
    initialAmount: Double,
    targetReturn: Double,
    modifier: Modifier = Modifier
) {
    if (history.isEmpty()) return

    val entries = remember(history) {
        history.mapIndexed { index, (_, value) ->
            // Use 1f precision for X to avoid GCD issues in Vico 2.0
            FloatEntry(index.toFloat(), value.toFloat())
        }
    }
    
    val targetEntries = remember(history, targetReturn, initialAmount) {
        if (history.size < 2) return@remember emptyList<FloatEntry>()
        val days = history.size
        val finalTargetValue = initialAmount * (1 + (targetReturn / 100))
        
        history.mapIndexed { index, _ ->
            val value = initialAmount + (finalTargetValue - initialAmount) * (index.toFloat() / (days - 1))
            FloatEntry(index.toFloat(), value.toFloat())
        }
    }
    
    val chartEntryModel = remember(entries, targetEntries) {
        if (targetEntries.isEmpty()) {
            entryModelOf(entries)
        } else {
            entryModelOf(entries, targetEntries)
        }
    }

    val chartColor = MaterialTheme.colorScheme.primary.toArgb()
    val targetColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f).toArgb()

    ProvideChartStyle(m3ChartStyle()) {
        Chart(
            chart = lineChart(
                lines = listOf(
                    com.patrykandpatrick.vico.core.chart.line.LineChart.LineSpec(
                        lineColor = chartColor,
                        lineBackgroundShader = verticalGradient(
                            arrayOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    ),
                    com.patrykandpatrick.vico.core.chart.line.LineChart.LineSpec(
                        lineColor = targetColor,
                        lineThicknessDp = 1f
                    )
                )
            ),
            model = chartEntryModel,
            startAxis = rememberStartAxis(
                valueFormatter = { value, _ ->
                    val v = value
                    when {
                        kotlin.math.abs(v) >= 1_000_000 -> "₹%.1fM".format(v / 1_000_000).replace(".0M", "M")
                        kotlin.math.abs(v) >= 1_000 -> "₹%.1fk".format(v / 1_000).replace(".0k", "k")
                        else -> "₹%.0f".format(v)
                    }
                }
            ),
            bottomAxis = rememberBottomAxis(
                valueFormatter = { value, _ ->
                    val index = value.toInt()
                    if (index in history.indices) {
                        val timestamp = history[index].first
                        val sdf = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
                        sdf.format(java.util.Date(timestamp))
                    } else {
                        ""
                    }
                }
            ),
            modifier = modifier
                .fillMaxWidth()
                .height(250.dp)
        )
    }
}
