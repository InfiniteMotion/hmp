package com.hearablemusic.player.ui.library.pages

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D2-12 判据：本端不支持写标签时，**入口整体收起**，不是"点下去再报错"。
 *
 * 修前 iOS 的「编辑标签」照常摆出可填的表单与保存按钮，而 `MusicTagEditor.ios.kt`
 * 在没有 Swift 写桥时必回 `UnsupportedOperationException`（全仓没有一处注册 `TagWriterBridge`）——
 * 于是用户唯一的反馈是一个 `tags_save_failed` 弹窗，看起来像自己填错了。
 *
 * 这条断言管的是规则本身："能力申报是唯一输入"。渲染那半（按钮真的没画出来）
 * 本模块没有 Compose 测试基建，仍然靠人看 —— 与 D5-09 是同一处已知缺口。
 */
class TagEditFormPolicyTest {

    @Test
    fun whenThisPlatformCannotWriteTags_theWholeEditingEntryIsClosed() {
        val policy = tagEditFormPolicy(canWriteTags = false)

        assertFalse(policy.fieldsEditable, "表单必须是只读 —— 不能让用户填完才发现存不了")
        assertFalse(policy.showSaveAction, "两个保存入口（顶栏与页尾）都不出现")
        assertTrue(policy.showUnsupportedHint, "要有一句明确的「本端不支持」，而不是只留一个只读页面")
    }

    @Test
    fun whenThisPlatformCanWriteTags_editingIsFullyAvailable() {
        // 反向这一半不能省：只断言 false 分支的话，实现写成"永远只读"也能绿
        val policy = tagEditFormPolicy(canWriteTags = true)

        assertTrue(policy.fieldsEditable)
        assertTrue(policy.showSaveAction)
        assertFalse(policy.showUnsupportedHint)
    }

    @Test
    fun capabilityIsTheOnlyInput() {
        // 钉住"三个开关由同一份申报决定"，避免以后有人给某一项另加条件
        listOf(true, false).forEach { canWrite ->
            val policy = tagEditFormPolicy(canWrite)
            assertEquals(canWrite, policy.fieldsEditable)
            assertEquals(canWrite, policy.showSaveAction)
            assertEquals(!canWrite, policy.showUnsupportedHint)
        }
    }
}
