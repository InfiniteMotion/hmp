package com.hmp.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * 用户认识模块（画像）的持久化层 —— Room v5 → v6 新增的两张表。
 *
 * 契约（单一事实来源）：`docs/7_x/B agent-build/design/agent-profile.md` v3.1 §2.3 / §2.4
 *
 * 两层结构：
 * - **证据层** `user_profile_evidence` —— 可审计的事实行，吃审计四问（§2.6）
 * - **侧写层** `user_profile_portrait` —— 由证据派生出的压缩印象，**不吃审计四问**，
 *   但必须能经 `evidenceRefs` 向下追溯到证据行
 *
 * ⚠️ **纪律**：若将来有人给侧写加 `source` 列，说明这个设计被误解了 ——
 * 侧写是**派生物**，它的"来源"就是 `evidenceRefs` 指向的那些证据行。
 */

/**
 * 证据层：一条可审计的事实行。
 *
 * `predicate` 用**槽位限定名** `"<portraitType>.<slotKey>"` —— 谓词闭集**就是** §5 的
 * 槽位闭集，不另设一套（两套闭集互相映射只会漂移）。
 *
 * 主键是自增 `id`：侧写的反链 `Portrait.evidenceRefs: List<Long>` 需要有 Long 可指。
 * `(subject, predicate, value)` 因此降为**唯一索引** —— 保证同一事实只累积、不重复建行。
 */
@Entity(
    tableName = "user_profile_evidence",
    indices = [Index(value = ["subject", "predicate", "value"], unique = true)],
)
data class UserProfileEvidenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** v1 恒为 "USER"（保留字段是为了将来可能的"关于伙伴自己"的认识，不提前扩语义） */
    val subject: String,
    /** `"<portraitType>.<slotKey>"`，闭集见契约 §5 */
    val predicate: String,
    val value: String,
    /** 来源分档：T1_USER / T0_BEHAVIOR / LIBRARY_CONTENT / LIBRARY_SHAPE / T2_DIALOGUE（契约 §2.5） */
    val source: String,
    val confidence: Double,
    /** 审计四问·何时建立 */
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** 审计四问·被确证过吗（最近一次确证时间） */
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** 审计四问·被确证过吗 —— 被观察到的**总次数**（含首次） */
    @ColumnInfo(name = "evidence_count") val evidenceCount: Int = 1,
    /**
     * L1 → L2 晋升用 —— 出现过的**不同会话数**。
     *
     * ⚠️ 与 [evidenceCount] **不是一个东西，不得合并**：
     * 同一会话内高频重复不算偏好（那是场景），所以晋升看的是"跨了几个会话"，
     * 审计看的是"被观察了多少次"。一个计数器表达不了两个含义。
     */
    @ColumnInfo(name = "distinct_sessions") val distinctSessions: Int = 1,
    /** 最近一次观察到它的会话 id —— 用于累计 [distinctSessions] 时去重 */
    @ColumnInfo(name = "last_session_id") val lastSessionId: String? = null,
)

/**
 * 侧写层：预设骨架 + 槽位取值 + 证据反链。
 *
 * `tier` 只落 **L2 情境态 / L3 性情态** —— L1 当下态是会话内存（`SessionStore`），不落库（契约 §3）。
 */
@Entity(tableName = "user_profile_portrait", primaryKeys = ["type"])
data class UserProfilePortraitEntity(
    /** PortraitType 闭集（契约 §5） */
    val type: String,
    /** "L2" / "L3" */
    val tier: String,
    /** `Map<SlotKey, SlotValue>` 的 JSON —— 序列化在 domain 层做，本层只存字符串 */
    @ColumnInfo(name = "slots_json") val slotsJson: String,
    /**
     * 证据行 id 的 CSV。规模是「≤8 个侧写 × ≤10 条反链」，关联表的 join 不划算（契约 §2.4）。
     * 将来若需要"按证据反查侧写"，再升关联表。
     */
    @ColumnInfo(name = "evidence_refs") val evidenceRefs: String,
    val confidence: Double,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** 内容建模当时的覆盖率快照 —— 用于 confidence 折扣与"是否该重做"的判定（契约 §4.1.4） */
    @ColumnInfo(name = "coverage_at_modeling") val coverageAtModeling: Double? = null,
)

@Dao
interface UserProfileEvidenceDao {

