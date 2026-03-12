package com.example.stockmarketsim.presentation.ui.components

import androidx.compose.animation.core.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/**
 * Animated counter that "rolls up" from 0 to [target] on first composition.
 *
 * @param target    The final value to display (e.g. 125000.0).
 * @param prefix    Optional prefix string, e.g. "₹".
 * @param suffix    Optional suffix string, e.g. "%".
 * @param format    Format string for the number, e.g. "%.2f" or "%,.0f".
 * @param durationMs Animation duration in milliseconds (default 800ms).
 * @param style     Text style.
 * @param color     Text colour.
 */
@Composable
fun AnimatedCounter(
    target: Double,
    prefix: String = "",
    suffix: String = "",
    format: String = "%.2f",
    durationMs: Int = 800,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    var displayValue by remember(target) { mutableStateOf(0.0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(target) {
        scope.launch {
            val animatable = Animatable(0f)
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing)
            )
        }
        // Manually step the value for smooth display
        val steps = 60
        val delayMs = (durationMs / steps).toLong()
        for (i in 1..steps) {
            displayValue = target * i / steps
            kotlinx.coroutines.delay(delayMs)
        }
        displayValue = target
    }

    Text(
        text = "$prefix${format.format(displayValue)}$suffix",
        style = style,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}
