package com.hmp.domain.agent.profile

import com.hmp.data.database.UserProfileEvidenceEntity
import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.test.db.createTestDatabase
import com.hmp.test.fakes.FakeAuditLogPort
import com.hmp.test.fakes.FakeMusicRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 用户认识模块的**端到端**集成测试（真 Room，非内存替身）。
 *
 * 契约：`agent-profile.md` v3.2 §11 的剧本 P1 / P2 / P20 / P21，以及数据层的幂等要求。
 *
 * 与 `LibraryModelerTest` / `PortraitComposerTest` 的分工：那两组测**规则**，
 * 这组测**规则接上真库之后还成不成立** —— 尤其是"重复刷新不会把证据表撑爆"
 * 和"渲染出来的东西不会泄漏绝对路径"这两件，只有在真库上才验得出来。
 */
class UserMemoryIntegrationTest {

    private lateinit var db: com.hmp.data.database.AppDatabase
    private lateinit var musicRepository: FakeMusicRepository
    private lateinit var audit: FakeAuditLogPort
    private lateinit var agent: UserMemory

    /** 可控时钟：每次刷新取一个不同的值 → 拿到不同的 sessionId（模拟"另一次扫描"）。 */
    private var now = 1_700_000_000_000L

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        musicRepository = FakeMusicRepository()
        audit = FakeAuditLogPort()
        agent = UserMemory(
            musicRepository = musicRepository,
            evidenceDao = db.userProfileEvidenceDao(),
            portraitDao = db.userProfilePortraitDao(),
            narrativeDao = db.userProfileNarrativeDao(),
            auditLog = audit,
            timeProvider = { now },
        )
    }

    @AfterTest
    fun teardown() {
        db.close()
    }

    private fun addTrack(
        id: Long,
        artist: String = "Artist$id",
        album: String = "Album$id",
        durationMs: Long = 240_000,
        path: String = "/Users/someone/Music/Rock/track$id.mp3",
    ) {
        musicRepository.addMusic(
            MusicInfo(
                music = Music(
                    id = id,
                    title = "Title$id",
                    artist = artist,
                    album = album,
                    duration = durationMs,
                    path = path,
                    albumArtUri = "",
                ),
                extra = null,
                userInfo = null,
            )
        )
    }

    private suspend fun evidenceCount(): Int = db.userProfileEvidenceDao().getAll().size
    private suspend fun portraitCount(): Int = db.userProfilePortraitDao().getAll().size

    // ── P1：全新用户，无曲库 ────────────────────────────────────────────

    @Test
    fun P1_emptyLibrary_writesNothing_andRendersNothing() = runTest {
        val written = agent.refreshFromLibrary()

        assertEquals(0, written, "空曲库不该产出侧写")
        assertEquals(0, evidenceCount(), "空曲库不该写证据")
        assertEquals(0, portraitCount())
        assertNull(agent.renderForContext(), "没有可说的就返回 null，好让首轮块整块省略")
    }

    // ── P2：有曲库、无播放记录 —— M1 的判据 ────────────────────────────

    @Test
    fun P2_libraryOnly_producesPortrait_andIsUsableInContext() = runTest {
        // 一个"口味集中 + 按目录分类 + 偏长曲"的库
        repeat(5) { i -> addTrack(id = i.toLong() + 1, artist = "Big", path = "/Users/someone/Music/深夜/t$i.mp3") }
        repeat(5) { i -> addTrack(id = i.toLong() + 6, artist = "S$i", path = "/Users/someone/Music/爵士/t$i.mp3") }
        repeat(5) { i -> addTrack(id = i.toLong() + 11, artist = "T$i", album = "Alb$i", durationMs = 400_000) }

        val written = agent.refreshFromLibrary()
        assertEquals(1, written, "M1 只产出曲库侧写一条")

        val portraits = agent.currentPortraits()
        val library = portraits.single()
        assertEquals(PortraitType.LIBRARY, library.type)
        assertEquals(PortraitTier.L3, library.tier, "曲库侧写直接落 L3")
        assertTrue(library.evidenceRefs.isNotEmpty(), "侧写必须能反查到证据行")
        assertEquals(0.4, library.confidence, 1e-9)

        // 反链真的指得到东西（这是"凭什么这么说"的落点）
        val refs = db.userProfileEvidenceDao().getByIds(library.evidenceRefs)
        assertEquals(library.evidenceRefs.size, refs.size, "evidenceRefs 应条条指得到实际证据行")

        val rendered = assertNotNull(agent.renderForContext(), "有新曲库时第一次对话就该有内容")
        assertTrue(rendered.startsWith("【对你的认识 · 仅供参考】"))
        assertTrue(rendered.contains("我还在认识你的曲库"), "阶段一必须如实说明看得还不够细")
    }

    // ── P20：渲染不得泄漏绝对路径 / 内部标识 ────────────────────────────

    @Test
    fun P20_renderedText_leaksNeitherAbsolutePathNorInternalKeys() = runTest {
        repeat(6) { i ->
            addTrack(
                id = i.toLong() + 1,
                artist = "A$i",
                path = "/Users/someone/Music/深夜开车/track$i.mp3",
            )
        }
        agent.refreshFromLibrary()
        val rendered = assertNotNull(agent.renderForContext())

        assertFalse(rendered.contains("/Users"), "绝对路径不得进上下文")
        assertFalse(rendered.contains("someone"), "家目录名（用户名）不得进上下文")
        assertFalse(rendered.contains("Music"), "通用容器名不是用户的分类，不该出现")
        assertTrue(rendered.contains("深夜开车"), "用户自己的分类名应当保留")

        listOf("library_portrait", "pathGroups", "artistConcentration", "LIBRARY_SHAPE").forEach {
            assertFalse(rendered.contains(it), "内部标识「$it」不得进上下文")
        }
    }

    // ── 幂等：重复扫描不撑爆证据表（唯一索引 + 累加，而不是重复建行）─────

    @Test
    fun repeatedRefresh_accumulatesCounters_withoutDuplicatingRows() = runTest {
        repeat(4) { i -> addTrack(id = i.toLong() + 1, artist = "A$i") }

        agent.refreshFromLibrary()
        val rowsAfterFirst = evidenceCount()
        val refsAfterFirst = agent.currentPortraits().single().evidenceRefs
        val createdAtFirst = db.userProfilePortraitDao().getByType(PortraitType.LIBRARY.id)!!.createdAt

        // 第二次扫描：时间推进 → sessionId 变化 → 算作"另一个会话"
        now += 60_000
        agent.refreshFromLibrary()

        assertEquals(rowsAfterFirst, evidenceCount(), "同一事实只该有一行（唯一索引），计数靠累加")
        val rows = db.userProfileEvidenceDao().getAll()
        rows.forEach { row ->
            assertEquals(2, row.evidenceCount, "两次观察 → evidence_count = 2")
            assertEquals(2, row.distinctSessions, "两次扫描是两个会话 → distinct_sessions = 2")
        }

        val portraitAfter = db.userProfilePortraitDao().getByType(PortraitType.LIBRARY.id)!!
        assertEquals(createdAtFirst, portraitAfter.createdAt, "覆盖不该重置『何时建立』（审计四问之一）")
        assertTrue(portraitAfter.updatedAt > portraitAfter.createdAt, "『何时被确证』应当刷新")
        assertEquals(refsAfterFirst, agent.currentPortraits().single().evidenceRefs, "反链应保持稳定")
    }

    @Test
    fun sameSessionRefresh_doesNotDoubleCountSessions() = runTest {
        addTrack(id = 1)
        // 时钟不动 → 两次 refresh 拿到同一个 sessionId
        agent.refreshFromLibrary()
        agent.refreshFromLibrary()

        val row = db.userProfileEvidenceDao().getAll().first()
        assertEquals(2, row.evidenceCount, "同会话内重复观察仍计入 evidence_count")
        assertEquals(1, row.distinctSessions, "但**不计入** distinct_sessions —— 单次会话内高频不是偏好")
    }

    // ── 清除（「清除画像」入口的基础）────────────────────────────────────

    @Test
    fun clear_removesBothLayers() = runTest {
        repeat(3) { i -> addTrack(id = i.toLong() + 1, artist = "A$i") }
        agent.refreshFromLibrary()
        assertTrue(evidenceCount() > 0)

        agent.clear()

        assertEquals(0, evidenceCount())
        assertEquals(0, portraitCount())
        assertNull(agent.renderForContext())
    }

    // ── 闭集闸门：闭集外的谓词不得变成侧写 ──────────────────────────────

    @Test
    fun evidenceOutsideClosedSet_isIgnoredByComposer() = runTest {
        // 模拟"有别的路径往证据表写了越界谓词"（DAO 本身不做语义校验）
        db.userProfileEvidenceDao().insert(
            UserProfileEvidenceEntity(
                subject = "USER",
                predicate = "library_portrait.madeUpSlot",
                value = "X",
                source = ProfileSources.LIBRARY_SHAPE,
                confidence = 0.99,
                createdAt = now,
                updatedAt = now,
            )
        )
        repeat(3) { i -> addTrack(id = i.toLong() + 1, artist = "A$i") }
        agent.refreshFromLibrary()

        val portraits = agent.currentPortraits()
        assertEquals(1, portraits.size, "越界证据不得凭空造出一个侧写")
        val slots = portraits.single().slots
        assertFalse(slots.containsKey("madeUpSlot"), "闭集外的槽位不得进侧写")
        assertTrue(slots.containsKey(PortraitType.SCALE), "正常槽位照常派生")
    }

    // ── 阶段二：覆盖率门槛与折扣（剧本 P4 / P6）─────────────────────────

    @Test
    fun contentModeling_onlyRunsOnceCoverageCrossesTarget() = runTest {
        repeat(10) { i -> addTrack(id = i.toLong() + 1, artist = "A$i") }
        // 覆盖率 0.3，远低于目标线 0.9
        musicRepository.contentSnapshot = LibraryContentSnapshot(
            totalSongs = 10,
            enrichedSongs = 3,
            genreCounts = mapOf("ROCK" to 8, "JAZZ" to 2),
        )
        agent.refreshFromLibrary()

        var slots = agent.currentPortraits().single().slots
        assertFalse(slots.containsKey(PortraitType.GENRE_BREADTH), "覆盖率没达标就不该断言类型构成")

        // 覆盖率跨过目标线后重算
        musicRepository.contentSnapshot = musicRepository.contentSnapshot.copy(enrichedSongs = 10)
        now += 60_000
        agent.refreshFromLibrary()

        slots = agent.currentPortraits().single().slots
        assertTrue(slots.containsKey(PortraitType.GENRE_BREADTH), "达标后才产出内容槽位")
        assertTrue(slots.containsKey(PortraitType.COVERAGE))

        // 折扣确实生效：内容证据的置信度 = 0.55 × 1.0
        val content = db.userProfileEvidenceDao().getAll()
            .first { it.source == ProfileSources.LIBRARY_CONTENT }
        assertEquals(0.55, content.confidence, 1e-6)
    }

    // ── 状态快照：用户显式给的信号 ───────────────────────────────────────

    @Test
    fun stateSnapshot_becomesT1Evidence_andShowsUpInRendering() = runTest {
        repeat(5) { i -> addTrack(id = i.toLong() + 1, artist = "A$i") }
        musicRepository.stateSnapshot = LibraryStateSnapshot(
            totalSongs = 5,
            likedCount = 3,
            dislikedCount = 0,
            playlistNames = listOf("深夜开车"),
            hiddenFolderCount = 0,
        )
        agent.refreshFromLibrary()

        val state = db.userProfileEvidenceDao().getAll().filter { it.source == ProfileSources.T1_USER }
        assertTrue(state.isNotEmpty(), "用户给的信号要落成 T1 证据")
        assertTrue(state.all { it.confidence == 0.9 })

        val rendered = assertNotNull(agent.renderForContext())
        assertTrue(rendered.contains("收藏"), "渲染要反映显式信号")
        assertTrue(rendered.contains("深夜开车"), "歌单名是用户自己的分类语言")
    }

    // ── 行为建模 + 日内闸门 ────────────────────────────────────────────

    @Test
    fun behaviorRefresh_writesPortrait_andSkipsWithinTheSameDay() = runTest {
        musicRepository.behaviorSnapshot = BehaviorSnapshot(
            windowDays = 90,
            totalPlays = 120,
            activeDays = 25,
            hourHistogram = mapOf(22 to 30, 23 to 20, 21 to 10),
            completedPlays = 90,
            skipPointBuckets = mapOf(BehaviorModeler.SKIP_INTRO to 15),
            distinctTracks = 50,
            novelTracks = 10,
        )

        assertEquals(4, agent.refreshBehavior(), "时段 + 听法 + 探索 + 秩序四个侧写（B 类注意力要会话数达标才有）")
        assertEquals(1, audit.byTool("profile.refresh.behavior").size)

        // 同一天内再调：闸门拦住（20 小时内不重复算）
        now += 3600_000
        assertEquals(0, agent.refreshBehavior(), "20 小时内不重复算")
        // force 可绕过
        assertEquals(4, agent.refreshBehavior(force = true))

        val rendered = assertNotNull(agent.renderForContext())
        assertTrue(rendered.contains("夜里") || rendered.contains("很少中途跳歌"), "行为侧写要能渲染")
    }

    @Test
    fun behaviorRefresh_staysQuiet_whenTooLittleData() = runTest {
        musicRepository.behaviorSnapshot = BehaviorSnapshot(
            windowDays = 90,
            totalPlays = 3,
            activeDays = 1,
            hourHistogram = mapOf(22 to 3),
            distinctTracks = 3,
        )
        assertEquals(0, agent.refreshBehavior(), "三次播放推不出习惯")
        assertTrue(agent.currentPortraits().none { it.type == PortraitType.HABITS })
    }

    // ── 工具面：profile_note / profile_forget ──────────────────────────

    @Test
    fun profileNote_acceptsClosedSetSlot_andRejectsInventedOne() = runTest {
        addTrack(id = 1)
        assertTrue(agent.noteStatedPreference("completionRate", "HIGH"), "裸槽位名应被规整成闭集内谓词")
        assertTrue(agent.renderForContext()!!.contains("很少中途跳歌"))

        assertFalse(
            agent.noteStatedPreference("他最近心情不好", "LOW"),
            "自由发明的谓词必须被拒 —— 对话推断只能落进闭集槽位（契约 §4.4）",
        )
    }

    @Test
    fun profileForget_removesPortrait_andDoesNotRebuildIt() = runTest {
        repeat(6) { i -> addTrack(id = i.toLong() + 1, artist = "A$i") }
        agent.refreshFromLibrary()
        assertEquals(1, agent.currentPortraits().size)

        assertTrue(agent.forgetPortrait(PortraitType.LIBRARY.id))
        assertEquals(0, agent.currentPortraits().size, "被否决的侧写要立刻消失")
        assertNull(agent.renderForContext())

        // 再刷新也不该重建（denied 证据在库里）
        now += 60_000
        agent.refreshFromLibrary()
        assertEquals(0, agent.currentPortraits().size, "用户否决过的不许重建（剧本 P14）")
    }

    // ── 审计：建模重算也要留痕（契约 §2.6）──────────────────────────────

    @Test
    fun everyRefresh_isAudited_withoutLeakingPortraitContent() = runTest {
        addTrack(id = 1)
        agent.refreshFromLibrary()
        agent.clear()

        assertEquals(1, audit.byTool("profile.refresh.library").size)
        assertEquals(1, audit.byTool("profile.clear").size)
        // reason 里只该有条数，不该有画像内容
        audit.entries.forEach { entry ->
            val reason = entry.reason.orEmpty()
            assertFalse(reason.contains("深夜"), "审计理由不得含画像内容")
            assertFalse(reason.contains("ROCK"), "审计理由不得含标签名")
        }
    }

    // ── T0b：B 类侧写与注意力会话推断 ─────────────────────────────────

    @Test
    fun attentionAndOrderPortraits_writtenFromBehaviorSessions_andRenderDegraded() = runTest {
        musicRepository.behaviorSnapshot = BehaviorSnapshot(
            windowDays = 90,
            totalPlays = 60,
            activeDays = 12,
            hourHistogram = mapOf(10 to 30),
            completedPlays = 30,
            distinctTracks = 30,
            novelTracks = 24,
            sessionCount = 6,
            avgSessionMinutes = 6f,
            avgTracksPerSession = 2f,
        )
        agent.refreshBehavior(force = true)

        val types = agent.currentPortraits().map { it.type }
        assertTrue(types.contains(PortraitType.ATTENTION), "会话数达标要产注意力侧写")
        assertTrue(types.contains(PortraitType.ORDER), "确定性由重复度×新歌交叉得出")

        val rendered = assertNotNull(agent.renderForContext())
        assertTrue(rendered.contains("你在音乐里表现出的"), "B 类必须降级表达（契约 §5.2）")
        assertTrue(rendered.contains("碎着听"), "碎片化结论要渲染出来")
    }

    // ── T0b：对话自动抽取写入面（T2_DIALOGUE）─────────────────────────

    @Test
    fun dialogueExtraction_writesT2Hypothesis_landsL2_staysOutOfContext() = runTest {
        val predicate = PortraitType.predicateOf(PortraitType.TASTE, PortraitType.LEADING_GENRE)
        val written = agent.ingestDialogueEvidence(
            listOf(DialogueExtractor.Extraction(predicate, "爵士")),
            sessionId = "chat-1",
        )
        assertEquals(1, written)

        val taste = agent.currentPortraits().single { it.type == PortraitType.TASTE }
        assertEquals(PortraitTier.L2, taste.tier, "对话假设默认落 L2（契约 §4.4）")
        assertEquals(setOf(ProfileSources.T2_DIALOGUE), taste.sources)

        // 0.35 的假设低于推断类门槛 0.5 —— 行为确证前不进上下文（PF4 / 剧本 P8-P9）
        assertNull(agent.renderForContext(), "未被行为确证的对话假设不该出现在画像块里")

        // 闭集外的抽取结果写不进去（第二道闸门，ingest 拒收不计数）
        val rejected = agent.ingestDialogueEvidence(
            listOf(DialogueExtractor.Extraction("user_mood.current", "压力大")),
            sessionId = "chat-2",
        )
        assertEquals(0, rejected, "闭集外的谓词必须被拒收且不计入写入数")
        assertTrue(agent.currentPortraits().none { it.slots.values.any { v -> v.contains("压力大") } })
    }

    // ── T0b：消费面人格卡端到端 ───────────────────────────────────────

    @Test
    fun personalityCard_composesFromLibraryAndBehavior() = runTest {
        repeat(5) { i -> addTrack(id = i.toLong() + 1, artist = "Big", path = "/Users/someone/Music/深夜/t$i.mp3") }
        musicRepository.behaviorSnapshot = BehaviorSnapshot(
            windowDays = 90,
            totalPlays = 120,
            activeDays = 25,
            hourHistogram = mapOf(23 to 40, 0 to 25),
            completedPlays = 110,
            skipPointBuckets = mapOf(BehaviorModeler.SKIP_INTRO to 5),
            distinctTracks = 50,
            novelTracks = 10,
        )
        agent.refreshFromLibrary()
        agent.refreshBehavior(force = true)

        val card = assertNotNull(agent.musicPersonalityCard())
        assertEquals("夜行", card.axes.first { it.title == "时段倾向" }.pole, "23 点与凌晨为主 → 夜行")
        assertEquals("沉浸", card.axes.first { it.title == "听法" }.pole, "完播率 0.9+ → 沉浸")
        // 标签未就绪：宽度轴走歌手集中度近似并标"初步"（PF16），三轴仍齐 → 命名「夜航者」
        val breadth = card.axes.first { it.title == "口味宽度" }
        assertTrue(breadth.preliminary)
        assertEquals("夜航者", card.typeName)
        assertFalse(card.sentences.isEmpty())
    }

    // ── T0b+：画像叙事（两面共用，契约 v3.5 §7.4）─────────────────────

    @Test
    fun narrative_writeReadRegenerate_andGuardrails() = runTest {
        repeat(5) { i -> addTrack(id = i.toLong() + 1, artist = "Big", path = "/Users/someone/Music/深夜/t$i.mp3") }
        agent.refreshFromLibrary()

        val fingerprint = agent.factsFingerprint()
        assertTrue(agent.updateNarrative("你常在天黑之后打开播放器，曲目集中在少数几位歌手身上，一听到底。", fingerprint))
        val state = assertNotNull(agent.narrativeState())
        assertEquals(fingerprint, state.factsHash)

        // 认知面：叙事进「仅供参考」块
        val rendered = assertNotNull(agent.renderForContext())
        assertTrue(rendered.contains("整体印象："), "叙事要以整体印象行进入画像块")
        assertTrue(rendered.contains("一听到底"))

        // 消费面：人格卡带同一份叙事
        assertEquals(state.text, agent.musicPersonalityCard()?.narrative)

        // 闸门：越界文本（禁词 / 过长）拒收，旧叙事保留
        val rejected = agent.updateNarrative("你是名副其实的夜航者，听歌风格非常专一。", fingerprint)
        assertFalse(rejected, "人格命名不得进叙事（称号独占消费面）")
        assertEquals(state.text, agent.narrativeState()?.text, "拒收后旧叙事原样保留")

        // 过期：侧写内容变化（新增目录分组）→ 指纹不一致，MasterAgent 据此触发重生成
        addTrack(id = 99, artist = "New", path = "/Users/someone/Music/爵士/x.mp3")
        now += 60_000
        agent.refreshFromLibrary()
        assertTrue(agent.factsFingerprint() != state.factsHash, "侧写变化后指纹必须改变")

        // 生成输入：只有事实句，无头无尾无旧叙事
        val facts = assertNotNull(agent.factsRenderForNarrative())
        assertFalse(facts.contains("仅供参考"))
        assertFalse(facts.contains("整体印象"))

        // clear 清三层
        agent.clear()
        assertNull(agent.narrativeState())
    }

    // ── v3.8：Hello/Radio 定向简报 ────────────────────────────────────

    @Test
    fun briefings_areRoleTrimmed_andCarryDiscipline() = runTest {
        repeat(5) { i -> addTrack(id = i.toLong() + 1, artist = "Big", path = "/Users/someone/Music/深夜/t$i.mp3") }
        musicRepository.behaviorSnapshot = BehaviorSnapshot(
            windowDays = 90,
            totalPlays = 120,
            activeDays = 25,
            hourHistogram = mapOf(23 to 40),
            completedPlays = 110,
            skipPointBuckets = mapOf(BehaviorModeler.SKIP_INTRO to 5),
            distinctTracks = 50,
            novelTracks = 10,
        )
        agent.refreshFromLibrary()
        agent.refreshBehavior(force = true)

        val radio = assertNotNull(agent.radioBriefing(), "有画像就该出电台简报")
        assertTrue(radio.contains("仅供参考"), "简报自带「仅供参考」口径")
        assertTrue(radio.contains("夜里") || radio.contains("很少中途跳歌"), "时段/听法行要在")
        // 电台简报不含曲库侧写行（"拥有什么"对编排没用——目录分类是曲库侧写独有句式）
        assertFalse(radio.contains("自己分好了类"))
        val hello = assertNotNull(agent.helloBriefing())
        assertTrue(hello.contains("仅供参考"))
        assertTrue(hello.contains("集中在少数几个歌手身上"), "Hello 要曲库行（DISCOVER 挖掘用）")
        assertTrue(radio.length <= 320, "简报限长 300（含余量断言）")

        // 冷启动：清空画像后两类简报都为 null（不留空壳）
        agent.clear()
        assertNull(agent.radioBriefing())
        assertNull(agent.helloBriefing())
    }
}
