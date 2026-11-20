package com.example.console

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.example.ui.theme.ConsoleColorTokens

/**
 * Parses standard ANSI SGR escape sequences (e.g. \u001B[1;33m, \u001B[31m, \u001B[0m)
 * AND PocketMine-MP 256-color sequences (e.g. \u001B[38;5;227m) into Compose AnnotatedString.
 *
 * Automatically adapts foreground color contrast for both dark and light console themes
 * so log text is never invisible (fixing the white-on-white bug on Android 15).
 */
object AnsiParser {

    // Matches OSC sequences like \u001B]0;title\u0007
    private val OSC_REGEX = Regex("\u001B\\][^\u0007\u001B]*(\u0007|\u001B\\\\)?")

    // Matches CSI SGR or control sequences like \u001B[1;33m or \u001B[38;5;214m
    private val CSI_REGEX = Regex("\u001B\\[([0-9;]*)([A-Za-z])")

    // Also matches literal "[1;33m" if an upstream stream already stripped ESC or logged raw brackets
    private val BARE_SGR_REGEX = Regex("\u001B\\[([0-9;]*)m")

    private val pocketMine256Dark = mapOf(
        16 to Color(0xFF64748B), // Black -> legible slate on dark bg
        19 to Color(0xFF3B82F6), // Dark Blue
        34 to Color(0xFF22C55E), // Dark Green
        37 to Color(0xFF06B6D4), // Dark Aqua
        124 to Color(0xFFEF4444), // Dark Red
        127 to Color(0xFFD946EF), // Dark Purple
        214 to Color(0xFFF59E0B), // Gold / Orange
        145 to Color(0xFFCBD5E1), // Gray
        59 to Color(0xFF94A3B8),  // Dark Gray
        63 to Color(0xFF60A5FA),  // Blue
        83 to Color(0xFF4ADE80),  // Green
        87 to Color(0xFF22D3EE),  // Aqua
        203 to Color(0xFFF87171), // Red
        207 to Color(0xFFE879F9), // Light Purple
        227 to Color(0xFFFDE047), // Yellow
        231 to Color(0xFFF8FAFC)  // White
    )

    private val pocketMine256Light = mapOf(
        16 to Color(0xFF0F172A),
        19 to Color(0xFF1D4ED8),
        34 to Color(0xFF15803D),
        37 to Color(0xFF0E7490),
        124 to Color(0xFFB91C1C),
        127 to Color(0xFF7E22CE),
        214 to Color(0xFFB45309),
        145 to Color(0xFF475569),
        59 to Color(0xFF64748B),
        63 to Color(0xFF2563EB),
        83 to Color(0xFF16A34A),
        87 to Color(0xFF0891B2),
        203 to Color(0xFFDC2626),
        207 to Color(0xFFA21CAF),
        227 to Color(0xFFA16207),
        231 to Color(0xFF0F172A)
    )

    fun stripAnsi(input: String): String {
        val noOsc = OSC_REGEX.replace(input, "")
        return CSI_REGEX.replace(noOsc, "")
    }

