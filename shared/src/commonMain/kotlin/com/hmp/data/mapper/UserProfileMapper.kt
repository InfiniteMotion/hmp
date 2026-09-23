package com.hmp.data.mapper

import com.hmp.data.database.UserProfileEvidenceEntity
import com.hmp.data.database.UserProfilePortraitEntity
import com.hmp.domain.agent.profile.EvidenceDraft
import com.hmp.domain.agent.profile.PortraitDraft
import com.hmp.domain.agent.profile.PortraitTier
import com.hmp.domain.agent.profile.PortraitType
import com.hmp.domain.agent.profile.ProfileEvidence
import kotlinx.serialization.json.Json

/**
 * 画像两层表的 实体 ↔ 领域对象 换算。
 *
 * 与 `MusicMapper` 的既有做法一致：领域模型不背 Room 注解，换算集中在这里。
 */
object UserProfileMapper {

    /** 契约 §2.3：`subject` v1 恒为 "USER" */
    const val SUBJECT_USER = "USER"

    private val json = Json { encodeDefaults = true }

    // ── 证据层 ─────────────────────────────────────────────────────────

    fun UserProfileEvidenceEntity.toDomain(): ProfileEvidence = ProfileEvidence(
        predicate = predicate,
        value = value,
        source = source,
        confidence = confidence,
        id = id,
        createdAt = createdAt,
        updatedAt = updatedAt,
        evidenceCount = evidenceCount,
        distinctSessions = distinctSessions,
        lastSessionId = lastSessionId,
    )

    fun EvidenceDraft.toEntity(nowMs: Long): UserProfileEvidenceEntity = UserProfileEvidenceEntity(
        subject = SUBJECT_USER,
        predicate = predicate,
        value = value,
        source = source,
        confidence = confidence,
        createdAt = nowMs,
        updatedAt = nowMs,
        evidenceCount = 1,
        distinctSessions = 1,
        lastSessionId = sessionId,
    )

    // ── 侧写层 ─────────────────────────────────────────────────────────

    /**
     * [sources] 由调用方从 `evidenceRefs` 反查得出 —— **侧写表不存来源**：
     * 侧写是派生物，它的"来源"就是它引用的那些证据行（契约 §2.6）。
     */
    fun UserProfilePortraitEntity.toDomain(sources: Set<String>): PortraitDraft = PortraitDraft(
        type = PortraitType.byId(type) ?: PortraitType.LIBRARY,
        tier = if (tier == PortraitTier.L3.id) PortraitTier.L3 else PortraitTier.L2,
        slots = decodeSlots(slotsJson),
        evidenceRefs = decodeRefs(evidenceRefs),
        confidence = confidence,
        sources = sources,
        coverageAtModeling = coverageAtModeling,
    )

    /** [createdAt] 传已存在的行的时间：覆盖时不该把"何时建立"重置（审计四问之一）。 */
    fun PortraitDraft.toEntity(nowMs: Long, createdAt: Long): UserProfilePortraitEntity =
        UserProfilePortraitEntity(
            type = type.id,
            tier = tier.id,
            slotsJson = encodeSlots(slots),
            evidenceRefs = encodeRefs(evidenceRefs),
            confidence = confidence,
            createdAt = createdAt,
            updatedAt = nowMs,
            coverageAtModeling = coverageAtModeling,
        )

    // ── 槽位与反链的编解码 ─────────────────────────────────────────────

    fun encodeSlots(slots: Map<String, String>): String = json.encodeToString(slots)

    fun decodeSlots(raw: String): Map<String, String> =
        runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())

    /** 反链存的是证据行 id 的 CSV（契约 §2.4：≤10 条/侧写，不用关联表）。 */
    fun encodeRefs(ids: List<Long>): String = ids.joinToString(",")

    fun decodeRefs(raw: String): List<Long> =
        raw.split(',').mapNotNull { it.trim().toLongOrNull() }
}
