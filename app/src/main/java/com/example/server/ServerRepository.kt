package com.example.server

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import com.example.service.ServerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class ServerStatus {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING
}

data class ConsoleLine(
    val id: Long,
    val rawText: String,
    val timestampMs: Long = System.currentTimeMillis()
)

data class ServerWorkspaceInfo(
    val workspacePath: String = "",
    val hasStorageAccess: Boolean = false,
    val hasPhar: Boolean = false,
    val hasSrcFolder: Boolean = false,
    val phpBinaryAvailable: Boolean = false,
    val phpBinaryPath: String = "",
    val deviceAbis: String = "",
    val backupCount: Int = 0
) {
    val detectedCoreLabel: String
        get() = when {
            !hasStorageAccess -> "Storage permission required"
            hasPhar -> "PocketMine-MP.phar"
            hasSrcFolder -> "src/pocketmine/PocketMine.php"
            else -> "No server core installed"
        }

    val hasServerCore: Boolean
        get() = hasPhar || hasSrcFolder
}

data class ServerFileItem(
    val name: String,
    val absolutePath: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    val childCount: Int = 0
) {
    val extension: String
        get() = name.substringAfterLast('.', "").lowercase(Locale.US)

    val isEditableText: Boolean
        get() = !isDirectory && extension in EDITABLE_TEXT_EXTENSIONS

    val isZipArchive: Boolean
        get() = !isDirectory && extension == "zip"

    companion object {
        private val EDITABLE_TEXT_EXTENSIONS = setOf(
            "properties", "yml", "yaml", "txt", "json", "log",
            "ini", "php", "md", "conf", "cfg", "sh", "xml", "csv"
        )
    }
}

data class ServerPropertiesConfig(
    val motd: String = "BlueLight PocketMine Server",
    val port: String = "19132",
    val maxPlayers: String = "20",
    val gamemode: String = "0",
    val onlineMode: Boolean = false
)

sealed class OperationProgress {
    data object Idle : OperationProgress()
    data class Running(val label: String, val progressPercent: Int? = null) : OperationProgress()
    data class Success(val message: String) : OperationProgress()
    data class Error(val message: String) : OperationProgress()
}

