package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TmsDarkColorScheme = darkColorScheme(
    primary = MetallicGold,
    onPrimary = Color(0xFF1E1700),
    primaryContainer = GoldContainer,
    onPrimaryContainer = OnGoldContainer,

    secondary = EmeraldGreen,
    onSecondary = Color(0xFF00391A),
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = Color(0xFF8CF8B5),

    tertiary = BrightGold,
    onTertiary = Color(0xFF241A00),
    tertiaryContainer = Color(0xFF382B00),
    onTertiaryContainer = Color(0xFFFFDF73),

    background = DeepBlackBackground,
    onBackground = TextWhitePrimary,

    surface = ObsidianSurface,
    onSurface = TextWhitePrimary,
    surfaceVariant = CardSurfaceDark,
    onSurfaceVariant = TextGraySecondary,

    outline = BorderGoldMuted,
    outlineVariant = BorderGoldHighlight,

    error = StatusOfflineRed,
    onError = Color.White
)

@Composable
fun TmsVpnTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TmsDarkColorScheme,
        typography = Typography,
        content = content
    )
}
