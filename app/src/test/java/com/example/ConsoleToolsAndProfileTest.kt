package com.example

import com.example.console.ConsoleLogLevel
import com.example.console.ConsoleTools
import com.example.server.BlueLightPreferencesCodec
import com.example.server.ConsoleLine
import com.example.server.OverlayMacro
import com.example.server.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConsoleToolsAndProfileTest {

    @Test
    fun classifyLogLine_identifiesErrorWarnChatAndInfo() {
        val errorLine = "\u001B[1;31m[12:00:00] [Server thread/ERROR]: Failed to bind port\u001B[0m"
        val warnLine = "\u001B[1;33m[12:00:01] [Server thread/WARNING]: Low memory\u001B[0m"
        val chatLine = "[12:00:02] [Server thread/INFO]: <Steve> hello world"
        val joinLine = "[12:00:03] [Server thread/INFO]: Alex joined the game"
        val infoLine = "[12:00:04] [Server thread/INFO]: Done (1.23s)! For help, type \"help\""

        assertEquals(ConsoleLogLevel.ERROR, ConsoleTools.classifyLogLine(errorLine))
        assertEquals(ConsoleLogLevel.WARN, ConsoleTools.classifyLogLine(warnLine))
        assertEquals(ConsoleLogLevel.CHAT, ConsoleTools.classifyLogLine(chatLine))
        assertEquals(ConsoleLogLevel.CHAT, ConsoleTools.classifyLogLine(joinLine))
        assertEquals(ConsoleLogLevel.INFO, ConsoleTools.classifyLogLine(infoLine))
    }

    @Test
    fun filterConsoleLines_appliesLevelAndKeywordSearch() {
        val lines = listOf(
            ConsoleLine(1L, "[Server thread/INFO]: Starting server"),
            ConsoleLine(2L, "[Server thread/WARNING]: Plugin API outdated"),
            ConsoleLine(3L, "[Server thread/ERROR]: Fatal crash in PluginA"),
            ConsoleLine(4L, "[Server thread/INFO]: <Player1> plugin broken?")
        )

        val errorsOnly = ConsoleTools.filterConsoleLines(lines, ConsoleLogLevel.ERROR, "")
        assertEquals(1, errorsOnly.size)
        assertEquals(3L, errorsOnly.first().id)

        val searchAll = ConsoleTools.filterConsoleLines(lines, ConsoleLogLevel.ALL, "plugin")
        assertEquals(3, searchAll.size)

        val searchChat = ConsoleTools.filterConsoleLines(lines, ConsoleLogLevel.CHAT, "plugin")
        assertEquals(1, searchChat.size)
        assertEquals(4L, searchChat.first().id)
    }

    @Test
    fun suggestCommands_handlesSlashPrefixAndHistory() {
        val slashSuggestions = ConsoleTools.suggestCommands("/", listOf("say hello"))
        assertTrue(slashSuggestions.isNotEmpty())
        assertTrue(slashSuggestions.any { it.command == "say hello" && it.fromHistory })

        val gamemodeSuggestions = ConsoleTools.suggestCommands("/gam", emptyList())
        assertTrue(gamemodeSuggestions.any { it.command == "gamemode" })

        val argHint = ConsoleTools.suggestCommands("tp Steve", emptyList())
        assertTrue(argHint.any { it.command == "tp" && it.syntax.contains("<player>") })
    }

    @Test
    fun preferencesCodec_roundTripsProfilesAndMacros() {
        val custom = ServerProfile(
            id = "profile_1",
            name = "Survival SMP",
            directoryName = "survival-smp",
            isDefault = false,
            createdAtMs = 123456789L
        )
        val encodedProfiles = BlueLightPreferencesCodec.encodeProfiles(
            listOf(ServerProfile.defaultProfile(), custom)
        )
        val decodedProfiles = BlueLightPreferencesCodec.decodeProfiles(encodedProfiles)
        assertEquals(2, decodedProfiles.size)
        assertEquals(ServerProfile.DEFAULT_PROFILE_ID, decodedProfiles[0].id)
        assertEquals("Survival SMP", decodedProfiles[1].name)
        assertEquals("survival-smp", decodedProfiles[1].directoryName)

        val macros = listOf(
            OverlayMacro("m1", "/list", "list"),
            OverlayMacro("m2", "Day", "time set day")
        )
        val encodedMacros = BlueLightPreferencesCodec.encodeOverlayMacros(macros)
        val decodedMacros = BlueLightPreferencesCodec.decodeOverlayMacros(encodedMacros)
        assertEquals(macros, decodedMacros)
    }

    @Test
    fun sanitizeSlug_producesSafeDirectoryNames() {
        assertEquals("survival-smp", ServerProfile.sanitizeSlug("  Survival SMP! "))
        assertEquals("dev-test", ServerProfile.sanitizeSlug("Dev / Test"))
        assertEquals("server", ServerProfile.sanitizeSlug("///..."))
    }
}
