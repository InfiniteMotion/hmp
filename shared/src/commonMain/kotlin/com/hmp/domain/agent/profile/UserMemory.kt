package com.hmp.domain.agent.profile

import co.touchlab.kermit.Logger
import com.hmp.data.database.UserProfileEvidenceDao
import com.hmp.data.database.UserProfileNarrativeDao
import com.hmp.data.database.UserProfileNarrativeEntity
import com.hmp.data.database.UserProfilePortraitDao
import com.hmp.data.database.currentTimeMillis
import com.hmp.data.mapper.UserProfileMapper
import com.hmp.data.mapper.UserProfileMapper.toDomain
import com.hmp.data.mapper.UserProfileMapper.toEntity
import com.hmp.domain.agent.port.AuditEntry
import com.hmp.domain.agent.port.AuditLogPort
import com.hmp.domain.music.MusicRepository

/**
 * 用户认识模块的**主体** —— 负责"什么时候写、写什么、怎么读"，本身不含规则。
 *
 * **身份（v3.7 定名 `UserMemory`，原 `UserProfileAgent`）**：它**不是 Agent** ——
 * 无自主循环、不调 LLM、不进 Scheduler，是 `MasterAgent` 的**记忆子系统**
 * （LLM 编排全部在 Master：叙事重生成、对话抽取）。命名就叫记忆，别再挂 Agent。
 *
 * 契约：`docs/7_x/B agent-build/design/agent-profile.md` v3.7 §4 / §7
 *
 * 分工（这是本模块最重要的结构约束）：
 * - **建模器**（[LibraryModeler] / [BehaviorModeler]）只产**证据**，不写侧写
 * - **派生器**（[PortraitComposer]）是侧写的**唯一产出路径**
 * - **本类**只做编排与持久化 —— 不判断"什么算集中"，只负责把结果存下来
 *
 * 四条纪律：
 * 1. **画像失败不得拖垮调用方** —— 扫描只要成功，画像出错不该让扫描报错（全部 `runCatching`）
 * 2. **日志只记条数，不记内容** —— 顺手 `Logger.d { "portrait=$portraits" }` 就把画像刷进
 *    logcat / 崩溃上报了（契约 §9.2 工程卫生）
 * 3. **写入前过谓词闭集闸门** —— 建模器不该产出闭集外的谓词，但这是流水线末端，多挡一道
 * 4. **每次写入都留审计** —— 契约 §2.6：建模重算不走工具，但**必须写 `agent_audit_log`**，
 *    与工具路径同等留痕。`reason` 只放条数与来源，不放画像内容
 */
