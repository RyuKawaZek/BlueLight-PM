package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// BlueLight Brand Palette — Electric Sapphire & Cyan
val Sapphire50 = Color(0xFFEFF6FF)
val Sapphire100 = Color(0xFFDBEAFE)
val Sapphire200 = Color(0xFFBFDBFE)
val Sapphire400 = Color(0xFF60A5FA)
val Sapphire500 = Color(0xFF3B82F6)
val Sapphire600 = Color(0xFF2563EB)
val Sapphire700 = Color(0xFF1D4ED8)
val Sapphire900 = Color(0xFF0F2557)

val Cyan400 = Color(0xFF22D3EE)
val Cyan500 = Color(0xFF06B6D4)
val Cyan700 = Color(0xFF0E7490)
val Cyan900 = Color(0xFF083344)

val Emerald400 = Color(0xFF34D399)
val Emerald600 = Color(0xFF059669)
val Amber400 = Color(0xFFFBBF24)
val Amber600 = Color(0xFFD97706)
val Rose400 = Color(0xFFFB7185)
val Rose600 = Color(0xFFE11D48)

// Light Theme Surfaces (tonal M3 hierarchy)
val LightBackground = Color(0xFFF6F7FA)
val LightSurface = Color(0xFFF6F7FA)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF0F2F7)
val LightSurfaceContainer = Color(0xFFEAEDF4)
val LightSurfaceContainerHigh = Color(0xFFE3E7F0)
val LightSurfaceContainerHighest = Color(0xFFDCE1EC)
val LightSurfaceVariant = Color(0xFFE3E7F0)
val LightOnBackground = Color(0xFF111827)
val LightOnSurface = Color(0xFF111827)
val LightOnSurfaceVariant = Color(0xFF4B5563)
val LightOutline = Color(0xFFC7CFD9)
val LightOutlineVariant = Color(0xFFE2E6EE)

// Dark Theme Surfaces (Metrolist-inspired elevated charcoal)
val DarkBackground = Color(0xFF0F1117)
val DarkSurface = Color(0xFF0F1117)
val DarkSurfaceContainerLowest = Color(0xFF0A0C10)
val DarkSurfaceContainerLow = Color(0xFF161922)
val DarkSurfaceContainer = Color(0xFF1C202B)
val DarkSurfaceContainerHigh = Color(0xFF242937)
val DarkSurfaceContainerHighest = Color(0xFF2D3344)
val DarkSurfaceVariant = Color(0xFF232836)
val DarkOnBackground = Color(0xFFF1F5F9)
val DarkOnSurface = Color(0xFFF1F5F9)
val DarkOnSurfaceVariant = Color(0xFF9CA3AF)
val DarkOutline = Color(0xFF394154)
val DarkOutlineVariant = Color(0xFF262C3A)

enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light")
}

