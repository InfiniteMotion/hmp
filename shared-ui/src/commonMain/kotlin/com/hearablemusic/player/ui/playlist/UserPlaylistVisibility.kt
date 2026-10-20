package com.hearablemusic.player.ui.playlist

import com.hmp.domain.playlist.Playlist

/**
 * "哪些歌单出现在列表页 / 管理页 / 「加入歌单」候选里"的唯一口径（D3-06）。
 *
 * 规则现在就一条：**全部可见**。原先这里是 `filter { it.songCount > 0 }`，
 * 于是新建但没选曲的歌单在三个地方同时不可见 —— 用户唯一能进去的入口是创建成功那一次导航，
 * 退出即失联：歌单在 UI 上凭空消失，DB 里却一直在。
 *
 * 单独成函数是为了能被机器看住：`PlaylistViewModel` 的 `init` 会解析 compose 资源名
 * （`getString(Res.string.heart)`），而本仓库的 `shared-ui` 测试源集没接 Robolectric，
 * VM 本身构造不出来。这是 D3-06 判据里预写的退路 —— 口径落在纯函数上，
 * VM 只是它的一个调用方。
 *
 * ⚠️ **还没决定的部分**：这里到底该不该排除三个系统歌单（默认播放 / 红心 / 最近播放）。
 * 那要按 id 显式排除而不是按计数，口径与 D3-17①（管理页要不要列出系统歌单）绑在一起等产品定；
 * 定了就改这一个函数，三个消费点自动跟着走。
 */
fun userVisiblePlaylists(all: List<Playlist>): List<Playlist> = all
