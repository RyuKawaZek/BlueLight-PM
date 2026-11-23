package com.example.server

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class BatchFileOpResult(
    val succeeded: Int,
    val skippedOrFailed: Int
)

/**
 * Pure JVM file-system operations scoped to a server workspace root directory.
 */
object WorkspaceFileOps {

    fun deleteEntries(rootDir: File, sourcePaths: List<String>): BatchFileOpResult {
        val rootCanon = rootDir.canonicalPath
        var deleted = 0
        var failed = 0
        sourcePaths.distinct().forEach { path ->
            val ok = runCatching {
                val target = File(path)
                val targetCanon = target.canonicalPath
                when {
                    !isWithinRoot(targetCanon, rootCanon) || targetCanon == rootCanon -> false
                    !target.exists() -> true
                    target.isDirectory -> target.deleteRecursively()
                    else -> target.delete()
                }
            }.getOrDefault(false)
            if (ok) deleted += 1 else failed += 1
        }
        return BatchFileOpResult(succeeded = deleted, skippedOrFailed = failed)
    }

    fun moveEntries(
        rootDir: File,
        sourcePaths: List<String>,
        destRelativePath: String
    ): BatchFileOpResult {
        val rootCanon = rootDir.canonicalPath
        val destDir = resolveSafeDirectory(rootDir, destRelativePath)
        val destCanon = destDir.canonicalPath
        if (!isWithinRoot(destCanon, rootCanon)) {
            return BatchFileOpResult(succeeded = 0, skippedOrFailed = sourcePaths.distinct().size)
        }
        if (!destDir.exists()) destDir.mkdirs()

        var moved = 0
        var skipped = 0
        sourcePaths.distinct().forEach { path ->
            val ok = runCatching {
                val src = File(path)
                if (!src.exists()) return@runCatching false
                val srcCanon = src.canonicalPath
                if (!isWithinRoot(srcCanon, rootCanon) || srcCanon == rootCanon) return@runCatching false
                if (src.parentFile?.canonicalPath == destCanon) return@runCatching false
                if (src.isDirectory && isSameOrDescendant(destCanon, srcCanon)) return@runCatching false

                val target = File(destDir, src.name)
                if (target.exists()) return@runCatching false

                if (src.renameTo(target)) {
                    true
                } else {
                    val copied = if (src.isDirectory) {
                        src.copyRecursively(target, overwrite = false)
                    } else {
                        src.copyTo(target, overwrite = false)
                        true
                    }
                    if (copied) {
                        if (src.isDirectory) src.deleteRecursively() else src.delete()
                        true
                    } else {
                        false
                    }
                }
            }.getOrDefault(false)
            if (ok) moved += 1 else skipped += 1
        }
        return BatchFileOpResult(succeeded = moved, skippedOrFailed = skipped)
    }

    fun copyEntries(
        rootDir: File,
        sourcePaths: List<String>,
        destRelativePath: String
    ): BatchFileOpResult {
        val rootCanon = rootDir.canonicalPath
        val destDir = resolveSafeDirectory(rootDir, destRelativePath)
        val destCanon = destDir.canonicalPath
        if (!isWithinRoot(destCanon, rootCanon)) {
            return BatchFileOpResult(succeeded = 0, skippedOrFailed = sourcePaths.distinct().size)
        }
        if (!destDir.exists()) destDir.mkdirs()

        var copied = 0
        var skipped = 0
        sourcePaths.distinct().forEach { path ->
            val ok = runCatching {
                val src = File(path)
                if (!src.exists()) return@runCatching false
                val srcCanon = src.canonicalPath
                if (!isWithinRoot(srcCanon, rootCanon) || srcCanon == rootCanon) return@runCatching false
                if (src.isDirectory && isSameOrDescendant(destCanon, srcCanon)) return@runCatching false

                val target = resolveUniqueChild(destDir, src.name)
                if (src.isDirectory) {
                    src.copyRecursively(target, overwrite = false)
                } else {
                    src.copyTo(target, overwrite = false)
                    true
                }
            }.getOrDefault(false)
            if (ok) copied += 1 else skipped += 1
        }
        return BatchFileOpResult(succeeded = copied, skippedOrFailed = skipped)
    }

