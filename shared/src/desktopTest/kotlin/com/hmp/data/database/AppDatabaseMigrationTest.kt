package com.hmp.data.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/**
 * Room 迁移链测试：逐环（1→2 / 5→6 / 6→7 / 7→8 / 8→9）+ 1→9 全链 + 链连续性门禁。
 *
 * 三端都已去掉 destructive 兜底，迁移写错 = 该端升级硬失败，所以这道关必须会响：
 * 故意写坏任一环的 DDL，这里的用例要红。
 *
 * JVM 版 Helper 是 JUnit TestWatcher 子类：`finished(description)` 为 protected 生命周期钩子，
 * 必须以 `@get:Rule` 注册，测试结束后由 JUnit 自动关闭连接。
 * @see androidx.room.testing.MigrationTestHelper
 */
class AppDatabaseMigrationTest {

    /**
     * 每个测试实例一个库文件。JUnit4 对每个 `@Test` 新建一个测试类实例，因此这里按类内计数器取名。
     *
     * 共用一个文件在 Windows 上会级联：用例失败时连接来不及关，句柄还开着 → `delete()` 静默失败 →
     * 下一个用例在同一份旧库上 `createDatabase(n)`，报出来的错与它自己要验的东西毫无关系
     * （实测见过 "A migration from 1 to 7 was required but not found"）。
     */
    private val dbFile = File("build", "migration_test_${nextFileSlot()}.db")

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
        // build/ 已被 gitignore，测试库文件不污染工作区；库文件名每实例唯一，不必担心上一用例的残留句柄
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

