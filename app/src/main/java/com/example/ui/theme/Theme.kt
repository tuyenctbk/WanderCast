package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BroadcastDarkColorScheme = darkColorScheme(
    primary = BrassAmber,
    onPrimary = MidnightSlate,
    primaryContainer = ElevatedConsoleSurface,
    onPrimaryContainer = WarmAmberLight,
    secondary = RadarTeal,
    onSecondary = MidnightSlate,
    secondaryContainer = RadarTealDim,
    onSecondaryContainer = ParchmentCream,
    tertiary = SignalCoral,
    onTertiary = MidnightSlate,
    background = MidnightSlate,
    onBackground = ParchmentCream,
    surface = BroadcastCharcoal,
    onSurface = ParchmentCream,
    surfaceVariant = StudioCardSurface,
    onSurfaceVariant = MutedSand,
    outline = ConsoleBorder,
    error = ErrorCrimson,
    onError = Color.White
)

private val FieldGuideLightColorScheme = lightColorScheme(
    primary = DaylightAmber,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF78350F),
    secondary = DaylightTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF134E4A),
    tertiary = SignalCoral,
    onTertiary = Color.White,
    background = DaylightParchment,
    onBackground = DaylightInk,
    surface = DaylightCard,
    onSurface = DaylightInk,
    surfaceVariant = DaylightSurface,
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = ErrorCrimson,
    onError = Color.White
)

@Composable
fun WanderCastTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) BroadcastDarkColorScheme else FieldGuideLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
