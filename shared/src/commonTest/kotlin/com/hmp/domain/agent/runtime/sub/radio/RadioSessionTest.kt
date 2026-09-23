package com.hmp.domain.agent.runtime.sub.radio

import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.port.PauseEvent
import com.hmp.domain.agent.port.TrackOutcome
import com.hmp.domain.agent.port.TrackSettledEvent
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 按 `docs/7_x/B agent-build/design/agent-radio.md` §4 的剧本验收决策内核。
 *
 * 这里验的是**我们这一侧的契约**：事实有没有被忠实记录、进度有没有传给模型、
 * 模型的 none 会不会真的什么都不做、在播那首有没有被标注为不可动。
 * 「该不该换批」是模型的判断，不由这些测试断言。
 */
class RadioSessionTest {

    /** 测试台：可编程的模型回复 + 记录所有产出。 */
    private class Harness(
        mergeWindowMs: Long = 200L,
        private val responses: MutableList<String?>,
        private val snapshot: RadioContextSnapshot = RadioContextSnapshot(
            playingTitle = "在播曲", playingMusicId = 999L,
            queueRemaining = 5,
            upcoming = listOf(
                RadioQueueEntry(11L, "A"), RadioQueueEntry(22L, "B"), RadioQueueEntry(33L, "C"),
            ),
        ),
        theme: String? = null,
        candidates: suspend () -> String = { "" },
        generation: () -> Int = { 0 },
    ) {
        val sentMessages = mutableListOf<List<LlmMessage>>()
        val verdicts = mutableListOf<RadioVerdict>()
        val dropCalls = mutableListOf<Int>()
        var refillCalls = 0
        var turns = 0

        val session = RadioSession(
            targetCount = 12,
            mergeWindowMs = mergeWindowMs,
            clock = { 0L },
            theme = theme,
            judge = { messages ->
                sentMessages += messages
                responses.removeFirstOrNull()
            },
            onVerdict = { g, verdict ->
                if (generation() == g) verdicts += verdict else dropCalls += g
                generation() == g
            },
            generation = generation,
            fallbackRefill = { refillCalls++ },
            candidates = candidates,
            snapshot = { snapshot },
            onTurn = { turns++ },
        )

        fun settled(
            title: String,
            outcome: TrackOutcome,
            playedMs: Long,
            totalMs: Long = 240_000L,
            musicId: Long = 1L,
        ) = TrackSettledEvent(
            musicId = musicId, title = title, outcome = outcome,
            playedMs = playedMs, totalMs = totalMs, atMs = 0L,
        )

        /** 最近一次发给模型的 user 消息。 */
        fun lastUserMessage(): String =
            sentMessages.last().last { it.role == "user" }.content.orEmpty()
    }

    private fun Harness.run(scope: kotlinx.coroutines.CoroutineScope) {
        session.begin()
        session.start(scope)
    }

    // ── S5：听满大半才换，不算负反馈 ──────────────────────────

    @Test
    fun `S5 听满 90 才切走 进度必须如实传给模型`() = runTest {
        val h = Harness(responses = mutableListOf("""{"action":"none"}"""))
        h.run(this)
        h.session.onTrackSettled(h.settled("晴天", TrackOutcome.SKIPPED_NEXT, playedMs = 216_000L))
        advanceUntilIdle()

        assertEquals(1, h.sentMessages.size)
        val user = h.lastUserMessage()
        assertContains(user, "已播 90%", message = "进度必须传给模型，否则它判不准")
        assertContains(user, "晴天")
        // 模型说 none → 什么都不做
        assertTrue(h.verdicts.isEmpty(), "none 不该产生动作")
        assertEquals(0, h.refillCalls)
        h.session.close()
    }

    // ── S2：连跳会被合并成一批 ────────────────────────────────

    @Test
    fun `S2 连跳三首合并成一次判断且三条事实都在`() = runTest {
        val h = Harness(
            responses = mutableListOf("""{"action":"replace","musicIds":[11,22,33]}"""),
        )
        h.run(this)
        h.session.onTrackSettled(h.settled("A", TrackOutcome.SKIPPED_NEXT, playedMs = 12_000L, musicId = 1))
        h.session.onTrackSettled(h.settled("B", TrackOutcome.SKIPPED_NEXT, playedMs = 9_000L, musicId = 2))
        h.session.onTrackSettled(h.settled("C", TrackOutcome.SKIPPED_NEXT, playedMs = 15_000L, musicId = 3))
        advanceUntilIdle()

        assertEquals(1, h.sentMessages.size, "连跳应合并为一次判断，而不是三次")
        val user = h.lastUserMessage()
        assertContains(user, "《A》")
        assertContains(user, "《B》")
        assertContains(user, "《C》")
        assertContains(user, "已播 5%", message = "每首的进度都要在")

        assertEquals(1, h.verdicts.size)
        assertEquals(RadioAction.REPLACE, h.verdicts.first().action)
        assertEquals(listOf(11L, 22L, 33L), h.verdicts.first().musicIds)
        h.session.close()
    }

