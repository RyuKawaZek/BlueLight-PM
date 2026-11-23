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
}