class UserMemory(
    private val musicRepository: MusicRepository,
    private val evidenceDao: UserProfileEvidenceDao,
    private val portraitDao: UserProfilePortraitDao,
    private val narrativeDao: UserProfileNarrativeDao? = null,
    private val auditLog: AuditLogPort? = null,
    private val timeProvider: () -> Long = { currentTimeMillis() },
) {

    private val logTag = "Agent.Profile"

    /**
     * 曲库侧全量刷新 —— 扫描完成 / 库变更后调用。三段一起跑：
     *
     * | 段 | 来源 | 触发条件 |
     * |----|------|---------|
     * | 形态（阶段一） | `LIBRARY_SHAPE` 0.4 | 有曲库就跑 |
     * | 内容（阶段二） | `LIBRARY_CONTENT` 0.55 × 覆盖率 | **覆盖率达标才跑**（契约 §4.1.4，不是 100%） |
     * | 状态快照 | `T1_USER` 0.9 | 用户给过任何显式信号就跑 |
     *
     * 契约 §4.1.4 明确这是**事件驱动**，不能放进每日聚合：
     * 用户导入 3000 首后若等到次日凌晨才建模，agent 会面对**整整一天的空画像**。
     *
     * @return 本次刷新后落库的侧写条数（0 表示曲库为空、没有可说的 —— 冷启动不留空壳）
     */
    suspend fun refreshFromLibrary(): Int = runCatching {
        val now = timeProvider()
        val sessionId = "scan-$now"

        // ① 形态（阶段一）：只用 Music 表，扫描即得
        val tracks = loadLibraryTracks()
        val shapeDrafts = LibraryModeler.toEvidenceDrafts(LibraryModeler.computeShape(tracks), sessionId)

        // ② 内容（阶段二）：覆盖率不达标就不写 —— 不假装知道（§4.1.6）
        val content = runCatching { musicRepository.getLibraryContentSnapshot() }.getOrNull()
        val coverage = content?.coverageRate ?: 0f
        val contentShape = content?.let { LibraryModeler.computeContent(it) }
        val contentDrafts = if (content != null && coverage >= ProfileConfig.ENRICH_TARGET_COVERAGE) {
            LibraryModeler.toContentEvidenceDrafts(
                content = contentShape,
                coverageRate = coverage,
                sessionId = sessionId,
            )
        } else {
            emptyList()
        } +
            // B 类情绪一致性（T0b）：moodMix 的近似解读，带覆盖率折扣、覆盖率过低不写。
            // 只在**确有 MOOD 分布**时才产 —— 没有情绪标签时 mixOf 退化的 MEDIUM 不算数据，
            // 拿它写"情绪一致性"就是假装知道（契约 §4.1.6 同一纪律）。
            listOfNotNull(
                BPortraitModeler.moodConsistencyDraft(
                    moodMix = contentShape?.moodMix?.takeIf { content?.moodCounts?.isNotEmpty() == true },
                    coverageRate = coverage,
                    sessionId = sessionId,
                ),
            )

        // ③ 状态快照：收藏 / 明确不喜欢 / 歌单 / 隐藏文件夹 / 用户改过的标签
        val state = runCatching { musicRepository.getLibraryStateSnapshot() }.getOrNull()
        val stateDrafts = state?.let { LibraryModeler.toStateEvidenceDrafts(it, sessionId) }.orEmpty()

        (shapeDrafts + contentDrafts + stateDrafts).forEach { upsertEvidence(it, now) }

        val portraitCount = recomputePortraits(now, coverageAtModeling = content?.coverageRate?.toDouble())
        Logger.i(logTag) {
            "library refresh: tracks=${tracks.size} shape=${shapeDrafts.size} " +
                "content=${contentDrafts.size} state=${stateDrafts.size} portraits=$portraitCount"
        }
        audit(
            "profile.refresh.library",
            "shape=${shapeDrafts.size} content=${contentDrafts.size} state=${stateDrafts.size} portraits=$portraitCount",
        )
        portraitCount
    }.onFailure { e ->
        Logger.w(logTag, e) { "library refresh failed (non-fatal)" }
    }.getOrDefault(0)

    /**
     * 行为建模 —— 契约 §4.2：定时读播放记录，**零新埋点**。
     *
     * 带**日内去重**：距上次行为刷新不足 [ProfileConfig.BEHAVIOR_REFRESH_MIN_INTERVAL_MS] 就直接跳过。
     * 判断依据是证据表里 `T0_BEHAVIOR` 的最新 `updated_at` —— **不额外建状态表**，
     * 证据行本身就是"上次什么时候算过"的记录。
     */
    suspend fun refreshBehavior(force: Boolean = false): Int = runCatching {
        val now = timeProvider()
        if (!force) {
            val last = evidenceDao.newestUpdatedAtBySource(ProfileSources.T0_BEHAVIOR)
            if (last != null && now - last < ProfileConfig.BEHAVIOR_REFRESH_MIN_INTERVAL_MS) {
                Logger.i(logTag) { "behavior refresh skipped: refreshed ${(now - last) / 3_600_000L}h ago" }
                return@runCatching 0
            }
        }

        val snapshot = musicRepository.getBehaviorSnapshot(ProfileConfig.BEHAVIOR_WINDOW_DAYS)
        val drafts = BehaviorModeler.toEvidenceDrafts(snapshot, sessionId = "behavior-$now") +
            // B 类行为侧写（T0b）：秩序确定性 + 注意力模式，同一份快照顺手算
            BPortraitModeler.toBehaviorEvidenceDrafts(snapshot, sessionId = "behavior-$now")
        if (drafts.isEmpty()) {
            Logger.i(logTag) {
                "behavior refresh: not enough signal (plays=${snapshot.totalPlays} days=${snapshot.activeDays})"
            }
            audit("profile.refresh.behavior", "skipped: plays=${snapshot.totalPlays} days=${snapshot.activeDays}")
            return@runCatching 0
        }

        drafts.forEach { upsertEvidence(it, now) }
        val portraitCount = recomputePortraits(now, coverageAtModeling = null)
        Logger.i(logTag) {
            "behavior refresh: plays=${snapshot.totalPlays} evidence=${drafts.size} portraits=$portraitCount"
        }
        audit("profile.refresh.behavior", "plays=${snapshot.totalPlays} evidence=${drafts.size}")
        portraitCount
    }.onFailure { e ->
        Logger.w(logTag, e) { "behavior refresh failed (non-fatal)" }
    }.getOrDefault(0)

    /**
     * 记下用户**显式陈述**的偏好 / 忌讳（`profile_note` 工具与设置页修正共用）。
     *
     * 走 `T1_USER`（0.9，最高 ground truth）—— 这是"用户给的"，不是"我们猜的"。
     * 契约 §4.4 的底线在这里落地：**只接受闭集内的谓词**，不接受自由发明的谓词。
     */
    suspend fun noteStatedPreference(rawPredicate: String, value: String, sessionId: String? = null): Boolean {
        val predicate = normalizePredicate(rawPredicate) ?: run {
            Logger.w(logTag) { "rejected unknown predicate from note: $rawPredicate" }
            return false
        }
        val now = timeProvider()
        upsertEvidence(
            EvidenceDraft(
                predicate = predicate,
                value = value.trim(),
                source = ProfileSources.T1_USER,
                confidence = ProfileSources.baseConfidence(ProfileSources.T1_USER),
                sessionId = sessionId,
            ),
            now,
        )
        recomputePortraits(now, coverageAtModeling = null)
        audit("profile.note", "predicate=$predicate")
        return true
    }

    /** 否决一个侧写（`profile_forget` 工具）：写一条 `portrait.denied` 证据，派生时该类型整体不产出。 */
    suspend fun forgetPortrait(typeId: String, sessionId: String? = null): Boolean {
        val type = PortraitType.byId(typeId) ?: return false
        val now = timeProvider()
        upsertEvidence(
            EvidenceDraft(
                predicate = PortraitType.DENIED,
                value = type.id,
                source = ProfileSources.T1_USER,
                confidence = ProfileSources.baseConfidence(ProfileSources.T1_USER),
                sessionId = sessionId,
            ),
            now,
        )
        // 被否决的侧写要把已落库的那一行删掉 —— 否则设置页还会显示"伙伴记得你不喜欢被这样理解"，
        // 而它是被否决掉的
        portraitDao.deleteByType(type.id)
        audit("profile.forget", "type=${type.id}")
        return true
    }

    /**
     * 对话自动抽取的写入面（契约 §4.4，T0b 新链路）。
     *
     * 走 `T2_DIALOGUE`（0.35）：对话给**假设**，行为给**确证** —— 假设落 L2，
     * 同槽位后续出现行为证据时由 [upsertEvidence] 的置信度合并自然走高；
     * 从未被印证的假设随 L2 衰减退出（剧本 P9）。
     *
     * 谓词仍过闭集闸门（[upsertEvidence] 内），调用方给的菜单只是第一道。
     */
    suspend fun ingestDialogueEvidence(drafts: List<DialogueExtractor.Extraction>, sessionId: String?): Int {
        if (drafts.isEmpty()) return 0
        val now = timeProvider()
        var written = 0
        drafts.forEach { extraction ->
            // 第二道闭集闸门：菜单给了，调用方仍可能出错；拒收的不计数（宁可不记，不可错记）
            if (PortraitType.isKnown(extraction.predicate)) {
                upsertEvidence(
                    EvidenceDraft(
                        predicate = extraction.predicate,
                        value = extraction.value,
                        source = ProfileSources.T2_DIALOGUE,
                        confidence = ProfileSources.baseConfidence(ProfileSources.T2_DIALOGUE),
                        sessionId = sessionId,
                    ),
                    now,
                )
                written++
            } else {
                Logger.w(logTag) { "dialogue extraction rejected unknown predicate: ${extraction.predicate}" }
            }
        }
        if (written > 0) {
            recomputePortraits(now, coverageAtModeling = null)
            // 审计只记条数 —— 谓词名与 value 都不进审计（§9.2 工程卫生）
            audit("profile.dialogue", "items=$written")
        }
        return written
    }

    /**
     * 渲染给 system prompt 的画像块。没有任何可说的时候返回 **null**（不是空串）——
     * 调用方据此整块省略，冷启动不留空壳（剧本 P1）。
     *
     * 已生成过画像叙事时一并带上（契约 v3.5 §7.4，两面共用）—— 叙事在块内
     * 同样处于「仅供参考」口径之下，且只可能是槽位事实的复述（写入闸门保证）。
     */
    suspend fun renderForContext(): String? = runCatching {
        val drafts = loadPortraitDrafts()
        if (drafts.isEmpty()) return@runCatching null
        val narrative = narrativeDao?.get()?.text
        UserProfileRenderer.render(PortraitComposer.selectForContext(drafts), narrative)
    }.onFailure { e ->
        Logger.w(logTag, e) { "renderForContext failed (non-fatal, block omitted)" }
    }.getOrNull()

    /**
     * 侧写事实的渲染正文（无头无尾、无叙事）—— 画像叙事的**唯一合法生成输入**
     * （契约 v3.5 §7.4：叙事只能是事实的复述，所以输入里不许有称号和旧叙事）。
     */
    suspend fun factsRenderForNarrative(): String? = runCatching {
        val drafts = loadPortraitDrafts()
        if (drafts.isEmpty()) return@runCatching null
        UserProfileRenderer.renderBody(PortraitComposer.selectForContext(drafts))
    }.getOrNull()

    /** 当前侧写内容的指纹 —— 与落库叙事的 `factsHash` 不同即视为过期。 */
    suspend fun factsFingerprint(): Int = runCatching {
        ProfileNarrative.factsFingerprint(loadPortraitDrafts())
    }.getOrDefault(0)

    /** 叙事现状（未生成过返回 null）。 */
    suspend fun narrativeState(): ProfileNarrativeState? = runCatching {
        narrativeDao?.get()?.let {
            ProfileNarrativeState(text = it.text, factsHash = it.factsHash, generatedAt = it.generatedAt)
        }
    }.getOrNull()

    /**
     * 落库一份新生成的叙事（MasterAgent 生成链的写入面）。
     *
     * 过 [ProfileNarrative.validate] 闸门：越界文本**拒收并返回 false**，
     * 旧叙事保留（宁旧勿假）。审计只记动作不记内容。
     */
    suspend fun updateNarrative(rawText: String, factsHash: Int): Boolean {
        val text = ProfileNarrative.validate(rawText)
        val dao = narrativeDao ?: return false
        if (text == null) {
            Logger.w(logTag) { "narrative rejected by gate (length or forbidden words)" }
            return false
        }
        runCatching {
            dao.upsert(UserProfileNarrativeEntity(text = text, factsHash = factsHash, generatedAt = timeProvider()))
            audit("profile.narrative", "regenerated")
        }.onFailure { e ->
            Logger.w(logTag, e) { "narrative persist failed (non-fatal)" }
            return false
        }
        return true
    }

    /** 全量侧写（设置页「伙伴对你的认识」用；M2 的 F9-T2 消费）。 */
    suspend fun currentPortraits(): List<PortraitDraft> = runCatching {
        loadPortraitDrafts()
    }.onFailure { e ->
        Logger.w(logTag, e) { "currentPortraits failed (non-fatal)" }
    }.getOrDefault(emptyList())

    /**
     * 消费面人格卡（契约 §6，T0b 首位交付）。
     *
     * 与 [renderForContext] 共用同一份侧写数据、各走各的包装 —— 认知面与消费面**单向隔离**：
     * 这里只读不写，产物不回流决策面。没有可说的返回 null（冷启动不出空卡）。
     * UI 落点在 F9-T2 的设置页，本方法只负责供数。
     */
    suspend fun musicPersonalityCard(): PersonalityCardComposer.PersonalityCard? = runCatching {
        PersonalityCardComposer.compose(loadPortraitDrafts(), narrativeDao?.get()?.text)
    }.onFailure { e ->
        Logger.w(logTag, e) { "musicPersonalityCard failed (non-fatal)" }
    }.getOrNull()

    // ── 定向简报（契约 v3.8：读取面新增 SubAgent 消费点）──────────────
    //
    // Hello/Radio 在启动时由 Master 注入的画像**摘录** —— 不是全量：
    // 各取"能改变该子代理动作"的侧写类型（认知准入标准，契约 §5），
    // 文本走同一条渲染纪律（仅供参考口径 / 无内部标识 / B 类降级表达），上限更小。

    /**
     * 电台开播简报：时段 / 口味 / 听法 / 探索 —— 选种子与能量匹配、
     * 「安全 vs 冒险」比例用。**不含曲库侧写**（"拥有什么"对编排没用）。
     */
    suspend fun radioBriefing(): String? = briefingFor(
        types = setOf(PortraitType.TIME, PortraitType.TASTE, PortraitType.HABITS, PortraitType.EXPLORATION),
        maxChars = 300,
    )

    /**
     * Hello 门面简报：曲库 / 口味 / 探索 —— 每日推荐、DISCOVER 卡与"从没听过的"挖掘用。
     */
    suspend fun helloBriefing(): String? = briefingFor(
        types = setOf(PortraitType.LIBRARY, PortraitType.TASTE, PortraitType.EXPLORATION),
        maxChars = 300,
    )

    private suspend fun briefingFor(types: Set<PortraitType>, maxChars: Int): String? = runCatching {
        val drafts = loadPortraitDrafts()
        if (drafts.isEmpty()) return@runCatching null
        val selected = PortraitComposer.selectForContext(drafts).filter { it.type in types }
        UserProfileRenderer.render(selected, narrativeDao?.get()?.text, maxChars)
    }.onFailure { e ->
        Logger.w(logTag, e) { "briefing failed (non-fatal)" }
    }.getOrNull()

    /** 证据行全量（设置页的「凭什么」展开用）。 */
    suspend fun currentEvidence(): List<ProfileEvidence> = runCatching {
        evidenceDao.getAll().map { it.toDomain() }
    }.getOrDefault(emptyList())

    /** 清空画像三层（证据 / 侧写 / 叙事 ——「清除画像 / 关闭个性化」入口用，契约 §13 PF11）。 */
    suspend fun clear() {
        runCatching {
            evidenceDao.deleteAll()
            portraitDao.deleteAll()
            narrativeDao?.deleteAll()
            Logger.i(logTag) { "cleared" }
            audit("profile.clear", "all")
        }.onFailure { e -> Logger.w(logTag, e) { "clear failed (non-fatal)" } }
    }

    // ── 内部 ───────────────────────────────────────────────────────────

    /**
     * 读曲库。
     *
     * `orderBy = "id"` 是**刻意**的：`title/artist/album` 会走各端的**内存拼音排序**
     * （见 `MusicRepositoryImpl.desktop` 的 `stringToPinyinSortKey`）——
     * 建模不看顺序，没必要为几千首歌付这个代价。非文本键在 ASC 下原样返回。
     *
     * 该查询本身带 `WHERE music.isDeleted = 0`，天然符合"排除软删除"纪律（契约 §4.3）。
     */
    private suspend fun loadLibraryTracks(): List<LibraryTrack> =
        musicRepository.getAllMusicInfoAsList("id", "ASC").map { info ->
            LibraryTrack(
                artist = info.music.artist,
                album = info.music.album,
                durationMs = info.music.duration,
                path = info.music.path,
            )
        }

    /** 侧写表不存来源（它是派生物），来源要按反链把证据取回来 —— 上下文门槛按"事实/推断"区分。 */
    private suspend fun loadPortraitDrafts(): List<PortraitDraft> {
        val rows = portraitDao.getAll()
        if (rows.isEmpty()) return emptyList()
        val refs = rows.flatMap { UserProfileMapper.decodeRefs(it.evidenceRefs) }.distinct()
        val sourceById = if (refs.isEmpty()) {
            emptyMap()
        } else {
            evidenceDao.getByIds(refs).associate { it.id to it.source }
        }
        return rows.map { row ->
            row.toDomain(
                UserProfileMapper.decodeRefs(row.evidenceRefs).mapNotNull { sourceById[it] }.toSet()
            )
        }
    }

    /** 把工具/用户面传来的谓词规整成闭集内的形式：接受 `type.slot` 或裸槽位名（默认落在曲库侧写）。 */
    private fun normalizePredicate(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed == PortraitType.DENIED) return PortraitType.DENIED
        if (PortraitType.isKnown(trimmed)) return trimmed
        // 只给了槽位名 → 在闭集里找唯一匹配；多处匹配或找不到都拒绝（不猜）
        val matches = PortraitType.entries
            .flatMap { type -> type.slots.filter { it == trimmed }.map { PortraitType.predicateOf(type, it) } }
            .distinct()
        return if (matches.size == 1) matches.single() else null
    }

    private suspend fun upsertEvidence(draft: EvidenceDraft, now: Long) {
        if (!PortraitType.isKnown(draft.predicate)) {
            Logger.w(logTag) { "rejected predicate outside closed set: ${draft.predicate}" }
            return
        }
        val existing = evidenceDao.find(UserProfileMapper.SUBJECT_USER, draft.predicate, draft.value)
        if (existing == null) {
            evidenceDao.insert(draft.toEntity(now))
        } else {
            // 同一会话内重复观察到同一事实不算新的"会话"（契约 §3.1：单次会话内高频不计）
            val sessionDelta = if (existing.lastSessionId != draft.sessionId) 1 else 0
            val mergedSource = if (existing.source == ProfileSources.T1_USER) existing.source else draft.source
            val mergedConfidence = maxOf(existing.confidence, draft.confidence)
            evidenceDao.confirmObservation(
                id = existing.id,
                source = mergedSource,
                confidence = mergedConfidence,
                updatedAt = now,
                sessionDelta = sessionDelta,
                sessionId = draft.sessionId,
            )
        }
    }

    /** 证据 → 侧写 → 落库。侧写的 `created_at` 不因覆盖而重置（审计四问之一）。 */
    private suspend fun recomputePortraits(now: Long, coverageAtModeling: Double?): Int {
        val evidence = evidenceDao.getAll().map { it.toDomain() }
        val portraits = PortraitComposer.compose(evidence, now)
        if (portraits.isEmpty()) return 0

        val existingCreatedAt = portraitDao.getAll().associate { it.type to it.createdAt }
        portraits.forEach { portrait ->
            val withCoverage = if (portrait.type == PortraitType.LIBRARY && coverageAtModeling != null) {
                portrait.copy(coverageAtModeling = coverageAtModeling)
            } else {
                portrait
            }
            portraitDao.upsert(
                withCoverage.toEntity(
                    nowMs = now,
                    createdAt = existingCreatedAt[portrait.type.id] ?: now,
                )
            )
        }
        return portraits.size
    }

    /** 审计：只记条数与来源，**绝不记画像内容**（契约 §9.2 工程卫生）。 */
    private suspend fun audit(tool: String, reason: String) {
        runCatching { auditLog?.record(AuditEntry(tool = tool, outcome = "success", reason = reason)) }
            .onFailure { Logger.w(logTag, it) { "audit failed (non-fatal)" } }
    }
}
