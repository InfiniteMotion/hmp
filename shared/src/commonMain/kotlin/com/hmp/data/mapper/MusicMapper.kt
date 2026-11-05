package com.hmp.data.mapper

import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicExtra
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicLabel
import com.hmp.domain.music.UserInfo
import com.hmp.domain.setting.model.ListeningDuration
import com.hmp.data.database.ListeningDuration as ListeningDurationEntity
import com.hmp.data.database.Music as MusicEntity
import com.hmp.data.database.MusicExtra as MusicExtraEntity
import com.hmp.data.database.MusicInfo as MusicInfoEntity
import com.hmp.data.database.MusicLabel as MusicLabelEntity
import com.hmp.data.database.UserInfo as UserInfoEntity
import com.hmp.data.database.myenum.LabelCategory as DataLabelCategory
import com.hmp.data.database.myenum.LabelName as DataLabelName
import com.hmp.domain.enum.LabelCategory as DomainLabelCategory
import com.hmp.domain.enum.LabelName as DomainLabelName
import com.hmp.domain.setting.model.PlaybackHistory as PlaybackHistoryDomain

/**
 * `music` / `musicExtra` / `userInfo` 的映射是**故意单向**的：只有 `entity → domain`。
 *
 * 反向的四个（`Music.toEntity` / `MusicExtra.toEntity` / `UserInfo.toEntity` / `MusicInfo.toEntity`）
 * 曾长期挂着，但生产调用点其实是零 —— 唯一看起来像调用方的那处
 * （三端 `PlaylistRepositoryImpl.resetPlaylistItems`）只是把实体图当 id 容器用，
 * 实体一个字节都没落库。2026-11-05 按 D2-04 把那条通道收窄成 `List<Long>` 之后，
 * 四个反向映射彻底没有存在理由，已删除。
 *
 * **为什么不补 `isDeleted` 再保留它们**：这三张表都有 `isDeleted`，而领域模型一个都不带
 * （见 `MusicModels.kt` 里各模型的 KDoc）。带上它意味着领域层要维护一套"我还在曲库里"的
 * 不变量，而实际的写路径根本不经过领域层 —— 曲目行由扫描器 `insertAll` 整行写，
 * 软删由 `markDeletedByIds` / `markActiveByIds` 定点写，两者都只搬 id。
 * 在这种结构下，一个"能往回写整行"的 mapper 只会把 `isDeleted` 冲刷成 `false`，
 * 那不是能力，是陷阱。真需要"曲目是否可见"，读侧看 `MusicDao` 的 SQL 过滤即可。
 *
 * 新增字段时只需改 `toDomain` 一处；如果哪天真的出现"domain → entity 整行回写"的需求，
 * 应当新开一个语义明确的函数（并在其中显式搬运 `isDeleted`），而不是把这些泛型 `toEntity` 请回来。
 */

fun MusicEntity.toDomain(): Music = Music(
    id = id,
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    path = path,
    albumArtUri = albumArtUri,
)

fun MusicExtraEntity.toDomain(): MusicExtra = MusicExtra(
    id = id,
    lyrics = lyrics,
    bitRate = bitRate,
    sampleRate = sampleRate,
    fileSize = fileSize,
    format = format,
    language = language,
    date = date,
    recommendationIds = recommendationIds,
    isGetExtraInfo = isGetExtraInfo,
    rewards = rewards,
    popLyric = popLyric,
    singerIntroduce = singerIntroduce,
    backgroundIntroduce = backgroundIntroduce,
    description = description,
    relevantMusic = relevantMusic,
)

fun UserInfoEntity.toDomain(): UserInfo = UserInfo(
    id = id,
    liked = liked,
    disLiked = disLiked,
    lastPlayed = lastPlayed,
    playCount = playCount,
    skippedCount = skippedCount,
    userRating = userRating,
    inCustomPlaylistCount = inCustomPlaylistCount,
)

fun MusicInfoEntity.toDomain(): MusicInfo = MusicInfo(
    music = music.toDomain(),
    extra = extra?.toDomain(),
    userInfo = userInfo?.toDomain(),
)

fun MusicLabelEntity.toDomain(): MusicLabel = MusicLabel(
    musicId = musicId,
    type = DomainLabelCategory.valueOf(type.name),
    label = DomainLabelName.valueOf(label.name),
)

fun MusicLabel.toEntity(): MusicLabelEntity = MusicLabelEntity(
    musicId = musicId,
    type = DataLabelCategory.valueOf(type.name),
    label = DataLabelName.valueOf(label.name),
)

fun ListeningDurationEntity.toDomain(): ListeningDuration = ListeningDuration(
    date = date,
    duration = duration,
    updatedAt = updatedAt,
)

fun ListeningDuration.toEntity(): ListeningDurationEntity = ListeningDurationEntity(
    date = date,
    duration = duration,
    updatedAt = updatedAt,
)

fun com.hmp.data.database.PlaybackHistory.toDomain(): PlaybackHistoryDomain = PlaybackHistoryDomain(
    id = id,
    musicId = musicId,
    playedAt = playedAt,
    playDuration = playDuration,
    isCompleted = isCompleted,
    source = source,
)

fun PlaybackHistoryDomain.toEntity(): com.hmp.data.database.PlaybackHistory = com.hmp.data.database.PlaybackHistory(
    id = id,
    musicId = musicId,
    playedAt = playedAt,
    playDuration = playDuration,
    isCompleted = isCompleted,
    source = source,
)
