package com.hmp.domain.playlist

/**
 * 系统歌单（默认播放 / 红心 / 最近播放）的**唯一**判定口径（D3-17③）。
 *
 * 为什么要有这一份：同一个判断原先抄了两处 —— `ManagePlaylistUseCase.removePlaylistById`
 * 里"抛 IllegalArgumentException"，Agent 的 `playlist_delete` 工具里"返回 failure"。
 * 两处语义一致但形式不同，于是 UI 侧只能靠"会不会崩"来感知保护，
 * 而管理页恰恰把系统歌单列了出来（D3-17①）：用户点删除 → 异常从 `viewModelScope` 上抛，
 * 全仓没有 `CoroutineExceptionHandler` 兜底（Android 表现为崩溃）。
 *
 * id 存在 DataStore、由调用方取来传入 —— 这一层不碰存储，所以三端与测试都能用同一份。
 */
object SystemPlaylists {

    fun ids(currentId: Long?, likedId: Long?, recentId: Long?): Set<Long> =
        setOfNotNull(currentId, likedId, recentId)

    fun isSystem(
        playlistId: Long,
        currentId: Long?,
        likedId: Long?,
        recentId: Long?,
    ): Boolean = playlistId == currentId || playlistId == likedId || playlistId == recentId
}
