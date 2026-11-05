package com.hmp.data.mapper

import com.hmp.domain.playlist.Playlist
import com.hmp.domain.playlist.PlaylistItem
import com.hmp.data.database.Playlist as PlaylistEntity
import com.hmp.data.database.PlaylistItem as PlaylistItemEntity

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
    isPinned = isPinned,
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
    isPinned = isPinned,
)

fun PlaylistItemEntity.toDomain(): PlaylistItem = PlaylistItem(
    songId = songId,
    playlistId = playlistId,
    itemOrder = itemOrder,
)

/**
 * 条目映射只有 `entity → domain` 单程。**没有** `PlaylistItem.toEntity()`，这是清过死码的决议不是遗漏
 * （2026-11-05）：全仓没有生产调用 —— `restoreFromSnapshot` 是**手写** `PlaylistItem(songId=…, playlistId=…, itemOrder=…)`
 * （重编号语义本来就在那里，见三端仓库实现），别把泛型 mapper 加回来。
 *
 * `itemOrder` 由领域模型自己带上（一-4 / D3-02）—— 这条不变的判据如今由
 * `PlaylistRepositoryImplTest.exportAndRestore_keepsUserOrder`（真 Room）钉住：导出带序、恢复重编不丢相对顺序。
 * 旧签名曾是 `toEntity(itemOrder: Int = 0)`，顺序由调用方临时给；默认值那个 0 尤其危险——
 * 一个歌单里第二首起全是 0，就撞 v10 的 `UNIQUE(playlistId, itemOrder)`。
 */
