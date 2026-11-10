package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import java.util.Calendar

val LocalThemeSetting = compositionLocalOf { "AUTO" }
val LocalOnThemeChange = compositionLocalOf<(String) -> Unit> { {} }
val LocalIsDarkTheme = compositionLocalOf { false }

/**
 * Determines whether dark mode should be enabled in AUTO mode.
 * - From morning 6 AM to 6 PM (06:00:00 to 17:59:59): Light mode (returns false)
 * - From 6 PM to 6 AM (18:00:00 to 05:59:59): Dark mode (returns true)
 */
fun isAutoDarkTheme(hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): Boolean {
    return hour < 6 || hour >= 18
}

fun isAutoDarkTheme(time: java.time.LocalTime): Boolean {
    return isAutoDarkTheme(time.hour)
}

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF3B82F6), // Vibrant accessible blue
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3A8A), // Deep navy blue pill
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF0C4A6E),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Amber400,
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Amber100,
    background = Color(0xFF090E17),
    onBackground = TextPrimaryDark,
    surface = Color(0xFF131C2B),
    onSurface = TextPrimaryDark,
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = TextSecondaryDark,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1E40AF), // Deep rich royal blue
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE), // Soft bright blue pill
    onPrimaryContainer = Color(0xFF1E40AF),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0369A1),
    tertiary = Amber500,
    onTertiary = Color.White,
    tertiaryContainer = Amber100,
    onTertiaryContainer = AmberDark,
    background = Color(0xFFF3F5F9),
    onBackground = TextPrimaryLight,
    surface = Color(0xFFFFFFFF),
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = TextSecondaryLight,
    error = ExpenseRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val targetColors = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                // Light theme uses dark status bar icons; dark theme uses light status bar icons
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
                val targetBg = if (darkTheme) android.graphics.Color.parseColor("#090E17") else android.graphics.Color.parseColor("#F3F5F9")
                window.decorView.setBackgroundColor(targetBg)
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(targetBg))
                window.statusBarColor = targetBg
                window.navigationBarColor = targetBg
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = targetColors,
            typography = Typography,
            content = content
        )
    }
}
