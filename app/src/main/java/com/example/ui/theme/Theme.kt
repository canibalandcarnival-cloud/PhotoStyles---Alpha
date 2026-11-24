package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PhotoStylesDarkColorScheme = darkColorScheme(
    primary = AppleYellow,
    onPrimary = CameraBlack,
    secondary = CoolCyan,
    onSecondary = CameraBlack,
    tertiary = WarmAmber,
    onTertiary = CameraBlack,
    background = CameraBlack,
    onBackground = TextPrimary,
    surface = CameraSurface,
    onSurface = TextPrimary,
    surfaceVariant = CameraSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = CameraHairline,
    error = DestructiveRed,
    onError = TextPrimary
)

@Composable
fun PhotoStylesTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PhotoStylesDarkColorScheme,
        typography = PhotoStylesTypography,
        content = content
    )
}