class ServerRepository private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _status = MutableStateFlow(ServerStatus.STOPPED)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private val _consoleLines = MutableStateFlow<List<ConsoleLine>>(emptyList())
    val consoleLines: StateFlow<List<ConsoleLine>> = _consoleLines.asStateFlow()

    private val _workspaceInfo = MutableStateFlow(ServerWorkspaceInfo())
    val workspaceInfo: StateFlow<ServerWorkspaceInfo> = _workspaceInfo.asStateFlow()

    private val _operationProgress = MutableStateFlow<OperationProgress>(OperationProgress.Idle)
    val operationProgress: StateFlow<OperationProgress> = _operationProgress.asStateFlow()

    private val _serverProperties = MutableStateFlow(ServerPropertiesConfig())
    val serverProperties: StateFlow<ServerPropertiesConfig> = _serverProperties.asStateFlow()

    private val _currentRelativePath = MutableStateFlow("")
    val currentRelativePath: StateFlow<String> = _currentRelativePath.asStateFlow()

    private val _browserFiles = MutableStateFlow<List<ServerFileItem>>(emptyList())
    val browserFiles: StateFlow<List<ServerFileItem>> = _browserFiles.asStateFlow()

    @Volatile
    private var serverProcess: Process? = null

    @Volatile
    private var processStdin: OutputStream? = null

    private var lineIdCounter = 0L
    private val lineBuffer = ArrayDeque<ConsoleLine>(MAX_CONSOLE_LINES)
    private val logLock = Any()

    private val dedupLock = Any()
    private val recentStdoutKeys = ArrayDeque<String>(MAX_DEDUP_KEYS)
    private val recentLogFileKeys = ArrayDeque<String>(MAX_DEDUP_KEYS)

    init {
        ensureDirectories()
        refreshWorkspaceInfo()
        loadServerProperties()
        browseDirectory("")
        appendSystemDiagnosticBanner()
    }

    fun hasStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getDataDirectory(): File {
        return File(Environment.getExternalStorageDirectory(), "PocketMine")
    }

    fun getBackupDirectory(): File {
        return File(Environment.getExternalStorageDirectory(), "PocketMine-Backups")
    }

    private fun resolvePhpBinary(): File? {
        val nativeLib = File(appContext.applicationInfo.nativeLibraryDir, "libphp.so")
        if (nativeLib.exists()) return nativeLib
        val localBin = File(appContext.filesDir, "php")
        if (localBin.exists()) return localBin
        return null
    }

    private fun ensureDirectories() {
        if (!hasStorageAccess()) return
        val dataDir = getDataDirectory()
        if (!dataDir.exists()) dataDir.mkdirs()
        val backupDir = getBackupDirectory()
        if (!backupDir.exists()) backupDir.mkdirs()
        val tmpDir = File(dataDir, "tmp")
        if (!tmpDir.exists()) tmpDir.mkdirs()
    }

    fun refreshWorkspaceInfo() {
        val granted = hasStorageAccess()
        if (granted) {
            ensureDirectories()
        }
        val dataDir = getDataDirectory()
        val hasPhar = granted && File(dataDir, "PocketMine-MP.phar").exists()
        val hasSrc = granted && File(dataDir, "src/pocketmine/PocketMine.php").exists()
        val phpBin = resolvePhpBinary()
        val backups = if (granted) {
            getBackupDirectory().listFiles { f -> f.extension.equals("zip", ignoreCase = true) }?.size ?: 0
        } else {
            0
        }

        _workspaceInfo.value = ServerWorkspaceInfo(
            workspacePath = dataDir.absolutePath,
            hasStorageAccess = granted,
            hasPhar = hasPhar,
            hasSrcFolder = hasSrc,
            phpBinaryAvailable = phpBin != null && phpBin.exists(),
            phpBinaryPath = phpBin?.absolutePath ?: "Not bundled for ${Build.SUPPORTED_ABIS.joinToString()}",
            deviceAbis = Build.SUPPORTED_ABIS.joinToString(", "),
            backupCount = backups
        )
        browseDirectory(_currentRelativePath.value)
    }

    private fun appendSystemDiagnosticBanner() {
        val info = _workspaceInfo.value
        appendLog("\u001B[1;36m[BlueLight]\u001B[0m Console ready (Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT})")
        appendLog("\u001B[36m[BlueLight]\u001B[0m Device ABIs: ${info.deviceAbis}")
        appendLog("\u001B[36m[BlueLight]\u001B[0m Server folder: ${info.workspacePath}")
        when {
            !info.hasStorageAccess -> appendLog("\u001B[1;33m[BlueLight]\u001B[0m Grant 'All files access' on the Server or Files tab to read/write ${info.workspacePath}.")
            info.hasServerCore -> appendLog("\u001B[1;32m[BlueLight]\u001B[0m Detected core: ${info.detectedCoreLabel}")
            else -> appendLog("\u001B[1;33m[BlueLight]\u001B[0m No server core found in ${info.workspacePath}. Place PocketMine-MP.phar / src/ there, or use Download / Import.")
        }
    }

    fun appendLog(rawLine: String) {
        synchronized(logLock) {
            if (lineBuffer.size >= MAX_CONSOLE_LINES) {
                lineBuffer.removeFirst()
            }
            lineIdCounter += 1
            lineBuffer.addLast(ConsoleLine(id = lineIdCounter, rawText = rawLine))
            _consoleLines.value = lineBuffer.toList()
        }
    }

    fun clearLogs() {
        synchronized(logLock) {
            lineBuffer.clear()
            _consoleLines.value = emptyList()
        }
    }

    fun startServer(ansiEnabled: Boolean) {
        if (_status.value == ServerStatus.RUNNING || _status.value == ServerStatus.STARTING) return

        scope.launch {
            _status.value = ServerStatus.STARTING
            refreshWorkspaceInfo()
            val info = _workspaceInfo.value

            if (!info.hasStorageAccess) {
                appendLog("\u001B[1;31m[Error]\u001B[0m Storage permission required to access ${info.workspacePath}.")
                _status.value = ServerStatus.STOPPED
                return@launch
            }

            if (!info.hasServerCore) {
                appendLog("\u001B[1;31m[Error]\u001B[0m Cannot start: neither PocketMine-MP.phar nor src/pocketmine/PocketMine.php exists in ${info.workspacePath}.")
                _status.value = ServerStatus.STOPPED
                return@launch
            }

            val phpBinary = resolvePhpBinary()
            if (phpBinary == null || !phpBinary.exists()) {
                appendLog("\u001B[1;31m[Error]\u001B[0m PHP binary not found for this device architecture (${info.deviceAbis}).")
                _status.value = ServerStatus.STOPPED
                return@launch
            }

            val dataDir = getDataDirectory()
            if (!dataDir.exists()) dataDir.mkdirs()
            val tmpDir = File(dataDir, "tmp")
            if (!tmpDir.exists()) tmpDir.mkdirs()

            val phpIni = ensurePhpIni(dataDir)
            val entryFile = if (info.hasPhar) {
                File(dataDir, "PocketMine-MP.phar").absolutePath
            } else {
                File(dataDir, "src/pocketmine/PocketMine.php").absolutePath
            }

            val args = listOf(
                phpBinary.absolutePath,
                "-c",
                phpIni.absolutePath,
                "-d", "output_buffering=0",
                "-d", "implicit_flush=1",
                entryFile,
                if (ansiEnabled) "--enable-ansi" else "--disable-ansi"
            )

            try {
                phpBinary.setExecutable(true, true)
            } catch (_: SecurityException) {
            }

            var logTailJob: Job? = null
            try {
                startForegroundServiceSafe()
                synchronized(dedupLock) {
                    recentStdoutKeys.clear()
                    recentLogFileKeys.clear()
                }
                val serverLogFile = File(dataDir, "server.log")
                val initialLogOffset = if (serverLogFile.exists()) serverLogFile.length() else 0L

                appendLog("\u001B[1;36m[BlueLight]\u001B[0m Launching PHP server (${info.detectedCoreLabel})...")
                val builder = ProcessBuilder(args)
                builder.redirectErrorStream(true)
                builder.directory(dataDir)
                val env = builder.environment()
                env["TMPDIR"] = tmpDir.absolutePath
                env["HOME"] = dataDir.absolutePath

                val proc = builder.start()
                serverProcess = proc
                processStdin = proc.outputStream
                _status.value = ServerStatus.RUNNING

                logTailJob = scope.launch {
                    tailServerLogFile(serverLogFile, initialLogOffset)
                }

                InputStreamReader(proc.inputStream, Charset.forName("UTF-8")).use { reader ->
                    val buffer = CharArray(4096)
                    val currentLine = StringBuilder()
                    while (true) {
                        val count = reader.read(buffer, 0, buffer.size)
                        if (count == -1) break
                        for (i in 0 until count) {
                            val c = buffer[i]
                            if (c == '\r') continue
                            if (c == '\n' || c == '\u0007') {
                                val line = currentLine.toString()
                                currentLine.setLength(0)
                                if (c == '\n' && !line.startsWith("\u001B]0;")) {
                                    appendServerLine(line, fromStdout = true)
                                }
                            } else {
                                currentLine.append(c)
                            }
                        }
                    }
                    if (currentLine.isNotEmpty()) {
                        val tail = currentLine.toString()
                        if (!tail.startsWith("\u001B]0;")) {
                            appendServerLine(tail, fromStdout = true)
                        }
                    }
                }

                val exitCode = proc.waitFor()
                delay(200)
                appendLog("\u001B[1;33m[BlueLight]\u001B[0m Server process exited (code $exitCode).")
            } catch (e: Exception) {
                appendLog("\u001B[1;31m[Error]\u001B[0m Failed to run PHP process: ${e.message ?: e::class.java.simpleName}")
                if (e.message?.contains("Exec format", ignoreCase = true) == true ||
                    !Build.SUPPORTED_64_BIT_ABIS.any { it.contains("arm64") }
                ) {
                    appendLog("\u001B[33m[BlueLight]\u001B[0m Note: Bundled PHP binary targets arm64-v8a devices; current ABI is ${Build.SUPPORTED_ABIS.joinToString()}.")
                }
            } finally {
                logTailJob?.cancel()
                serverProcess = null
                processStdin = null
                _status.value = ServerStatus.STOPPED
                stopForegroundServiceSafe()
            }
        }
    }

    fun writeCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return
        appendLog("\u001B[1;36m> $trimmed\u001B[0m")

        scope.launch {
            val stream = processStdin
            if (stream == null || _status.value != ServerStatus.RUNNING) {
                appendLog("\u001B[33m[BlueLight]\u001B[0m Server is not running. Start the server first to execute commands.")
                return@launch
            }
            try {
                stream.write((trimmed + "\r\n").toByteArray(Charsets.UTF_8))
                stream.flush()
            } catch (e: Exception) {
                appendLog("\u001B[1;31m[Error]\u001B[0m Failed to send command: ${e.message}")
            }
        }
    }

    fun stopServer() {
        if (_status.value != ServerStatus.RUNNING) return
        _status.value = ServerStatus.STOPPING
        writeCommand("stop")
    }

    fun forceKillServer() {
        scope.launch {
            try {
                val proc = serverProcess
                if (proc != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        proc.destroyForcibly()
                    } else {
                        proc.destroy()
                    }
                }
                appendLog("\u001B[1;31m[BlueLight]\u001B[0m Server process force-killed.")
            } catch (e: Exception) {
                appendLog("\u001B[1;31m[Error]\u001B[0m Kill failed: ${e.message}")
            } finally {
                serverProcess = null
                processStdin = null
                _status.value = ServerStatus.STOPPED
                stopForegroundServiceSafe()
            }
        }
    }

    // --- Direct File Manager for /storage/emulated/0/PocketMine ---

    fun browseDirectory(relativePath: String) {
        if (!hasStorageAccess()) {
            _currentRelativePath.value = ""
            _browserFiles.value = emptyList()
            return
        }

        val rootDir = getDataDirectory()
        if (!rootDir.exists()) rootDir.mkdirs()

        val cleanRel = relativePath.trim('/').replace("\\", "/")
        val candidate = if (cleanRel.isEmpty()) rootDir else File(rootDir, cleanRel)
        val targetDir = runCatching {
            if (candidate.canonicalPath.startsWith(rootDir.canonicalPath) && candidate.exists() && candidate.isDirectory) {
                candidate
            } else {
                rootDir
            }
        }.getOrDefault(rootDir)

        val actualRel = if (targetDir.canonicalPath == rootDir.canonicalPath) {
            ""
        } else {
            targetDir.relativeTo(rootDir).path.replace("\\", "/")
        }
        _currentRelativePath.value = actualRel

        val children = targetDir.listFiles().orEmpty()
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .map { file ->
                val rel = file.relativeTo(rootDir).path.replace("\\", "/")
                ServerFileItem(
                    name = file.name,
                    absolutePath = file.absolutePath,
                    relativePath = rel,
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isDirectory) 0L else file.length(),
                    lastModifiedMs = file.lastModified(),
                    childCount = if (file.isDirectory) file.listFiles()?.size ?: 0 else 0
                )
            }
        _browserFiles.value = children
    }

    fun navigateUpDirectory(): Boolean {
        val current = _currentRelativePath.value
        if (current.isEmpty()) return false
        val parent = current.substringBeforeLast('/', missingDelimiterValue = "")
        browseDirectory(parent)
        return true
    }

    private fun currentTargetDir(): File {
        val root = getDataDirectory()
        val rel = _currentRelativePath.value
        val dir = if (rel.isEmpty()) root else File(root, rel)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun createFolder(name: String) {
        val sanitized = sanitizeSingleName(name)
        if (sanitized.isEmpty()) {
            _operationProgress.value = OperationProgress.Error("Invalid folder name")
            return
        }
        scope.launch {
            if (!hasStorageAccess()) {
                _operationProgress.value = OperationProgress.Error("Storage access is required")
                return@launch
            }
            val folder = File(currentTargetDir(), sanitized)
            if (folder.exists()) {
                _operationProgress.value = OperationProgress.Error("'$sanitized' already exists")
                return@launch
            }
            if (folder.mkdirs()) {
                refreshWorkspaceInfo()
                _operationProgress.value = OperationProgress.Success("Created folder '$sanitized'")
            } else {
                _operationProgress.value = OperationProgress.Error("Could not create folder '$sanitized'")
            }
        }
    }

    fun createFile(name: String, initialContent: String = "") {
        val sanitized = sanitizeSingleName(name)
        if (sanitized.isEmpty()) {
            _operationProgress.value = OperationProgress.Error("Invalid file name")
            return
        }
        scope.launch {
            if (!hasStorageAccess()) {
                _operationProgress.value = OperationProgress.Error("Storage access is required")
                return@launch
            }
            val file = File(currentTargetDir(), sanitized)
            if (file.exists()) {
                _operationProgress.value = OperationProgress.Error("'$sanitized' already exists")
                return@launch
            }
            try {
                file.writeText(initialContent, Charsets.UTF_8)
                refreshWorkspaceInfo()
                _operationProgress.value = OperationProgress.Success("Created file '$sanitized'")
            } catch (e: Exception) {
                _operationProgress.value = OperationProgress.Error("Create failed: ${e.message}")
            }
        }
    }

    fun renameEntry(item: ServerFileItem, newName: String) {
        val sanitized = sanitizeSingleName(newName)
        if (sanitized.isEmpty()) {
            _operationProgress.value = OperationProgress.Error("Invalid name")
            return
        }
        scope.launch {
            try {
                val src = File(item.absolutePath)
                val dest = File(src.parentFile, sanitized)
                if (dest.exists()) {
                    _operationProgress.value = OperationProgress.Error("'$sanitized' already exists")
                    return@launch
                }
                if (src.renameTo(dest)) {
                    refreshWorkspaceInfo()
                    _operationProgress.value = OperationProgress.Success("Renamed to '$sanitized'")
                } else {
                    _operationProgress.value = OperationProgress.Error("Rename failed")
                }
            } catch (e: Exception) {
                _operationProgress.value = OperationProgress.Error("Rename failed: ${e.message}")
            }
        }
    }

    fun deleteEntry(item: ServerFileItem) {
        scope.launch {
            try {
                val target = File(item.absolutePath)
                val ok = if (target.isDirectory) target.deleteRecursively() else target.delete()
                if (ok) {
                    refreshWorkspaceInfo()
                    _operationProgress.value = OperationProgress.Success("Deleted '${item.name}'")
                } else {
                    _operationProgress.value = OperationProgress.Error("Could not delete '${item.name}'")
                }
            } catch (e: Exception) {
                _operationProgress.value = OperationProgress.Error("Delete failed: ${e.message}")
            }
        }
    }

    fun importFileIntoCurrentDir(uri: Uri) {
        scope.launch {
            if (!hasStorageAccess()) {
                _operationProgress.value = OperationProgress.Error("Storage access is required")
                return@launch
            }
            _operationProgress.value = OperationProgress.Running("Importing file...", null)
            try {
                val fileName = sanitizeSingleName(resolveFileName(uri) ?: "imported_file")
                    .ifEmpty { "imported_file" }
                val dest = File(currentTargetDir(), fileName)
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Could not read selected file")
                refreshWorkspaceInfo()
                appendLog("\u001B[1;32m[BlueLight]\u001B[0m Imported '${dest.relativeTo(getDataDirectory()).path}'.")
                _operationProgress.value = OperationProgress.Success("Imported '$fileName'")
            } catch (e: Exception) {
                _operationProgress.value = OperationProgress.Error("Import failed: ${e.message}")
            }
        }
    }

    fun extractZipEntry(item: ServerFileItem) {
        scope.launch {
            _operationProgress.value = OperationProgress.Running("Extracting ${item.name}...", null)
            try {
                val zipFile = File(item.absolutePath)
                val destDir = zipFile.parentFile ?: getDataDirectory()
                extractZipToDir(zipFile, destDir, stripSingleTopFolder = false)
                refreshWorkspaceInfo()
                _operationProgress.value = OperationProgress.Success("Extracted '${item.name}'")
            } catch (e: Exception) {
                _operationProgress.value = OperationProgress.Error("Extract failed: ${e.message}")
            }
        }
    }

    fun exportFileToUri(item: ServerFileItem, destUri: Uri) {
        scope.launch {
            _operationProgress.value = OperationProgress.Running("Exporting ${item.name}...", null)
            try {
                val src = File(item.absolutePath)
                appContext.contentResolver.openOutputStream(destUri)?.use { output ->
                    FileInputStream(src).use { input ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Could not open destination")
                _operationProgress.value = OperationProgress.Success("Exported '${item.name}'")
            } catch (e: Exception) {
                _operationProgress.value = OperationProgress.Error("Export failed: ${e.message}")
            }
        }
    }

    suspend fun readTextFile(item: ServerFileItem): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(item.absolutePath)
            if (!file.exists() || !file.isFile) throw IllegalStateException("File not found")
            if (file.length() > MAX_EDITABLE_BYTES) {
                throw IllegalStateException("File is larger than 512 KB (${file.length() / 1024} KB)")
            }
            file.readText(Charsets.UTF_8)
        }
    }

    suspend fun writeTextFile(item: ServerFileItem, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(item.absolutePath)
            file.writeText(content, Charsets.UTF_8)
            if (file.name == "server.properties") {
                loadServerProperties()
            }
            refreshWorkspaceInfo()
        }
    }

    // --- Core Download / Import / Backup / Delete ---

    fun downloadBlueLightCore(url: String = BLUELIGHT_MASTER_ZIP_URL) {
        scope.launch {
            if (!hasStorageAccess()) {
                _operationProgress.value = OperationProgress.Error("Grant storage access first to write to ${getDataDirectory().absolutePath}")
                return@launch
            }
            _operationProgress.value = OperationProgress.Running("Downloading BlueLight core...", 0)
            val tempZip = File(appContext.cacheDir, "bluelight_download.zip")
            try {
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code}: ${response.message}")
                    }
                    val body = response.body ?: throw IllegalStateException("Empty download body")
                    val totalBytes = body.contentLength()
                    body.byteStream().use { input ->
                        FileOutputStream(tempZip).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesCopied = 0L
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                bytesCopied += read
                                if (totalBytes > 0) {
                                    val pct = ((bytesCopied * 100) / totalBytes).toInt().coerceIn(0, 100)
                                    _operationProgress.value = OperationProgress.Running("Downloading BlueLight core...", pct)
                                }
                            }
                        }
                    }
                }

                if (url.endsWith(".phar", ignoreCase = true)) {
                    val destPhar = File(getDataDirectory(), "PocketMine-MP.phar")
                    tempZip.copyTo(destPhar, overwrite = true)
                    tempZip.delete()
                } else {
                    _operationProgress.value = OperationProgress.Running("Extracting server files...", null)
                    extractZipToDir(tempZip, getDataDirectory(), stripSingleTopFolder = true)
                    tempZip.delete()
                }

                refreshWorkspaceInfo()
                appendLog("\u001B[1;32m[BlueLight]\u001B[0m Core installed in ${getDataDirectory().absolutePath} (${_workspaceInfo.value.detectedCoreLabel}).")
                _operationProgress.value = OperationProgress.Success("BlueLight core installed")
            } catch (e: Exception) {
                tempZip.delete()
                val msg = e.message ?: "Download failed"
                appendLog("\u001B[1;31m[Error]\u001B[0m Core download failed: $msg")
                _operationProgress.value = OperationProgress.Error("Download failed: $msg")
            }
        }
    }

    fun importServerPackage(uri: Uri) {
        scope.launch {
            if (!hasStorageAccess()) {
                _operationProgress.value = OperationProgress.Error("Grant storage access first to write to ${getDataDirectory().absolutePath}")
                return@launch
            }
            _operationProgress.value = OperationProgress.Running("Importing server package...", null)
            try {
                val fileName = resolveFileName(uri) ?: "imported_package"
                val dataDir = getDataDirectory()
                if (!dataDir.exists()) dataDir.mkdirs()

                if (fileName.endsWith(".phar", ignoreCase = true)) {
                    val dest = File(dataDir, "PocketMine-MP.phar")
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(dest).use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw IllegalStateException("Could not open selected file")
                    appendLog("\u001B[1;32m[BlueLight]\u001B[0m Imported $fileName as PocketMine-MP.phar")
                } else {
                    val tempZip = File(appContext.cacheDir, "import_temp.zip")
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempZip).use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw IllegalStateException("Could not open selected archive")
                    extractZipToDir(tempZip, dataDir, stripSingleTopFolder = true)
                    tempZip.delete()
                    appendLog("\u001B[1;32m[BlueLight]\u001B[0m Extracted archive $fileName to ${dataDir.absolutePath}.")
                }

                refreshWorkspaceInfo()
                _operationProgress.value = OperationProgress.Success("Imported $fileName")
            } catch (e: Exception) {
                val msg = e.message ?: "Import failed"
                appendLog("\u001B[1;31m[Error]\u001B[0m Import failed: $msg")
                _operationProgress.value = OperationProgress.Error("Import failed: $msg")
            }
        }
    }

    fun backupServer(exportUri: Uri? = null) {
        scope.launch {
            if (!hasStorageAccess()) {
                _operationProgress.value = OperationProgress.Error("Storage access is required for backup")
                return@launch
            }
            _operationProgress.value = OperationProgress.Running("Creating server backup...", null)
            try {
                val dataDir = getDataDirectory()
                val backupDir = getBackupDirectory()
                if (!backupDir.exists()) backupDir.mkdirs()
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val outFile = File(backupDir, "bluelight_backup_$stamp.zip")

                ZipOutputStream(FileOutputStream(outFile)).use { zos ->
                    zipDirectory(dataDir, dataDir, zos)
                }

                if (exportUri != null) {
                    appContext.contentResolver.openOutputStream(exportUri)?.use { output ->
                        FileInputStream(outFile).use { input ->
                            input.copyTo(output)
                        }
                    }
                }

                refreshWorkspaceInfo()
                appendLog("\u001B[1;32m[BlueLight]\u001B[0m Backup created: ${outFile.absolutePath}")
                _operationProgress.value = OperationProgress.Success("Backup saved: ${outFile.name}")
            } catch (e: Exception) {
                val msg = e.message ?: "Backup failed"
                appendLog("\u001B[1;31m[Error]\u001B[0m Backup failed: $msg")
                _operationProgress.value = OperationProgress.Error("Backup failed: $msg")
            }
        }
    }

    fun deleteServerData() {
        scope.launch {
            if (!hasStorageAccess()) {
                _operationProgress.value = OperationProgress.Error("Storage access is required")
                return@launch
            }
            forceKillServer()
            _operationProgress.value = OperationProgress.Running("Deleting server workspace...", null)
            try {
                val dataDir = getDataDirectory()
                dataDir.deleteRecursively()
                dataDir.mkdirs()
                File(dataDir, "tmp").mkdirs()
                _currentRelativePath.value = ""
                refreshWorkspaceInfo()
                loadServerProperties()
                appendLog("\u001B[1;33m[BlueLight]\u001B[0m Server folder cleared: ${dataDir.absolutePath}")
                _operationProgress.value = OperationProgress.Success("Server data deleted")
            } catch (e: Exception) {
                _operationProgress.value = OperationProgress.Error("Delete failed: ${e.message}")
            }
        }
    }

    fun loadServerProperties() {
        if (!hasStorageAccess()) {
            _serverProperties.value = ServerPropertiesConfig()
            return
        }
        val propFile = File(getDataDirectory(), "server.properties")
        if (!propFile.exists()) {
            _serverProperties.value = ServerPropertiesConfig()
            return
        }
        try {
            val props = Properties()
            FileInputStream(propFile).use { props.load(it) }
            _serverProperties.value = ServerPropertiesConfig(
                motd = props.getProperty("motd", "BlueLight PocketMine Server"),
                port = props.getProperty("server-port", "19132"),
                maxPlayers = props.getProperty("max-players", "20"),
                gamemode = props.getProperty("gamemode", "0"),
                onlineMode = props.getProperty("xbox-auth", "off").equals("on", ignoreCase = true) ||
                    props.getProperty("online-mode", "false").equals("true", ignoreCase = true)
            )
        } catch (_: Exception) {
        }
    }

    fun saveServerProperties(updated: ServerPropertiesConfig) {
        scope.launch {
            if (!hasStorageAccess()) {
                _operationProgress.value = OperationProgress.Error("Storage access is required")
                return@launch
            }
            try {
                val dataDir = getDataDirectory()
                if (!dataDir.exists()) dataDir.mkdirs()
                val propFile = File(dataDir, "server.properties")
                val props = Properties()
                if (propFile.exists()) {
                    FileInputStream(propFile).use { props.load(it) }
                }
                props.setProperty("motd", updated.motd)
                props.setProperty("server-port", updated.port.ifBlank { "19132" })
                props.setProperty("max-players", updated.maxPlayers.ifBlank { "20" })
                props.setProperty("gamemode", updated.gamemode.ifBlank { "0" })
                props.setProperty("online-mode", updated.onlineMode.toString())
                FileOutputStream(propFile).use { props.store(it, "BlueLight PocketMine Server Properties") }
                _serverProperties.value = updated
                refreshWorkspaceInfo()
                appendLog("\u001B[32m[BlueLight]\u001B[0m Updated server.properties (port ${updated.port}).")
                _operationProgress.value = OperationProgress.Success("Saved server.properties")
            } catch (e: Exception) {
                _operationProgress.value = OperationProgress.Error("Failed saving properties: ${e.message}")
            }
        }
    }

    fun clearOperationProgress() {
        _operationProgress.value = OperationProgress.Idle
    }

    private fun sanitizeSingleName(raw: String): String {
        return raw.trim()
            .replace("/", "")
            .replace("\\", "")
            .replace("\u0000", "")
            .takeIf { it != "." && it != ".." }
            .orEmpty()
    }

    private suspend fun tailServerLogFile(logFile: File, startOffset: Long) {
        var offset = startOffset
        while (scope.isActive && _status.value != ServerStatus.STOPPED) {
            try {
                if (logFile.exists()) {
                    val length = logFile.length()
                    if (length < offset) {
                        offset = 0L
                    }
                    if (length > offset) {
                        RandomAccessFile(logFile, "r").use { raf ->
                            raf.seek(offset)
                            val chunkSize = (length - offset).coerceAtMost(64 * 1024L).toInt()
                            val bytes = ByteArray(chunkSize)
                            val readCount = raf.read(bytes)
                            if (readCount > 0) {
                                val lastNewlineIdx = bytes.indexOfLastByte('\n'.code.toByte(), readCount)
                                if (lastNewlineIdx >= 0) {
                                    val text = String(bytes, 0, lastNewlineIdx + 1, Charsets.UTF_8)
                                    offset += (lastNewlineIdx + 1)
                                    text.split('\n').forEach { raw ->
                                        val cleaned = raw.trimEnd('\r')
                                        if (cleaned.isNotBlank()) {
                                            appendServerLine(cleaned, fromStdout = false)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
            delay(120)
        }
    }

    private fun ByteArray.indexOfLastByte(target: Byte, limit: Int): Int {
        for (i in (limit - 1) downTo 0) {
            if (this[i] == target) return i
        }
        return -1
    }

    private fun appendServerLine(rawLine: String, fromStdout: Boolean) {
        val stripped = com.example.console.AnsiParser.stripAnsi(rawLine)
        val normalizedKey = LOG_DATE_PREFIX_REGEX.replaceFirst(stripped, "").trim()

        if (normalizedKey.isEmpty()) {
            if (fromStdout) appendLog(rawLine)
            return
        }

        val shouldEmit = synchronized(dedupLock) {
            if (fromStdout) {
                if (recentLogFileKeys.remove(normalizedKey)) {
                    false
                } else {
                    if (recentStdoutKeys.size >= MAX_DEDUP_KEYS) recentStdoutKeys.removeFirst()
                    recentStdoutKeys.addLast(normalizedKey)
                    true
                }
            } else {
                if (recentStdoutKeys.remove(normalizedKey)) {
                    false
                } else {
                    if (recentLogFileKeys.size >= MAX_DEDUP_KEYS) recentLogFileKeys.removeFirst()
                    recentLogFileKeys.addLast(normalizedKey)
                    true
                }
            }
        }

        if (shouldEmit) {
            val displayLine = if (fromStdout) {
                rawLine
            } else {
                formatServerLogLine(normalizedKey)
            }
            appendLog(displayLine)
        }
    }

    private fun formatServerLogLine(line: String): String {
        return when {
            line.contains("/ERROR]:") || line.contains("/CRITICAL]:") || line.contains("/EMERGENCY]:") || line.contains("/ALERT]:") ->
                "\u001B[1;31m$line\u001B[0m"
            line.contains("/WARNING]:") ->
                "\u001B[1;33m$line\u001B[0m"
            line.contains("/NOTICE]:") ->
                "\u001B[36m$line\u001B[0m"
            else -> line
        }
    }

    private fun ensurePhpIni(dataDir: File): File {
        val ini = File(dataDir, "php.ini")
        if (!ini.exists()) {
            val content = """
                phar.readonly=0
                phar.require_hash=1
                output_buffering=0
                implicit_flush=1
                date.timezone=UTC
                short_open_tag=0
                asp_tags=0
                opcache.enable=1
                opcache.enable_cli=1
                opcache.save_comments=1
                opcache.fast_shutdown=0
                opcache.max_accelerated_files=4096
                opcache.interned_strings_buffer=8
                opcache.memory_consumption=128
                opcache.optimization_level=0xffffffff
            """.trimIndent()
            ini.writeText(content, Charsets.UTF_8)
        }
        return ini
    }

    private fun extractZipToDir(zipFile: File, destDir: File, stripSingleTopFolder: Boolean) {
        if (!destDir.exists()) destDir.mkdirs()

        val commonPrefix = if (stripSingleTopFolder) {
            val entryNames = mutableListOf<String>()
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    entryNames.add(entry.name)
                    entry = zis.nextEntry
                }
            }
            entryNames
                .mapNotNull { it.substringBefore('/', missingDelimiterValue = "").takeIf { p -> p.isNotEmpty() } }
                .distinct()
                .singleOrNull()
                ?.let { "$it/" }
                ?: ""
        } else {
            ""
        }

        val canonicalDest = destDir.canonicalPath
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val strippedName = if (commonPrefix.isNotEmpty() && entry.name.startsWith(commonPrefix)) {
                    entry.name.removePrefix(commonPrefix)
                } else {
                    entry.name
                }
                if (strippedName.isNotEmpty()) {
                    val target = File(destDir, strippedName)
                    if (!target.canonicalPath.startsWith(canonicalDest)) {
                        throw SecurityException("Invalid zip entry path: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { out ->
                            zis.copyTo(out)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun zipDirectory(rootDir: File, currentDir: File, zos: ZipOutputStream) {
        val files = currentDir.listFiles() ?: return
        for (file in files) {
            if (file.name == "tmp") continue
            val relPath = file.relativeTo(rootDir).path
            if (file.isDirectory) {
                zipDirectory(rootDir, file, zos)
            } else {
                zos.putNextEntry(ZipEntry(relPath))
                FileInputStream(file).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    private fun resolveFileName(uri: Uri): String? {
        if (uri.scheme == "content") {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    return cursor.getString(nameIndex)
                }
            }
        }
        return uri.lastPathSegment
    }

    private suspend fun startForegroundServiceSafe() = withContext(Dispatchers.Main) {
        val intent = Intent(appContext, ServerService::class.java)
        ContextCompat.startForegroundService(appContext, intent)
    }

    private suspend fun stopForegroundServiceSafe() = withContext(Dispatchers.Main) {
        val intent = Intent(appContext, ServerService::class.java)
        appContext.stopService(intent)
    }

    companion object {
        const val MAX_CONSOLE_LINES = 2000
        const val MAX_DEDUP_KEYS = 300
        const val MAX_EDITABLE_BYTES = 512 * 1024L
        const val BLUELIGHT_MASTER_ZIP_URL = "https://github.com/BlueLightJapan/BlueLight/archive/refs/heads/master.zip"
        private val LOG_DATE_PREFIX_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}\\s+")

        @Volatile
        private var INSTANCE: ServerRepository? = null

        fun getInstance(context: Context): ServerRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ServerRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
