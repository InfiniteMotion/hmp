package com.hmp.data.mapper

import com.hmp.data.database.Playlist as PlaylistEntity
import com.hmp.data.database.PlaylistItem as PlaylistItemEntity
import com.hmp.domain.playlist.Playlist
import com.hmp.domain.playlist.PlaylistItem

fun PlaylistEntity.toDomain(): Playlist = Playlist(
    id = id,
    name = name,
    coverUri = coverUri,
    playCount = playCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastPlayedAt = lastPlayedAt,
    description = description,
    songCount = songCount,
    totalDurationMs = totalDurationMs,
    isPinned = isPinned
)

fun Playlist.toEntity(): PlaylistEntity = PlaylistEntity(
    id = id,
    name = name,
    coverUri = coverUri,
    playCount = playCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastPlayedAt = lastPlayedAt,
    description = description,
    songCount = songCount,
    totalDurationMs = totalDurationMs,
    isPinned = isPinned
)

fun PlaylistItemEntity.toDomain(): PlaylistItem = PlaylistItem(
    songId = songId,
    playlistId = playlistId,
    itemOrder = itemOrder
)

/**
 * `itemOrder` 由领域模型自己带上（一-4 / D3-02）。
 *
 * 旧签名是 `toEntity(itemOrder: Int = 0)` —— 顺序由调用方临时给，领域模型里没有它的位置，
 * 于是"导出时把顺序丢掉"这件事在类型上就是合法的。默认值那个 0 尤其危险：
 * 一个歌单里第二首起全是 0，就撞 v10 的 `UNIQUE(playlistId, itemOrder)`。
 */
fun PlaylistItem.toEntity(): PlaylistItemEntity = PlaylistItemEntity(
    songId = songId,
    playlistId = playlistId,
    itemOrder = itemOrder
)
