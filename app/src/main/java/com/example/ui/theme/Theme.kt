package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val TmsCyberDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF001F28),
    primaryContainer = DarkCyanContainer,
    onPrimaryContainer = Color(0xFFB8F4FF),

    secondary = PremiumTeal,
    onSecondary = Color(0xFF00211B),
    secondaryContainer = DarkTealContainer,
    onSecondaryContainer = Color(0xFFA7F7E5),

    tertiary = MetallicSilver,
    onTertiary = Color(0xFF141B22),
    tertiaryContainer = ElevatedSurface,
    onTertiaryContainer = SilverHighlight,

    background = DeepGraphite,
    onBackground = TextCyberPrimary,

    surface = SecondarySurface,
    onSurface = TextCyberPrimary,
    surfaceVariant = ElevatedSurface,
    onSurfaceVariant = TextCyberSecondary,

    outline = GlassBorderSubtle,
    outlineVariant = GlassBorderMetallic,

    error = StatusError,
    onError = Color.White
)

val TmsCyberShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TmsVpnTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TmsCyberDarkColorScheme,
        typography = Typography,
        shapes = TmsCyberShapes,
        content = content
    )
}
