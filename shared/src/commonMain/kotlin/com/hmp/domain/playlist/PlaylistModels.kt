package com.hmp.domain.playlist

import kotlinx.serialization.Serializable

/**
 * 播放列表领域模型（生成算法相关模型已于 2026-11-03 随旧生成器退役）。
 */

@Serializable
data class Playlist(
    val id: Long = 0,
    val name: String,
    val coverUri: String? = null,
    val playCount: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastPlayedAt: Long? = null,
    val description: String? = null,
    val songCount: Int = 0,
    val totalDurationMs: Long = 0L,
    val isPinned: Boolean = false,
)

/**
 * 歌单条目。
 *
 * `itemOrder` 是**用户排出来的顺序**（置顶 / 上移下移 / Agent 重排的结果），不是插入顺序的副产品。
 * 一-4 / D3-02 之前这个字段只活在实体列上、领域模型不承载它，于是"导出 → 删 → 恢复"这条路上
 * 顺序先在导出时被丢（领域模型没地方放），再在恢复时被按任意行序重编 —— 用户手排的长歌单
 * 是这次操作里唯一不可再生的数据。
 */
@Serializable
data class PlaylistItem(
    val songId: Long,
    val playlistId: Long,
    val itemOrder: Int = 0,
)
