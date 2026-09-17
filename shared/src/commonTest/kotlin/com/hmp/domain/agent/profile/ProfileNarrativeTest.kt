package com.hmp.domain.agent.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 画像叙事规则单测 —— Prompt v2（放松版：允许合理推断，但禁止编造数据 / 禁止性格情绪推断 / 禁止起称号）。
 *
 * 闸门分层：语言层（禁编造 / 禁类型学 / 长度 / 禁称号）+ 指纹确定性。
 * "不加新事实"靠 validate 闸门 + 输入只含槽位事实句双重压制。
 */
class ProfileNarrativeTest {

    // ── 生成输入 ─────────────────────────────────────────────────────

    @Test
    fun prompt_allowsReasonableInference_butBansFabrication_andPersonInference() {
        val messages = ProfileNarrative.buildMessages("- 主要在夜里听\n- 很少中途跳歌")
        val system = messages.first().content!!
        // Prompt v2：允许合理推断（"你偏好深夜听歌"这类基于数据的归纳），但禁止编造数据
        assertTrue(system.contains("合理推断"), "Prompt 应明确允许基于数据的推断")
        assertTrue(system.contains("不许编造数据"), "禁止编造数据是铁律")
        assertTrue(system.contains("不许推断性格、情绪、处境"), "性格情绪推断仍然禁止")
        assertTrue(messages.last().content!!.contains("很少中途跳歌"))
    }

    // ── 写入闸门 ─────────────────────────────────────────────────────

    @Test
    fun validate_acceptsPlainRestatement() {
        val text = "你常在天黑之后打开播放器，听的东西不杂，歌一开就听到底，很少中途切走。"
        assertEquals(text, ProfileNarrative.validate(text))
    }

    @Test
    fun validate_rejectsNamingTableWords() {
        // 称号独占消费面 —— 叙事里出现任何一个 8 型名字都整篇作废
        PersonalityCardComposer.allNames.forEach { name ->
            assertNull(
                ProfileNarrative.validate("你是名副其实的$name，常在夜里听歌，很少中途跳歌。"),
                "叙事不得出现人格命名：$name",
            )
        }
    }

    @Test
    fun validate_rejectsTypologyProperNouns() {
        assertNull(ProfileNarrative.validate("你是典型的 INFP，常在夜里听歌，很少中途跳歌。"))
        assertNull(ProfileNarrative.validate("按九型人格看，你常在夜里听歌，很少中途跳歌。"))
        assertNull(ProfileNarrative.validate("你是标准的 I人，常在夜里听歌，很少中途跳歌。"))
    }

    @Test
    fun validate_rejectsWrongLength() {
        assertNull(ProfileNarrative.validate("太短。"))
        assertNull(ProfileNarrative.validate("很".repeat(ProfileNarrative.MAX_CHARS + 1)))
    }

    @Test
    fun validate_collapsesMultiLineIntoOneParagraph() {
        val text = ProfileNarrative.validate("你常在夜里听歌。\n听的东西不杂。\n歌一开就听到底。")
        assertTrue(text != null && !text.contains('\n'))
    }

    // ── 指纹 ─────────────────────────────────────────────────────────

    @Test
    fun fingerprint_stableForSameSlots_changesOnAnyDelta() {
        fun time(value: String) = PortraitDraft(
            type = PortraitType.TIME,
            tier = PortraitTier.L2,
            slots = mapOf(PortraitType.PRIMARY_PART to value),
            evidenceRefs = listOf(1),
            confidence = 0.6,
        )
        val base = listOf(time("NIGHT"))
        assertEquals(ProfileNarrative.factsFingerprint(base), ProfileNarrative.factsFingerprint(listOf(time("NIGHT"))))
        assertNotEquals(ProfileNarrative.factsFingerprint(base), ProfileNarrative.factsFingerprint(listOf(time("NOON"))))
        assertNotEquals(
            ProfileNarrative.factsFingerprint(base),
            ProfileNarrative.factsFingerprint(listOf(time("NIGHT"), time("NIGHT"))),
            "侧写行数变化也要引起指纹变化",
        )
    }
}
