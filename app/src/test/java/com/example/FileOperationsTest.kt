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

    @Test
    fun resolveServerCore_detectsStandardCustomPharAndSrcEntryPoints() {
        val standardDir = tempFolder.newFolder("core_standard")
        File(standardDir, "PocketMine-MP.phar").writeText("phar")
        assertEquals("PocketMine-MP.phar", WorkspaceFileOps.resolveServerCore(standardDir).label)

        val customPharDir = tempFolder.newFolder("core_custom_phar")
        File(customPharDir, "BlueLight.phar").writeText("phar")
        val customRes = WorkspaceFileOps.resolveServerCore(customPharDir)
        assertTrue(customRes.hasPhar)
        assertEquals("BlueLight.phar", customRes.label)

        val legacySrcDir = tempFolder.newFolder("core_legacy_src")
        File(legacySrcDir, "src/pocketmine").mkdirs()
        File(legacySrcDir, "src/pocketmine/PocketMine.php").writeText("<?php")
        val legacyRes = WorkspaceFileOps.resolveServerCore(legacySrcDir)
        assertTrue(legacyRes.hasSrc)
        assertEquals("src/pocketmine/PocketMine.php", legacyRes.label)

        val altSrcDir = tempFolder.newFolder("core_alt_src")
        File(altSrcDir, "src").mkdirs()
        File(altSrcDir, "src/PocketMine.php").writeText("<?php")
        val altRes = WorkspaceFileOps.resolveServerCore(altSrcDir)
        assertTrue(altRes.hasSrc)
        assertEquals("src/PocketMine.php", altRes.label)

        val emptyDir = tempFolder.newFolder("core_empty")
        assertEquals(null, WorkspaceFileOps.resolveServerCore(emptyDir).entryFile)
    }

    @Test
    fun discoverStorageProfiles_detectsProfilesInProfilesRootAndExternalRootWhileIgnoringSystemDirs() {
        val extRoot = tempFolder.newFolder("ext_storage")
        val profilesRoot = File(extRoot, "PocketMine-Profiles").apply { mkdirs() }
        File(extRoot, "PocketMine").mkdirs()
        File(extRoot, "PocketMine-Backups").mkdirs()

        // Existing subfolders in PocketMine-Profiles
        File(profilesRoot, "survival").mkdirs()
        File(profilesRoot, "creative_hub").apply {
            mkdirs()
            File(this, "Genisys.phar").writeText("phar")
        }
        File(profilesRoot, "tmp").mkdirs() // should be skipped

        // External server workspaces at root level
        File(extRoot, "BlueLight").apply {
            mkdirs()
            File(this, "BlueLight.phar").writeText("phar")
        }
        File(extRoot, "PocketMine-Legacy").apply {
            mkdirs()
            File(this, "server.properties").writeText("motd=Legacy")
            File(this, "plugins").mkdirs()
        }

        // System/ignored folders should never be added even if they contain files
        File(extRoot, "DCIM").apply {
            mkdirs()
            File(this, "test.phar").writeText("ignored")
        }
        File(extRoot, "Android").apply {
            mkdirs()
            File(this, "pocketmine.yml").writeText("ignored: true")
        }

        val discovered = WorkspaceFileOps.discoverStorageProfiles(
            profilesRoot = profilesRoot,
            externalRoot = extRoot,
            existingProfiles = emptyList()
        )

        val ids = discovered.map { it.id }
        assertTrue(com.example.server.ServerProfile.DEFAULT_PROFILE_ID in ids)
        assertTrue("discovered_prof_survival" in ids)
        assertTrue("discovered_prof_creative_hub" in ids)
        assertFalse("discovered_prof_tmp" in ids)

        val blueLight = discovered.firstOrNull { it.name == "BlueLight" }
        assertTrue(blueLight != null)
        assertEquals(File(extRoot, "BlueLight").absolutePath, blueLight!!.customPath)

        assertTrue(discovered.any { it.name == "PocketMine-Legacy" })
        assertFalse(discovered.any { it.name.equals("DCIM", ignoreCase = true) })
        assertFalse(discovered.any { it.name.equals("Android", ignoreCase = true) })
    }
}