    // ── S3：连听不动 ──────────────────────────────────────────

    @Test
    fun `S3 连听五首模型说none则队列一动不动`() = runTest {
        val h = Harness(
            responses = mutableListOf("""{"action":"none"}""", """{"action":"none"}"""),
        )
        h.run(this)
        repeat(2) { i ->
            h.session.onTrackSettled(h.settled("歌$i", TrackOutcome.COMPLETED, playedMs = 240_000L))
        }
        advanceUntilIdle()

        // 模型给的是 none → 没有任何动作落地
        assertTrue(h.verdicts.isEmpty())
        assertEquals(0, h.refillCalls)
        assertTrue(h.sentMessages.isNotEmpty(), "判断确实发起了，只是模型选择不动")
        h.session.close()
    }

    // ── 在播那首：必须标注为不可动 ────────────────────────────

    @Test
    fun `上下文必须标注在播曲目且声明不可替换`() = runTest {
        val h = Harness(responses = mutableListOf("""{"action":"none"}"""))
        h.run(this)
        h.session.onTrackSettled(h.settled("X", TrackOutcome.COMPLETED, playedMs = 240_000L))
        advanceUntilIdle()

        val user = h.lastUserMessage()
        assertContains(user, "在播曲")
        assertContains(user, "不可替换")
        // system 里也要有这条硬约束
        val system = h.sentMessages.last().first { it.role == "system" }.content.orEmpty()
        assertContains(system, "必须放完")
        h.session.close()
    }

    // ── 暂停不触发判断，但要进上下文 ──────────────────────────

    @Test
    fun `暂停不触发判断 但会进入上下文`() = runTest {
        val h = Harness(responses = mutableListOf())
        h.run(this)
        h.session.onPause(PauseEvent(resumed = false, atMs = 0L))
        advanceUntilIdle()
        assertTrue(h.sentMessages.isEmpty(), "暂停不是表态，不该触发判断")

        h.session.onTrackSettled(h.settled("Y", TrackOutcome.SKIPPED_NEXT, playedMs = 30_000L))
        advanceUntilIdle()
        assertContains(h.lastUserMessage(), "暂停/继续", message = "暂停要进上下文，S5 靠它区分「中途离开」")
        h.session.close()
    }

    // ── D2：队列见底的保底补歌 ────────────────────────────────

    @Test
    fun `队列见底 模型没给动作则本地补歌`() = runTest {
        val h = Harness(responses = mutableListOf("""{"action":"none"}"""))
        h.run(this)
        h.session.onQueueLow(1)
        advanceUntilIdle()
        assertEquals(1, h.refillCalls, "模型判 none 但队列确实见底了 → 保底出声")
        h.session.close()
    }

    @Test
    fun `队列见底 模型给了动作就不再本地补`() = runTest {
        val h = Harness(responses = mutableListOf("""{"action":"append","count":6}"""))
        h.run(this)
        h.session.onQueueLow(1)
        advanceUntilIdle()
        assertEquals(1, h.verdicts.size)
        assertEquals(RadioAction.APPEND, h.verdicts.first().action)
        assertEquals(6, h.verdicts.first().appendCount)
        assertEquals(0, h.refillCalls)
        h.session.close()
    }

    // ── 对话：全量台账 + 历史 ─────────────────────────────────

    @Test
    fun `每轮携带开播以来完整台账 并标出本轮新增`() = runTest {
        val h = Harness(
            responses = mutableListOf("""{"action":"none"}""", """{"action":"none"}"""),
        )
        h.run(this)
        h.session.onTrackSettled(h.settled("第一首", TrackOutcome.COMPLETED, playedMs = 240_000L))
        advanceUntilIdle()
        h.session.onTrackSettled(h.settled("第二首", TrackOutcome.COMPLETED, playedMs = 240_000L))
        advanceUntilIdle()

        assertEquals(2, h.sentMessages.size)
        val second = h.sentMessages.last()
        assertTrue(second.size > 3, "第二轮必须带上历史：system + user + assistant + user")
        val user = h.lastUserMessage()
        assertContains(user, "第一首", message = "台账全量携带：开播以来的事实不因轮次推进而消失")
        assertContains(user, "第二首")
        assertEquals(1, user.split("-★").size - 1, "★ 只标本轮新增的那一条")
        assertContains(user, "本档收听台账")
        h.session.close()
    }

