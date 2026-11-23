package com.example.server

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class ServerProfile(
    val id: String,
    val name: String,
    val directoryName: String,
    val isDefault: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis(),
    val customPath: String = ""
) {
    companion object {
        const val DEFAULT_PROFILE_ID = "default"

        fun defaultProfile(): ServerProfile = ServerProfile(
            id = DEFAULT_PROFILE_ID,
            name = "Default (PocketMine)",
            directoryName = "",
            isDefault = true,
            createdAtMs = 0L,
            customPath = ""
        )

        fun sanitizeSlug(rawName: String): String {
            val cleaned = rawName.trim()
                .lowercase(Locale.US)
                .replace(Regex("[^a-z0-9._-]+"), "-")
                .replace(Regex("-{2,}"), "-")
                .trim('-', '.', '_')
            return cleaned.ifEmpty { "server" }
        }
    }
}

data class OverlayMacro(
    val id: String,
    val label: String,
    val command: String
)

enum class OverlayBubbleSize(val label: String, val sizeDp: Int, val iconDp: Int) {
    COMPACT("Small", 46, 32),
    STANDARD("Medium", 58, 42),
    LARGE("Large", 70, 50)
}

object BlueLightPreferencesCodec {

    fun defaultOverlayMacros(): List<OverlayMacro> = listOf(
        OverlayMacro(id = "macro_list", label = "/list", command = "list"),
        OverlayMacro(id = "macro_save", label = "/save-all", command = "save-all"),
        OverlayMacro(id = "macro_day", label = "/time set day", command = "time set day"),
        OverlayMacro(id = "macro_gmc", label = "/gamemode c", command = "gamemode c"),
        OverlayMacro(id = "macro_plugins", label = "/plugins", command = "plugins"),
        OverlayMacro(id = "macro_stop", label = "/stop", command = "stop")
    )

    fun encodeProfiles(profiles: List<ServerProfile>): String {
        val array = JSONArray()
        profiles.filter { !it.isDefault && it.id != ServerProfile.DEFAULT_PROFILE_ID }.forEach { profile ->
            val obj = JSONObject()
            obj.put("id", profile.id)
            obj.put("name", profile.name)
            obj.put("directoryName", profile.directoryName)
            obj.put("createdAtMs", profile.createdAtMs)
            if (profile.customPath.isNotEmpty()) {
                obj.put("customPath", profile.customPath)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun decodeProfiles(rawJson: String?): List<ServerProfile> {
        val list = mutableListOf(ServerProfile.defaultProfile())
        if (rawJson.isNullOrBlank()) return list
        runCatching {
            val array = JSONArray(rawJson)
            val seenIds = mutableSetOf(ServerProfile.DEFAULT_PROFILE_ID)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id").trim()
                val name = obj.optString("name").trim()
                val dirName = obj.optString("directoryName").trim()
                val customPath = obj.optString("customPath", "").trim()
                if (id.isEmpty() || name.isEmpty() || (dirName.isEmpty() && customPath.isEmpty()) || !seenIds.add(id)) {
                    continue
                }
                list.add(
                    ServerProfile(
                        id = id,
                        name = name,
                        directoryName = dirName,
                        isDefault = false,
                        createdAtMs = obj.optLong("createdAtMs", 0L),
                        customPath = customPath
                    )
                )
            }
        }
        return list
    }

    fun encodeOverlayMacros(macros: List<OverlayMacro>): String {
        val array = JSONArray()
        macros.forEach { macro ->
            val obj = JSONObject()
            obj.put("id", macro.id)
            obj.put("label", macro.label)
            obj.put("command", macro.command)
            array.put(obj)
        }
        return array.toString()
    }

    fun decodeOverlayMacros(rawJson: String?): List<OverlayMacro> {
        if (rawJson == null) return defaultOverlayMacros()
        if (rawJson.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(rawJson)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim().ifEmpty { "macro_$i" }
                    val command = obj.optString("command").trim().removePrefix("/")
                    val label = obj.optString("label").trim().ifEmpty { "/$command" }
                    if (command.isNotEmpty()) {
                        add(OverlayMacro(id = id, label = label, command = command))
                    }
                }
            }
        }.getOrElse { defaultOverlayMacros() }
    }
}