    fun zipEntries(
        rootDir: File,
        targetDir: File,
        sourcePaths: List<String>,
        archiveName: String
    ): File {
        val rootCanon = rootDir.canonicalPath
        val outFile = resolveUniqueChild(targetDir, archiveName)
        try {
            ZipOutputStream(FileOutputStream(outFile)).use { zos ->
                sourcePaths.distinct().forEach { path ->
                    val src = File(path)
                    val srcCanon = runCatching { src.canonicalPath }.getOrNull()
                    if (src.exists() &&
                        srcCanon != null &&
                        isWithinRoot(srcCanon, rootCanon) &&
                        srcCanon != outFile.canonicalPath
                    ) {
                        val baseRoot = src.parentFile ?: targetDir
                        if (src.isDirectory) {
                            zipDirectory(baseRoot, src, zos)
                        } else {
                            zos.putNextEntry(ZipEntry(src.name))
                            FileInputStream(src).use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
            }
            return outFile
        } catch (e: Exception) {
            outFile.delete()
            throw e
        }
    }

    fun listDirectories(rootDir: File, maxDepth: Int = 4, limit: Int = 150): List<String> {
        if (!rootDir.exists()) return listOf("")
        val dirs = mutableListOf("")
        fun walk(dir: File, depth: Int) {
            if (depth > maxDepth || dirs.size >= limit) return
            dir.listFiles()
                ?.filter { it.isDirectory && it.name != "tmp" }
                ?.sortedBy { it.name.lowercase(Locale.US) }
                ?.forEach { child ->
                    if (dirs.size < limit) {
                        dirs.add(child.relativeTo(rootDir).path.replace("\\", "/"))
                        walk(child, depth + 1)
                    }
                }
        }
        walk(rootDir, 0)
        return dirs
    }

    fun zipDirectory(rootDir: File, currentDir: File, zos: ZipOutputStream) {
        val files = currentDir.listFiles() ?: return
        for (file in files) {
            if (file.name == "tmp") continue
            val relPath = file.relativeTo(rootDir).path.replace("\\", "/")
            if (file.isDirectory) {
                zipDirectory(rootDir, file, zos)
            } else {
                zos.putNextEntry(ZipEntry(relPath))
                FileInputStream(file).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    fun resolveSafeDirectory(rootDir: File, relativePath: String): File {
        val cleanRel = relativePath.trim('/').replace("\\", "/")
        val candidate = if (cleanRel.isEmpty()) rootDir else File(rootDir, cleanRel)
        val candCanon = runCatching { candidate.canonicalPath }.getOrNull() ?: return rootDir
        val rootCanon = runCatching { rootDir.canonicalPath }.getOrNull() ?: return rootDir
        return if (isWithinRoot(candCanon, rootCanon)) candidate else rootDir
    }

    fun resolveUniqueChild(destDir: File, originalName: String): File {
        val initial = File(destDir, originalName)
        if (!initial.exists()) return initial
        val dotIdx = originalName.lastIndexOf('.')
        val base = if (dotIdx > 0) originalName.substring(0, dotIdx) else originalName
        val ext = if (dotIdx > 0) originalName.substring(dotIdx) else ""
        var counter = 1
        while (true) {
            val suffix = if (counter == 1) "_copy" else "_copy$counter"
            val candidate = File(destDir, "$base$suffix$ext")
            if (!candidate.exists()) return candidate
            counter += 1
        }
    }

    private fun isWithinRoot(targetCanon: String, rootCanon: String): Boolean {
        return targetCanon == rootCanon || targetCanon.startsWith("$rootCanon${File.separator}")
    }

    private fun isSameOrDescendant(targetCanon: String, ancestorCanon: String): Boolean {
        return targetCanon == ancestorCanon || targetCanon.startsWith("$ancestorCanon${File.separator}")
    }

    data class ServerCoreResolution(
        val label: String,
        val entryFile: File?,
        val hasPhar: Boolean,
        val hasSrc: Boolean
    )

    fun resolveServerCore(dir: File): ServerCoreResolution {
        if (!dir.exists() || !dir.isDirectory) {
            return ServerCoreResolution(
                label = "No server core installed",
                entryFile = null,
                hasPhar = false,
                hasSrc = false
            )
        }

        val defaultPhar = File(dir, "PocketMine-MP.phar")
        if (defaultPhar.exists() && defaultPhar.isFile) {
            return ServerCoreResolution(
                label = defaultPhar.name,
                entryFile = defaultPhar,
                hasPhar = true,
                hasSrc = false
            )
        }

        val anyPhar = dir.listFiles()
            ?.filter { it.isFile && !it.name.startsWith(".") && it.extension.equals("phar", ignoreCase = true) }
            ?.minByOrNull { it.name.lowercase(Locale.US) }
        if (anyPhar != null) {
            return ServerCoreResolution(
                label = anyPhar.name,
                entryFile = anyPhar,
                hasPhar = true,
                hasSrc = false
            )
        }

        val legacySrc = File(dir, "src/pocketmine/PocketMine.php")
        if (legacySrc.exists() && legacySrc.isFile) {
            return ServerCoreResolution(
                label = "src/pocketmine/PocketMine.php",
                entryFile = legacySrc,
                hasPhar = false,
                hasSrc = true
            )
        }

        val altSrc = File(dir, "src/PocketMine.php")
        if (altSrc.exists() && altSrc.isFile) {
            return ServerCoreResolution(
                label = "src/PocketMine.php",
                entryFile = altSrc,
                hasPhar = false,
                hasSrc = true
            )
        }

        return ServerCoreResolution(
            label = "No server core installed",
            entryFile = null,
            hasPhar = false,
            hasSrc = false
        )
    }

    private val IGNORED_EXTERNAL_DIRS = setOf(
        "android", "dcim", "download", "downloads", "music", "pictures",
        "movies", "documents", "alarms", "notifications", "ringtones",
        "podcasts", "audiobooks", "pocketmine", "pocketmine-profiles",
        "pocketmine-backups", "lost+found"
    )

    fun discoverStorageProfiles(
        profilesRoot: File,
        externalRoot: File,
        existingProfiles: List<ServerProfile>
    ): List<ServerProfile> {
        val defaultProfile = ServerProfile.defaultProfile()
        val kept = mutableListOf<ServerProfile>()
        kept.add(defaultProfile)

        val knownProfileDirs = mutableSetOf<String>()
        val knownCustomCanonPaths = mutableSetOf<String>()

        existingProfiles.filter { !it.isDefault && it.id != ServerProfile.DEFAULT_PROFILE_ID }.forEach { profile ->
            if (profile.customPath.isNotEmpty()) {
                val dir = File(profile.customPath)
                if (dir.exists() && dir.isDirectory) {
                    kept.add(profile)
                    runCatching { knownCustomCanonPaths.add(dir.canonicalPath) }
                } else if (!profile.id.startsWith("discovered_")) {
                    kept.add(profile)
                    runCatching { knownCustomCanonPaths.add(dir.canonicalPath) }
                }
            } else {
                val dir = File(profilesRoot, profile.directoryName)
                if (dir.exists() && dir.isDirectory || !profile.id.startsWith("discovered_")) {
                    kept.add(profile)
                    knownProfileDirs.add(profile.directoryName.lowercase(Locale.US))
                }
            }
        }

        // 1. Scan /storage/emulated/0/PocketMine-Profiles/*
        if (profilesRoot.exists() && profilesRoot.isDirectory) {
            profilesRoot.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") && it.name != "tmp" }
                ?.sortedBy { it.name.lowercase(Locale.US) }
                ?.forEach { dir ->
                    val dirKey = dir.name.lowercase(Locale.US)
                    if (knownProfileDirs.add(dirKey)) {
                        kept.add(
                            ServerProfile(
                                id = "discovered_prof_$dirKey",
                                name = formatProfileName(dir.name),
                                directoryName = dir.name,
                                isDefault = false,
                                createdAtMs = dir.lastModified(),
                                customPath = ""
                            )
                        )
                    }
                }
        }

        // 2. Scan /storage/emulated/0/* for existing server workspaces (e.g. BlueLight, PocketMine-MP, etc.)
        if (externalRoot.exists() && externalRoot.isDirectory) {
            externalRoot.listFiles()
                ?.filter { dir ->
                    dir.isDirectory &&
                        !dir.name.startsWith(".") &&
                        dir.name.lowercase(Locale.US) !in IGNORED_EXTERNAL_DIRS
                }
                ?.sortedBy { it.name.lowercase(Locale.US) }
                ?.forEach { dir ->
                    val core = resolveServerCore(dir)
                    val looksLikeServer = core.entryFile != null ||
                        File(dir, "pocketmine.yml").isFile ||
                        (File(dir, "server.properties").isFile &&
                            (File(dir, "plugins").isDirectory ||
                                File(dir, "worlds").isDirectory ||
                                File(dir, "src").isDirectory))
                    if (looksLikeServer) {
                        val canon = runCatching { dir.canonicalPath }.getOrNull() ?: dir.absolutePath
                        if (knownCustomCanonPaths.add(canon)) {
                            val slugKey = ServerProfile.sanitizeSlug(dir.name)
                            kept.add(
                                ServerProfile(
                                    id = "discovered_ext_$slugKey",
                                    name = dir.name,
                                    directoryName = dir.name,
                                    isDefault = false,
                                    createdAtMs = dir.lastModified(),
                                    customPath = dir.absolutePath
                                )
                            )
                        }
                    }
                }
        }

        return kept
    }

    private fun formatProfileName(folderName: String): String {
        val hasUpper = folderName.any { it.isUpperCase() }
        if (hasUpper) return folderName.replace('-', ' ').replace('_', ' ')
        return folderName.split('-', '_')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { ch -> ch.titlecase(Locale.US) }
            }
            .ifEmpty { folderName }
    }
}