    @Test
    fun `模型输出不可解析时沉默且补全assistant保持对话交替`() = runTest {
        val h = Harness(responses = mutableListOf("这个我不好说，你自己看着办吧"))
        h.run(this)
        h.session.onTrackSettled(h.settled("Z", TrackOutcome.SKIPPED_NEXT, playedMs = 10_000L))
        advanceUntilIdle()

        assertTrue(h.verdicts.isEmpty(), "不可解析 = 沉默")
        val roles = h.session.messagesSnapshot().map { it.role }
        // system + user + assistant（补全的 none），保证下一轮仍是合法交替
        assertEquals(listOf("system", "user", "assistant"), roles)
        h.session.close()
    }

    @Test
    fun `模型调用失败时撤回user消息 事实下一轮再送`() = runTest {
        val h = Harness(responses = mutableListOf(null, """{"action":"none"}"""))
        h.run(this)
        h.session.onTrackSettled(h.settled("Q", TrackOutcome.SKIPPED_NEXT, playedMs = 10_000L))
        advanceUntilIdle()

        val roles = h.session.messagesSnapshot().map { it.role }
        assertEquals(listOf("system"), roles, "调用失败 → 撤回 user 消息，不留下悬空的一轮")

        h.session.onTrackSettled(h.settled("W", TrackOutcome.COMPLETED, playedMs = 240_000L))
        advanceUntilIdle()
        val user = h.lastUserMessage()
        assertContains(user, "《Q》", message = "上一轮没送出去的事实，这一轮补上")
        assertContains(user, "《W》")
        h.session.close()
    }

    // ── 输出解析 ──────────────────────────────────────────────

    @Test
    fun `解析容忍 markdown 代码块与多余字段`() {
        val raw = """
            好的，我的判断如下：
            ```json
            {"action":"replace","musicIds":[7,8,9],"count":0,"reason":"连跳太多","extra":"ignored"}
            ```
        """.trimIndent()
        val ids = RadioSession.parseMusicIds(raw)
        assertEquals(listOf(7L, 8L, 9L), ids)
    }

    @Test
    fun `解析失败返回 null`() {
        assertNull(RadioSession.parseMusicIds("我觉得什么都不用改"))
        assertNull(RadioSession.parseMusicIds("""{"musicIds":[]}"""))
    }

    @Test
    fun `verdict 携带每首歌按语 whys 且兼容旧格式`() = runTest {
        // parseVerdict 是实例方法（依赖 jsonBody 等私有设施），借一个最小 session 调用
        fun parse(raw: String) = Harness(responses = mutableListOf())
            .let { h -> h.session.parseVerdict(raw) }

        val raw = """{"action":"replace","musicIds":[7,8],"count":0,"reason":"换段","whys":["压住情绪","接到夜色"]}"""
        val parsed = parse(raw)!!
        assertEquals(RadioAction.REPLACE, parsed.action)
        assertEquals(listOf("压住情绪", "接到夜色"), parsed.whys)
        // 开播解析走同一套 whys 字段
        assertEquals(listOf("压住情绪", "接到夜色"), RadioSession.parseOpeningWhys(raw))

        // 旧格式没有 whys → 空表，不炸
        val legacy = parse("{\"action\":\"replace\",\"musicIds\":[7,8],\"count\":0,\"reason\":\"换段\"}")!!
        assertTrue(legacy.whys.isEmpty())
        // 非法 JSON / 空白按语 → 过滤干净
        assertTrue(RadioSession.parseOpeningWhys("不是 JSON").isEmpty())
        assertTrue(RadioSession.parseOpeningWhys("""{"whys":["  ",""]}""").isEmpty())

        // seedWhy：开播为在播曲写的按语；缺省/空白/非法 JSON → null
        val withSeed = RadioSession.parseOpeningSeedWhy("""{"action":"replace","musicIds":[7],"seedWhy":"回到起点"}""")
        assertEquals("回到起点", withSeed)
        assertNull(RadioSession.parseOpeningSeedWhy("""{"action":"replace","musicIds":[7]}"""))
        assertNull(RadioSession.parseOpeningSeedWhy("""{"seedWhy":"   "}"""))
        assertNull(RadioSession.parseOpeningSeedWhy("不是 JSON"))
    }

