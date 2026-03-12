package com.example.stockmarketsim.presentation.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.stockmarketsim.presentation.ui.theme.Navy700
import com.example.stockmarketsim.presentation.ui.theme.Navy800

/**
 * A shimmering placeholder box, shown while data is loading.
 * Replaces raw CircularProgressIndicator in list skeletons.
 */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -300f,
        targetValue  = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Navy800,
            Navy700,
            Color.White.copy(alpha = 0.07f),
            Navy700,
            Navy800,
        ),
        start  = Offset(translateX, 0f),
        end    = Offset(translateX + 400f, 0f)
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(shimmerBrush)
    )
}
