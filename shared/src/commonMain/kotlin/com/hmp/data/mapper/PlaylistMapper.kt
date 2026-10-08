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
    playlistId = playlistId
)

fun PlaylistItem.toEntity(itemOrder: Int = 0): PlaylistItemEntity = PlaylistItemEntity(
    songId = songId,
    playlistId = playlistId,
    itemOrder = itemOrder
)
