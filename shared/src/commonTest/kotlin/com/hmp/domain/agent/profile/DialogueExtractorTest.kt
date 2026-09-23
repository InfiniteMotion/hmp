package com.hmp.domain.agent.profile

import com.hmp.domain.agent.port.LlmMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 对话自动抽取单测 —— 契约 §4.4（T2_DIALOGUE / 闭集落点 / "推断音乐选择，不推断人"）。
 *
 * [DialogueExtractor] 是纯逻辑（闸门 / prompt / 解析），LLM 调用与写库在
 * MasterAgent 与 [UserMemory.ingestDialogueEvidence] 侧。
 */
class DialogueExtractorTest {

    // ── 线索闸门 ─────────────────────────────────────────────────────

    @Test
    fun cueGate_passesPreferenceStatements_andBlocksOrdinaryChat() {
        assertTrue(DialogueExtractor.shouldExtract("我特别喜欢爵士，有没有推荐的"))
        assertTrue(DialogueExtractor.shouldExtract("别再给我推快歌了，不喜欢"))
        assertTrue(DialogueExtractor.shouldExtract("最近在听一些老歌"))
        assertFalse(DialogueExtractor.shouldExtract("这首歌播放有杂音，帮我看看"))
        assertFalse(DialogueExtractor.shouldExtract("下一首"))
        assertFalse(DialogueExtractor.shouldExtract(""))
    }

    // ── prompt 纪律 ─────────────────────────────────────────────────

    @Test
    fun prompt_menuIsClosedSet_andStatesTheBottomLine() {
        val messages = DialogueExtractor.buildMessages("我喜欢爵士")
        val system = messages.first() as LlmMessage
        assertTrue(system.role == "system")
        DialogueExtractor.allowedPredicates.forEach { slot ->
            assertTrue(system.content!!.contains(slot), "菜单必须列出闭集谓词：$slot")
        }
        assertTrue(system.content!!.contains("没有资格推断") || system.content!!.contains("一律不抽"))
    }

    // ── 解析与闸门 ──────────────────────────────────────────────────

    @Test
    fun parse_plainJsonArray() {
        val out = DialogueExtractor.parse(
            """[{"slot":"taste_portrait.leadingGenre","value":"爵士"},{"slot":"taste_portrait.topArtists","value":"周杰伦"}]""",
        )
        assertEquals(2, out.size)
        assertEquals("taste_portrait.leadingGenre", out[0].predicate)
        assertEquals("爵士", out[0].value)
    }

    @Test
    fun parse_toleratesCodeFence_andSurroundingNoise() {
        val out = DialogueExtractor.parse(
            "好的，抽取结果如下：\n```json\n[{\"slot\":\"taste_portrait.dislikedStyles\",\"value\":\"快歌\"}]\n```\n以上。",
        )
        assertEquals(1, out.size)
        assertEquals("快歌", out[0].value)
    }

    @Test
    fun parse_dropsSlotsOutsideClosedSet() {
        // 模型试图发明谓词 / 抽"人的处境" —— 结构上必须挡住
        val out = DialogueExtractor.parse(
            """[
                {"slot":"user_mood.current","value":"压力大"},
                {"slot":"taste_portrait.leadingGenre","value":"民谣"},
                {"slot":"personality_type.mbti","value":"INFP"}
            ]""",
        )
        assertEquals(1, out.size)
        assertEquals("taste_portrait.leadingGenre", out[0].predicate)
    }

    @Test
    fun parse_dropsOverlongOrBlankValues() {
        val out = DialogueExtractor.parse(
            """[
                {"slot":"taste_portrait.leadingGenre","value":"  "},
                {"slot":"taste_portrait.leadingGenre","value":"这是一个远超十二个字限制的超级长的所谓偏好描述"},
                {"slot":"taste_portrait.leadingGenre","value":"爵士"}
            ]""",
        )
        assertEquals(1, out.size)
        assertEquals("爵士", out[0].value)
    }

    @Test
    fun parse_nonJsonOrEmpty_returnsEmpty() {
        assertTrue(DialogueExtractor.parse("").isEmpty())
        assertTrue(DialogueExtractor.parse("没有可抽取的偏好。").isEmpty())
        assertTrue(DialogueExtractor.parse("[]").isEmpty())
        assertTrue(DialogueExtractor.parse("[{broken").isEmpty())
    }

    @Test
    fun parse_capsAtThreeItems_andDedupes() {
        val item = """{"slot":"taste_portrait.leadingGenre","value":"爵士"}"""
        val out = DialogueExtractor.parse("[$item,$item,$item,$item,$item]")
        assertEquals(1, out.size, "同谓词同值去重")
    }
}
