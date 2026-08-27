package com.novastream.app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val ColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = TextPrimary,
    primaryContainer = PrimaryDark,
    onPrimaryContainer = TextPrimary,
    background = BgPure,
    onBackground = TextPrimary,
    surface = BgSurface,
    onSurface = TextPrimary,
    surfaceVariant = BgSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    secondary = Accent,
    onSecondary = BgPure,
    tertiary = AccentBlue,
    onTertiary = BgPure,
    outline = Outline,
    outlineVariant = Divider,
    error = Error,
    onError = TextPrimary,
    errorContainer = ErrorDark,
    onErrorContainer = TextPrimary,
    scrim = Color(0x99000000)
)

private val Type = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Black, fontSize = 32.sp, lineHeight = 38.sp,
        color = TextPrimary, letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp,
        color = TextPrimary, letterSpacing = (-0.3).sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp,
        color = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp,
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp,
        color = TextPrimary
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp,
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp,
        color = TextSecondary
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp,
        color = TextTertiary
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp,
        color = TextPrimary
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp,
        color = TextSecondary
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp,
        color = TextTertiary
    )
)

@Composable
fun NovaStreamTheme(
    useDynamicColor: Boolean = true,
    isTvDevice: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // Dynamic Color (Material You) auf Android 12+ - behält unsere Palette als Basis
    // aber passt Accent Colors an den Wallpaper des Users an
    // Auf TV Geräten wird Dynamic Color deaktiviert (TV hat kein Wallpaper-basiertes Theme)
    val colorScheme = if (useDynamicColor && !isTvDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context).copy(
            // Behalte unsere Premium-Palette für die wichtigsten Colors
            primary = Primary,
            primaryContainer = PrimaryDark,
            background = BgPure,
            surface = BgSurface,
            surfaceVariant = BgSurfaceElevated
        )
    } else if (isTvDevice) {
        // TV: Nutze TV-optimierte Farben für bessere 10-foot UI Sichtbarkeit
        ColorScheme.copy(
            primary = TvPrimary,
            background = TvBgPure,
            surface = TvBgSurface,
            onSurface = TvTextPrimary,
            onSurfaceVariant = TvTextSecondary
        )
    } else {
        ColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Type,
        content = content
    )
}
