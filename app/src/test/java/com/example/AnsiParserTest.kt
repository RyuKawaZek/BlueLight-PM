package com.example

import com.example.console.AnsiParser
import com.example.ui.theme.ConsoleThemeStyle
import com.example.ui.theme.toTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnsiParserTest {

    @Test
    fun stripAnsi_removesStandardAndOscSequences() {
        val raw = "\u001B]0;PocketMine\u0007\u001B[1;33m[@] \u001B[31mSERVER IS RUNNING\u001B[0m"
        assertEquals("[@] SERVER IS RUNNING", AnsiParser.stripAnsi(raw))
    }

    @Test
    fun parse_handlesStandardSgrAnd256ColorsWithoutRawCodes() {
        val darkTokens = ConsoleThemeStyle.DARK_TERMINAL.toTokens()
        val raw = "\u001B[1;33m[@] \u001B[38;5;203mWarning\u001B[0m"
        val parsed = AnsiParser.parse(raw, ansiEnabled = true, tokens = darkTokens)
        assertEquals("[@] Warning", parsed.text)
        assertTrue(parsed.spanStyles.isNotEmpty())
    }

    @Test
    fun parse_adaptsWhiteTextOnLightConsoleTheme() {
        val lightTokens = ConsoleThemeStyle.HIGH_CONTRAST_LIGHT.toTokens()
        val raw = "\u001B[38;5;231mWhite text in log\u001B[0m"
        val parsed = AnsiParser.parse(raw, ansiEnabled = true, tokens = lightTokens)
        assertEquals("White text in log", parsed.text)
        val spanColor = parsed.spanStyles.first().item.color
        // Must NOT be white on light background
        assertNotEquals(androidx.compose.ui.graphics.Color.White, spanColor)
        assertTrue(spanColor.red < 0.5f && spanColor.green < 0.5f && spanColor.blue < 0.5f)
    }
}
