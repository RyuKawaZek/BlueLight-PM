package com.example

import com.example.server.WorkspaceFileOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class FileOperationsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun moveEntries_movesFilesAndBlocksSelfDescendantOrSameParent() {
        val root = tempFolder.newFolder("workspace")
        val plugins = File(root, "plugins").apply { mkdirs() }
        val nested = File(plugins, "MyPlugin").apply { mkdirs() }
        File(nested, "plugin.yml").writeText("name: MyPlugin")
        val fileA = File(root, "ops.txt").apply { writeText("Steve") }
        val fileB = File(root, "whitelist.txt").apply { writeText("Alex") }

        val res = WorkspaceFileOps.moveEntries(
            rootDir = root,
            sourcePaths = listOf(fileA.absolutePath, fileB.absolutePath),
            destRelativePath = "plugins"
        )
        assertEquals(2, res.succeeded)
        assertEquals(0, res.skippedOrFailed)
        assertFalse(fileA.exists())
        assertTrue(File(plugins, "ops.txt").exists())
        assertTrue(File(plugins, "whitelist.txt").exists())

        // Trying to move plugins into plugins/MyPlugin (descendant) must be blocked
        val selfDescendant = WorkspaceFileOps.moveEntries(
            rootDir = root,
            sourcePaths = listOf(plugins.absolutePath),
            destRelativePath = "plugins/MyPlugin"
        )
        assertEquals(0, selfDescendant.succeeded)
        assertEquals(1, selfDescendant.skippedOrFailed)
        assertTrue(plugins.exists())
    }

    @Test
    fun copyEntries_createsUniqueCopyNamesOnConflictAndCopiesFoldersRecursively() {
        val root = tempFolder.newFolder("workspace_copy")
        val target = File(root, "archive").apply { mkdirs() }
        val config = File(root, "server.properties").apply { writeText("motd=BlueLight") }
        File(target, "server.properties").writeText("motd=Existing")

        val world = File(root, "worlds/lobby").apply { mkdirs() }
        File(world, "level.dat").writeText("world-data")

        val res = WorkspaceFileOps.copyEntries(
            rootDir = root,
            sourcePaths = listOf(config.absolutePath, world.absolutePath),
            destRelativePath = "archive"
        )
        assertEquals(2, res.succeeded)
        assertEquals(0, res.skippedOrFailed)
        assertTrue(config.exists())
        assertEquals("motd=Existing", File(target, "server.properties").readText())
        assertEquals("motd=BlueLight", File(target, "server_copy.properties").readText())
        assertEquals("world-data", File(target, "lobby/level.dat").readText())
    }

    @Test
    fun zipEntries_bundlesMultipleFilesAndDirectoriesWithUniqueArchiveName() {
        val root = tempFolder.newFolder("workspace_zip")
        val f1 = File(root, "ops.txt").apply { writeText("Admin") }
        val pluginDir = File(root, "plugins/Test").apply { mkdirs() }
        File(pluginDir, "main.php").writeText("<?php")

        val zip1 = WorkspaceFileOps.zipEntries(
            rootDir = root,
            targetDir = root,
            sourcePaths = listOf(f1.absolutePath, File(root, "plugins").absolutePath),
            archiveName = "bundle.zip"
        )
        assertTrue(zip1.exists())
        assertEquals("bundle.zip", zip1.name)

        ZipFile(zip1).use { zf ->
            val names = zf.entries().toList().map { it.name.replace("\\", "/") }
            assertTrue("ops.txt" in names)
            assertTrue("plugins/Test/main.php" in names)
        }

        val zip2 = WorkspaceFileOps.zipEntries(
            rootDir = root,
            targetDir = root,
            sourcePaths = listOf(f1.absolutePath),
            archiveName = "bundle.zip"
        )
        assertEquals("bundle_copy.zip", zip2.name)
    }

    @Test
    fun deleteEntries_deletesInsideRootAndBlocksTraversalOutsideRoot() {
        val outside = tempFolder.newFile("outside_secret.txt").apply { writeText("keep") }
        val root = tempFolder.newFolder("workspace_del")
        val victim = File(root, "temp.log").apply { writeText("bye") }
        val victimDir = File(root, "old_world").apply { mkdirs() }
        File(victimDir, "chunk.dat").writeText("123")

        val res = WorkspaceFileOps.deleteEntries(
            rootDir = root,
            sourcePaths = listOf(victim.absolutePath, victimDir.absolutePath, outside.absolutePath)
        )
        assertEquals(2, res.succeeded)
        assertEquals(1, res.skippedOrFailed)
        assertFalse(victim.exists())
        assertFalse(victimDir.exists())
        assertTrue(outside.exists())
    }

    @Test
    fun listDirectories_listsWorkspaceTreeExcludingTmp() {
        val root = tempFolder.newFolder("workspace_dirs")
        File(root, "plugins/Auth").mkdirs()
        File(root, "worlds").mkdirs()
        File(root, "tmp/cache").mkdirs()

        val dirs = WorkspaceFileOps.listDirectories(root)
        assertEquals(listOf("", "plugins", "plugins/Auth", "worlds"), dirs)
    }
}
