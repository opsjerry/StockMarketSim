package com.example.stockmarketsim.presentation.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ── Typography (system sans-serif — close to Inter on Android) ─────────────
val AppTypography = Typography(
    headlineLarge  = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,      fontSize = 30.sp, lineHeight = 37.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,      fontSize = 24.sp, lineHeight = 32.sp),
    headlineSmall  = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,  fontSize = 20.sp, lineHeight = 28.sp),

    titleLarge   = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp),
    titleMedium  = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp),
    titleSmall   = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,   fontSize = 13.sp, lineHeight = 20.sp),

    bodyLarge    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium   = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 17.sp),

    labelLarge   = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    labelMedium  = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,   fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall   = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,   fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp),
)

// ── Colour Scheme ─────────────────────────────────────────────────────────────
private val AppDarkColorScheme = darkColorScheme(
    primary            = ElectricBlue,
    onPrimary          = Navy950,
    primaryContainer   = NavyGlow,
    onPrimaryContainer = ElectricBlue,

    secondary            = CyanAccent,
    onSecondary          = Navy950,
    secondaryContainer   = Navy700,
    onSecondaryContainer = NeutralSlate,

    tertiary            = BullGreen,
    onTertiary          = Navy950,
    tertiaryContainer   = BullGreenDim,
    onTertiaryContainer = BullGreen,

    error            = BearRed,
    onError          = Navy950,
    errorContainer   = BearRedDim,
    onErrorContainer = BearRed,

    background           = Navy900,
    onBackground         = androidx.compose.ui.graphics.Color.White,
    surface              = Navy800,
    onSurface            = androidx.compose.ui.graphics.Color.White,
    surfaceVariant       = Navy700,
    onSurfaceVariant     = NeutralSlate,
    outline              = Navy600,
    outlineVariant       = Navy600.copy(alpha = 0.5f),
    inverseSurface       = androidx.compose.ui.graphics.Color.White,
    inverseOnSurface     = Navy900,
    surfaceTint          = ElectricBlue.copy(alpha = 0.04f),
)

// ── Theme ─────────────────────────────────────────────────────────────────────
@Composable
fun StockMarketSimTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Navy950.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = AppDarkColorScheme,
        typography  = AppTypography,
        content     = content
    )
}
