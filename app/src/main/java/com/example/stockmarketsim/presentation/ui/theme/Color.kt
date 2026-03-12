package com.example.stockmarketsim.presentation.ui.theme

import androidx.compose.ui.graphics.Color

// ── Base Palette ──────────────────────────────────────────────────────────────
val Navy950  = Color(0xFF040D1A)   // Deepest background
val Navy900  = Color(0xFF0B1120)   // Primary background
val Navy800  = Color(0xFF0F1929)   // Card surface
val Navy700  = Color(0xFF172034)   // Elevated card / input bg
val Navy600  = Color(0xFF1E2D45)   // Dividers / subtle borders
val NavyGlow = Color(0xFF1B3A6B)   // Blue glow stroke on glass cards

// ── Accent ───────────────────────────────────────────────────────────────────
val ElectricBlue = Color(0xFF3B82F6)   // Primary interactive / links
val CyanAccent   = Color(0xFF06B6D4)   // Secondary accent (ML / AI features)
val VioletAccent = Color(0xFF8B5CF6)   // ML / Deep Learning badge
val AmberWarning = Color(0xFFF59E0B)   // Warnings, near-goal progress

// ── Semantic: Market ─────────────────────────────────────────────────────────
val BullGreen    = Color(0xFF10B981)   // Positive returns, BUY, uptrend
val BullGreenDim = Color(0xFF064E3B)   // BUY card bg
val BearRed      = Color(0xFFEF4444)   // Negative returns, SELL, downtrend
val BearRedDim   = Color(0xFF7F1D1D)   // SELL card bg
val NeutralSlate = Color(0xFF94A3B8)   // Neutral / muted text, benchmarks

// ── Glass Surface ─────────────────────────────────────────────────────────────
val GlassStroke  = Color(0x26FFFFFF)   // White 15% — glass card border
val GlassOverlay = Color(0x0DFFFFFF)   // White 5%  — glass card fill
val GlassGlow    = Color(0x1A3B82F6)   // Blue 10%  — active card glow

// ── Legacy aliases (keep existing references compiling) ───────────────────────
val SurfaceDark  = Navy900
val PrimaryBlue  = ElectricBlue
val SuccessGreen = BullGreen
val ErrorRed     = BearRed
