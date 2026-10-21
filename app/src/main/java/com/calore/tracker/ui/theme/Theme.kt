package com.calore.tracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6B3A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB0F2B5),
    onPrimaryContainer = Color(0xFF00210A),
    secondary = Color(0xFF6B5E10),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF5E388),
    onSecondaryContainer = Color(0xFF211B00),
    tertiary = Color(0xFF39656C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCEBF3),
    onTertiaryContainer = Color(0xFF001F24),
    background = Color(0xFFF7FBF2),
    onBackground = Color(0xFF181D17),
    surface = Color(0xFFF7FBF2),
    onSurface = Color(0xFF181D17),
    surfaceVariant = Color(0xFFDDE5D9),
    onSurfaceVariant = Color(0xFF414941),
    outline = Color(0xFF717970),
    outlineVariant = Color(0xFFC1C9BE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF95D59B),
    onPrimary = Color(0xFF003915),
    primaryContainer = Color(0xFF135225),
    onPrimaryContainer = Color(0xFFB0F2B5),
    secondary = Color(0xFFD8C76F),
    onSecondary = Color(0xFF383000),
    secondaryContainer = Color(0xFF524600),
    onSecondaryContainer = Color(0xFFF5E388),
    tertiary = Color(0xFFA1CED6),
    onTertiary = Color(0xFF00363D),
    tertiaryContainer = Color(0xFF1F4D54),
    onTertiaryContainer = Color(0xFFBCEBF3),
    background = Color(0xFF10140F),
    onBackground = Color(0xFFE0E4DB),
    surface = Color(0xFF10140F),
    onSurface = Color(0xFFE0E4DB),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9BE),
    outline = Color(0xFF8B9389),
    outlineVariant = Color(0xFF414941),
)

/** Uses the wallpaper-based palette on Android 12+, and the app's own green palette before that. */
@Composable
fun CaloreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
