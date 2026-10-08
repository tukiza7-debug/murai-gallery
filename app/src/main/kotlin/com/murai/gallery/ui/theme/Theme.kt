package com.murai.gallery.ui.theme

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

/** Theme mode persisted in settings. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val LightScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBFE8E2),
    onPrimaryContainer = Color(0xFF043F3A),
    secondary = AmberDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3B3),
    onSecondaryContainer = Color(0xFF3E2B00),
    tertiary = Ink,
    onTertiary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperDim,
    onSurfaceVariant = Color(0xFF454A52),
    outline = Mist,
    error = DangerRed,
    surfaceContainer = Color(0xFFF1EEE7),
    surfaceContainerHigh = Color(0xFFE9E6DF)
)

private val DarkScheme = darkColorScheme(
    primary = TealSoft,
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF0F5B54),
    onPrimaryContainer = Color(0xFFBFE8E2),
    secondary = Amber,
    onSecondary = Color(0xFF3E2B00),
    secondaryContainer = Color(0xFF57400C),
    onSecondaryContainer = Color(0xFFFFE3B3),
    tertiary = Paper,
    onTertiary = Ink,
    background = DarkSurface,
    onBackground = Paper,
    surface = DarkSurface,
    onSurface = Paper,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = Color(0xFFBFC7CF),
    outline = Color(0xFF6C7681),
    error = Color(0xFFF2B8B5),
    surfaceContainer = Color(0xFF182129),
    surfaceContainerHigh = Color(0xFF202A33)
)

private val AmoledScheme = DarkScheme.copy(
    background = AmoledBlack,
    surface = AmoledBlack,
    surfaceContainer = AmoledSurface,
    surfaceContainerHigh = Color(0xFF111111),
    surfaceVariant = AmoledSurface,
    onSurfaceVariant = Color(0xFFB5BDC5)
)

@Composable
fun MuraiTheme(
    themeMode: ThemeMode,
    amoled: Boolean,
    dynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val base = when {
        dark && amoled -> AmoledScheme
        dark -> DarkScheme
        else -> LightScheme
    }
    val scheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        if (dark && amoled) dynamic.copy(
            background = AmoledBlack,
            surface = AmoledBlack,
            surfaceContainer = AmoledSurface,
            surfaceVariant = AmoledSurface
        ) else dynamic
    } else base
    MaterialTheme(
        colorScheme = scheme,
        typography = MuraiTypography,
        content = content
    )
}
