package com.hmp.domain.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D2-09：格式白名单收成 commonMain 一份之后，把"哪些算受支持"这条口径本身钉住。
 *
 * 三端原先各写一份（Desktop 9 项、iOS 7 项、Android 没有），分歧不是靠注释能守住的；
 * 这份断言的作用是让"顺手往名单里加一个 ape"变成一次红，而不是一次静默的三端不一致。
 */
class AudioFormatsTest {

    @Test
    fun supportedExtensions_areTheSingleList() {
        assertEquals(
            setOf("mp3", "flac", "m4a", "wav", "aac", "ogg", "opus", "wma", "alac"),
            AudioFormats.supportedExtensions,
        )
    }

    @Test
    fun isSupported_matchesCaseInsensitively() {
        assertTrue(AudioFormats.isSupported("/music/SONG.MP3"))
        assertTrue(AudioFormats.isSupported("track.FlAc"))
        // iOS 原先缺的两项
        assertTrue(AudioFormats.isSupported("track.opus"))
        assertTrue(AudioFormats.isSupported("track.wma"))
    }

    @Test
    fun isSupported_rejectsUnlistedAndExtensionless() {
        // ape / dsf / wv 未放行：Desktop 走 FFmpeg，放宽要以"随包那份真能解"为前件
        assertFalse(AudioFormats.isSupported("track.ape"))
        assertFalse(AudioFormats.isSupported("track.dsf"))
        assertFalse(AudioFormats.isSupported("track.wv"))
        assertFalse(AudioFormats.isSupported("notes.txt"))
        assertFalse(AudioFormats.isSupported("no_extension"))
        // substringAfterLast 在没有分隔符时**返回整串**，少了 missingDelimiterValue 就会把
        // "no_extension" 当成扩展名 —— 名字里带点、本身却没扩展名的文件就是这么漏进来的
        assertFalse(AudioFormats.isSupported("mp3"))
    }

    @Test
    fun durationFloor_blocksFragments_butNotUnknownDurations() {
        // 下限是 >=：正好 5 秒要收
        assertEquals(5_000L, AudioFormats.MIN_TRACK_DURATION_MS)
        assertTrue(AudioFormats.isPlausibleTrack(5_000L))
        assertFalse(AudioFormats.isPlausibleTrack(4_999L))
        assertFalse(AudioFormats.isPlausibleTrack(0L))
        // null 是"读不到时长"，不是"时长为零"。因为读不到而丢条目 = 在扫描阶段静默少歌，
        // 那是比碎片文件更糟的失败模式（用户无从知道少了哪首）
        assertTrue(AudioFormats.isPlausibleTrack(null))
    }

    @Test
    fun isIncludedInLibrary_isTheConjunctionBothEndsAsk() {
        // 白名单与下限是**合取**，缺一不收；两端各自拼这两条就会拼出第三种口径
        assertTrue(AudioFormats.isIncludedInLibrary("/Music/a.mp3", 60_000L))
        assertFalse(AudioFormats.isIncludedInLibrary("/Music/a.mp3", 3_000L), "格式对但太短")
        assertFalse(AudioFormats.isIncludedInLibrary("/Music/a.ape", 60_000L), "时长对但格式未放行")
        assertFalse(AudioFormats.isIncludedInLibrary("/Music/a.ape", 3_000L), "两条都不满足")
        assertTrue(AudioFormats.isIncludedInLibrary("/Music/a.flac", null), "格式对、时长未知 → 收")
    }
}
