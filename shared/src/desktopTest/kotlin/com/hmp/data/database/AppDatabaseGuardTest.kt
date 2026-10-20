package com.hmp.data.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * 一-7 / D7-10 判据：库版本高于代码时要**拦得住、说得出、看得见**，而不是崩在启动路上。
 *
 * 用真 `BundledSQLiteDriver` 造库文件（与三端 builder 同一个驱动）—— 守卫读的是 `PRAGMA user_version`，
 * 用假版本号喂 `judge` 只能证明 `if` 写对了，证明不了"探得到真库的版"。
 * 全程只碰临时文件，绝不碰 `~/.hmp` 里那份真库。
 */
class AppDatabaseGuardTest {

    private val codeVersion = AppDatabase.CODE_SCHEMA_VERSION

    private companion object {
        val fileCounter = AtomicInteger()
    }

    private fun newTempDbPath(label: String): String =
        File("build", "guard_test_${label}_${fileCounter.incrementAndGet()}.db").absolutePath.also { createdFiles += it }

    @BeforeTest
    fun setup() {
        AppDatabaseGuard.reset()
    }

    @AfterTest
    fun teardown() {
        AppDatabaseGuard.reset()
        createdFiles.forEach { runCatching { File(it).delete() } }
    }

    private val createdFiles = mutableListOf<String>()

    /** 临时库文件（与迁移测试同法：唯一文件名，避免 Windows 上互相继承旧库）。 */
    /** 建一个临时库文件并把 `user_version` 写成指定值（模拟"新设备的库被拷回旧安装"）。 */
    private fun dbFileWithUserVersion(version: Int): String {
        val path = newTempDbPath("v$version")
        val connection = BundledSQLiteDriver().open(path)
        try {
            connection.execSQL("CREATE TABLE probe (id INTEGER PRIMARY KEY)")
            connection.execSQL("PRAGMA user_version = $version")
        } finally {
            connection.close()
        }
        return path
    }

    // ── 纯判定：三端共用的那条规则本身 ────────────────────────────────

    @Test
    fun onlyNewerThanCode_isRefused() {
        assertNull(AppDatabaseGuard.judge(dbVersion = codeVersion, codeVersion = codeVersion), "同版本是正常打开")
        assertNull(AppDatabaseGuard.judge(dbVersion = codeVersion - 1, codeVersion = codeVersion), "落后版本该走迁移链")
        assertNull(AppDatabaseGuard.judge(dbVersion = null, codeVersion = codeVersion), "探不到版本不当成降级")
        assertNull(AppDatabaseGuard.judge(dbVersion = 0, codeVersion = codeVersion), "user_version=0 是空库/新库")

        val verdict = AppDatabaseGuard.judge(dbVersion = codeVersion + 1, codeVersion = codeVersion)
        assertEquals(codeVersion + 1, verdict?.dbVersion)
        assertEquals(codeVersion, verdict?.codeVersion)
    }

    // ── 真文件探测 ──────────────────────────────────────────────────

    @Test
    fun enforce_throwsTypedSignal_whenFileIsNewerThanCode() {
        val path = dbFileWithUserVersion(codeVersion + 1)

        val error = assertFailsWith<DatabaseTooNewException> {
            AppDatabaseGuard.enforce(path, codeVersion)
        }

        assertEquals(codeVersion + 1, error.state.dbVersion, "异常里必须带得出版本号，否则日志与文案都说不清")
        assertEquals(codeVersion, error.state.codeVersion)
        // 判据要求的"异常类型被收敛"：冒出去的是这个类型，不是 Room 的 "Migration … not found"
        assertEquals(false, error.message?.contains("migration"), "消息应是给人看的说明：${error.message}")
        assertTrue(error.message!!.contains("数据未被修改"), "必须讲清数据没动 —— 这是用户敢关应用的依据")
    }

    @Test
    fun enforce_publishesTheVisibleState_forTheUiBranch() {
        val path = dbFileWithUserVersion(codeVersion + 1)

        assertFailsWith<DatabaseTooNewException> {
            AppDatabaseGuard.enforce(path, codeVersion)
        }

        // UI 根在解析任何 DAO 之前读这一个值决定分流；守卫只抛不挂状态的话，界面就没有可依据的信号
        assertEquals(DatabaseTooNew(dbVersion = codeVersion + 1, codeVersion = codeVersion), AppDatabaseGuard.tooNew)
    }

    @Test
    fun enforce_passesThrough_whenFileIsSameOrOlder() {
        val same = dbFileWithUserVersion(codeVersion)
        AppDatabaseGuard.enforce(same, codeVersion)
        assertNull(AppDatabaseGuard.tooNew, "同版本不该留下任何守卫痕迹")

        val older = dbFileWithUserVersion(codeVersion - 1)
        AppDatabaseGuard.enforce(older, codeVersion)
        assertNull(AppDatabaseGuard.tooNew, "落后版本由迁移链处理，不是降级")
    }

    /** 没有库文件 = 全新安装：既不拦，也不能顺手把文件建出来。 */
    @Test
    fun enforce_doesNotCreateTheDatabaseFile() {
        val path = newTempDbPath("absent")
        assertFalse(File(path).exists(), "前置条件：文件本来不存在")

        AppDatabaseGuard.enforce(path, codeVersion)

        assertNull(AppDatabaseGuard.tooNew)
        assertFalse(File(path).exists(), "守卫只读不建 —— 建库是 Room 的事，探版本探出副作用就是越权")
    }
}
