package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.data.settings.ThemePreference

private val CarbonDarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = CyanOnPrimary,
    primaryContainer = CyanContainer,
    onPrimaryContainer = CyanOnContainer,
    secondary = TealSecondary,
    onSecondary = TealOnSecondary,
    secondaryContainer = TealContainer,
    onSecondaryContainer = TealOnContainer,
    tertiary = AmberTertiary,
    onTertiary = AmberOnTertiary,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberOnContainer,
    background = CarbonBg,
    onBackground = CarbonTextPrimary,
    surface = CarbonSurface,
    onSurface = CarbonTextPrimary,
    surfaceVariant = CarbonSurfaceVariant,
    onSurfaceVariant = CarbonTextSecondary,
    surfaceContainerLowest = CarbonBg,
    surfaceContainerLow = CarbonSurface,
    surfaceContainer = CarbonSurfaceVariant,
    surfaceContainerHigh = CarbonSurfaceElevated,
    outline = CarbonOutline,
    outlineVariant = CarbonOutlineSubtle,
    error = CarbonError,
    onError = CarbonOnError,
    errorContainer = CarbonErrorContainer,
    onErrorContainer = CarbonOnErrorContainer
)

private val CarbonLightColorScheme = lightColorScheme(
    primary = LightCyanPrimary,
    onPrimary = LightCyanOnPrimary,
    primaryContainer = LightCyanContainer,
    onPrimaryContainer = LightCyanOnContainer,
    secondary = LightTealSecondary,
    onSecondary = LightTealOnSecondary,
    secondaryContainer = LightTealContainer,
    onSecondaryContainer = LightTealOnContainer,
    tertiary = LightAmberTertiary,
    onTertiary = LightAmberOnTertiary,
    tertiaryContainer = LightAmberContainer,
    onTertiaryContainer = LightAmberOnContainer,
    background = PlatinumBg,
    onBackground = LightTextPrimary,
    surface = PlatinumSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = PlatinumSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = PlatinumOutline,
    outlineVariant = PlatinumSurfaceVariant
)

val CarbonShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CarbonProTheme(
    themePreference: ThemePreference = ThemePreference.DARK,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themePreference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDarkTheme) CarbonDarkColorScheme else CarbonLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CarbonTypography,
        shapes = CarbonShapes,
        content = content
    )
}
