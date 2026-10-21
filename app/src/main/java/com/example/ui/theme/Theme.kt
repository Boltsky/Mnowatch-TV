package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MunowatchDarkColorScheme = darkColorScheme(
    primary = MunowatchGreen,
    onPrimary = Color.White,
    primaryContainer = MunowatchGreenDark,
    onPrimaryContainer = Color.White,
    secondary = MunowatchGreenLight,
    onSecondary = MunowatchBackground,
    background = MunowatchBackground,
    onBackground = MunowatchTextPrimary,
    surface = MunowatchSurface,
    onSurface = MunowatchTextPrimary,
    surfaceVariant = MunowatchSurfaceElevated,
    onSurfaceVariant = MunowatchTextSecondary,
    error = MunowatchError,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Always enforce consistent cinematic TV palette
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MunowatchDarkColorScheme,
        typography = Typography,
        content = content
    )
}