    /**
     * 迁移后的库要能被 Room 打开并**真的执行 SQL**。
     *
     * 旧写法只跑到 v2、再断言一堆 DAO accessor 非 null —— accessor 是对象取用，不碰数据库，
     * 所以 DDL 写错也照样绿（假用例）。这里改造成真实用户的升级路径：
     * v1 库落存量数据 → 由 Room 自己按 `ALL_MIGRATIONS` 升到声明版本 → 读写各走一次。
     * 「bump 了 `@Database.version` 却忘了写迁移」也在这条上红：Room 找不到覆盖该区间的路径。
     */
    @Test
    fun migratedDatabase_canBeOpenedByRoom() = runTest {
        val v1 = helper.createDatabase(1)
        v1.execSQL(
            "INSERT INTO `music` (id, title, artist, album, duration, path, albumArtUri, isDeleted) " +
                "VALUES (97, 'Song', 'Artist', 'Album', 200000, '/m/97.mp3', '', 0)"
        )
        v1.close()

        val db = Room.databaseBuilder<AppDatabase>(dbFile.path)
            .setDriver(driver)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
        try {
            // 存量行经 Room 驱动的 1→9 升级后仍在
            assertEquals(listOf(97L), db.musicDao().getAllActiveIds(), "升级后存量曲目应可读")

            // 写路径 + Flow 查询各走一次：任何一环把表名/列名写坏，SQLite 在这里就抛
            db.musicDao().updateMusicTags(97, "NewTitle", "Artist2", "Album2")
            val updated = db.musicDao().getMusicById(97).first()
            assertNotNull(updated, "getMusicById 应命中升级后的行")
            assertEquals("Artist2", updated.artist)
            assertEquals(1, db.musicDao().getMusicCount().first())

            // 后续版本新增的表要真能用生成码写入
            db.tokenLedgerDao().insert(
                TokenLedgerEntry(
                    createdAt = 1000L,
                    agentId = "master",
                    endpointHost = "api.example.com",
                    model = "gpt-test",
                    promptTokens = 10,
                    completionTokens = 20,
                    cachedTokens = 0,
                    measured = true,
                    taskId = null,
                )
            )
            assertEquals(1L, db.tokenLedgerDao().count(), "v9 新增表应可写")
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

    /** v7 → v8（F9-T1 遗忘唤醒送达标记）：建表 + `musicId` 主键去重 + 存量数据保留。 */
    @Test
    fun migrate_7_8_createsForgottenDeliveryTable_withMusicIdPrimaryKey() {
        val v7 = helper.createDatabase(7)
        insertMusic(v7, 71)
        v7.close()

        val v8 = helper.runMigrationsAndValidate(8, listOf(AppDatabase.MIGRATION_7_8))

        assertEquals(1L, countRows(v8, "`music` WHERE id = 71"), "存量 music 行应保留")

        v8.execSQL("INSERT INTO `forgotten_delivery` (musicId, deliveredAt) VALUES (71, 1000)")
        // 同一曲目的重复送达必须被主键拒掉 —— 它治的是「同一首歌被反复唤醒推送」
        val duplicate = runCatching {
            v8.execSQL("INSERT INTO `forgotten_delivery` (musicId, deliveredAt) VALUES (71, 2000)")
        }
        assertTrue(duplicate.isFailure, "musicId 主键应拒绝重复送达行（违反则说明主键没建成）")

        v8.prepare("SELECT COUNT(*), deliveredAt FROM `forgotten_delivery`").use { stmt ->
            assertTrue(stmt.step())
            assertEquals(1L, stmt.getLong(0), "同一 musicId 只应有一行送达标记")
            assertEquals(1000L, stmt.getLong(1), "首次写入不应被覆盖")
        }
        v8.close()
    }

    /**
     * v8 → v9（F12-T1 Token 明细账本）：建表 + 两个索引 + NOT NULL 真生效 + 存量数据保留。
     *
     * 这条是本次补环的重点之一：`token_ledger` 建表 DDL 里 8 列 NOT NULL，此前**没有任何测试跑过这一环**
     * —— 把 `prompt_tokens` 的 NOT NULL 写错，整套套件照绿。
     */
    @Test
    fun migrate_8_9_createsTokenLedgerTable_andBothIndexes() {
        val v8 = helper.createDatabase(8)
        insertMusic(v8, 81)
        v8.close()

        val v9 = helper.runMigrationsAndValidate(9, listOf(AppDatabase.MIGRATION_8_9))

        assertEquals(1L, countRows(v9, "`music` WHERE id = 81"), "存量 music 行应保留")

        v9.execSQL(
            "INSERT INTO `token_ledger` (created_at, agent_id, endpoint_host, model, " +
                "prompt_tokens, completion_tokens, cached_tokens, measured) " +
                "VALUES (1000, 'master', 'api.example.com', 'gpt-test', 10, 20, 0, 1)"
        )
        v9.execSQL(
            "INSERT INTO `token_ledger` (created_at, agent_id, endpoint_host, model, " +
                "prompt_tokens, completion_tokens, cached_tokens, measured) " +
                "VALUES (2000, 'radio', 'api.example.com', 'gpt-test', 5, 7, 1, 0)"
        )
        v9.prepare("SELECT COUNT(*) FROM `token_ledger`").use { stmt ->
            assertTrue(stmt.step())
            assertEquals(2L, stmt.getLong(0), "一次 LLM 调用一行的明细语义应允许并存两行")
        }

        // 关键计数列不得为 NULL —— 分了账却不知分母是无意义的
        val nullTokens = runCatching {
            v9.execSQL(
                "INSERT INTO `token_ledger` (created_at, agent_id, endpoint_host, model, " +
                    "prompt_tokens, completion_tokens, cached_tokens, measured) " +
                    "VALUES (3000, 'hello', 'api.example.com', 'gpt-test', NULL, 7, 1, 1)"
            )
        }
        assertTrue(nullTokens.isFailure, "prompt_tokens 应拒绝 NULL")

        assertIndex(v9, "token_ledger", "index_token_ledger_created_at")
        assertIndex(v9, "token_ledger", "index_token_ledger_agent_id")
        v9.close()
    }

    /**
     * 1 → 9 全链：一次跑完 `ALL_MIGRATIONS` 并对照 KSP 导出的 `9.json` 校验。
     *
     * 单环各自绿不等于连起来对：环与环的相互影响（同名表、索引顺序、列累积）只有全链跑起来才暴露。
     * 这也是 v10 的起跑基线 —— 新增一环后这条会自动多跑一环。
     */
    @Test
    fun migrate_1_to_9_fullChain_validatesSchema9_andPreservesData() {
        val v1 = helper.createDatabase(1)
        insertMusic(v1, 91)
        v1.execSQL("INSERT INTO `musicLabel` (musicId, type, label) VALUES (91, 'GENRE', 'ROCK')")
        v1.close()

        val v9 = helper.runMigrationsAndValidate(9, AppDatabase.ALL_MIGRATIONS.toList())

        assertEquals(1L, countRows(v9, "`music` WHERE id = 91"), "跨 8 环后存量曲目应保留")
        assertEquals(1L, countRows(v9, "`musicLabel` WHERE musicId = 91"), "跨 8 环后存量认识应保留")

        // 各环新增的表逐张在场（缺任何一张，runMigrationsAndValidate 已先红；这里给可读的失败点名）
        val introducedByRing = listOf(
            "agent_task" to 2,
            "agent_audit_log" to 2,
            "agent_message" to 2,
            "hello_card_cache" to 4,
            "hello_report_narrative" to 4,
            "user_profile_evidence" to 6,
            "user_profile_portrait" to 6,
            "user_profile_narrative" to 7,
            "forgotten_delivery" to 8,
            "token_ledger" to 9,
        )
        introducedByRing.forEach { (table, ring) ->
            assertTableExists(v9, table, "v$ring 引入的表")
        }
        v9.close()
    }

    /**
     * 链连续性门禁：`ALL_MIGRATIONS` 从 v1 起、逐环相邻衔接、无重复无空洞，末端触达声明版本。
     *
     * 「bump 了 `@Database.version` 却忘了写迁移」有两种红法：本条在 `latestSchemaVersion` 未同步时红；
     * 同步了常量却漏迁移时，由 [migratedDatabase_canBeOpenedByRoom] 红 —— 它让 Room 自己找升级路径。
     */
    @Test
    fun migrationChain_isContinuous_andCoversEveryVersion() {
        val chain = AppDatabase.ALL_MIGRATIONS.sortedBy { it.startVersion }

        assertEquals(1, chain.first().startVersion, "迁移链必须从 v1 起")
        assertEquals(latestSchemaVersion, chain.last().endVersion, "链末端应等于 @Database 声明版本")
        assertEquals(
            latestSchemaVersion - 1,
            chain.size,
            "链长度应为 version - 1；不符说明漏环或掺了跨环迁移",
        )

        chain.forEachIndexed { index, migration ->
            assertEquals(
                migration.startVersion + 1,
                migration.endVersion,
                "第 $index 环 ${migration.startVersion}→${migration.endVersion} 不是相邻升级",
            )
            if (index > 0) {
                assertEquals(
                    chain[index - 1].endVersion,
                    migration.startVersion,
                    "链在 v${migration.startVersion} 处断开",
                )
            }
        }
    }

    /** `latestSchemaVersion` 与 `AppDatabase` 的 `@Database(version = …)` 手工同步。 */
    private val latestSchemaVersion = 9

    private fun insertMusic(connection: SQLiteConnection, id: Long) {
        connection.execSQL(
            "INSERT INTO `music` (id, title, artist, album, duration, path, albumArtUri, isDeleted) " +
                "VALUES ($id, 'Song$id', 'Artist', 'Album', 200000, '/m/$id.mp3', '', 0)"
        )
    }

    private fun countRows(connection: SQLiteConnection, from: String): Long {
        connection.prepare("SELECT COUNT(*) FROM $from").use { stmt ->
            assertTrue(stmt.step(), "$from 查询应有结果行")
            return stmt.getLong(0)
        }
    }

    private fun assertTableExists(connection: SQLiteConnection, table: String, origin: String) {
        assertEquals(
            1L,
            countRows(connection, "sqlite_master WHERE type = 'table' AND name = '$table'"),
            "$origin $table 应存在于迁移后的库",
        )
    }

    private fun assertIndex(connection: SQLiteConnection, table: String, index: String) {
        val names = mutableListOf<String>()
        connection.prepare("PRAGMA index_list(`$table`)").use { stmt ->
            while (stmt.step()) names += stmt.getText(1)
        }
        assertTrue(index in names, "$table 应有索引 $index，实际：$names")
    }

    private companion object {
        val fileCounter = AtomicInteger()

        fun nextFileSlot(): Int = fileCounter.incrementAndGet()
    }
}