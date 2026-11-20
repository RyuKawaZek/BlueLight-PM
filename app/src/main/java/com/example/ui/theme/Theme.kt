package com.example.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private fun buildDarkColorScheme(accent: AccentPreset): ColorScheme = darkColorScheme(
    primary = accent.darkPrimary,
    onPrimary = accent.darkOnPrimary,
    primaryContainer = accent.darkPrimaryContainer,
    onPrimaryContainer = accent.darkOnPrimaryContainer,
    secondary = accent.darkSecondary,
    onSecondary = Color(0xFF03232C),
    secondaryContainer = accent.darkSecondaryContainer,
    onSecondaryContainer = accent.darkOnSecondaryContainer,
    tertiary = Emerald400,
    onTertiary = Color(0xFF022C22),
    tertiaryContainer = Color(0xFF064E3B),
    onTertiaryContainer = Color(0xFFD1FAE5),
    error = Rose400,
    onError = Color(0xFF4C0519),
    errorContainer = Color(0xFF5F1124),
    onErrorContainer = Color(0xFFFFE4E6),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceDim = Color(0xFF0B0D12),
    surfaceBright = Color(0xFF282E3D),
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant
)

private fun buildLightColorScheme(accent: AccentPreset): ColorScheme = lightColorScheme(
    primary = accent.lightPrimary,
    onPrimary = accent.lightOnPrimary,
    primaryContainer = accent.lightPrimaryContainer,
    onPrimaryContainer = accent.lightOnPrimaryContainer,
    secondary = accent.lightSecondary,
    onSecondary = Color.White,
    secondaryContainer = accent.lightSecondaryContainer,
    onSecondaryContainer = accent.lightOnSecondaryContainer,
    tertiary = Emerald600,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF064E3B),
    error = Rose600,
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF881337),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceDim = Color(0xFFD9DFE9),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant
)

private fun ColorScheme.withPureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF14161D),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF07080B),
    surfaceContainer = Color(0xFF0E1015),
    surfaceContainerHigh = Color(0xFF161922),
    surfaceContainerHighest = Color(0xFF1F2430),
    surfaceVariant = Color(0xFF12151C),
    outlineVariant = Color(0xFF1E222D)
)

@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    val spec = spring<Color>(stiffness = Spring.StiffnessMediumLow)
    return target.copy(
        primary = animateColorAsState(target.primary, spec, label = "primary").value,
        onPrimary = animateColorAsState(target.onPrimary, spec, label = "onPrimary").value,
        primaryContainer = animateColorAsState(target.primaryContainer, spec, label = "primaryContainer").value,
        onPrimaryContainer = animateColorAsState(target.onPrimaryContainer, spec, label = "onPrimaryContainer").value,
        secondary = animateColorAsState(target.secondary, spec, label = "secondary").value,
        onSecondary = animateColorAsState(target.onSecondary, spec, label = "onSecondary").value,
        secondaryContainer = animateColorAsState(target.secondaryContainer, spec, label = "secondaryContainer").value,
        onSecondaryContainer = animateColorAsState(target.onSecondaryContainer, spec, label = "onSecondaryContainer").value,
        background = animateColorAsState(target.background, spec, label = "background").value,
        onBackground = animateColorAsState(target.onBackground, spec, label = "onBackground").value,
        surface = animateColorAsState(target.surface, spec, label = "surface").value,
        onSurface = animateColorAsState(target.onSurface, spec, label = "onSurface").value,
        surfaceVariant = animateColorAsState(target.surfaceVariant, spec, label = "surfaceVariant").value,
        onSurfaceVariant = animateColorAsState(target.onSurfaceVariant, spec, label = "onSurfaceVariant").value,
        surfaceContainerLowest = animateColorAsState(target.surfaceContainerLowest, spec, label = "scll").value,
        surfaceContainerLow = animateColorAsState(target.surfaceContainerLow, spec, label = "scl").value,
        surfaceContainer = animateColorAsState(target.surfaceContainer, spec, label = "sc").value,
        surfaceContainerHigh = animateColorAsState(target.surfaceContainerHigh, spec, label = "sch").value,
        surfaceContainerHighest = animateColorAsState(target.surfaceContainerHighest, spec, label = "schh").value
    )
}

@Composable
fun BlueLightTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    pureBlack: Boolean = false,
    dynamicColor: Boolean = false,
    accentPreset: AccentPreset = AccentPreset.SAPPHIRE,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }

    val context = LocalContext.current
    val baseColorScheme = remember(isDark, dynamicColor, accentPreset, context) {
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            isDark -> buildDarkColorScheme(accentPreset)
            else -> buildLightColorScheme(accentPreset)
        }
    }

    val resolvedScheme = remember(baseColorScheme, isDark, pureBlack) {
        if (isDark && pureBlack) baseColorScheme.withPureBlack() else baseColorScheme
    }

    val animatedScheme = animateColorScheme(resolvedScheme)

    MaterialTheme(
        colorScheme = animatedScheme,
        typography = Typography,
        shapes = BlueLightShapes,
        content = content
    )
}
