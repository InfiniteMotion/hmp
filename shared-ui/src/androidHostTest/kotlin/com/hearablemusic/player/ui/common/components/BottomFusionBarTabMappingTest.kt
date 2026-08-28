package com.hearablemusic.player.ui.common.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 三胶囊底栏页↔tab 索引重映射测试（任务书 M1-T1 验收项）。
 *
 * 设计总纲 2.2：bottomTabs 去 Home 后，pager 页 0（门面）= 伙伴的家，无对应 Tab；
 * 页 N ↔ Tab N-1（Gallery/List/User 三个 Tab 索引 0..2）。
 */
class BottomFusionBarTabMappingTest {

    @Test
    fun `face page has no tab`() {
        assertEquals(-1, tabIndexForPage(0))
    }

    @Test
    fun `page one maps to gallery tab`() {
        assertEquals(0, tabIndexForPage(1))
    }

    @Test
    fun `page two maps to list tab`() {
        assertEquals(1, tabIndexForPage(2))
    }

    @Test
    fun `page three maps to user tab`() {
        assertEquals(2, tabIndexForPage(3))
    }
}