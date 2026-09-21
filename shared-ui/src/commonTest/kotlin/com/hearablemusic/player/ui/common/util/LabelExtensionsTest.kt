package com.hearablemusic.player.ui.common.util

import com.hmp.domain.enum.LabelName
import kotlin.test.assertTrue
import kotlin.test.Test

class LabelExtensionsTest {

    /**
     * exhaustive when 已在编译期保证全覆盖；本测试防复制粘贴错误——
     * 两个标签映射到同一图标资源时失败。
     */
    @Test
    fun iconRes_allEntries_mappedToDistinctResources() {
        val distinctCount = LabelName.entries.map { it.iconRes }.distinct().size
        assertTrue(
            distinctCount == LabelName.entries.size,
            "每个标签应映射到独立图标资源：$distinctCount/${LabelName.entries.size}"
        )
    }
}
