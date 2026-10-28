package com.hmp.data.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.hmp.log.HmpLog
import com.hmp.log.LogTag

/**
 * 库版本高于本应用时的降级守卫（一-7 / D7-10）。
 *
 * 三端都刻意**没有**挂 `fallbackToDestructiveMigration`：用户的库版本只要高于本应用
 * （beta 回退、新设备整机备份恢复、把新库拷回旧安装），破坏式兜底会整库静默 drop。
 * 但"宁可打不开"走到极端就成了另一件事：Room 在 `user_version > 代码版本` 时直接抛，
 * 抛在 Koin 首次解析 `AppDatabase` 的路上 —— 用户看到"app 打不开"，日志里一个字的解释都没有，
 * 而数据其实完好无损（升回新版本就能用）。
 *
 * 所以这里在**建库之前**先探一次 `PRAGMA user_version`：
 * 命中降级就记一条带 `dbVersion` / `codeVersion` 的 error 日志、把状态挂出来给 UI 分流，
 * 并抛一个说得出人话的类型化异常，而不是让 Room 的 "Migration ... not found" 冒到启动流程。
 */
object AppDatabaseGuard {

    /** 探到的"库比代码新"状态；`null` = 正常。UI 根在解析任何 DAO 之前读它（D7-10 判据里的"可见提示"）。 */
    var tooNew: DatabaseTooNew? = null
        private set

    /**
     * 只探测并记账，**不抛**。
     *
     * 给三端的启动路径用：UI 根需要在解析任何 DAO 之前先读到这个状态，才能决定是渲染正常界面
     * 还是"库版本过高"的提示页。真正拦下打开动作的是 [enforce]。
     */
    fun probe(dbPath: String, codeVersion: Int = AppDatabase.CODE_SCHEMA_VERSION): DatabaseTooNew? {
        val verdict = judge(readUserVersionIfPresent(dbPath), codeVersion)
        tooNew = verdict
        return verdict
    }

    /**
     * 探测 + 记账 + 命中就抛（建库前的最后一道）。
     *
     * 三端 `getDatabaseBuilder()` 都调它：绕开 UI 分流直接取库的路径（未来的命令行工具、测试装配、
     * 或哪天有人新增入口）也会拿到同一个说得出人话的异常，而不是 Room 的 "Migration ... not found"。
     */
    fun enforce(dbPath: String, codeVersion: Int = AppDatabase.CODE_SCHEMA_VERSION) {
        probe(dbPath, codeVersion)?.let { throw DatabaseTooNewException(it) }
    }

    /**
     * 只有**文件已存在**时才打开它 —— `BundledSQLiteDriver.open()` 对不存在的路径会顺手建出空文件，
     * 守卫不该有"替用户建库"这种副作用。
     */
    private fun readUserVersionIfPresent(dbPath: String): Int? =
        if (databaseFileExists(dbPath)) readUserVersion(dbPath) else null

    /** 纯判定（与 IO 分开，判据才能直接喂数字进来验）。 */
    fun judge(dbVersion: Int?, codeVersion: Int): DatabaseTooNew? {
        if (dbVersion == null || dbVersion <= 0 || dbVersion <= codeVersion) return null
        // 只记版本号，不记路径与任何内容（日志纪律：路径可能含用户名）
        HmpLog.e(LogTag.DataRoom) {
            "数据库版本高于本版本，已拒绝打开 | dbVersion=$dbVersion codeVersion=$codeVersion"
        }
        return DatabaseTooNew(dbVersion = dbVersion, codeVersion = codeVersion)
    }

    /** 测试与三端启动路径共用的复位点。 */
    fun reset() {
        tooNew = null
    }

    private fun readUserVersion(path: String): Int? =
        runCatching {
            // SQLiteConnection 不是 Closeable，`use` 只给语句用；连接必须自己关掉，
            // 否则守卫会在每次启动上留一个悬着的库句柄。
            val connection: SQLiteConnection = BundledSQLiteDriver().open(path)
            try {
                connection.prepare("PRAGMA user_version").use { statement ->
                    if (statement.step()) statement.getLong(0).toInt() else null
                }
            } finally {
                connection.close()
            }
        }.getOrNull()
}

/** 库比代码新。版本号只用于把话说清楚，不做任何"能不能凑合用"的判断。 */
data class DatabaseTooNew(val dbVersion: Int, val codeVersion: Int)

/**
 * 拒绝打开过新库的信号异常。
 *
 * 类型化是判据要求的一半：不收敛的话，冒到启动流程的是 Room 内部的 `IllegalStateException`
 * （"A migration ... was not found"），它既不说明版本关系，也不该出现在用户面前。
 */
class DatabaseTooNewException(val state: DatabaseTooNew) : Exception(
    "数据库版本（${state.dbVersion}）高于本应用支持的版本（${state.codeVersion}）。" +
        "数据未被修改：请升级应用到新版本，或从更早的备份恢复。"
)

/**
 * 库文件是否存在。三端的"文件"语义不同（`java.io.File` vs `NSFileManager`），
 * 而 `BundledSQLiteDriver.open()` 又会对不存在的路径直接建文件 —— 所以这件事必须问平台，不能猜。
 */
internal expect fun databaseFileExists(path: String): Boolean
