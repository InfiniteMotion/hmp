package com.hmp.domain.agent.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 画像叙事规则单测 —— 契约 v3.5 §7.4（两面共用 → 生成闸门是安全性的全部来源）。
 *
 * 铁律：叙事只能是侧写渲染的复述。这里验证语言层闸门（禁称号 / 禁类型学 / 长度）
 * 与指纹的确定性；语义层的"不加新事实"靠生成输入只含事实渲染（buildMessages）压制。
 */
class ProfileNarrativeTest {

    // ── 生成输入 ─────────────────────────────────────────────────────

    @Test
    fun prompt_demandsRestatement_andBansPersonInference() {
        val messages = ProfileNarrative.buildMessages("- 主要在夜里听\n- 很少中途跳歌")
        val system = messages.first().content!!
        assertTrue(system.contains("只能复述"), "铁律必须写进 prompt")
        assertTrue(system.contains("禁止推断性格、情绪、处境、人格类型"))
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
