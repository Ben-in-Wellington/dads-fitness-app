package com.di.fitric.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * A custom, non-dynamic dark color scheme designed for high contrast and clarity.
 * This is the only color scheme the application will use.
 *
 * It maps our semantic colors from Color.kt (e.g., ActionGreen) to the Material3
 * color roles that Composables like Button use by default. This makes applying
 * colors more consistent.
 */
private val AppDarkColorScheme = darkColorScheme(
    // Core App Colors
    background = AppDarkBackground,
    surface = AppDarkSurface,
    onBackground = AppTextPrimary,
    onSurface = AppTextPrimary,

    // Default Action Color (used for the Trainer button)
    primary = ActionBlue,
    onPrimary = AppTextPrimary,

    // Custom Mapped Action Colors
    // We map START to 'tertiary' and STOP to 'error' as these are distinct
    // roles we can easily reference in our Button components.
    tertiary = ActionGreen,
    onTertiary = AppTextPrimary,
    error = ActionRed,
    onError = AppTextPrimary
)

@Composable
fun FitRicTheme(
    content: @Composable () -> Unit
) {
    // The app is always in a dark theme, so we directly assign our custom dark scheme.
    // All logic for switching between light/dark or using dynamic colors is removed.
    val colorScheme = AppDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            // This side-effect ensures the system UI (like the top status bar)
            // matches our app's dark background for a seamless look.
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()

            // This tells the system that the status bar icons (time, battery) should be light.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography, // Use our custom, large-font typography
        content = content
    )

}