package com.hmp.data.mapper

import com.hmp.domain.music.MusicLabel
import com.hmp.domain.setting.model.ListeningDuration
import kotlin.test.Test
import kotlin.test.assertEquals
import com.hmp.data.database.ListeningDuration as ListeningDurationEntity
import com.hmp.data.database.Music as MusicEntity
import com.hmp.data.database.MusicExtra as MusicExtraEntity
import com.hmp.data.database.MusicLabel as MusicLabelEntity
import com.hmp.data.database.PlaybackHistory as PlaybackHistoryEntity
import com.hmp.data.database.UserInfo as UserInfoEntity
import com.hmp.data.database.myenum.LabelCategory as DataLabelCategory
import com.hmp.data.database.myenum.LabelName as DataLabelName
import com.hmp.domain.enum.LabelCategory as DomainLabelCategory
import com.hmp.domain.enum.LabelName as DomainLabelName
import com.hmp.domain.setting.model.PlaybackHistory as PlaybackHistoryDomain

/**
 * 曲目图（`music` / `musicExtra` / `userInfo`）的映射测试 —— **只测 `entity → domain`**。
 *
 * 这里原先还有 4 条反向用例（`music_toEntity_mapsAllFields`、两个 `*_roundTrip`、
 * `musicExtra_toEntity_mapsAllFields`）。它们在 2026-11-05 随 D2-04 一起删了：
 * 反向的四个 `toEntity` 本身已删除，留下测它们的用例等于给死码发绿灯。
 *
 * **不要因为"字段没覆盖全"就把反向映射加回来。** 为什么只有单程、为什么领域模型不带
 * `isDeleted`，见 `MusicMapper.kt` 文件头与 `MusicModels.kt` 里各模型的 KDoc。
 * 真要防"复活已软删曲目"，那条防线在 `PlaylistRepositoryImplTest`
 * （`resetPlaylistItems_neverResurrectsSoftDeletedTracks`，真 Room）与编译期（反向映射不存在）。
 */
class MusicMapperTest {

    @Test
    fun musicEntity_toDomain_mapsAllFields() {
        val entity = MusicEntity(
            id = 1,
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            duration = 180000L,
            path = "/path/to/song.mp3",
            albumArtUri = "/path/to/art.jpg",
        )
        val domain = entity.toDomain()
        assertEquals(1L, domain.id)
        assertEquals("Test Song", domain.title)
        assertEquals("Test Artist", domain.artist)
        assertEquals("Test Album", domain.album)
        assertEquals(180000L, domain.duration)
        assertEquals("/path/to/song.mp3", domain.path)
        assertEquals("/path/to/art.jpg", domain.albumArtUri)
    }

    @Test
    fun musicExtraEntity_toDomain_mapsAllFields() {
        val entity = MusicExtraEntity(
            id = 1,
            lyrics = "Some lyrics",
            bitRate = 320,
            sampleRate = 44100,
            fileSize = 1024000L,
            format = "mp3",
            language = "en",
            date = 1700000000L,
            recommendationIds = "1,2,3",
            isGetExtraInfo = true,
            rewards = "reward",
            popLyric = "pop",
            singerIntroduce = "intro",
            backgroundIntroduce = "bg",
            description = "desc",
            relevantMusic = "related",
        )
        val domain = entity.toDomain()
        assertEquals(1L, domain.id)
        assertEquals("Some lyrics", domain.lyrics)
        assertEquals(320, domain.bitRate)
        assertEquals(44100, domain.sampleRate)
        assertEquals(1024000L, domain.fileSize)
        assertEquals("mp3", domain.format)
        assertEquals("en", domain.language)
        assertEquals(1700000000L, domain.date)
        assertEquals("1,2,3", domain.recommendationIds)
        assertEquals(true, domain.isGetExtraInfo)
    }

    @Test
    fun userInfoEntity_toDomain_mapsFields() {
        val entity = UserInfoEntity(
            id = 1,
            liked = true,
            disLiked = false,
            lastPlayed = 1700000000L,
            playCount = 42,
            skippedCount = 3,
            userRating = 5,
            inCustomPlaylistCount = 2,
        )
        val domain = entity.toDomain()
        assertEquals(1L, domain.id)
        assertEquals(true, domain.liked)
        assertEquals(false, domain.disLiked)
        assertEquals(1700000000L, domain.lastPlayed)
        assertEquals(42, domain.playCount)
        assertEquals(3, domain.skippedCount)
        assertEquals(5, domain.userRating)
        assertEquals(2, domain.inCustomPlaylistCount)
    }

    @Test
    fun musicLabelEntity_toDomain_mapsEnumCorrectly() {
        val entity = MusicLabelEntity(
            musicId = 1,
            type = DataLabelCategory.GENRE,
            label = DataLabelName.ROCK,
        )
        val domain = entity.toDomain()
        assertEquals(1L, domain.musicId)
        assertEquals(DomainLabelCategory.GENRE, domain.type)
        assertEquals(DomainLabelName.ROCK, domain.label)
    }

    @Test
    fun musicLabel_toEntity_mapsEnumCorrectly() {
        val domain = MusicLabel(
            musicId = 2,
            type = DomainLabelCategory.MOOD,
            label = DomainLabelName.HAPPY,
        )
        val entity = domain.toEntity()
        assertEquals(2L, entity.musicId)
        assertEquals(DataLabelCategory.MOOD, entity.type)
        assertEquals(DataLabelName.HAPPY, entity.label)
    }

    @Test
    fun listeningDurationEntity_toDomain_mapsCorrectly() {
        val entity = ListeningDurationEntity(
            date = "2024-01-15",
            duration = 3600000L,
            updatedAt = 1700000000L,
        )
        val domain = entity.toDomain()
        assertEquals("2024-01-15", domain.date)
        assertEquals(3600000L, domain.duration)
        assertEquals(1700000000L, domain.updatedAt)
    }

    @Test
    fun listeningDuration_toEntity_roundTrip() {
        val original = ListeningDuration(
            date = "2024-06-01",
            duration = 7200000L,
            updatedAt = 1700100000L,
        )
        val entity = original.toEntity()
        val restored = entity.toDomain()
        assertEquals(original, restored)
    }

    @Test
    fun playbackHistoryEntity_toDomain_mapsCorrectly() {
        val entity = PlaybackHistoryEntity(
            id = 1,
            musicId = 100,
            playedAt = 1700000000L,
            playDuration = 120000L,
            isCompleted = true,
            source = "playlist",
        )
        val domain = entity.toDomain()
        assertEquals(1L, domain.id)
        assertEquals(100L, domain.musicId)
        assertEquals(1700000000L, domain.playedAt)
        assertEquals(120000L, domain.playDuration)
        assertEquals(true, domain.isCompleted)
        assertEquals("playlist", domain.source)
    }

    @Test
    fun playbackHistory_toEntity_roundTrip() {
        val original = PlaybackHistoryDomain(
            id = 5,
            musicId = 200,
            playedAt = 1700100000L,
            playDuration = 180000L,
            isCompleted = false,
            source = "search",
        )
        val entity = original.toEntity()
        val restored = entity.toDomain()
        assertEquals(original, restored)
    }
}