    fun parseToSpanned(
        rawLine: String,
        ansiEnabled: Boolean,
        tokens: ConsoleColorTokens
    ): CharSequence {
        val annotated = parse(rawLine, ansiEnabled, tokens)
        val ssb = SpannableStringBuilder(annotated.text)
        for (range in annotated.spanStyles) {
            val start = range.start
            val end = range.end
            if (start >= end) continue
            val style = range.item
            if (style.color != Color.Unspecified) {
                ssb.setSpan(
                    ForegroundColorSpan(style.color.toArgb()),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            val bold = style.fontWeight == FontWeight.Bold
            val italic = style.fontStyle == FontStyle.Italic
            if (bold && italic) {
                ssb.setSpan(StyleSpan(Typeface.BOLD_ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (bold) {
                ssb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (italic) {
                ssb.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            style.textDecoration?.let { decor ->
                if (decor.contains(TextDecoration.Underline)) {
                    ssb.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (decor.contains(TextDecoration.LineThrough)) {
                    ssb.setSpan(StrikethroughSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        return ssb
    }

    fun parse(
        rawLine: String,
        ansiEnabled: Boolean,
        tokens: ConsoleColorTokens
    ): AnnotatedString {
        val cleanedOsc = OSC_REGEX.replace(rawLine, "")
        if (!ansiEnabled) {
            return AnnotatedString(stripAnsi(cleanedOsc))
        }

        return buildAnnotatedString {
            var currentColor: Color = tokens.defaultText
            var isBold = false
            var isItalic = false
            var isUnderline = false
            var isStrikethrough = false
            var lastIndex = 0

            fun appendStyled(segment: String) {
                if (segment.isEmpty()) return
                val start = length
                append(segment)
                val end = length
                val decor = when {
                    isUnderline && isStrikethrough -> TextDecoration.combine(
                        listOf(TextDecoration.Underline, TextDecoration.LineThrough)
                    )
                    isUnderline -> TextDecoration.Underline
                    isStrikethrough -> TextDecoration.LineThrough
                    else -> null
                }
                addStyle(
                    style = SpanStyle(
                        color = currentColor,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                        textDecoration = decor
                    ),
                    start = start,
                    end = end
                )
            }

            for (match in CSI_REGEX.findAll(cleanedOsc)) {
                val textBefore = cleanedOsc.substring(lastIndex, match.range.first)
                appendStyled(textBefore)
                lastIndex = match.range.last + 1

                val command = match.groupValues[2]
                if (command != "m") {
                    continue
                }

                val paramsRaw = match.groupValues[1]
                if (paramsRaw.isEmpty()) {
                    currentColor = tokens.defaultText
                    isBold = false
                    isItalic = false
                    isUnderline = false
                    isStrikethrough = false
                    continue
                }

                val codes = paramsRaw.split(';').mapNotNull { it.toIntOrNull() }
                var idx = 0
                while (idx < codes.size) {
                    when (val code = codes[idx]) {
                        0 -> {
                            currentColor = tokens.defaultText
                            isBold = false
                            isItalic = false
                            isUnderline = false
                            isStrikethrough = false
                        }
                        1 -> isBold = true
                        3 -> isItalic = true
                        4 -> isUnderline = true
                        9 -> isStrikethrough = true
                        22 -> isBold = false
                        23 -> isItalic = false
                        24 -> isUnderline = false
                        29 -> isStrikethrough = false
                        in 30..37 -> currentColor = standardColor(code - 30, bright = isBold, isDark = tokens.isDark)
                        39 -> currentColor = tokens.defaultText
                        in 90..97 -> currentColor = standardColor(code - 90, bright = true, isDark = tokens.isDark)
                        38 -> {
                            if (idx + 2 < codes.size && codes[idx + 1] == 5) {
                                val paletteIndex = codes[idx + 2]
                                currentColor = xterm256Color(paletteIndex, tokens)
                                idx += 2
                            } else if (idx + 4 < codes.size && codes[idx + 1] == 2) {
                                val r = codes[idx + 2].coerceIn(0, 255)
                                val g = codes[idx + 3].coerceIn(0, 255)
                                val b = codes[idx + 4].coerceIn(0, 255)
                                currentColor = adaptRgbForTheme(Color(r, g, b), tokens)
                                idx += 4
                            }
                        }
                    }
                    idx++
                }
            }

            if (lastIndex < cleanedOsc.length) {
                appendStyled(cleanedOsc.substring(lastIndex))
            }
        }
    }

    private fun standardColor(index: Int, bright: Boolean, isDark: Boolean): Color {
        return if (isDark) {
            when (index) {
                0 -> if (bright) Color(0xFF94A3B8) else Color(0xFF64748B) // Black/Slate
                1 -> if (bright) Color(0xFFF87171) else Color(0xFFEF4444) // Red
                2 -> if (bright) Color(0xFF4ADE80) else Color(0xFF22C55E) // Green
                3 -> if (bright) Color(0xFFFDE047) else Color(0xFFFACC15) // Yellow
                4 -> if (bright) Color(0xFF60A5FA) else Color(0xFF3B82F6) // Blue
                5 -> if (bright) Color(0xFFE879F9) else Color(0xFFD946EF) // Magenta
                6 -> if (bright) Color(0xFF22D3EE) else Color(0xFF06B6D4) // Cyan
                else -> if (bright) Color(0xFFFFFFFF) else Color(0xFFE2E8F0) // White
            }
        } else {
            when (index) {
                0 -> if (bright) Color(0xFF475569) else Color(0xFF0F172A)
                1 -> if (bright) Color(0xFFDC2626) else Color(0xFFB91C1C)
                2 -> if (bright) Color(0xFF16A34A) else Color(0xFF15803D)
                3 -> if (bright) Color(0xFFB45309) else Color(0xFF92400E)
                4 -> if (bright) Color(0xFF2563EB) else Color(0xFF1D4ED8)
                5 -> if (bright) Color(0xFFA21CAF) else Color(0xFF86198F)
                6 -> if (bright) Color(0xFF0891B2) else Color(0xFF0E7490)
                else -> Color(0xFF0F172A)
            }
        }
    }

    private fun xterm256Color(index: Int, tokens: ConsoleColorTokens): Color {
        val map = if (tokens.isDark) pocketMine256Dark else pocketMine256Light
        map[index]?.let { return it }

        val raw = when {
            index in 0..7 -> return standardColor(index, bright = false, isDark = tokens.isDark)
            index in 8..15 -> return standardColor(index - 8, bright = true, isDark = tokens.isDark)
            index in 16..231 -> {
                val c = index - 16
                val rIdx = c / 36
                val gIdx = (c % 36) / 6
                val bIdx = c % 6
                val levels = intArrayOf(0, 95, 135, 175, 215, 255)
                Color(levels[rIdx], levels[gIdx], levels[bIdx])
            }
            index in 232..255 -> {
                val gray = 8 + (index - 232) * 10
                Color(gray, gray, gray)
            }
            else -> tokens.defaultText
        }
        return adaptRgbForTheme(raw, tokens)
    }

    private fun adaptRgbForTheme(color: Color, tokens: ConsoleColorTokens): Color {
        val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
        return if (tokens.isDark && luminance < 0.25f) {
            // Boost overly dark colors on dark console background
            Color(
                red = (color.red + 0.35f).coerceAtMost(1f),
                green = (color.green + 0.35f).coerceAtMost(1f),
                blue = (color.blue + 0.35f).coerceAtMost(1f)
            )
        } else if (!tokens.isDark && luminance > 0.72f) {
            // Darken bright/white colors on light console background
            Color(
                red = (color.red * 0.45f).coerceIn(0f, 1f),
                green = (color.green * 0.45f).coerceIn(0f, 1f),
                blue = (color.blue * 0.45f).coerceIn(0f, 1f)
            )
        } else {
            color
        }
    }
}