    @Test
    fun `reason 只进日志 不会被当成动作`() = runTest {
        val h = Harness(
            responses = mutableListOf("""{"action":"none","reason":"用户听得很开心，不要打扰"}"""),
        )
        h.run(this)
        h.session.onTrackSettled(h.settled("R", TrackOutcome.COMPLETED, playedMs = 240_000L))
        advanceUntilIdle()

        // none 不产生动作；reason 即便被模型写了也不影响（且绝不流向 UI）
        assertTrue(h.verdicts.isEmpty())
        assertEquals(0, h.refillCalls)
        h.session.close()
    }

    @Test
    fun `解析非法 action 视为沉默`() = runTest {
        val h = Harness(responses = mutableListOf("""{"action":"delete_everything"}"""))
        h.run(this)
        h.session.onTrackSettled(h.settled("T", TrackOutcome.SKIPPED_NEXT, playedMs = 5_000L))
        advanceUntilIdle()

        assertTrue(h.verdicts.isEmpty(), "模型给了不在三选一里的动作 → 沉默，不执行")
        h.session.close()
    }

    // ── 节目档案 + 曲库候选池（spec §7.2 重设计） ──────────────

    @Test
    fun `节目档案与候选池每轮重渲染进上下文`() = runTest {
        val h = Harness(
            responses = mutableListOf(
                """{"action":"none","reason":"连切两首但比例不低，先看"}""",
                """{"action":"none"}""",
            ),
            theme = "爵士",
            candidates = { "## 曲库候选（musicIds 只能从这里挑）\n★ 91 | The Nights | 候选" },
        )
        h.run(this)
        // 第 1 轮：模型的 reason 在解析后入档
        h.session.onTrackSettled(h.settled("X", TrackOutcome.SKIPPED_NEXT, playedMs = 10_000L))
        advanceUntilIdle()
        // 第 2 轮：上一轮的编排思路必须出现在本轮档案里（工作记忆）
        h.session.onTrackSettled(h.settled("Y", TrackOutcome.COMPLETED, playedMs = 240_000L))
        advanceUntilIdle()

        val user = h.lastUserMessage()
        assertContains(user, "节目档案", message = "档案块必须每轮重渲染")
        assertContains(user, "主题：爵士")
        assertContains(user, "11|A", message = "队列构成要带 id（replace 需要 id 来源）")
        assertContains(user, "编排思路", message = "上一轮的 reason 要写进档案")
        assertContains(user, "曲库候选", message = "候选池进上下文（消幻觉 id 的供给端方案）")
        h.session.close()
    }

    // ── per-turn 世代守卫（spec §7.2：修「一次暂停毒化整档会话」） ──

    @Test
    fun `perTurnGeneration_快照与丢弃`() = runTest {
        var gen = 0
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val verdicts = mutableListOf<RadioVerdict>()
        val session = RadioSession(
            targetCount = 12,
            clock = { 0L },
            judge = { gate.await(); """{"action":"replace","musicIds":[11]}""" },
            onVerdict = { g, v ->
                if (g == gen) verdicts += v
                g == gen
            },
            generation = { gen },
            snapshot = { RadioContextSnapshot("在播", 9L, 5) },
        )
        session.begin()
        session.start(this)
        session.onTrackSettled(
            TrackSettledEvent(musicId = 1, title = "歌", outcome = TrackOutcome.SKIPPED_NEXT,
                playedMs = 1_000L, totalMs = 240_000L, atMs = 0L),
        )
        advanceUntilIdle()          // judge 挂起在 gate 上

        gen = 1                     // 「暂停/关闭」发生
        gate.complete(Unit)         // 结果此刻才回来
        advanceUntilIdle()

        assertTrue(verdicts.isEmpty(), "该轮在途期间世代变了 → 结果必须丢弃")
        assertEquals(listOf("system"), session.messagesSnapshot().map { it.role }.take(1))
        session.close()
    }

    @Test
    fun `perTurnGeneration_世代没变结果正常执行`() = runTest {
        var gen = 7
        val verdicts = mutableListOf<RadioVerdict>()
        val session = RadioSession(
            targetCount = 12,
            clock = { 0L },
            judge = { """{"action":"append","count":5}""" },
            onVerdict = { g, v ->
                if (g == gen) verdicts += v
                g == gen
            },
            generation = { gen },
            snapshot = { RadioContextSnapshot("在播", 9L, 5) },
        )
        session.begin()
        session.start(this)
        session.onTrackSettled(
            TrackSettledEvent(musicId = 1, title = "歌", outcome = TrackOutcome.SKIPPED_NEXT,
                playedMs = 1_000L, totalMs = 240_000L, atMs = 0L),
        )
        advanceUntilIdle()

        assertEquals(1, verdicts.size, "世代没变 → 正常执行")
        gen = 8                     // 之后才发生的暂停不影响已执行的判定
        session.close()
    }
}
