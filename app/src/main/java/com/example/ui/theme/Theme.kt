package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val VozSyncDarkColorScheme = darkColorScheme(
    primary = StudioCyan,
    onPrimary = StudioOnCyan,
    primaryContainer = StudioCyanContainer,
    onPrimaryContainer = StudioCyan,
    secondary = StudioAmber,
    onSecondary = StudioOnAmber,
    secondaryContainer = StudioAmberContainer,
    onSecondaryContainer = StudioAmber,
    tertiary = StudioEmerald,
    onTertiary = StudioOnCyan,
    tertiaryContainer = StudioEmeraldContainer,
    onTertiaryContainer = StudioEmerald,
    background = StudioObsidian,
    onBackground = StudioTextPrimary,
    surface = StudioSurface,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = StudioTextSecondary,
    surfaceContainerLowest = StudioObsidian,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceElevated,
    surfaceContainerHigh = StudioSurfaceBright,
    surfaceContainerHighest = StudioOutline,
    outline = StudioOutline,
    outlineVariant = StudioOutlineSubtle,
    error = StudioError,
    onError = StudioObsidian,
    errorContainer = StudioErrorContainer,
    onErrorContainer = StudioError
)

val VozSyncShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VozSyncTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VozSyncDarkColorScheme,
        typography = VozSyncTypography,
        shapes = VozSyncShapes,
        content = content
    )
}
