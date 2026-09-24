package com.hmp.domain.agent.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 消费面人格卡单测 —— 契约 §6（换轴不是换皮 / 单向隔离 / PF10 命名表 / PF16 退化）。
 *
 * 关键校验点：
 * - 8 型命名表三轴全对上才命名（缺轴不硬凑）
 * - B 类不进卡正面（§5.3 / §9.2）
 * - 消费面文本不出内部标识（§7.2 同源纪律）
 * - 命名/文案不得出现类型学专名（§6.3）
 */
class PersonalityCardComposerTest {

    private val night = PortraitDraft(
        type = PortraitType.TIME,
        tier = PortraitTier.L3,
        slots = mapOf(PortraitType.PRIMARY_PART to BehaviorModeler.PART_NIGHT),
        evidenceRefs = listOf(1),
        confidence = 0.6,
        sources = setOf(ProfileSources.T0_BEHAVIOR),
    )

    private fun draft(
        type: PortraitType,
        vararg slots: Pair<String, String>,
        source: String = ProfileSources.T0_BEHAVIOR,
        confidence: Double = 0.6,
    ) = PortraitDraft(
        type = type,
        tier = if (type.maturity == PortraitMaturity.LIBRARY) PortraitTier.L3 else PortraitTier.L2,
        slots = slots.toMap(),
        evidenceRefs = listOf(1),
        confidence = confidence,
        sources = setOf(source),
    )

    /** 三轴齐全的典型输入：夜行 × 专精 × 沉浸 → 「夜航者」 */
    private fun fullNightFocusedImmersive(): List<PortraitDraft> = listOf(
        night,
        draft(
            PortraitType.LIBRARY,
            PortraitType.GENRE_BREADTH to LibraryModeler.BREADTH_NARROW,
            source = ProfileSources.LIBRARY_CONTENT,
        ),
        draft(PortraitType.HABITS, PortraitType.COMPLETION_RATE to BehaviorModeler.RATE_HIGH),
    )

    // ── 命名表：3 轴 → 8 型 ──────────────────────────────────────────

    @Test
    fun nightFocusedImmersive_isNamedYeHangZhe() {
        val card = PersonalityCardComposer.compose(fullNightFocusedImmersive())
        assertEquals("夜航者", card?.typeName)
    }

    @Test
    fun dayBroadFiltering_isNamedChongLangZhe() {
        val card = PersonalityCardComposer.compose(
            listOf(
                draft(PortraitType.TIME, PortraitType.PRIMARY_PART to BehaviorModeler.PART_NOON),
                draft(
                    PortraitType.LIBRARY,
                    PortraitType.GENRE_BREADTH to LibraryModeler.BREADTH_BROAD,
                    source = ProfileSources.LIBRARY_CONTENT,
                ),
                draft(PortraitType.HABITS, PortraitType.COMPLETION_RATE to BehaviorModeler.RATE_LOW),
            ),
        )
        assertEquals("冲浪者", card?.typeName)
    }

    @Test
    fun namingTable_coversAllEightCombinations() {
        val combos = setOf(
            Triple("夜行", "专精", "沉浸") to "夜航者",
            Triple("夜行", "专精", "筛选") to "夜巡者",
            Triple("夜行", "广谱", "沉浸") to "月下漫游者",
            Triple("夜行", "广谱", "筛选") to "午夜调频师",
            Triple("日行", "专精", "沉浸") to "老唱片",
            Triple("日行", "专精", "筛选") to "点唱机",
            Triple("日行", "广谱", "沉浸") to "拾光者",
            Triple("日行", "广谱", "筛选") to "冲浪者",
        )
        // 通过公开的 compose 逐组合验证命名表覆盖（不需要暴露内部表）
        combos.forEach { (axes, expected) ->
            val (time, breadth, listen) = axes
            val card = PersonalityCardComposer.compose(
                listOf(
                    draft(
                        PortraitType.TIME,
                        PortraitType.PRIMARY_PART to if (time == "夜行") BehaviorModeler.PART_NIGHT else BehaviorModeler.PART_NOON,
                    ),
                    draft(
                        PortraitType.LIBRARY,
                        PortraitType.GENRE_BREADTH to if (breadth == "专精") LibraryModeler.BREADTH_NARROW else LibraryModeler.BREADTH_BROAD,
                        source = ProfileSources.LIBRARY_CONTENT,
                    ),
                    draft(
                        PortraitType.HABITS,
                        PortraitType.COMPLETION_RATE to if (listen == "沉浸") BehaviorModeler.RATE_HIGH else BehaviorModeler.RATE_LOW,
                    ),
                ),
            )
            assertEquals(expected, card?.typeName, "组合 $axes 应命名为 $expected")
        }
    }

