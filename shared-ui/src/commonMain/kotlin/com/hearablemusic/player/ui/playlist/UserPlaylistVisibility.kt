package com.hearablemusic.player.ui.playlist

import com.hmp.domain.playlist.Playlist

/**
 * "哪些歌单出现在列表页 / 「加入歌单」候选里"的唯一口径（D3-06）。
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
 * 注意**不包括管理页**：管理页的口径见 [manageablePlaylists]。两者不是同一个问题 ——
 * 心动 / 最近播放要在列表页能点进去听，但不该出现在"管理用户歌单"里。
 */
fun userVisiblePlaylists(all: List<Playlist>): List<Playlist> = all

/**
 * 管理页可见的歌单（D3-17①，用户 2026-11-05 决议：**不列系统歌单**）。
 *
 * 为什么不在 [userVisiblePlaylists] 里按同一套条件滤掉：那个函数服务的是列表页与「加入歌单」，
 * 心动 / 最近播放必须在；而管理页给的是重命名与删除按钮，系统歌单两样都不该有
 * （删除会被 `removePlaylistById` 拒掉，改名则会让名字与 `Res.string.heart` 脱钩，
 * 因为自愈只看 id、不认名字）。
 *
 * 按 **id** 排除而不是按名字：名字是多语言的，也可能是用户改过的。
 *
 * @param systemIds 由 `ManagePlaylistUseCase` / `SystemPlaylists` 给出的当前 / 心动 / 最近 id。
 */
fun manageablePlaylists(all: List<Playlist>, systemIds: Set<Long>): List<Playlist> = userVisiblePlaylists(all).filter { it.id !in systemIds }
