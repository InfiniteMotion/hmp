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
            assertNotNull(db.userProfileEvidenceDao())
            assertNotNull(db.userProfilePortraitDao())
        } finally {
            db.close()
        }
    }

    /**
     * v5 → v6 迁移（F9-T0 用户认识模块，契约 agent-profile.md v3.1 §2.4）。
     *
     * 验三件事：① 存量表数据完好；② 两张新表建成且可写；③ 证据表的三元组**唯一索引**真的生效
     * —— 这条索引是"同一事实只累积不重复建行"的保证，漏了它自增 id 就会膨胀、反链会漂。
     */
    @Test
    fun migrate_5_6_createsUserProfileTables_andEnforcesUniqueTriple() {
        // 1. 以 v5 schema 建库并写入存量数据
        val v5 = helper.createDatabase(5)
        v5.execSQL(
            "INSERT INTO `music` (id, title, artist, album, duration, path, albumArtUri, isDeleted) " +
                "VALUES (11, 'Song', 'Artist', 'Album', 200000, '/m/11.mp3', '', 0)"
        )
        v5.close()

        // 2. 迁移到 v6 并验证 schema（对照 KSP 导出的 schemas/6.json）
        val v6 = helper.runMigrationsAndValidate(6, listOf(AppDatabase.MIGRATION_5_6))

        // 3. 存量表数据保留
        v6.prepare("SELECT COUNT(*) FROM `music` WHERE id = 11").use { stmt ->
            assertTrue(stmt.step(), "查询应有结果行")
            assertEquals(1L, stmt.getLong(0), "存量 music 行应保留")
        }

        // 4. 证据表可写；自增 id 生效
        v6.execSQL(
            "INSERT INTO `user_profile_evidence` " +
                "(subject, predicate, value, source, confidence, created_at, updated_at, " +
                "evidence_count, distinct_sessions, last_session_id) " +
                "VALUES ('USER', 'time_portrait.primaryPart', 'NIGHT', 'T0_BEHAVIOR', 0.6, 1000, 1000, 1, 1, 's1')"
        )
        v6.prepare("SELECT id FROM `user_profile_evidence` WHERE predicate = 'time_portrait.primaryPart'")
            .use { stmt ->
                assertTrue(stmt.step())
                assertTrue(stmt.getLong(0) > 0, "自增主键应生成非 0 id")
            }

        // 5. 同一 (subject, predicate, value) 再插一次 → 必须被唯一索引拒绝
        val duplicate = runCatching {
            v6.execSQL(
                "INSERT INTO `user_profile_evidence` " +
                    "(subject, predicate, value, source, confidence, created_at, updated_at, " +
                    "evidence_count, distinct_sessions, last_session_id) " +
                    "VALUES ('USER', 'time_portrait.primaryPart', 'NIGHT', 'T0_BEHAVIOR', 0.7, 2000, 2000, 1, 1, 's2')"
            )
        }
        assertTrue(duplicate.isFailure, "重复的三元组应被唯一索引拒绝（违反则说明索引没建成）")

        // 6. 换个 value 则可插 —— 确认拒绝的是三元组而非整表
        v6.execSQL(
            "INSERT INTO `user_profile_evidence` " +
                "(subject, predicate, value, source, confidence, created_at, updated_at, " +
                "evidence_count, distinct_sessions, last_session_id) " +
                "VALUES ('USER', 'time_portrait.secondaryPart', 'EVENING', 'T0_BEHAVIOR', 0.5, 1000, 1000, 1, 1, 's1')"
        )
        v6.prepare("SELECT COUNT(*) FROM `user_profile_evidence`").use { stmt ->
            assertTrue(stmt.step())
            assertEquals(2L, stmt.getLong(0), "两条不同谓词的证据应共存")
        }

        // 7. 侧写表：type 是主键，可空列 coverage_at_modeling 允许 NULL
        v6.execSQL(
            "INSERT INTO `user_profile_portrait` " +
                "(type, tier, slots_json, evidence_refs, confidence, created_at, updated_at, coverage_at_modeling) " +
                "VALUES ('time_portrait', 'L2', '{\"primaryPart\":\"NIGHT\"}', '1', 0.6, 1000, 1000, NULL)"
        )
        v6.prepare("SELECT coverage_at_modeling IS NULL, evidence_refs FROM `user_profile_portrait`")
            .use { stmt ->
                assertTrue(stmt.step())
                assertEquals(1L, stmt.getLong(0), "coverage_at_modeling 应允许 NULL")
                assertEquals("1", stmt.getText(1), "evidence_refs 存的是证据行 id")
            }

        // 8. 侧写按 type 覆盖（REPLACE 语义），不该出现两行
        v6.execSQL(
            "INSERT OR REPLACE INTO `user_profile_portrait` " +
                "(type, tier, slots_json, evidence_refs, confidence, created_at, updated_at, coverage_at_modeling) " +
                "VALUES ('time_portrait', 'L3', '{}', '1,2', 0.7, 1000, 3000, 0.9)"
        )
        v6.prepare("SELECT COUNT(*), tier FROM `user_profile_portrait` WHERE type = 'time_portrait'")
            .use { stmt ->
                assertTrue(stmt.step())
                assertEquals(1L, stmt.getLong(0), "同一 type 只应有一行")
                assertEquals("L3", stmt.getText(1), "覆盖后应取新值")
            }
        v6.close()
    }

    /** v6 → v7（画像叙事表，契约 v3.5 §7.4）：建表 + 单行 REPLACE 语义 + 存量数据保留。 */
    @Test
    fun migrate_6_7_createsNarrativeTable_withSingleRowSemantics() {
        val v6 = helper.createDatabase(6)
        v6.execSQL(
            "INSERT INTO `music` (id, title, artist, album, duration, path, albumArtUri, isDeleted) " +
                "VALUES (21, 'Song', 'Artist', 'Album', 200000, '/m/21.mp3', '', 0)"
        )
        v6.close()

        val v7 = helper.runMigrationsAndValidate(7, listOf(AppDatabase.MIGRATION_6_7))

        v7.prepare("SELECT COUNT(*) FROM `music` WHERE id = 21").use { stmt ->
            assertTrue(stmt.step())
            assertEquals(1L, stmt.getLong(0), "存量 music 行应保留")
        }

        // 叙事表可写，指纹与时间戳原样读回
        v7.execSQL(
            "INSERT INTO `user_profile_narrative` (id, text, facts_hash, generated_at) " +
                "VALUES (1, '你常在夜里听歌，很少中途跳过。', 42, 1000)"
        )
        v7.prepare("SELECT text, facts_hash, generated_at FROM `user_profile_narrative` WHERE id = 1")
            .use { stmt ->
                assertTrue(stmt.step())
                assertEquals("你常在夜里听歌，很少中途跳过。", stmt.getText(0))
                assertEquals(42L, stmt.getLong(1))
                assertEquals(1000L, stmt.getLong(2))
            }

        // 单行表：同 id REPLACE 覆盖，不产生第二行
        v7.execSQL(
            "INSERT OR REPLACE INTO `user_profile_narrative` (id, text, facts_hash, generated_at) " +
                "VALUES (1, '你听得杂而广，经常听一段就切走。', 43, 2000)"
        )
        v7.prepare("SELECT COUNT(*), text, facts_hash FROM `user_profile_narrative`").use { stmt ->
            assertTrue(stmt.step())
            assertEquals(1L, stmt.getLong(0), "单行表 REPLACE 后应只有一行")
            assertEquals("你听得杂而广，经常听一段就切走。", stmt.getText(1))
            assertEquals(43L, stmt.getLong(2), "指纹应随重生成更新")
        }
        v7.close()
    }
}