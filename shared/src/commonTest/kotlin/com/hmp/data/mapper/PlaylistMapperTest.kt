package com.hmp.data.mapper

import com.hmp.domain.playlist.Playlist
import kotlin.test.Test
import kotlin.test.assertEquals
import com.hmp.data.database.Playlist as PlaylistEntity
import com.hmp.data.database.PlaylistItem as PlaylistItemEntity

class PlaylistMapperTest {

    @Test
    fun playlistEntity_toDomain_mapsAllFields() {
        val entity = PlaylistEntity(
            id = 1,
            name = "My Playlist",
            coverUri = "/cover.jpg",
            playCount = 10,
            createdAt = 1700000000L,
            updatedAt = 1700100000L,
            lastPlayedAt = 1700200000L,
            description = "Test description",
            songCount = 25,
            totalDurationMs = 5400000L,
            isPinned = true,
        )
        val domain = entity.toDomain()
        assertEquals(1L, domain.id)
        assertEquals("My Playlist", domain.name)
        assertEquals("/cover.jpg", domain.coverUri)
        assertEquals(10, domain.playCount)
        assertEquals(1700000000L, domain.createdAt)
        assertEquals(1700100000L, domain.updatedAt)
        assertEquals(1700200000L, domain.lastPlayedAt)
        assertEquals("Test description", domain.description)
        assertEquals(25, domain.songCount)
        assertEquals(5400000L, domain.totalDurationMs)
        assertEquals(true, domain.isPinned)
    }

    @Test
    fun playlist_toEntity_mapsAllFields() {
        val domain = Playlist(
            id = 2,
            name = "Domain Playlist",
            coverUri = null,
            playCount = 5,
            createdAt = 1700000000L,
            updatedAt = 1700100000L,
            lastPlayedAt = null,
            description = null,
            songCount = 0,
            totalDurationMs = 0L,
            isPinned = false,
        )
        val entity = domain.toEntity()
        assertEquals(2L, entity.id)
        assertEquals("Domain Playlist", entity.name)
        assertEquals(null, entity.coverUri)
        assertEquals(5, entity.playCount)
        assertEquals(null, entity.lastPlayedAt)
        assertEquals(null, entity.description)
        assertEquals(false, entity.isPinned)
    }

    @Test
    fun playlistEntity_toDomain_roundTrip() {
        val original = Playlist(
            id = 10,
            name = "Round Trip",
            coverUri = "/art.png",
            playCount = 99,
            createdAt = 100L,
            updatedAt = 200L,
            lastPlayedAt = 300L,
            description = "desc",
            songCount = 50,
            totalDurationMs = 100000L,
            isPinned = true,
        )
        val entity = original.toEntity()
        val restored = entity.toDomain()
        assertEquals(original, restored)
    }

    @Test
    fun playlistItemEntity_toDomain_mapsCorrectly() {
        val entity = PlaylistItemEntity(
            songId = 42,
            playlistId = 1,
            itemOrder = 3,
        )
        val domain = entity.toDomain()
        assertEquals(42L, domain.songId)
        assertEquals(1L, domain.playlistId)
        // D3-02：顺序是用户资产，领域模型必须承载它 —— 丢在这里就等于备份/恢复时无法还原
        assertEquals(3, domain.itemOrder)
    }

    // 原有 playlistItem 的三条反向用例（toEntity mapsWithItemOrder / defaultItemOrder / roundTrip_keepsItemOrder）
    // 已随 `PlaylistItem.toEntity()` 一起删除（2026-11-05）：该 mapper 全仓零生产调用，是清死码时删掉的。
    // "itemOrder 双向不丢"的判据由真 Room 的 PlaylistRepositoryImplTest.exportAndRestore_keepsUserOrder 承担，
    // 它比 mapper 往返测试更接近真实故障面（导出查询的 ORDER BY、恢复时的重编号都在那里）。
}
