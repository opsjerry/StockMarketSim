package com.example.stockmarketsim.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stockmarketsim.presentation.ui.theme.Navy700

/**
 * A compact label + value chip used in metrics rows.
 * e.g.  [label: "α vs Nifty"]  [value: "+4.23%"]
 *
 * @param label     Subtitle label above the value.
 * @param value     The displayed metric string.
 * @param valueColor Colour of the value text (bull green, bear red, primary etc.)
 * @param badge     Optional 1-2 word badge appended below value (e.g. "Excellent").
 * @param modifier  Outer modifier.
 */
@Composable
fun StatChip(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    badge: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Navy700)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
        if (badge != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = valueColor.copy(alpha = 0.75f)
            )
        }
    }
}
