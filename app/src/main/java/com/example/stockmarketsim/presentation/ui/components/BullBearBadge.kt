package com.example.stockmarketsim.presentation.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stockmarketsim.presentation.ui.theme.*

/**
 * Regime state pill displayed on Dashboard cards and the Detail screen.
 *
 * @param regime One of "BULL", "BEAR", "NEUTRAL", "FAST_BEAR".
 */
@Composable
fun BullBearBadge(regime: String, modifier: Modifier = Modifier) {
    val (label, bg, fg) = when (regime.uppercase()) {
        "BULL"      -> Triple("📈 BULL",      BullGreenDim,     BullGreen)
        "BEAR"      -> Triple("📉 BEAR",      BearRedDim,       BearRed)
        "FAST_BEAR" -> Triple("⚡ FAST BEAR", BearRedDim,       BearRed)
        "NEUTRAL"   -> Triple("➡️ NEUTRAL",   Color(0xFF1C2A3A), NeutralSlate)
        else        -> Triple(regime,          Color(0xFF1C2A3A), NeutralSlate)
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
