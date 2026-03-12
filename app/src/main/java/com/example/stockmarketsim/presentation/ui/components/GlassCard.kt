package com.example.stockmarketsim.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.stockmarketsim.presentation.ui.theme.GlassOverlay
import com.example.stockmarketsim.presentation.ui.theme.GlassStroke
import com.example.stockmarketsim.presentation.ui.theme.Navy800

/**
 * Glassmorphism card — a translucent navy surface with a 0.5dp white-glass border.
 *
 * @param modifier   Layout modifier (width, padding etc.)
 * @param glowColor  Optional left/bottom glow tint — use BullGreen, BearRed, ElectricBlue.
 *                   Pass Color.Transparent for no glow (default).
 * @param cornerRadius  Corner radius, default 16dp.
 * @param content    Composable content placed inside the card.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glowColor: Color = Color.Transparent,
    cornerRadius: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    // Background: navy-800 base + subtle white glass overlay
    val bgBrush = Brush.linearGradient(
        colors = listOf(
            Navy800.copy(alpha = 1f),
            Navy800.copy(alpha = 0.92f),
        )
    )

    // Glow accent on the left edge (optional)
    val borderBrush: Brush = if (glowColor != Color.Transparent) {
        Brush.verticalGradient(
            colors = listOf(
                glowColor.copy(alpha = 0.6f),
                GlassStroke,
                GlassStroke,
                glowColor.copy(alpha = 0.2f),
            )
        )
    } else {
        Brush.linearGradient(listOf(GlassStroke, GlassStroke))
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(bgBrush, shape)
            .background(GlassOverlay, shape)
            .border(0.5.dp, borderBrush, shape),
        content = content
    )
}
