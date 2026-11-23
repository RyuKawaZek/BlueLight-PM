package com.example.console

import com.example.server.ConsoleLine
import java.util.Locale

enum class ConsoleLogLevel(val label: String) {
    ALL("All"),
    INFO("Info"),
    WARN("Warn"),
    ERROR("Error"),
    CHAT("Chat")
}

data class CommandSuggestion(
    val command: String,
    val syntax: String,
    val description: String,
    val takesArgs: Boolean = true,
    val fromHistory: Boolean = false
)

object ConsoleTools {

    private val CHAT_ANGLE_REGEX = Regex("]:\\s*<[^>]+>\\s+")
    private val CHAT_SAY_REGEX = Regex("\\[(Server|Rcon|Broadcast)]\\s+", RegexOption.IGNORE_CASE)
    private val PLAYER_EVENT_REGEX = Regex("\\b(joined the game|left the game|logged in with entity id|logged out due to)\\b", RegexOption.IGNORE_CASE)

    val BUILTIN_COMMANDS: List<CommandSuggestion> = listOf(
        CommandSuggestion("help", "/help [page|command]", "List available server commands", takesArgs = false),
        CommandSuggestion("list", "/list", "Show online players", takesArgs = false),
        CommandSuggestion("plugins", "/plugins", "List installed plugins", takesArgs = false),
        CommandSuggestion("version", "/version", "Show BlueLight / PocketMine version", takesArgs = false),
        CommandSuggestion("status", "/status", "Show TPS, players, and memory usage", takesArgs = false),
        CommandSuggestion("stop", "/stop", "Gracefully save worlds and stop server", takesArgs = false),
        CommandSuggestion("save-all", "/save-all", "Force save all loaded worlds", takesArgs = false),
        CommandSuggestion("say", "/say <message>", "Broadcast message to all players", takesArgs = true),
        CommandSuggestion("tell", "/tell <player> <message>", "Send private message to player", takesArgs = true),
        CommandSuggestion("gamemode", "/gamemode <0|1|2|3|s|c> [player]", "Change player gamemode", takesArgs = true),
        CommandSuggestion("tp", "/tp <player> <target|x y z>", "Teleport player or coordinates", takesArgs = true),
        CommandSuggestion("give", "/give <player> <item> [amount]", "Give item to a player", takesArgs = true),
        CommandSuggestion("op", "/op <player>", "Grant operator permissions", takesArgs = true),
        CommandSuggestion("deop", "/deop <player>", "Revoke operator permissions", takesArgs = true),
        CommandSuggestion("kick", "/kick <player> [reason]", "Disconnect a player", takesArgs = true),
        CommandSuggestion("ban", "/ban <player> [reason]", "Ban player by username", takesArgs = true),
        CommandSuggestion("pardon", "/pardon <player>", "Unban a player", takesArgs = true),
        CommandSuggestion("whitelist", "/whitelist <on|off|add|remove|list> [player]", "Manage server whitelist", takesArgs = true),
        CommandSuggestion("time", "/time <set|add|query> <day|night|value>", "Inspect or change world time", takesArgs = true),
        CommandSuggestion("weather", "/weather <clear|rain|thunder> [seconds]", "Set world weather", takesArgs = true),
        CommandSuggestion("difficulty", "/difficulty <0|1|2|3>", "Set world difficulty", takesArgs = true),
        CommandSuggestion("gamerule", "/gamerule <rule> [true|false|value]", "Configure world gamerule", takesArgs = true),
        CommandSuggestion("effect", "/effect <player> <effect|clear> [sec] [amp]", "Apply or clear status effects", takesArgs = true),
        CommandSuggestion("enchant", "/enchant <player> <enchantment> [level]", "Enchant held item", takesArgs = true),
        CommandSuggestion("xp", "/xp <amount> [player]", "Give experience points or levels", takesArgs = true),
        CommandSuggestion("kill", "/kill [player]", "Kill entity or player", takesArgs = true),
        CommandSuggestion("clear", "/clear [player] [item]", "Clear player inventory", takesArgs = true),
        CommandSuggestion("setworldspawn", "/setworldspawn [x y z]", "Set default world spawn", takesArgs = false),
        CommandSuggestion("spawnpoint", "/spawnpoint [player] [x y z]", "Set player respawn location", takesArgs = true),
        CommandSuggestion("seed", "/seed", "Display world seed", takesArgs = false),
        CommandSuggestion("timings", "/timings <on|off|paste|report>", "Record performance timings", takesArgs = true),
        CommandSuggestion("gc", "/gc", "Run garbage collector on chunks/memory", takesArgs = false),
        CommandSuggestion("transferserver", "/transferserver <player> <ip> [port]", "Transfer player to another server", takesArgs = true)
    )

    fun classifyLogLine(rawLine: String): ConsoleLogLevel {
        return classifyPlainLine(AnsiParser.stripAnsi(rawLine))
    }

