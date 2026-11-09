package com.hearablemusic.player.ui.playlist

import com.hmp.domain.playlist.Playlist

/**
 * 歌单的展示排序：置顶优先，再按最近播放、更新时间。
 *
 * 歌单首页与列表管理页共用这一处口径；此前两屏逐字复制同一段 comparator。
 */
internal fun sortedForManagement(playlists: List<Playlist>): List<Playlist> = playlists.sortedWith(
    compareByDescending<Playlist> { it.isPinned }
        .thenByDescending { it.lastPlayedAt ?: 0L }
        .thenByDescending { it.updatedAt },
)