enum class AccentPreset(
    val label: String,
    val swatchColor: Color,
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightOnPrimaryContainer: Color,
    val lightSecondary: Color,
    val lightSecondaryContainer: Color,
    val lightOnSecondaryContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color,
    val darkSecondary: Color,
    val darkSecondaryContainer: Color,
    val darkOnSecondaryContainer: Color
) {
    SAPPHIRE(
        label = "Sapphire",
        swatchColor = Color(0xFF3B82F6),
        lightPrimary = Color(0xFF2563EB),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFDBEAFE),
        lightOnPrimaryContainer = Color(0xFF0B2559),
        lightSecondary = Color(0xFF0E7490),
        lightSecondaryContainer = Color(0xFFCFFAFE),
        lightOnSecondaryContainer = Color(0xFF083344),
        darkPrimary = Color(0xFF7CB3FF),
        darkOnPrimary = Color(0xFF052454),
        darkPrimaryContainer = Color(0xFF183B7A),
        darkOnPrimaryContainer = Color(0xFFDBEAFE),
        darkSecondary = Color(0xFF38BDF8),
        darkSecondaryContainer = Color(0xFF0E3A53),
        darkOnSecondaryContainer = Color(0xFFBAE6FD)
    ),
    CYAN(
        label = "Cyan",
        swatchColor = Color(0xFF06B6D4),
        lightPrimary = Color(0xFF0891B2),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFCFFAFE),
        lightOnPrimaryContainer = Color(0xFF063240),
        lightSecondary = Color(0xFF2563EB),
        lightSecondaryContainer = Color(0xFFDBEAFE),
        lightOnSecondaryContainer = Color(0xFF0F2557),
        darkPrimary = Color(0xFF38DFEF),
        darkOnPrimary = Color(0xFF00363F),
        darkPrimaryContainer = Color(0xFF084C5C),
        darkOnPrimaryContainer = Color(0xFFCFFAFE),
        darkSecondary = Color(0xFF60A5FA),
        darkSecondaryContainer = Color(0xFF173566),
        darkOnSecondaryContainer = Color(0xFFDBEAFE)
    ),
    EMERALD(
        label = "Emerald",
        swatchColor = Color(0xFF10B981),
        lightPrimary = Color(0xFF059669),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFD1FAE5),
        lightOnPrimaryContainer = Color(0xFF022C22),
        lightSecondary = Color(0xFF0D9488),
        lightSecondaryContainer = Color(0xFFCCFBF1),
        lightOnSecondaryContainer = Color(0xFF042F2E),
        darkPrimary = Color(0xFF4ADEA0),
        darkOnPrimary = Color(0xFF003824),
        darkPrimaryContainer = Color(0xFF065239),
        darkOnPrimaryContainer = Color(0xFFD1FAE5),
        darkSecondary = Color(0xFF2DD4BF),
        darkSecondaryContainer = Color(0xFF114440),
        darkOnSecondaryContainer = Color(0xFFCCFBF1)
    ),
    VIOLET(
        label = "Violet",
        swatchColor = Color(0xFF8B5CF6),
        lightPrimary = Color(0xFF6D28D9),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFEDE9FE),
        lightOnPrimaryContainer = Color(0xFF2E1065),
        lightSecondary = Color(0xFF4F46E5),
        lightSecondaryContainer = Color(0xFFE0E7FF),
        lightOnSecondaryContainer = Color(0xFF1E1B4B),
        darkPrimary = Color(0xFFC4B5FD),
        darkOnPrimary = Color(0xFF28105C),
        darkPrimaryContainer = Color(0xFF432687),
        darkOnPrimaryContainer = Color(0xFFEDE9FE),
        darkSecondary = Color(0xFFA5B4FC),
        darkSecondaryContainer = Color(0xFF292B68),
        darkOnSecondaryContainer = Color(0xFFE0E7FF)
    ),
    AMBER(
        label = "Amber",
        swatchColor = Color(0xFFF59E0B),
        lightPrimary = Color(0xFFB45309),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFFEF3C7),
        lightOnPrimaryContainer = Color(0xFF451A03),
        lightSecondary = Color(0xFFD97706),
        lightSecondaryContainer = Color(0xFFFFEDD5),
        lightOnSecondaryContainer = Color(0xFF431407),
        darkPrimary = Color(0xFFFCD34D),
        darkOnPrimary = Color(0xFF422006),
        darkPrimaryContainer = Color(0xFF663809),
        darkOnPrimaryContainer = Color(0xFFFEF3C7),
        darkSecondary = Color(0xFFFDBA74),
        darkSecondaryContainer = Color(0xFF5C2D0C),
        darkOnSecondaryContainer = Color(0xFFFFEDD5)
    )
}

enum class ConsoleThemeStyle(val label: String) {
    DARK_TERMINAL("Terminal Dark"),
    MIDNIGHT_OLED("Midnight OLED"),
    HIGH_CONTRAST_LIGHT("High-Contrast Light")
}

data class ConsoleColorTokens(
    val background: Color,
    val defaultText: Color,
    val secondaryText: Color,
    val toolbarBackground: Color,
    val toolbarContent: Color,
    val inputContainer: Color,
    val inputText: Color,
    val inputPlaceholder: Color,
    val border: Color,
    val promptAccent: Color,
    val isDark: Boolean
)

fun ConsoleThemeStyle.toTokens(): ConsoleColorTokens = when (this) {
    ConsoleThemeStyle.DARK_TERMINAL -> ConsoleColorTokens(
        background = Color(0xFF0D1117),
        defaultText = Color(0xFFE6EDF3),
        secondaryText = Color(0xFF8B949E),
        toolbarBackground = Color(0xFF161B22),
        toolbarContent = Color(0xFFF0F6FC),
        inputContainer = Color(0xFF161B22),
        inputText = Color(0xFFF0F6FC),
        inputPlaceholder = Color(0xFF7D8590),
        border = Color(0xFF30363D),
        promptAccent = Color(0xFF38BDF8),
        isDark = true
    )
    ConsoleThemeStyle.MIDNIGHT_OLED -> ConsoleColorTokens(
        background = Color(0xFF000000),
        defaultText = Color(0xFFF8FAFC),
        secondaryText = Color(0xFF94A3B8),
        toolbarBackground = Color(0xFF090D16),
        toolbarContent = Color(0xFFF8FAFC),
        inputContainer = Color(0xFF0B101B),
        inputText = Color(0xFFF8FAFC),
        inputPlaceholder = Color(0xFF64748B),
        border = Color(0xFF1E293B),
        promptAccent = Color(0xFF22D3EE),
        isDark = true
    )
    ConsoleThemeStyle.HIGH_CONTRAST_LIGHT -> ConsoleColorTokens(
        background = Color(0xFFF8FAFC),
        defaultText = Color(0xFF0F172A),
        secondaryText = Color(0xFF475569),
        toolbarBackground = Color(0xFFE2E8F0),
        toolbarContent = Color(0xFF0F172A),
        inputContainer = Color(0xFFFFFFFF),
        inputText = Color(0xFF0F172A),
        inputPlaceholder = Color(0xFF64748B),
        border = Color(0xFFCBD5E1),
        promptAccent = Color(0xFF1D4ED8),
        isDark = false
    )
}
