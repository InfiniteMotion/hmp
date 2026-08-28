package com.hmp.domain.backup

import com.hmp.domain.enum.LabelCategory
import com.hmp.domain.enum.LabelName
import com.hmp.domain.playlist.Playlist
import com.hmp.domain.playlist.PlaylistItem
import com.hmp.domain.setting.model.ListeningDuration
import com.hmp.domain.setting.model.PlaybackHistory
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class UserBackupSnapshot(
    val version: Int = 1,
    val createdAt: Long = 0L,
    val appSettings: AppSettingsSnapshot? = null,
    val musicUserState: MusicUserStateSnapshot = MusicUserStateSnapshot(),
    val playlists: PlaylistsSnapshot = PlaylistsSnapshot(),
    val listeningStats: ListeningStatsSnapshot = ListeningStatsSnapshot(),
    val dailyRecommendation: DailyRecommendationSnapshot? = null
)

@Serializable
data class AppSettingsSnapshot(
    val userName: String? = null,
    val avatarUri: String? = null,
    val themeMode: String = "default",
    val backgroundStyle: String = "FLUID",
    val hazeMode: String = "custom",
    val hazeMaterialPreset: String = "regular",
    val hazeBlurRadius: Float = 20f,
    val hazeNoiseFactor: Float = 0.15f,
    val hazeTintAlpha: Float = 0.22f,
    val hazeIntensity: Float = 0f,
    val autoBatchProcess: Boolean = true,
    val dailyRefreshMode: String = "off",
    val dailyRefreshHours: Int = 8,
    val dailyRefreshStartupCount: Int = 5,
    val aiAccessMode: String = "FREE",
    val customAiEndpoint: String = "",
    val customAiModel: String = ""
)

@Serializable
data class MusicUserStateSnapshot(
    val userInfos: List<UserInfoSnapshot> = emptyList(),
    val extras: List<MusicExtraUserSnapshot> = emptyList(),
    val labels: List<MusicLabelSnapshot> = emptyList()
)

@Serializable
data class UserInfoSnapshot(
    val id: Long,
    val liked: Boolean = false,
    val disLiked: Boolean = false,
    val lastPlayed: Long? = null,
    val playCount: Int? = null,
    val skippedCount: Int? = null,
    val userRating: Int? = null,
    val inCustomPlaylistCount: Int? = null
)

@Serializable
data class MusicExtraUserSnapshot(
    val id: Long,
    val isGetExtraInfo: Boolean = false,
    val rewards: String? = null,
    val popLyric: String? = null,
    val singerIntroduce: String? = null,
    val backgroundIntroduce: String? = null,
    val description: String? = null,
    val relevantMusic: String? = null
)

@Serializable
data class MusicLabelSnapshot(
    val musicId: Long = 0L,
    val label: LabelName = LabelName.ROCK,
    val category: LabelCategory = LabelCategory.GENRE,
    /** 认识来源（LLM/USER/AGENT）与时间戳：v1 存量备份无此字段 → null（还原后按 LLM 旧认识处理，规则①丢失） */
    val source: String? = null,
    val confidence: Double? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

@Serializable
data class PlaylistsSnapshot(
    @Contextual
    val playlists: List<Playlist> = emptyList(),
    @Contextual
    val playlistItems: List<PlaylistItem> = emptyList()
)

@Serializable
data class ListeningStatsSnapshot(
    @Contextual
    val listeningDurations: List<ListeningDuration> = emptyList(),
    @Contextual
    val playbackHistories: List<PlaybackHistory> = emptyList()
)

@Serializable
data class DailyRecommendationSnapshot(
    val currentDailyMusicId: Long? = null,
    val lastRefreshTimestamp: Long = 0L,
    val mode: String = "off",
    val refreshHours: Int = 8,
    val startupCount: Int = 5,
    val launchCountSinceRefresh: Int = 0
)
