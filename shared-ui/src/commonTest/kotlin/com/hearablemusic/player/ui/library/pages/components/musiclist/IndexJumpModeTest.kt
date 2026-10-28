package com.hearablemusic.player.ui.library.pages.components.musiclist

import com.hmp.domain.music.MusicSortKey
import com.hmp.domain.music.SortDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D2-08 的 UI 半边：每个排序键该配哪种索引形态，以及降序时字母条的方向。
 *
 * 仓库侧那份判据只管"列表按什么顺序"，这里管"索引条跟不跟得上那个顺序"：
 * 时长 / 大小 / 播放次数用 A–Z 字母条是没有意义的（那些列不是文本），它们要走分档锚点；
 * 反过来文本键走锚点也一样是错的。
 *
 * 断言写成**对全部枚举值分划**，不是逐个键点名 —— 新增一个 `MusicSortKey` 时，
 * 它必然落进两边的名单之外，这条就红，逼调用方当场为它选一种索引形态。
 */
class IndexJumpModeTest {

    private val textKeys = setOf(MusicSortKey.TITLE, MusicSortKey.ARTIST, MusicSortKey.ALBUM)

    @Test
    fun everySortKeyIsClassified_andOnlyTextKeysUseLetters() {
        val anchorKeys = MusicSortKey.entries.filter { indexJumpConfigForOrderBy(it).isAnchorMode }
        val letterKeys = MusicSortKey.entries.filter { !indexJumpConfigForOrderBy(it).isAnchorMode }

        assertEquals(
            MusicSortKey.entries.toSet(),
            (anchorKeys + letterKeys).toSet(),
            "每个排序键都要有索引形态：锚点或字母，二者必居其一",
        )
        assertEquals(textKeys, letterKeys.toSet(), "只有文本三键用字母索引")
    }

    @Test
    fun lettersFollowDirection() {
        val ascending = indexJumpConfigForOrderBy(MusicSortKey.TITLE, SortDirection.ASC).letters
        val descending = indexJumpConfigForOrderBy(MusicSortKey.TITLE, SortDirection.DESC).letters

        assertEquals('A', ascending.first())
        assertEquals('Z', descending.first())
        assertEquals('#', ascending.last())
        assertEquals('#', descending.last())
        // 降序条就是升序条倒过来再挂上「未知」档
        assertEquals(ascending.dropLast(1).reversed() + '#', descending)
    }

    @Test
    fun nonTextKeysCarryTheirOwnAnchors_notEmptyConfigs() {
        // 数值键走锚点：锚点函数必须在位，否则索引条会渲染成一条空的、点不动的栏
        textKeys.forEach {
            assertFalse(indexJumpConfigForOrderBy(it).isAnchorMode, "$it 是文本键")
        }
        (MusicSortKey.entries - textKeys).forEach {
            val config = indexJumpConfigForOrderBy(it)
            assertTrue(config.isAnchorMode, "$it 应走分档锚点")
            assertTrue(config.enabled, "$it 的索引条不该被关掉")
        }
    }
}
