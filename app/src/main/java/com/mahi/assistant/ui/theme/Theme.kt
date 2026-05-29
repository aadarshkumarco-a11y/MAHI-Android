package com.mahi.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * MAHI custom dark color scheme — Iron Man / JARVIS futuristic palette.
 * Uses our custom NeonCyan/ElectricPurple/DeepSpaceBlack colors throughout
 * ALL Material3 components (TopAppBar, TextField, Switch, etc.).
 */
private val MahiDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = DeepSpaceBlack,
    primaryContainer = NeonCyanDim,
    onPrimaryContainer = TextPrimary,
    secondary = ElectricPurple,
    onSecondary = DeepSpaceBlack,
    secondaryContainer = ElectricPurpleDim,
    onSecondaryContainer = TextPrimary,
    tertiary = NeonGreen,
    onTertiary = DeepSpaceBlack,
    tertiaryContainer = NeonGreenDim,
    onTertiaryContainer = TextPrimary,
    error = ErrorRed,
    onError = TextPrimary,
    errorContainer = ErrorRedDim,
    onErrorContainer = TextPrimary,
    background = DeepSpaceBlack,
    onBackground = TextPrimary,
    surface = DarkPanel,
    onSurface = TextPrimary,
    surfaceVariant = DarkPanelLight,
    onSurfaceVariant = TextSecondary,
    outline = DarkPanelBorder,
    outlineVariant = TextTertiary,
    inverseSurface = TextPrimary,
    inverseOnSurface = DeepSpaceBlack,
    inversePrimary = NeonCyanDim,
)

/**
 * MAHI theme — always uses the dark JARVIS color scheme.
 * Dynamic colors are DISABLED to ensure consistent MAHI branding.
 */
@Composable
fun MAHITheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MahiDarkColorScheme,
        typography = MahiTypography,
        content = content
    )
}
