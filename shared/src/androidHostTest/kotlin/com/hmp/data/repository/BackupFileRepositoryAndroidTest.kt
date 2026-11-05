package com.hmp.data.repository

import android.content.Context
import com.hmp.domain.backup.BackupFileRepository
import com.hmp.domain.backup.UserBackupSnapshot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * X-06：`BackupFileRepositoryImpl.android`（140 行）此前零测试。
 *
 * Desktop 侧同判据已有 [BackupFileRepositoryImplTest]（`:shared:desktopTest`，D7-05），
 * 但 Android 是另一份实现：`backupDir` 来自 `context.filesDir`（Robolectric 给真实临时目录），
 * 路径守卫、导入复制、列表过滤都是**照着 Desktop 手抄**的那类代码 —— 正是最容易抄错一笔
 * 的地方（D7-05 的守卫在 Android 侧如果抄丢了，是安全洞，不是功能 bug）。
 *
 * 所以这里按 desktop 判据原样重钉一遍：守卫必须真的拒绝、越界文件必须原样还在。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupFileRepositoryAndroidTest {

    private lateinit var context: Context
    private lateinit var repo: BackupFileRepositoryImpl

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @BeforeTest
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        repo = BackupFileRepositoryImpl(context, json)
    }

    @AfterTest
    fun teardown() {
        // Robolectric 的 filesDir 是本次运行的临时目录，直接清掉防止跨用例串扰
        context.filesDir.deleteRecursively()
    }

    private fun backupDir(): File = File(context.filesDir, "backups")

    private fun snapshot() = UserBackupSnapshot(
        createdAt = 1_700_000_000_000L,
        appSettings = com.hmp.domain.backup.AppSettingsSnapshot(userName = "Xi"),
    )

    /** D7-05 的安全判据：备份目录之外的文件，读与删都必须拒绝，且文件原样无伤。 */
    @Test
    fun outsideFile_loadAndDeleteAreRejected_andFileIsUntouched() = runTest {
        val outside = File(context.filesDir, "outside.json")
        repo.saveBackup(snapshot()).getOrThrow() // 保证备份目录存在且有内容可比对
        outside.writeText("""{"not":"a backup"}""")

        val load = repo.loadBackup(outside.absolutePath)
        val delete = repo.deleteBackup(outside.absolutePath)

        assertTrue(load.isFailure, "备份目录之外的文件不该被读取")
        assertTrue(delete.isFailure, "备份目录之外的文件不该被删除")
        assertTrue(outside.exists(), "越界文件必须原样留在原地")
        assertEquals("""{"not":"a backup"}""", outside.readText(), "内容也不能被动过")
    }

    /** `..` 逃出备份目录也不行（仍落在临时根内，teardown 能清掉）。 */
    @Test
    fun dotDotEscape_isRejected() = runTest {
        val escaped = File(backupDir(), "../escape.json")
        escaped.writeText("{}")

        assertTrue(repo.loadBackup(escaped.absolutePath).isFailure)
        assertTrue(repo.deleteBackup(escaped.absolutePath).isFailure)
        assertTrue(escaped.exists())
    }

    /** 存 → 读往返，且落点必须是 `filesDir/backups`。 */
    @Test
    fun saveThenLoad_roundTrip_landsUnderFilesDirBackups() = runTest {
        val path = repo.saveBackup(snapshot()).getOrThrow()

        assertTrue(path.startsWith(backupDir().absolutePath), "写出的文件必须落在备份目录内")
        val loaded = repo.loadBackup(path).getOrThrow()
        assertEquals("Xi", loaded.appSettings?.userName)
        assertEquals(1_700_000_000_000L, loaded.createdAt)
    }

    /**
     * 列表只认 [BackupFileRepository.isBackupFileName]，且按 `lastModified` 新的在前。
     * 杂物（别的名字、别的扩展名）不能混进恢复列表。
     */
    @Test
    fun getBackups_filtersNonBackupFiles_andSortsNewestFirst() = runTest {
        // saveBackup 返回的已是绝对路径，不要再包一层 File(backupDir(), …)
        val user = repo.saveBackup(snapshot(), BackupFileRepository.USER_BACKUP_PREFIX).getOrThrow()
        val preRestore = repo.saveBackup(snapshot(), BackupFileRepository.PRE_RESTORE_PREFIX).getOrThrow()
        File(backupDir(), "hearable-backup-notjson.txt").writeText("x") // 前缀对但扩展名不对
        File(backupDir(), "random.json").writeText("x") // 扩展名对但前缀不对

        // 手工把修改时间拉开（同秒内连写两次会拿到同一秒的时间戳，排不出稳定次序）
        File(user).setLastModified(1_000_000L)
        File(preRestore).setLastModified(2_000_000L)

        val listed = repo.getBackups().getOrThrow()

        assertEquals(listOf(preRestore, user), listed, "新的在前，杂物不进列表")
    }

    /** 外部挑进来的文件被复制进备份目录并出现在列表里；原文件不搬走。 */
    @Test
    fun importExternalFile_copiesIntoBackupDir_andBecomesListed() = runTest {
        val outside = File(context.filesDir, "picked.json")
        outside.writeText(json.encodeToString(UserBackupSnapshot.serializer(), snapshot()))

        val imported = repo.importExternalFile(outside.absolutePath).getOrThrow()

        assertTrue(imported.startsWith(backupDir().absolutePath), "导入的副本必须在备份目录内")
        assertTrue(outside.exists(), "导入是复制，原文件不该被搬走或删掉")
        assertEquals("Xi", repo.loadBackup(imported).getOrThrow().appSettings?.userName)
        assertTrue(
            repo.getBackups().getOrThrow().contains(imported),
            "导入的副本要能在本地备份列表里看到，否则恢复失败后用户无法手工找回",
        )
    }

    /** 已在备份目录内的路径原样返回（幂等），不复制第二份。 */
    @Test
    fun importExternalFile_onOwnedPath_isIdempotent() = runTest {
        val saved = repo.saveBackup(snapshot()).getOrThrow()

        val again = repo.importExternalFile(saved).getOrThrow()

        assertEquals(saved, again)
        assertEquals(1, repo.getBackups().getOrThrow().size)
    }

    /** 来源不存在时不许留下任何半成品副本。 */
    @Test
    fun importExternalFile_missingSource_failsWithoutCreatingAnything() = runTest {
        val result = repo.importExternalFile(File(context.filesDir, "nope.json").absolutePath)

        assertTrue(result.isFailure)
        assertFalse(
            backupDir().listFiles()?.any { it.name.startsWith(BackupFileRepository.IMPORTED_PREFIX) } ?: false,
        )
    }
}
