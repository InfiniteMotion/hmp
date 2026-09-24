package com.hmp.domain.agent.card

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * CardPool（W0 Hello 卡片池）状态机测试。
 *
 * 契约：
 * - 同类型只保留一张，replace 在**原位置**替换（轮播稳定），新类型 push 到栈顶
 * - setFocus 决定 focusedAt（>0 → UI 聚焦；=0 → 不抢焦点，ANCHOR 每秒刷新用）
 * - setVisible 控制"今天是否展示"（containsType 只认 visible 卡）
 * - popByType 彻底移除；clear 清空
 */
class CardPoolTest {

    private fun anchorCard(id: String, title: String = "默认歌") = SlideCard(
        cardId = id,
        type = SlideType.ANCHOR,
        content = AnchorContent(
            trackTitle = title, artistName = "甲", bpm = null, phase = null,
            albumArtUri = null, isPlaying = true, durationSec = 180,
            progressPercent = 10, sourceLabel = null,
        ),
    )

    private fun greetingCard(id: String, text: String = "hi") = SlideCard(
        cardId = id,
        type = SlideType.GREETING,
        content = GreetingContent(text = text, fromFallback = true, phase = TimePhase.WORK),
    )

    private fun recommendCard(id: String) = SlideCard(
        cardId = id,
        type = SlideType.RECOMMEND,
        content = RecommendContent(
            trackId = 1, trackTitle = "夜航", reason = "因为你最近常听",
            currentPhase = TimePhase.WORK, sourceLabel = null, durationSec = 180,
        ),
    )

    @Test
    fun replace_newType_pushesToFront() {
        val pool = CardPool()
        val card = anchorCard("a1")

        pool.replace(SlideType.ANCHOR, card)

        // 字段断言而非全对象相等：setFocus 默认 true → replace 内部写入当前时间戳 focusedAt，
        // 不可预知，全对象相等必然失败
        val stored = pool.cards.value.single()
        assertEquals("a1", stored.cardId)
        assertEquals(card.content, stored.content)
    }

    @Test
    fun replace_existingType_keepsPosition() {
        val pool = CardPool()
        pool.replace(SlideType.ANCHOR, anchorCard("a1"))
        pool.replace(SlideType.GREETING, greetingCard("g1"))
        pool.replace(SlideType.RECOMMEND, recommendCard("r1"))
        assertEquals(listOf("r1", "g1", "a1"), pool.cards.value.map { it.cardId }, "push 到栈顶的既有顺序")

        // RECOMMEND 时段变化 → 内容变了但位置不变（轮播稳定）
        pool.replace(SlideType.RECOMMEND, recommendCard("r2"))

        val cards = pool.cards.value
        assertEquals(listOf("r2", "g1", "a1"), cards.map { it.cardId }, "RECOMMEND 原位置替换")
        assertEquals(1, cards.count { it.type == SlideType.RECOMMEND }, "同类型只保留一张")
        assertEquals(3, cards.size, "其他类型不受影响")
    }

    @Test
    fun replace_setFocus_controlsFocusedAt() {
        val pool = CardPool()

        pool.replace(SlideType.ANCHOR, anchorCard("a1"), setFocus = false)
        assertEquals(0L, pool.cards.value.single().focusedAt, "setFocus=false 不抢焦点（ANCHOR 每秒刷新路径）")

        pool.replace(SlideType.ANCHOR, anchorCard("a2"), setFocus = true)
        assertTrue(pool.cards.value.single().focusedAt > 0L, "setFocus=true → UI 聚焦展示")
    }

    @Test
    fun push_prependsToTop() {
        val pool = CardPool()
        pool.push(anchorCard("a1"))
        pool.push(greetingCard("g1"))

        assertEquals(listOf("g1", "a1"), pool.cards.value.map { it.cardId }, "push 恒在列表头")
    }

    @Test
    fun setVisible_togglesCard_andAffectsContainsType() {
        val pool = CardPool()
        pool.replace(SlideType.GREETING, greetingCard("g1"))
        assertTrue(pool.containsType(SlideType.GREETING), "visible 卡被 containsType 认可")

        pool.setVisible(SlideType.GREETING, false)
        assertFalse(pool.cards.value.single().visible, "visible 被置 false")
        assertFalse(pool.containsType(SlideType.GREETING), "今日无数据 → containsType 不再认可（缺失重试守卫依据）")

        pool.setVisible(SlideType.GREETING, true)
        assertTrue(pool.containsType(SlideType.GREETING))
    }

    @Test
    fun popByType_removesOnlyThatType() {
        val pool = CardPool()
        pool.replace(SlideType.ANCHOR, anchorCard("a1"))
        pool.replace(SlideType.RADIO_STATUS, SlideCard(
            cardId = "rs1",
            type = SlideType.RADIO_STATUS,
            content = RadioStatusContent(
                stationTheme = "摇滚", actionText = "播放中",
                nowPlayingTitle = "夜航", nowPlayingArtist = "甲", albumArtUri = null,
                nowPlayingWhy = null, nextTrackTitle = null, nextTrackWhy = null,
                playlistCount = 3, progressPercent = null, targetCount = null,
            ),
        ))

        pool.popByType(SlideType.RADIO_STATUS)

        assertEquals(listOf("a1"), pool.cards.value.map { it.cardId }, "只移除目标类型")
    }

    @Test
    fun popByType_missingType_isNoOp() {
        val pool = CardPool()
        pool.replace(SlideType.ANCHOR, anchorCard("a1"))

        pool.popByType(SlideType.FORGOTTEN)

        assertEquals(1, pool.cards.value.size, "移除不存在的类型不崩、不影响既有卡")
    }

    @Test
    fun clear_emptiesPool() {
        val pool = CardPool()
        pool.replace(SlideType.ANCHOR, anchorCard("a1"))
        pool.replace(SlideType.GREETING, greetingCard("g1"))

        pool.clear()

        assertTrue(pool.cards.value.isEmpty(), "shutdown 清空所有卡")
        assertFalse(pool.containsType(SlideType.ANCHOR))
    }
}
