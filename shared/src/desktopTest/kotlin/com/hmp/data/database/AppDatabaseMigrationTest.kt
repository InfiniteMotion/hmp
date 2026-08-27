package com.hmp.data.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.hmp.data.database.myenum.LabelCategory
import com.hmp.data.database.myenum.LabelName
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Rule
import java.io.File
import java.nio.file.Path

/**
 * v1 → v2 迁移测试（设计总纲 7.3 + 任务书 M0-T1）。
 *
 * JVM 版 Helper 是 JUnit TestWatcher 子类：`finished(description)` 为 protected 生命周期钩子，
 * 必须以 `@get:Rule` 注册，测试结束后由 JUnit 自动关闭连接。
 * @see androidx.room.testing.MigrationTestHelper
 */
class AppDatabaseMigrationTest {

    private val dbFileName = "migration_test.db"
    private val dbFile = File("build", dbFileName)

    @get:Rule
    val helper = MigrationTestHelper(
        schemaDirectoryPath = Path.of("schemas"),
        databasePath = dbFile.toPath(),
        driver = BundledSQLiteDriver(),
        databaseClass = AppDatabase::class,
    )

    private val driver: SQLiteDriver = BundledSQLiteDriver()

    @BeforeTest
    fun setup() {
        // build/ 已被 gitignore，测试库文件不污染工作区；上一测试的连接在 Rule.finished 之后已关闭
        dbFile.parentFile?.mkdirs()
        dbFile.delete()
    }

    @AfterTest
    fun teardown() {
        dbFile.delete()
    }

    @Test
    fun migrate_1_2_preservesData_andAddsColumnsAndTables() {
        // 1. 以 v1 schema 建库并写入存量数据
        val v1 = helper.createDatabase(1)
        v1.execSQL(
            "INSERT INTO `musicLabel` (musicId, type, label) VALUES (7, 'GENRE', 'ROCK')"
        )
        v1.execSQL(
            "INSERT INTO `music` (id, title, artist, album, duration, path, albumArtUri, isDeleted) " +
                "VALUES (7, 'Song', 'Artist', 'Album', 200000, '/m/1.mp3', '', 0)"
        )
        v1.close()

        // 2. 迁移到 v2 并验证 schema
        val v2 = helper.runMigrationsAndValidate(2, listOf(AppDatabase.MIGRATION_1_2))

        // 3. 旧数据保留；v1 存量认识的 source/confidence 应为 NULL
        v2.prepare("SELECT musicId, type, label FROM musicLabel WHERE musicId = 7").use { stmt ->
            assertTrue(stmt.step(), "musicLabel 存量行应保留")
            assertEquals(7L, stmt.getLong(0))
            assertEquals("GENRE", stmt.getText(1))
            assertEquals("ROCK", stmt.getText(2))
        }
        v2.prepare("SELECT source IS NULL, confidence IS NULL FROM musicLabel WHERE musicId = 7").use { stmt ->
            assertTrue(stmt.step())
            assertEquals(1L, stmt.getLong(0), "旧认识的 source 应为 NULL")
            assertEquals(1L, stmt.getLong(1), "旧认识的 confidence 应为 NULL")
        }

        // 4. 新列可写（实体层行为）
        v2.execSQL(
            "UPDATE `musicLabel` SET source='USER', confidence=0.95, created_at=1000, updated_at=2000 WHERE musicId=7"
        )
        v2.prepare("SELECT source, confidence FROM musicLabel WHERE musicId = 7").use { stmt ->
            assertTrue(stmt.step())
            assertEquals("USER", stmt.getText(0))
            assertEquals(0.95, stmt.getDouble(1))
        }

        // 5. 三张新表存在且可写入
        v2.execSQL(
            "INSERT INTO `agent_task` (trigger_type, status, budget_used, result, created_at) " +
                "VALUES ('manual', 'completed', 3, '{\"ok\":true}', 1000)"
        )
        v2.prepare("SELECT COUNT(*) FROM agent_task").use { stmt ->
            assertTrue(stmt.step())
            assertEquals(1L, stmt.getLong(0))
        }
        v2.execSQL(
            "INSERT INTO `agent_audit_log` (task_id, tool, args_hash, outcome, reason, created_at) " +
                "VALUES (1, 'searchLibrary', 'abc123', 'success', 'test', 1000)"
        )
        v2.execSQL(
            "INSERT INTO `agent_message` (session_id, role, content, render_hint, created_at) " +
                "VALUES ('s1', 'agent', '你好', 'text', 1000)"
        )
        v2.close()
    }

    @Test
    fun migratedDatabase_canBeOpenedByRoom() {
        // 迁移后的库应能被 Room 正常打开（构建 AppDatabase 全量校验通过）
        helper.createDatabase(1).close()
        helper.runMigrationsAndValidate(2, listOf(AppDatabase.MIGRATION_1_2)).close()

        val db = Room.databaseBuilder<AppDatabase>(dbFile.path)
            .setDriver(driver)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
        try {
            assertNotNull(db)
            // 触达各 DAO，确保 KSP 生成的 accessor 可用
            assertNotNull(db.musicLabelDao())
            assertNotNull(db.agentTaskDao())
            assertNotNull(db.agentAuditLogDao())
            assertNotNull(db.agentMessageDao())
        } finally {
            db.close()
        }
    }
}