package com.mahi.assistant.ui.theme

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

private val MahiPrimary = Color(0xFF6750A4)
private val MahiOnPrimary = Color(0xFFFFFFFF)
private val MahiPrimaryContainer = Color(0xFFEADDFF)
private val MahiOnPrimaryContainer = Color(0xFF21005D)

private val MahiSecondary = Color(0xFF625B71)
private val MahiOnSecondary = Color(0xFFFFFFFF)
private val MahiSecondaryContainer = Color(0xFFE8DEF8)
private val MahiOnSecondaryContainer = Color(0xFF1D192B)

private val MahiTertiary = Color(0xFF7D5260)
private val MahiOnTertiary = Color(0xFFFFFFFF)
private val MahiTertiaryContainer = Color(0xFFFFD8E4)
private val MahiOnTertiaryContainer = Color(0xFF31111D)

private val MahiError = Color(0xFFB3261E)
private val MahiOnError = Color(0xFFFFFFFF)
private val MahiErrorContainer = Color(0xFFF9DEDC)
private val MahiOnErrorContainer = Color(0xFF410E0B)

private val MahiBackground = Color(0xFFFFFBFE)
private val MahiOnBackground = Color(0xFF1C1B1F)
private val MahiSurface = Color(0xFFFFFBFE)
private val MahiOnSurface = Color(0xFF1C1B1F)

private val MahiDarkBackground = Color(0xFF1C1B1F)
private val MahiDarkOnBackground = Color(0xFFE6E1E5)
private val MahiDarkSurface = Color(0xFF1C1B1F)
private val MahiDarkOnSurface = Color(0xFFE6E1E5)

private val LightColorScheme = lightColorScheme(
    primary = MahiPrimary,
    onPrimary = MahiOnPrimary,
    primaryContainer = MahiPrimaryContainer,
    onPrimaryContainer = MahiOnPrimaryContainer,
    secondary = MahiSecondary,
    onSecondary = MahiOnSecondary,
    secondaryContainer = MahiSecondaryContainer,
    onSecondaryContainer = MahiOnSecondaryContainer,
    tertiary = MahiTertiary,
    onTertiary = MahiOnTertiary,
    tertiaryContainer = MahiTertiaryContainer,
    onTertiaryContainer = MahiOnTertiaryContainer,
    error = MahiError,
    onError = MahiOnError,
    errorContainer = MahiErrorContainer,
    onErrorContainer = MahiOnErrorContainer,
    background = MahiBackground,
    onBackground = MahiOnBackground,
    surface = MahiSurface,
    onSurface = MahiOnSurface,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = MahiDarkBackground,
    onBackground = MahiDarkOnBackground,
    surface = MahiDarkSurface,
    onSurface = MahiDarkOnSurface,
)

@Composable
fun MAHITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