    /**
     * 首次观察到一条事实。
     *
     * ⚠️ 用 `IGNORE` 而**不是** `REPLACE`：`REPLACE` 会删旧行插新行，自增 id 随之改变，
     * 而侧写的 `evidence_refs` 存的是旧 id —— 反链会整片断掉。
     * 已存在时返回 `-1`，调用方改用 [confirmObservation] 累加。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(evidence: UserProfileEvidenceEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(evidence: List<UserProfileEvidenceEntity>): List<Long>

    /**
     * 又观察到一条**已存在**的事实 —— 累加计数并刷新时间，**不改 id**。
     *
     * `sessionDelta` 与 `sessionId` 由调用方算好（比较 `lastSessionId` 得出），
     * 好让"同一会话内重复不计"这条规则留在可单测的纯逻辑里。
     */
    @Query(
        """
        UPDATE user_profile_evidence
        SET source = :source,
            confidence = :confidence,
            updated_at = :updatedAt,
            evidence_count = evidence_count + 1,
            distinct_sessions = distinct_sessions + :sessionDelta,
            last_session_id = :sessionId
        WHERE id = :id
        """
    )
    suspend fun confirmObservation(
        id: Long,
        source: String,
        confidence: Double,
        updatedAt: Long,
        sessionDelta: Int,
        sessionId: String?,
    ): Int

    /** 全量 —— `PortraitComposer` 的输入、设置页"伙伴记住了什么" */
    @Query("SELECT * FROM user_profile_evidence")
    suspend fun getAll(): List<UserProfileEvidenceEntity>

    @Query("SELECT * FROM user_profile_evidence WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<UserProfileEvidenceEntity>

    @Query(
        """
        SELECT * FROM user_profile_evidence
        WHERE subject = :subject AND predicate = :predicate AND value = :value
        LIMIT 1
        """
    )
    suspend fun find(subject: String, predicate: String, value: String): UserProfileEvidenceEntity?

    /**
     * 某个来源最近一次被写的时间 —— 行为建模的**日内去重**靠它，
     * 不必再建一张"上次刷新时间"表：证据行本身就是那条记录。
     */
    @Query("SELECT MAX(updated_at) FROM user_profile_evidence WHERE source = :source")
    suspend fun newestUpdatedAtBySource(source: String): Long?

    @Query("SELECT * FROM user_profile_evidence WHERE predicate = :predicate")
    suspend fun getByPredicate(predicate: String): List<UserProfileEvidenceEntity>

    /** 某谓词下由特定来源写的证据 —— 同类冲突走信任链时用（T1_USER 覆盖 T0，契约 §2.5 规则一） */
    @Query("SELECT * FROM user_profile_evidence WHERE predicate = :predicate AND source = :source")
    suspend fun getByPredicateAndSource(predicate: String, source: String): List<UserProfileEvidenceEntity>

    /** 某条事实被否决时删掉它（否决语义 = 写 `pref_denied` 证据，见契约 §4.5） */
    @Query("DELETE FROM user_profile_evidence WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM user_profile_evidence")
    suspend fun deleteAll()
}

@Dao
interface UserProfilePortraitDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(portrait: UserProfilePortraitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(portraits: List<UserProfilePortraitEntity>)

    @Query("SELECT * FROM user_profile_portrait")
    suspend fun getAll(): List<UserProfilePortraitEntity>

    @Query("SELECT * FROM user_profile_portrait WHERE type = :type LIMIT 1")
    suspend fun getByType(type: String): UserProfilePortraitEntity?

    /** 按层取 —— 上下文块只要 L3 全部 + L2 高分（契约 §7） */
    @Query("SELECT * FROM user_profile_portrait WHERE tier = :tier")
    suspend fun getByTier(tier: String): List<UserProfilePortraitEntity>

    @Query("DELETE FROM user_profile_portrait WHERE type = :type")
    suspend fun deleteByType(type: String): Int

    @Query("DELETE FROM user_profile_portrait")
    suspend fun deleteAll()
}

/**
 * 画像叙事（Room v6 → v7 新增，契约 v3.5 §7.4）：LLM 依据**侧写渲染**生成的描述性文本。
 *
 * **两面共用**同一份文本：消费面作人格卡叙事段，认知面随画像块注入 —— 共用是安全的，
 * 因为生成闸门（domain 层 `ProfileNarrative`）保证它**只能复述渲染里已有的事实**：
 * 输入不含称号、不含证据原文，禁词表拦截人格命名与类型学专名。
 * 「夜航者」这类人格命名仍然独占消费面，不进本表。
 *
 * **单行表**（id 恒 1，REPLACE 覆盖）：`facts_hash` 是生成输入的指纹 ——
 * 侧写重算后指纹变了，叙事即过期，由 MasterAgent 在对话启动时异步重生成；
 * **重生成失败保留旧文本**（宁旧勿假，离线也有得看）。
 */
@Entity(tableName = "user_profile_narrative")
data class UserProfileNarrativeEntity(
    @PrimaryKey val id: Int = 1,
    val text: String,
    /** 生成时侧写的指纹（[com.hmp.domain.agent.profile.ProfileNarrative.factsFingerprint]） */
    @ColumnInfo(name = "facts_hash") val factsHash: Int,
    @ColumnInfo(name = "generated_at") val generatedAt: Long,
)

@Dao
interface UserProfileNarrativeDao {

    @Query("SELECT * FROM user_profile_narrative WHERE id = 1")
    suspend fun get(): UserProfileNarrativeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(narrative: UserProfileNarrativeEntity)

    @Query("DELETE FROM user_profile_narrative")
    suspend fun deleteAll()
}
