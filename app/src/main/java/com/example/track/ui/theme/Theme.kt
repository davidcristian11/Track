package com.example.track.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val TrackLightColorScheme = lightColorScheme(
    primary = TrackSage,
    onPrimary = TrackSurface,
    primaryContainer = TrackSagePale,
    onPrimaryContainer = TrackSageDark,
    secondary = TrackSageDark,
    onSecondary = TrackSurface,
    secondaryContainer = TrackSagePale,
    onSecondaryContainer = TrackTextPrimary,
    tertiary = TrackSage,
    onTertiary = TrackSurface,
    tertiaryContainer = TrackSurfaceVariant,
    onTertiaryContainer = TrackTextPrimary,
    background = TrackBackground,
    onBackground = TrackTextPrimary,
    surface = TrackSurface,
    onSurface = TrackTextPrimary,
    surfaceVariant = TrackSurfaceVariant,
    onSurfaceVariant = TrackTextSecondary,
    outline = TrackOutline,
    outlineVariant = TrackOutline,
    inverseSurface = TrackTextPrimary,
    inverseOnSurface = TrackBackground,
    inversePrimary = TrackSurfaceVariant,
    surfaceTint = TrackSage,
    scrim = TrackTextPrimary
)

private val TrackShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun TrackTheme(content: @Composable () -> Unit) {
    // Phase 1 intentionally uses one branded light theme instead of dynamic color.
    MaterialTheme(
        colorScheme = TrackLightColorScheme,
        typography = TrackTypography,
        shapes = TrackShapes,
        content = content
    )
}
