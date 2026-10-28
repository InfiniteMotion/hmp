package com.hmp.data.repository

import com.hmp.domain.backup.UserBackupSnapshot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D7-05 的 IO 层判据（`:shared:desktopTest`）。
 *
 * Desktop 实现只依赖 `java.io.File`，可以在 JVM 上直接实例化；三端共用的判定在
 * [com.hmp.domain.backup.BackupPathPolicyTest]，这里验的是"真的没有去碰那个文件"。
 */
class BackupFileRepositoryImplTest {

    private lateinit var root: File
    private lateinit var backupDir: File
    private lateinit var repo: BackupFileRepositoryImpl

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private fun snapshot() = UserBackupSnapshot(
        createdAt = 1_700_000_000_000L,
        appSettings = com.hmp.domain.backup.AppSettingsSnapshot(userName = "Xi"),
    )

    @BeforeTest
    fun setup() {
        root = Files.createTempDirectory("hmp-backup-test").toFile()
        backupDir = File(root, "backups").apply { mkdirs() }
        repo = BackupFileRepositoryImpl(json, backupDir)
    }

    @AfterTest
    fun teardown() {
        root.deleteRecursively()
    }

    @Test
    fun outsideFile_loadAndDeleteAreRejected_andFileIsUntouched() = runTest {
        val outside = File(root, "outside.json")
        repo.saveBackup(snapshot()).getOrThrow() // 保证备份目录存在且有内容可比对
        outside.writeText("""{"not":"a backup"}""")

        val load = repo.loadBackup(outside.absolutePath)
        val delete = repo.deleteBackup(outside.absolutePath)

        assertTrue(load.isFailure, "备份目录之外的文件不该被读取")
        assertTrue(delete.isFailure, "备份目录之外的文件不该被删除")
        assertTrue(outside.exists(), "越界文件必须原样留在原地")
        assertEquals("""{"not":"a backup"}""", outside.readText(), "内容也不能被动过")
    }

    @Test
    fun dotDotEscape_isRejected() = runTest {
        // `..` 逃到备份目录的父目录（仍在临时根内，teardown 能清掉，不会往系统 Temp 里丢垃圾）
        val escaped = File(backupDir, "../escape.json")
        escaped.writeText("{}")

        assertTrue(repo.loadBackup(escaped.absolutePath).isFailure)
        assertTrue(repo.deleteBackup(escaped.absolutePath).isFailure)
        assertTrue(escaped.exists())
    }

    @Test
    fun saveThenLoad_roundTrip_throughOwnedPath() = runTest {
        val path = repo.saveBackup(snapshot()).getOrThrow()

        assertTrue(path.startsWith(backupDir.absolutePath), "写出的文件必须落在备份目录内")
        val loaded = repo.loadBackup(path).getOrThrow()
        assertEquals("Xi", loaded.appSettings?.userName)
        assertEquals(1_700_000_000_000L, loaded.createdAt)
    }

    @Test
    fun importExternalFile_copiesIntoBackupDir_andBecomesListed() = runTest {
        val outside = File(root, "picked.json")
        outside.writeText(json.encodeToString(UserBackupSnapshot.serializer(), snapshot()))

        val imported = repo.importExternalFile(outside.absolutePath).getOrThrow()

        assertTrue(imported.startsWith(backupDir.absolutePath), "导入的副本必须在备份目录内")
        assertTrue(outside.exists(), "导入是复制，原文件不该被搬走或删掉")
        assertEquals("Xi", repo.loadBackup(imported).getOrThrow().appSettings?.userName)
        assertTrue(
            repo.getBackups().getOrThrow().contains(imported),
            "导入的副本要能在本地备份列表里看到，否则用户无法在恢复失败后手工找回",
        )
    }

    @Test
    fun importExternalFile_onOwnedPath_isIdempotent() = runTest {
        val saved = repo.saveBackup(snapshot()).getOrThrow()

        val again = repo.importExternalFile(saved).getOrThrow()

        assertEquals(saved, again, "已在备份目录内的路径原样返回，不该再复制一份")
        assertEquals(1, repo.getBackups().getOrThrow().size)
    }

    @Test
    fun importExternalFile_missingSource_failsWithoutCreatingAnything() = runTest {
        val result = repo.importExternalFile(File(root, "nope.json").absolutePath)

        assertTrue(result.isFailure)
        assertFalse(backupDir.listFiles()?.any { it.name.startsWith("hearable-backup-imported") } ?: false)
    }
}
