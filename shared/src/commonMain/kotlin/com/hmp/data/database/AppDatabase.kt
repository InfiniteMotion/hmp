package com.hmp.data.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.hmp.data.database.myenum.LabelConverters

// RoomDatabaseConstructor for KMP
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

@ConstructedBy(AppDatabaseConstructor::class)
@Database(
    entities = [
        Music::class,
        MusicExtra::class,
        UserInfo::class,
        MusicLabel::class,
        Playlist::class,
        PlaylistItem::class,
        PlaybackHistory::class,
        ListeningDuration::class,
        AgentTask::class,
        AgentAuditLog::class,
        AgentMessage::class,
        HelloCardCache::class,
        HelloReportNarrativeEntity::class,
        UserProfileEvidenceEntity::class,
        UserProfilePortraitEntity::class,
        UserProfileNarrativeEntity::class,
        ForgottenDeliveryEntity::class,
        TokenLedgerEntry::class,
    ],
    version = 9,
    exportSchema = true
)
@TypeConverters(LabelConverters::class, HelloCardConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
    abstract fun musicExtraDao(): MusicExtraDao
    abstract fun userInfoDao(): UserInfoDao
    abstract fun musicAllDao(): MusicAllDao
    abstract fun musicLabelDao(): MusicLabelDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistItemDao(): PlaylistItemDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun listeningDurationDao(): ListeningDurationDao
    abstract fun agentTaskDao(): AgentTaskDao
    abstract fun agentAuditLogDao(): AgentAuditLogDao
    abstract fun agentMessageDao(): AgentMessageDao
    abstract fun helloCardCacheDao(): HelloCardCacheDao
    abstract fun helloReportNarrativeDao(): HelloReportNarrativeDao
    abstract fun userProfileEvidenceDao(): UserProfileEvidenceDao
    abstract fun userProfilePortraitDao(): UserProfilePortraitDao
    abstract fun userProfileNarrativeDao(): UserProfileNarrativeDao
    abstract fun forgottenDeliveryDao(): ForgottenDeliveryDao
    abstract fun tokenLedgerDao(): TokenLedgerDao

    companion object {
        /**
         * v1 → v2（设计总纲 7.3）：
         * - musicLabel 加 4 列（source/confidence/created_at/updated_at）——认识可演化的存储基础；
         * - 新增 agent_task / agent_audit_log / agent_message 三表。
         * 只加表和列，不动存量数据结构。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `musicLabel` ADD COLUMN `source` TEXT")
                connection.execSQL("ALTER TABLE `musicLabel` ADD COLUMN `confidence` REAL")
                connection.execSQL("ALTER TABLE `musicLabel` ADD COLUMN `created_at` INTEGER")
                connection.execSQL("ALTER TABLE `musicLabel` ADD COLUMN `updated_at` INTEGER")
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `agent_task` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`trigger_type` TEXT NOT NULL, `persona_snapshot` TEXT, `status` TEXT NOT NULL, " +
                        "`budget_used` INTEGER, `result` TEXT, `created_at` INTEGER NOT NULL)"
                )
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `agent_audit_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`task_id` INTEGER, `tool` TEXT NOT NULL, `args_hash` TEXT, " +
                        "`outcome` TEXT NOT NULL, `reason` TEXT, `created_at` INTEGER NOT NULL)"
                )
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `agent_message` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`session_id` TEXT NOT NULL, `role` TEXT NOT NULL, `content` TEXT, " +
                        "`render_hint` TEXT, `created_at` INTEGER NOT NULL)"
                )
            }
        }

        /**
         * v2 → v3（M6-T1 电台三轮协作）：
         * - agent_message 加 data_json 列（songlist/confirm 结构化 payload）。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `agent_message` ADD COLUMN `data_json` TEXT")
            }
        }

        /**
         * v3 → v4（W0 HelloSubAgent）：
         * - 新增 hello_card_cache 表（推荐卡缓存）
         * - 新增 hello_report_narrative 表（报告叙事段，P5 用）
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """CREATE TABLE IF NOT EXISTS `hello_card_cache` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `cardType` TEXT NOT NULL,
                    `cardContentJson` TEXT NOT NULL,
                    `generatedAt` INTEGER NOT NULL,
                    `generatedForDate` TEXT NOT NULL
                )"""
                )
                connection.execSQL(
                    """CREATE TABLE IF NOT EXISTS `hello_report_narrative` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `timeRange` TEXT NOT NULL,
                    `narrative` TEXT NOT NULL,
                    `generatedAt` INTEGER NOT NULL,
                    `avg_daily_minutes` REAL
                )"""
                )
            }
        }

        /**
         * v4 → v5（Hello 记忆协调）：
         * - hello_card_cache 扩展字段：生成元信息 + 各卡类型的记忆字段
         * - 保留全量历史，不再同类型只留最新（改为同日同类型删）
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                // 生成元信息
                connection.execSQL("ALTER TABLE `hello_card_cache` ADD COLUMN `llm_used` INTEGER NOT NULL DEFAULT 0")
                connection.execSQL("ALTER TABLE `hello_card_cache` ADD COLUMN `generation_duration_ms` INTEGER")
                connection.execSQL("ALTER TABLE `hello_card_cache` ADD COLUMN `llm_prompt_tokens` INTEGER")
                connection.execSQL("ALTER TABLE `hello_card_cache` ADD COLUMN `llm_response_tokens` INTEGER")
                // RECOMMEND 专用
                connection.execSQL("ALTER TABLE `hello_card_cache` ADD COLUMN `recommend_song_ids` TEXT")
                connection.execSQL("ALTER TABLE `hello_card_cache` ADD COLUMN `recommend_artists` TEXT")
                connection.execSQL("ALTER TABLE `hello_card_cache` ADD COLUMN `recommend_labels` TEXT")
                // GREETING 专用
                connection.execSQL("ALTER TABLE `hello_card_cache` ADD COLUMN `greeting_type` TEXT")
                connection.execSQL("ALTER TABLE `hello_card_cache` ADD COLUMN `greeting_mentioned_artists` TEXT")
                // DISCOVER 专用
                connection.execSQL("ALTER TABLE `hello_card_cache` ADD COLUMN `discover_labels` TEXT")
                // FORGOTTEN 专用
                connection.execSQL("ALTER TABLE `hello_card_cache` ADD COLUMN `forgotten_artists` TEXT")
                connection.execSQL("ALTER TABLE `hello_card_cache` ADD COLUMN `forgotten_song_ids` TEXT")
                // ANNIVERSARY 专用
                connection.execSQL("ALTER TABLE `hello_card_cache` ADD COLUMN `anniversary_artist` TEXT")
                connection.execSQL("ALTER TABLE `hello_card_cache` ADD COLUMN `anniversary_subject` TEXT")
            }
        }

        /**
         * v5 → v6（F9-T0 用户认识模块 / 画像，契约 agent-profile.md v3.1 §2.4）：
         * - 新增 `user_profile_evidence` 表（证据层：可审计的事实行）+ 三元组唯一索引
         * - 新增 `user_profile_portrait` 表（侧写层：由证据派生的压缩印象）
         *
         * 纯 `CREATE TABLE` + 一个索引，**无列变更**，不动任何存量表。
         *
         * ⚠️ 证据表用**自增主键**：侧写的 `evidence_refs` 存的是证据行 id，
         * 而 `(subject, predicate, value)` 降为唯一索引 —— 有 id 才能被反链指到。
         * 唯一索引名必须是 Room 生成的格式，否则 schema 校验会失败。
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """CREATE TABLE IF NOT EXISTS `user_profile_evidence` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `subject` TEXT NOT NULL,
                    `predicate` TEXT NOT NULL,
                    `value` TEXT NOT NULL,
                    `source` TEXT NOT NULL,
                    `confidence` REAL NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `evidence_count` INTEGER NOT NULL,
                    `distinct_sessions` INTEGER NOT NULL,
                    `last_session_id` TEXT
                )"""
                )
                connection.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_user_profile_evidence_subject_predicate_value` " +
                        "ON `user_profile_evidence` (`subject`, `predicate`, `value`)"
                )
                connection.execSQL(
                    """CREATE TABLE IF NOT EXISTS `user_profile_portrait` (
                    `type` TEXT NOT NULL,
                    `tier` TEXT NOT NULL,
                    `slots_json` TEXT NOT NULL,
                    `evidence_refs` TEXT NOT NULL,
                    `confidence` REAL NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `coverage_at_modeling` REAL,
                    PRIMARY KEY(`type`)
                )"""
                )
            }
        }

        /**
         * v6 → v7（画像叙事，契约 v3.5 §7.4）：
         * - 新增 `user_profile_narrative` 单行表（LLM 依据侧写渲染生成的描述性文本，两面共用）。
         *
         * 纯 `CREATE TABLE`，无列变更，不动存量表。重生成失败保留旧文本，迁移只管建表。
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """CREATE TABLE IF NOT EXISTS `user_profile_narrative` (
                    `id` INTEGER NOT NULL,
                    `text` TEXT NOT NULL,
                    `facts_hash` INTEGER NOT NULL,
                    `generated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )"""
                )
            }
        }

        /**
         * v7 → v8（F9-T1）：
         * - 新增 `forgotten_delivery` 极简表——遗忘唤醒送达标记（musicId PK + deliveredAt）。
         * 只加表，不动存量表。
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """CREATE TABLE IF NOT EXISTS `forgotten_delivery` (
                    `musicId` INTEGER NOT NULL,
                    `deliveredAt` INTEGER NOT NULL,
                    PRIMARY KEY(`musicId`)
                )"""
                )
            }
        }

        /**
         * v8 → v9（F12-T1 Token 明细账本）：
         * - 新增 `token_ledger` 表——**一次 LLM 调用一行**，承载"各 Agent × 各端点 × 分时"的分账。
         *
         * 纯 `CREATE TABLE`，无列变更，不动存量表。
         * 「明细永久保留、不做清理」是刻意决定（体积 ~16 MB/年，见 `design/agent-token.md` §3.1），
         * 因此这里**不建清理相关索引**；时间维度的聚合走 `created_at` 的范围扫描。
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """CREATE TABLE IF NOT EXISTS `token_ledger` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `agent_id` TEXT NOT NULL,
                    `endpoint_host` TEXT NOT NULL,
                    `model` TEXT NOT NULL,
                    `prompt_tokens` INTEGER NOT NULL,
                    `completion_tokens` INTEGER NOT NULL,
                    `cached_tokens` INTEGER NOT NULL,
                    `measured` INTEGER NOT NULL,
                    `task_id` TEXT
                )"""
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_token_ledger_created_at` ON `token_ledger` (`created_at`)"
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_token_ledger_agent_id` ON `token_ledger` (`agent_id`)"
                )
            }
        }
    }
}

// Factory function to create database - implemented per-platform
expect fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase
