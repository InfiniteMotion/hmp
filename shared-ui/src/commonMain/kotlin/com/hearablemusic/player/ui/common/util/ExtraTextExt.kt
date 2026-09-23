package com.hearablemusic.player.ui.common.util

/**
 * 富化文案是否值得渲染（null / 空白 / 字面量 "None" 都不渲染）。
 *
 * 富化文本在 `MusicExtra` 里是 `String?`，而历史存量数据中存在 LLM 直接输出
 * `"None"` 的情况（新版 EnrichResponseParser 默认值已改为空串，但仍可能有老数据
 * 躺在库里），所以空白判断之外必须保留对 `"None"` 的排除，否则旧数据会把
 * `"None"` 当正文显示出来。原先各 UI 散落的 `x.isNotBlank() && x != "None"` 统一收敛到这里。
 */
fun String?.isRenderableExtra(): Boolean = !this.isNullOrBlank() && this != "None"
