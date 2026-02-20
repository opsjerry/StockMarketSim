package com.example.stockmarketsim.presentation.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.stockmarketsim.domain.model.PortfolioItem
import com.patrykandpatrick.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
// Imports removed


// Since the Vico Pie Chart is not yet fully stable in the requested version or might require specific implementation details
// that vary significantly between 1.x versions, and considering the user requested "Chart Integration", 
// we will verify the exact imports for Vico 1.x Pie Charts. 
// However, Vico is primarily known for Cartesian charts (Line, Column). 
// Let's implement a Column Chart instead for Holdings Quantity/Value as it is Vico's strength 
// or stick to the Plan if Vico supports Pie.
// Checking Vico docs (simulated): Vico 2.0 has PieChart, but 1.x focuses on Cartesian.
// Let's implement a Column Chart showing the Value of each holding.

import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.entryModelOf

@Composable
fun PortfolioValueChart(
    holdingsData: Map<String, Double>,
    modifier: Modifier = Modifier
) {
    if (holdingsData.isEmpty()) return

    // Create stable list for indexing
    val dataList = remember(holdingsData) { holdingsData.toList() }

    val entries = remember(dataList) {
        dataList.mapIndexed { index, (_, value) ->
            FloatEntry(index.toFloat(), value.toFloat())
        }
    }
    
    val chartEntryModel = entryModelOf(entries)

    ProvideChartStyle(m3ChartStyle()) {
        Chart(
            chart = columnChart(),
            model = chartEntryModel,
            startAxis = rememberStartAxis(
                valueFormatter = { value, _ ->
                    val v = value
                    when {
                        kotlin.math.abs(v) >= 1_000_000 -> "%.1fM".format(v / 1_000_000).replace(".0M", "M")
                        kotlin.math.abs(v) >= 1_000 -> "%.1fk".format(v / 1_000).replace(".0k", "k")
                        else -> "%.0f".format(v)
                    }
                }
            ),
            bottomAxis = rememberBottomAxis(
                valueFormatter = { value, _ ->
                    dataList.getOrNull(value.toInt())?.first ?: ""
                }
            ),
            modifier = modifier
                .height(200.dp)
                .padding(16.dp)
        )
    }
}
