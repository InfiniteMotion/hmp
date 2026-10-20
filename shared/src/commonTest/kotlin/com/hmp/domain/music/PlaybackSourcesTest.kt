package com.hmp.domain.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 一-3 / **D5-06**：`PlaybackHistory.source` 取值闭集的判据。
 *
 * 这张表此前不存在，三端各写字面量：Android 一套大小写混排的串、Desktop 只写 `"direct"`、
 * iOS 恒传 `null`（而来源分布的 SQL 是 `WHERE source IS NOT NULL`，于是 iOS 那块饼图永不渲染）。
 * 把闭集落成常量是收口的第一步；第二步是三端都引用它（iOS 在 Swift 侧，只能复制拼写 ——
 * 那条要靠 `ios/HMP/HMP/Features/Player/MusicPlayerController.swift` 的 `PlaybackSource` 对齐，
 * 本机编不了 Swift，实机签收）。
 *
 * 变异探针：把 [PlaybackSources.all] 少写一项、或改掉某个拼写，本类即红。
 */
class PlaybackSourcesTest {

    @Test
    fun closedSetHasNoDuplicatesOrBlanks() {
        assertEquals(PlaybackSources.all.size, PlaybackSources.all.toSet().size, "来源取值重复")
        assertTrue(PlaybackSources.all.none { it.isBlank() }, "来源取值不能是空白串")
    }

    /**
     * 已发布库里的历史行用的就是这些拼写（Android 是唯一已有真数据的端）。
     * 改名不会报错，只会在饼图里把同一来源裂成两条 —— 所以这条按字面量钉死。
     */
    @Test
    fun publishedSpellingsArePinned() {
        assertEquals("Manual", PlaybackSources.MANUAL)
        assertEquals("Next", PlaybackSources.NEXT)
        assertEquals("Previous", PlaybackSources.PREVIOUS)
        assertEquals("Resume", PlaybackSources.RESUME)
        assertEquals("Order", PlaybackSources.ORDER)
        assertEquals("Shuffle", PlaybackSources.SHUFFLE)
        assertEquals("HeartMode", PlaybackSources.HEART_MODE)
        assertEquals("Release", PlaybackSources.RELEASE)
        assertEquals("Auto", PlaybackSources.AUTO)
    }

    @Test
    fun everyConstantIsInTheClosedSet() {
        val constants = listOf(
            PlaybackSources.MANUAL, PlaybackSources.NEXT, PlaybackSources.PREVIOUS,
            PlaybackSources.RESUME, PlaybackSources.ORDER, PlaybackSources.SHUFFLE,
            PlaybackSources.HEART_MODE, PlaybackSources.RELEASE, PlaybackSources.AUTO,
        )
        assertEquals(PlaybackSources.all.toSet(), constants.toSet(), "常量和 all 名单必须同源")
        assertTrue(constants.all(PlaybackSources::isKnown))
    }

    /** null 是"来源分布恒空"的那个形状：它必须被判为未知，而不是被默认归到某个来源。 */
    @Test
    fun unknownAndNullAreNotKnown() {
        assertFalse(PlaybackSources.isKnown(null))
        assertFalse(PlaybackSources.isKnown("direct"), "Desktop 的旧拼写不该算合法取值")
        assertFalse(PlaybackSources.isKnown("manual"), "大小写混排的第二套拼写不该被接受")
    }
}
