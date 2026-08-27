package com.novastream.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ─── Premium Color Palette ──────────────────────────────────────────────────
// Deep, rich dark theme inspired by Netflix / Disney+ / Apple TV+

val BgPure = Color(0xFF08090C)
val BgDark = Color(0xFF0E1117)
val BgSurface = Color(0xFF151921)
val BgSurfaceElevated = Color(0xFF1C2230)
val BgCard = Color(0xFF1A1F2B)

val Primary = Color(0xFFE50914)       // Netflix Red
val PrimaryDark = Color(0xFFB20710)
val PrimaryLight = Color(0xFFFF3D44)
val Accent = Color(0xFFFFB041)        // Gold accent
val AccentBlue = Color(0xFF00C2FF)

val TextPrimary = Color(0xFFF5F7FA)
val TextSecondary = Color(0xFFA8B0BD)
val TextTertiary = Color(0xFF6B7280)

val Divider = Color(0xFF252B38)
val Outline = Color(0xFF2D3444)

// Glassmorphism
val GlassLight = Color(0x15FFFFFF)
val GlassMedium = Color(0x22FFFFFF)
val GlassDark = Color(0x10FFFFFF)

// Gradients
val HeroGradientTop = Color(0x00000000)
val HeroGradientBottom = Color(0xE60E1117)
val HeroGradientLeft = Color(0x9908090C)
val CardGradientBottom = Color(0xCC08090C)

val PrimaryGradient = Brush.horizontalGradient(
    colors = listOf(Primary, PrimaryLight)
)
val PremiumGradient = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color(0xE608090C))
)
val AccentGradient = Brush.horizontalGradient(
    colors = listOf(AccentBlue, Accent)
)
val ShimmerBase = Color(0xFF1A1F2B)
val ShimmerHighlight = Color(0xFF2D3444)

// ─── Additional Card Colors ──────────────────────────────────────────────────
val BgCardHover = Color(0xFF232938)     // Hover state for cards
val BgCardActive = Color(0xFF2A3142)    // Active/pressed state
val BgCardSelected = Color(0xFF1F2533)  // Selected state

// ─── Status Colors ──────────────────────────────────────────────────────────
val Success = Color(0xFF22C55E)        // Green for success states
val SuccessDark = Color(0xFF16A34A)
val Error = Color(0xFFEF4444)          // Red for error states
val ErrorDark = Color(0xFFDC2626)
val Warning = Color(0xFFF59E0B)        // Amber for warnings
val Info = Color(0xFF3B82F6)           // Blue for info

// ─── TV-Optimized Colors ────────────────────────────────────────────────────
// Höhere Sättigung und hellerer Hintergrund für 10-foot UI Sichtbarkeit
val TvBgPure = Color(0xFF050608)       // Dunkler für besseren Kontrast auf TV
val TvBgSurface = Color(0xFF1A1F2B)    // Etwas heller als Phone BgSurface
val TvPrimary = Color(0xFFFF1A2A)      // Helleres Rot für TV Sichtbarkeit
val TvAccent = Color(0xFFFFC857)       // Helleres Gold für TV
val TvTextPrimary = Color(0xFFFFFFFF)  // Voll weiß für max Kontrast
val TvTextSecondary = Color(0xFFB8C0CC) // Heller als Phone TextSecondary
val TvFocusRing = Color(0xFFFFB041)    // Goldener Focus-Ring für D-Pad Navigation
