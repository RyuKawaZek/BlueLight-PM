package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.console.AnsiParser
import com.example.server.BlueLightPreferencesCodec
import com.example.server.ConsoleLine
import com.example.server.OperationProgress
import com.example.server.OverlayBubbleSize
import com.example.server.OverlayMacro
import com.example.server.ServerFileItem
import com.example.server.ServerProfile
import com.example.server.ServerPropertiesConfig
import com.example.server.ServerRepository
import com.example.server.ServerStatus
import com.example.server.ServerWorkspaceInfo
import com.example.service.FloatingConsoleService
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.ConsoleThemeStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppTab {
    DASHBOARD,
    CONSOLE,
    FILES,
    SETTINGS
}

sealed class TextEditorState {
    data object Closed : TextEditorState()
    data class Open(
        val file: ServerFileItem,
        val content: String,
        val isSaving: Boolean = false,
        val errorMessage: String? = null
    ) : TextEditorState()
}

enum class FileClipboardMode {
    MOVE,
    COPY
}

data class FileClipboardState(
    val mode: FileClipboardMode,
    val items: List<ServerFileItem>
)

class BlueLightViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(ServerRepository.PREFS_NAME, Context.MODE_PRIVATE)
    private val repository = ServerRepository.getInstance(application)

    val serverStatus: StateFlow<ServerStatus> = repository.status
    val consoleLines: StateFlow<List<ConsoleLine>> = repository.consoleLines
    val workspaceInfo: StateFlow<ServerWorkspaceInfo> = repository.workspaceInfo
    val operationProgress: StateFlow<OperationProgress> = repository.operationProgress
    val serverProperties: StateFlow<ServerPropertiesConfig> = repository.serverProperties
    val currentRelativePath: StateFlow<String> = repository.currentRelativePath
    val browserFiles: StateFlow<List<ServerFileItem>> = repository.browserFiles
    val profiles: StateFlow<List<ServerProfile>> = repository.profiles
    val activeProfile: StateFlow<ServerProfile> = repository.activeProfile

    private val _selectedTab = MutableStateFlow(AppTab.DASHBOARD)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    private val _ansiEnabled = MutableStateFlow(prefs.getBoolean(KEY_ANSI_ENABLED, true))
    val ansiEnabled: StateFlow<Boolean> = _ansiEnabled.asStateFlow()

    private val _fontSizeSp = MutableStateFlow(prefs.getFloat(KEY_FONT_SIZE, 12f).coerceIn(9f, 22f))
    val fontSizeSp: StateFlow<Float> = _fontSizeSp.asStateFlow()

    private val _autoScroll = MutableStateFlow(prefs.getBoolean(KEY_AUTO_SCROLL, true))
    val autoScroll: StateFlow<Boolean> = _autoScroll.asStateFlow()

    private val _appThemeMode = MutableStateFlow(
        AppThemeMode.entries.getOrElse(prefs.getInt(KEY_APP_THEME, 0)) { AppThemeMode.SYSTEM }
    )
    val appThemeMode: StateFlow<AppThemeMode> = _appThemeMode.asStateFlow()

    private val _pureBlack = MutableStateFlow(prefs.getBoolean(KEY_PURE_BLACK, false))
    val pureBlack: StateFlow<Boolean> = _pureBlack.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLOR, false))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _accentPreset = MutableStateFlow(
        AccentPreset.entries.getOrElse(prefs.getInt(KEY_ACCENT_PRESET, 0)) { AccentPreset.SAPPHIRE }
    )
    val accentPreset: StateFlow<AccentPreset> = _accentPreset.asStateFlow()

    private val _floatingNavBar = MutableStateFlow(prefs.getBoolean(KEY_FLOATING_NAV_BAR, true))
    val floatingNavBar: StateFlow<Boolean> = _floatingNavBar.asStateFlow()

    private val _consoleThemeStyle = MutableStateFlow(
        ConsoleThemeStyle.entries.getOrElse(prefs.getInt(KEY_CONSOLE_THEME, 0)) {
            ConsoleThemeStyle.DARK_TERMINAL
        }
    )
    val consoleThemeStyle: StateFlow<ConsoleThemeStyle> = _consoleThemeStyle.asStateFlow()

    private val _commandHistory = MutableStateFlow<List<String>>(emptyList())
    val commandHistory: StateFlow<List<String>> = _commandHistory.asStateFlow()

    private val _overlayBubbleSize = MutableStateFlow(
        OverlayBubbleSize.entries.getOrElse(
            prefs.getInt(FloatingConsoleService.KEY_OVERLAY_BUBBLE_SIZE, OverlayBubbleSize.STANDARD.ordinal)
        ) { OverlayBubbleSize.STANDARD }
    )
    val overlayBubbleSize: StateFlow<OverlayBubbleSize> = _overlayBubbleSize.asStateFlow()

    private val _overlayOpacityPercent = MutableStateFlow(
        prefs.getInt(FloatingConsoleService.KEY_OVERLAY_OPACITY, 92).coerceIn(40, 100)
    )
    val overlayOpacityPercent: StateFlow<Int> = _overlayOpacityPercent.asStateFlow()

    private val _overlayMacros = MutableStateFlow(
        BlueLightPreferencesCodec.decodeOverlayMacros(
            prefs.getString(FloatingConsoleService.KEY_OVERLAY_MACROS, null)
        )
    )
    val overlayMacros: StateFlow<List<OverlayMacro>> = _overlayMacros.asStateFlow()

    private val _textEditorState = MutableStateFlow<TextEditorState>(TextEditorState.Closed)
    val textEditorState: StateFlow<TextEditorState> = _textEditorState.asStateFlow()

    private val _fileClipboard = MutableStateFlow<FileClipboardState?>(null)
    val fileClipboard: StateFlow<FileClipboardState?> = _fileClipboard.asStateFlow()

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
        if (tab == AppTab.FILES) {
            repository.refreshWorkspaceInfo()
        }
    }

    fun setAnsiEnabled(enabled: Boolean) {
        _ansiEnabled.value = enabled
        prefs.edit().putBoolean(KEY_ANSI_ENABLED, enabled).apply()
    }

    fun setFontSizeSp(size: Float) {
        val clamped = size.coerceIn(9f, 22f)
        _fontSizeSp.value = clamped
        prefs.edit().putFloat(KEY_FONT_SIZE, clamped).apply()
    }

    fun setAutoScroll(enabled: Boolean) {
        _autoScroll.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_SCROLL, enabled).apply()
    }

    fun setAppThemeMode(mode: AppThemeMode) {
        _appThemeMode.value = mode
        prefs.edit().putInt(KEY_APP_THEME, mode.ordinal).apply()
    }

    fun setPureBlack(enabled: Boolean) {
        _pureBlack.value = enabled
        prefs.edit().putBoolean(KEY_PURE_BLACK, enabled).apply()
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    fun setAccentPreset(preset: AccentPreset) {
        _accentPreset.value = preset
        prefs.edit().putInt(KEY_ACCENT_PRESET, preset.ordinal).apply()
    }

    fun setFloatingNavBar(enabled: Boolean) {
        _floatingNavBar.value = enabled
        prefs.edit().putBoolean(KEY_FLOATING_NAV_BAR, enabled).apply()
    }

    fun setConsoleThemeStyle(style: ConsoleThemeStyle) {
        _consoleThemeStyle.value = style
        prefs.edit().putInt(KEY_CONSOLE_THEME, style.ordinal).apply()
    }

    fun cycleConsoleTheme() {
        val entries = ConsoleThemeStyle.entries
        val next = entries[(_consoleThemeStyle.value.ordinal + 1) % entries.size]
        setConsoleThemeStyle(next)
    }

    fun startServer() {
        repository.startServer(_ansiEnabled.value)
    }

    fun stopServer() {
        repository.stopServer()
    }

    fun forceKillServer() {
        repository.forceKillServer()
    }

    fun sendCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return
        val updated = (_commandHistory.value.filterNot { it == trimmed } + trimmed).takeLast(20)
        _commandHistory.value = updated
        repository.writeCommand(trimmed)
    }

    fun clearConsole() {
        repository.clearLogs()
    }

    fun getPlainLogText(): String {
        return consoleLines.value.joinToString("\n") { AnsiParser.stripAnsi(it.rawText) }
    }

    fun downloadBlueLightCore(customUrl: String? = null) {
        val url = customUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?: ServerRepository.BLUELIGHT_MASTER_ZIP_URL
        repository.downloadBlueLightCore(url)
    }

    fun importServerPackage(uri: Uri) {
        repository.importServerPackage(uri)
    }

    fun backupServer(exportUri: Uri? = null) {
        repository.backupServer(exportUri)
    }

    fun deleteServerData() {
        repository.deleteServerData()
    }

    fun saveServerProperties(updated: ServerPropertiesConfig) {
        repository.saveServerProperties(updated)
    }

    fun dismissOperationProgress() {
        repository.clearOperationProgress()
    }

    fun refreshWorkspace() {
        repository.refreshWorkspaceInfo()
        repository.loadServerProperties()
    }

    // --- Multi-Server Profiles ---

    fun switchProfile(profileId: String): Boolean {
        val switched = repository.switchProfile(profileId)
        if (switched) {
            _fileClipboard.value = null
        }
        return switched
    }

    fun createProfile(name: String, copyPropertiesFromCurrent: Boolean = false) {
        repository.createProfile(name = name, copyPropertiesFromCurrent = copyPropertiesFromCurrent, switchAfterCreate = true)
    }

    fun renameProfile(profileId: String, newName: String) {
        repository.renameProfile(profileId, newName)
    }

    fun deleteProfile(profileId: String, deleteFiles: Boolean) {
        repository.deleteProfile(profileId, deleteFiles)
    }

    fun detectCoreLabelForProfile(profile: ServerProfile): String {
        return repository.detectCoreLabelForProfile(profile)
    }

    // --- Floating Overlay Customization ---

    fun setOverlayBubbleSize(size: OverlayBubbleSize) {
        _overlayBubbleSize.value = size
        prefs.edit().putInt(FloatingConsoleService.KEY_OVERLAY_BUBBLE_SIZE, size.ordinal).apply()
        FloatingConsoleService.refreshConfig(getApplication())
    }

    fun setOverlayOpacityPercent(percent: Int) {
        val clamped = percent.coerceIn(40, 100)
        _overlayOpacityPercent.value = clamped
        prefs.edit().putInt(FloatingConsoleService.KEY_OVERLAY_OPACITY, clamped).apply()
        FloatingConsoleService.refreshConfig(getApplication())
    }

    fun addOverlayMacro(label: String, command: String) {
        val cleanCmd = command.trim().removePrefix("/")
        if (cleanCmd.isEmpty()) return
        val cleanLabel = label.trim().ifEmpty { "/$cleanCmd" }
        val macro = OverlayMacro(
            id = "macro_${System.currentTimeMillis()}",
            label = cleanLabel,
            command = cleanCmd
        )
        persistOverlayMacros(_overlayMacros.value + macro)
    }

    fun updateOverlayMacro(id: String, label: String, command: String) {
        val cleanCmd = command.trim().removePrefix("/")
        if (cleanCmd.isEmpty()) return
        val cleanLabel = label.trim().ifEmpty { "/$cleanCmd" }
        persistOverlayMacros(
            _overlayMacros.value.map {
                if (it.id == id) it.copy(label = cleanLabel, command = cleanCmd) else it
            }
        )
    }

    fun removeOverlayMacro(id: String) {
        persistOverlayMacros(_overlayMacros.value.filterNot { it.id == id })
    }

    fun resetOverlayMacros() {
        persistOverlayMacros(BlueLightPreferencesCodec.defaultOverlayMacros())
    }

    private fun persistOverlayMacros(macros: List<OverlayMacro>) {
        _overlayMacros.value = macros
        prefs.edit()
            .putString(FloatingConsoleService.KEY_OVERLAY_MACROS, BlueLightPreferencesCodec.encodeOverlayMacros(macros))
            .apply()
        FloatingConsoleService.refreshConfig(getApplication())
    }

    // --- File Manager Actions ---

    fun browseDirectory(relativePath: String) {
        repository.browseDirectory(relativePath)
    }

    fun navigateUpDirectory(): Boolean {
        return repository.navigateUpDirectory()
    }

    fun createFolder(name: String) {
        repository.createFolder(name)
    }

    fun createFile(name: String, initialContent: String = "") {
        repository.createFile(name, initialContent)
    }

    fun renameEntry(item: ServerFileItem, newName: String) {
        repository.renameEntry(item, newName)
    }

    fun deleteEntry(item: ServerFileItem) {
        repository.deleteEntry(item)
    }

    fun deleteEntries(items: List<ServerFileItem>) {
        if (items.isEmpty()) return
        val paths = items.map { it.absolutePath }.toSet()
        repository.deleteEntries(items.map { it.absolutePath })
        val currentClip = _fileClipboard.value
        if (currentClip != null) {
            val remaining = currentClip.items.filterNot { it.absolutePath in paths }
            _fileClipboard.value = if (remaining.isEmpty()) null else currentClip.copy(items = remaining)
        }
    }

    fun setFileClipboard(mode: FileClipboardMode, items: List<ServerFileItem>) {
        if (items.isEmpty()) {
            _fileClipboard.value = null
            return
        }
        _fileClipboard.value = FileClipboardState(mode = mode, items = items)
    }

    fun clearFileClipboard() {
        _fileClipboard.value = null
    }

    fun pasteClipboardHere() {
        val clip = _fileClipboard.value ?: return
        val dest = currentRelativePath.value
        when (clip.mode) {
            FileClipboardMode.MOVE -> {
                repository.moveEntries(clip.items.map { it.absolutePath }, dest)
                _fileClipboard.value = null
            }
            FileClipboardMode.COPY -> {
                repository.copyEntries(clip.items.map { it.absolutePath }, dest)
                _fileClipboard.value = null
            }
        }
    }

    fun moveEntries(items: List<ServerFileItem>, destRelativePath: String) {
        if (items.isEmpty()) return
        repository.moveEntries(items.map { it.absolutePath }, destRelativePath)
        _fileClipboard.value = null
    }

    fun copyEntries(items: List<ServerFileItem>, destRelativePath: String) {
        if (items.isEmpty()) return
        repository.copyEntries(items.map { it.absolutePath }, destRelativePath)
        _fileClipboard.value = null
    }

    fun zipEntries(items: List<ServerFileItem>, archiveName: String) {
        if (items.isEmpty()) return
        repository.zipEntries(items.map { it.absolutePath }, archiveName)
    }

    fun listWorkspaceDirectories(): List<String> {
        return repository.listDirectoriesInWorkspace()
    }

    fun importFileIntoCurrentDir(uri: Uri) {
        repository.importFileIntoCurrentDir(uri)
    }

    fun importMultipleFilesIntoCurrentDir(uris: List<Uri>) {
        repository.importMultipleFilesIntoCurrentDir(uris)
    }

    fun extractZipEntry(item: ServerFileItem) {
        repository.extractZipEntry(item)
    }

    fun exportFileToUri(item: ServerFileItem, destUri: Uri) {
        repository.exportFileToUri(item, destUri)
    }

    fun openTextFile(item: ServerFileItem) {
        viewModelScope.launch {
            repository.readTextFile(item)
                .onSuccess { content ->
                    _textEditorState.value = TextEditorState.Open(file = item, content = content)
                }
                .onFailure { err ->
                    repository.appendLog("\u001B[31m[Files]\u001B[0m Cannot open ${item.name}: ${err.message}")
                }
        }
    }

    fun updateEditorContent(newContent: String) {
        val current = _textEditorState.value
        if (current is TextEditorState.Open) {
            _textEditorState.value = current.copy(content = newContent, errorMessage = null)
        }
    }

    fun saveOpenTextFile() {
        val current = _textEditorState.value
        if (current !is TextEditorState.Open) return
        _textEditorState.value = current.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            repository.writeTextFile(current.file, current.content)
                .onSuccess {
                    _textEditorState.value = TextEditorState.Closed
                }
                .onFailure { err ->
                    _textEditorState.value = current.copy(
                        isSaving = false,
                        errorMessage = err.message ?: "Save failed"
                    )
                }
        }
    }

    fun closeTextEditor() {
        _textEditorState.value = TextEditorState.Closed
    }

    companion object {
        private const val KEY_ANSI_ENABLED = "ansi_enabled"
        private const val KEY_FONT_SIZE = "font_size_sp"
        private const val KEY_AUTO_SCROLL = "auto_scroll"
        private const val KEY_APP_THEME = "app_theme_mode"
        private const val KEY_PURE_BLACK = "pure_black"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_ACCENT_PRESET = "accent_preset"
        private const val KEY_FLOATING_NAV_BAR = "floating_nav_bar"
        private const val KEY_CONSOLE_THEME = "console_theme_style"
    }
}
