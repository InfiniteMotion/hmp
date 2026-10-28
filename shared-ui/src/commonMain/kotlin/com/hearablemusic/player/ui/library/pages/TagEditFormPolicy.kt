package com.hearablemusic.player.ui.library.pages

/**
 * 「编辑标签」页的降级规则（D2-12）。
 *
 * 单独成函数有两个理由：
 * - 这条规则原先**不存在** —— 三端无条件摆出表单与保存按钮，而 iOS 的保存必失败
   （那侧没有注册任何写桥），用户填完只拿到一个 `tags_save_failed` 弹窗，
     看起来像自己填错了；"不支持"必须是可见事实，不是一个错误码。
 * - 放在 Compose 里就只有一双眼睛能看；放成纯函数，`commonTest` 就能钉住
     "能力申报是唯一输入"。渲染那半（按钮真的没画出来）本模块没有 Compose 测试基建，仍然靠人看。
 *
 * @param canWriteTags 本端能力申报，来自 `MusicTagEditService.canWriteTags`。
 */
data class TagEditFormPolicy(
    val fieldsEditable: Boolean,
    val showSaveAction: Boolean,
    val showUnsupportedHint: Boolean,
)

fun tagEditFormPolicy(canWriteTags: Boolean): TagEditFormPolicy = TagEditFormPolicy(
    fieldsEditable = canWriteTags,
    showSaveAction = canWriteTags,
    showUnsupportedHint = !canWriteTags,
)