    fun classifyPlainLine(plain: String): ConsoleLogLevel {
        val upper = plain.uppercase(Locale.US)
        return when {
            upper.contains("/ERROR]:") ||
                upper.contains("/CRITICAL]:") ||
                upper.contains("/EMERGENCY]:") ||
                upper.contains("/ALERT]:") ||
                upper.contains("[ERROR]") ||
                upper.contains("FATAL ERROR") ||
                upper.contains("UNCAUGHT ") ||
                upper.contains("EXCEPTION:") -> ConsoleLogLevel.ERROR

            upper.contains("/WARNING]:") ||
                upper.contains("/NOTICE]:") ||
                upper.contains("[WARNING]") -> ConsoleLogLevel.WARN

            CHAT_ANGLE_REGEX.containsMatchIn(plain) ||
                CHAT_SAY_REGEX.containsMatchIn(plain) ||
                PLAYER_EVENT_REGEX.containsMatchIn(plain) -> ConsoleLogLevel.CHAT

            else -> ConsoleLogLevel.INFO
        }
    }

    fun filterConsoleLines(
        lines: List<ConsoleLine>,
        level: ConsoleLogLevel,
        query: String
    ): List<ConsoleLine> {
        val needle = query.trim()
        if (level == ConsoleLogLevel.ALL && needle.isEmpty()) return lines
        return lines.filter { line ->
            val levelMatches = level == ConsoleLogLevel.ALL || line.level == level
            if (!levelMatches) return@filter false
            if (needle.isEmpty()) return@filter true
            line.plainText.contains(needle, ignoreCase = true)
        }
    }

    fun suggestCommands(
        rawInput: String,
        history: List<String>,
        limit: Int = 6
    ): List<CommandSuggestion> {
        val trimmed = rawInput.trimStart()
        if (trimmed.isEmpty()) return emptyList()

        val hasSlash = trimmed.startsWith("/")
        val normalized = trimmed.removePrefix("/").lowercase(Locale.US)
        val token = normalized.substringBefore(' ')

        // Do not cover the input if the user is already typing arguments beyond the first word
        // unless nothing else matches — show syntax reminder for exact command match when typing args!
        val typingArgs = normalized.contains(' ')

        val results = mutableListOf<CommandSuggestion>()
        val seenCommands = mutableSetOf<String>()

        if (typingArgs) {
            BUILTIN_COMMANDS.firstOrNull { it.command.equals(token, ignoreCase = true) }?.let { exact ->
                results.add(exact)
                seenCommands.add(exact.command)
            }
            history.asReversed().forEach { histCmd ->
                val cleanHist = histCmd.removePrefix("/").trim()
                if (cleanHist.lowercase(Locale.US).startsWith(normalized) &&
                    !cleanHist.equals(normalized, ignoreCase = true) &&
                    seenCommands.add("hist:$cleanHist")
                ) {
                    results.add(
                        CommandSuggestion(
                            command = cleanHist,
                            syntax = "/$cleanHist",
                            description = "Recent command",
                            takesArgs = false,
                            fromHistory = true
                        )
                    )
                }
            }
            return results.take(limit)
        }

        if (token.isEmpty()) {
            // User typed just "/" -> show most useful starter commands + recent history
            history.asReversed().take(2).forEach { histCmd ->
                val clean = histCmd.removePrefix("/").trim()
                if (clean.isNotEmpty() && seenCommands.add(clean.lowercase(Locale.US))) {
                    results.add(
                        CommandSuggestion(
                            command = clean,
                            syntax = "/$clean",
                            description = "Recent command",
                            takesArgs = false,
                            fromHistory = true
                        )
                    )
                }
            }
            BUILTIN_COMMANDS.forEach { cmd ->
                if (results.size < limit && seenCommands.add(cmd.command)) {
                    results.add(cmd)
                }
            }
            return results.take(limit)
        }

        // Prefix matches in built-ins first, then contains matches, plus matching recent commands
        val prefixMatches = BUILTIN_COMMANDS.filter { it.command.startsWith(token, ignoreCase = true) }
        val containsMatches = BUILTIN_COMMANDS.filter {
            !it.command.startsWith(token, ignoreCase = true) &&
                (it.command.contains(token, ignoreCase = true) || it.description.contains(token, ignoreCase = true))
        }

        (prefixMatches + containsMatches).forEach { cmd ->
            if (seenCommands.add(cmd.command)) {
                results.add(cmd)
            }
        }

        history.asReversed().forEach { histCmd ->
            val clean = histCmd.removePrefix("/").trim()
            if (clean.contains(token, ignoreCase = true) && seenCommands.add(clean.lowercase(Locale.US))) {
                results.add(
                    CommandSuggestion(
                        command = clean,
                        syntax = "/$clean",
                        description = "Recent command",
                        takesArgs = false,
                        fromHistory = true
                    )
                )
            }
        }

        return if (hasSlash || results.isNotEmpty()) results.take(limit) else emptyList()
    }
}
