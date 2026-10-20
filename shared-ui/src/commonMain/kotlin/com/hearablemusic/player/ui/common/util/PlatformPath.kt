package com.hearablemusic.player.ui.common.util

/**
 * 取父目录的 UI 侧入口。
 *
 * **规则只有一份**，在 `shared` 的 `com.hmp.data.util.parentPathOf`（D2-13）；
 * 三端的 actual 只声明本端认哪些分隔符（POSIX `/`，Desktop 在 Windows 上 `\` 与 `/` 都认）。
 *
 * 之所以要有这一层：设置页的「已隐藏文件夹」列表（仓库侧 `getDeletedMusicIdsGroupedByFolder`）
 * 与播放列表侧的"这首歌属于哪个被隐藏的文件夹"（本函数）**必须给同一个字符串**，
 * 否则用户点"取消隐藏"会恢复不了任何东西 —— 两边以前是两套切法，Windows 上必分叉。
 */
expect fun fileParentOf(path: String): String?