    // ── 缺轴不硬凑 / 不假装知道 ─────────────────────────────────────

    @Test
    fun missingAxis_noTypeName_butSentencesSurvive() {
        val card = PersonalityCardComposer.compose(
            listOf(
                night,
                draft(PortraitType.HABITS, PortraitType.COMPLETION_RATE to BehaviorModeler.RATE_HIGH),
                // 缺口味宽度轴
            ),
        )
        assertNull(card?.typeName)
        assertTrue(card!!.sentences.isNotEmpty())
        assertEquals(2, card.axes.size)
    }

    @Test
    fun emptyPortraits_returnsNull() {
        assertNull(PersonalityCardComposer.compose(emptyList()))
    }

    // ── PF16：标签未就绪的退化近似 ─────────────────────────────────

    @Test
    fun breadthFallsBackToArtistConcentration_markedPreliminary() {
        val card = PersonalityCardComposer.compose(
            listOf(
                night,
                draft(
                    PortraitType.LIBRARY,
                    PortraitType.ARTIST_CONCENTRATION to LibraryModeler.CONCENTRATION_FOCUSED,
                ),
                draft(PortraitType.HABITS, PortraitType.COMPLETION_RATE to BehaviorModeler.RATE_HIGH),
            ),
        )
        assertEquals("夜航者", card?.typeName)
        val breadth = card!!.axes.first { it.title == "口味宽度" }
        assertTrue(breadth.preliminary, "歌手集中度近似必须标『初步』（PF16）")
        assertTrue(card.whys.any { it.contains("初步") })
    }

    @Test
    fun solidBreadthBeatsPreliminaryApproximation() {
        val card = PersonalityCardComposer.compose(
            listOf(
                night,
                draft(
                    PortraitType.LIBRARY,
                    PortraitType.GENRE_BREADTH to LibraryModeler.BREADTH_BROAD,
                    PortraitType.ARTIST_CONCENTRATION to LibraryModeler.CONCENTRATION_FOCUSED,
                    source = ProfileSources.LIBRARY_CONTENT,
                ),
                draft(PortraitType.HABITS, PortraitType.COMPLETION_RATE to BehaviorModeler.RATE_HIGH),
            ),
        )
        assertEquals("月下漫游者", card?.typeName, "标签结论优先于集中度近似")
        assertTrue(card!!.axes.none { it.preliminary })
    }

    // ── B 类不进卡正面（§5.3）──────────────────────────────────────

    @Test
    fun weakPortraits_doNotEnterCardFace() {
        val card = PersonalityCardComposer.compose(
            listOf(
                draft(
                    PortraitType.ATTENTION,
                    PortraitType.SESSION_LENGTH to "LONG",
                    PortraitType.FRAGMENTATION to "LOW",
                ),
            ),
        )
        assertNull(card, "B 类侧写不得单独撑起人格卡正面")
    }

    // ── 渲染纪律 ─────────────────────────────────────────────────────

    @Test
    fun cardText_leaksNoInternalIdentifiers() {
        val card = PersonalityCardComposer.compose(
            fullNightFocusedImmersive() + draft(
                PortraitType.TASTE,
                PortraitType.LEADING_GENRE to "民谣",
                PortraitType.TOP_ARTISTS to "某歌手A、某歌手B",
            ),
        )
        val all = (card!!.sentences + card.whys + listOfNotNull(card.typeName) + card.axes.map { it.pole }).joinToString("；")
        listOf("_portrait", "genreBreadth", "primaryPart", "completionRate", "NIGHT", "BREADTH").forEach { leak ->
            assertTrue(!all.contains(leak), "消费面文本不得出现内部标识：$leak")
        }
        assertTrue(card.sentences.any { it.contains("民谣") })
    }

    @Test
    fun naming_neverContainsTypologyProperNouns() {
        // §6.3：禁止 MBTI / 九型 / 大五 / 16Personalities 及混淆性近似 —— 对命名表整体把关
        val forbidden = listOf("MBTI", "Myers", "九型", "大五", "16型", "INFP", "INTJ", "E人", "I人")
        val allNames = listOf("夜航者", "夜巡者", "月下漫游者", "午夜调频师", "老唱片", "点唱机", "拾光者", "冲浪者")
        allNames.forEach { name ->
            forbidden.forEach { bad -> assertTrue(!name.contains(bad)) }
        }
    }

    @Test
    fun sentences_cappedAtFour() {
        val card = PersonalityCardComposer.compose(
            fullNightFocusedImmersive() + draft(
                PortraitType.TASTE,
                PortraitType.LEADING_GENRE to "民谣",
                PortraitType.TOP_ARTISTS to "某歌手",
            ),
        )
        assertTrue(card!!.sentences.size <= 4, "§6.3：3~4 句人话，超出要删")
    }
}
