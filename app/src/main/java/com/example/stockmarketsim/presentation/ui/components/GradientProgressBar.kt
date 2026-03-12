package com.example.stockmarketsim.presentation.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.stockmarketsim.presentation.ui.theme.*

/**
 * A rounded gradient-filled progress bar.
 *
 * @param progress   0f..1f fill fraction.
 * @param startColor Gradient start colour (left).
 * @param endColor   Gradient end colour (right).
 * @param trackColor Background track colour.
 * @param height     Bar height in dp.
 * @param label      Optional left label shown above the bar.
 * @param trailingLabel Optional right label shown above the bar.
 */
@Composable
fun GradientProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    startColor: Color = ElectricBlue,
    endColor: Color = BullGreen,
    trackColor: Color = Navy600,
    height: Dp = 8.dp,
    label: String? = null,
    trailingLabel: String? = null
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "progressBar"
    )

    if (label != null || trailingLabel != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            label?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailingLabel?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(3.dp))
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
    ) {
        // Track
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = CornerRadius(size.height / 2)
        )
        // Fill
        if (animatedProgress > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(startColor, endColor),
                    startX = 0f,
                    endX = size.width * animatedProgress
                ),
                size = Size(size.width * animatedProgress, size.height),
                cornerRadius = CornerRadius(size.height / 2)
            )
        }
    }
}
