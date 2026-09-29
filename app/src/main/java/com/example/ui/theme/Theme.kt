package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SasukeXDarkColorScheme = darkColorScheme(
    primary = AccentIndigo,
    onPrimary = TextPrimary,
    primaryContainer = SurfaceContainerHighDark,
    onPrimaryContainer = AccentIndigoLight,
    secondary = AccentCyan,
    onSecondary = ObsidianBg,
    secondaryContainer = SurfaceContainerDark,
    onSecondaryContainer = AccentCyan,
    tertiary = AccentPurple,
    onTertiary = TextPrimary,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceContainerDark,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderMedium,
    error = ErrorRed,
    errorContainer = ErrorContainer,
    onError = TextPrimary,
    onErrorContainer = ErrorRed
)

@Composable
fun SasukeXTheme(
    darkTheme: Boolean = true, // Dark-first interface requested
    content: @Composable () -> Unit
) {
    val colorScheme = SasukeXDarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = ObsidianBg.toArgb()
            window.navigationBarColor = ObsidianBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backward compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    SasukeXTheme(content = content)
}
