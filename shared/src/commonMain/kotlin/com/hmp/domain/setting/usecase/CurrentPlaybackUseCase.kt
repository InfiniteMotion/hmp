package com.hmp.domain.setting.usecase

import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.playlist.PlaylistRepository
import com.hmp.domain.setting.SettingsRepository
import kotlinx.coroutines.flow.Flow

/**
 * 当前播放管理Use Case
 *
 * 负责当前播放状态的管理，包括：
 * - 当前播放音乐ID的保存和获取
 * - 播放列表中音乐的查询
 * - 音乐标签和歌词获取
 * - 音乐喜爱状态管理
 * - 相似歌曲推荐
 *
 * @property musicRepository 音乐数据仓库
 * @property playlistRepository 播放列表数据仓库
 * @property settingsRepository 设置数据仓库
 */
class CurrentPlaybackUseCase(

    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
    private val settingsRepository: SettingsRepository,
) {
    /**
     * 获取当前播放列表中的音乐
     * @param playlistId 播放列表ID
     * @return 音乐列表Flow
     */
    fun getMusicInPlaylist(playlistId: Long): Flow<List<MusicInfo>> = playlistRepository.getMusicInfoInPlaylist(playlistId)

    /**
     * 获取当前播放音乐ID
     */
    fun getCurrentMusicId(): Flow<Long?> = settingsRepository.currentMusicId

    /**
     * 保存当前播放音乐ID
     */
    suspend fun saveCurrentMusicId(musicId: Long) {
        settingsRepository.saveCurrentMusicId(musicId)
    }

    /**
     * 获取音乐的标签信息
     * @param musicId 音乐ID
     * @return 标签列表
     */
    suspend fun getMusicLabels(musicId: Long) = musicRepository.getMusicLabels(musicId)

    /**
     * 获取音乐的歌词
     * @param musicId 音乐ID
     * @return 歌词文本
     */
    suspend fun getMusicLyrics(musicId: Long) = musicRepository.getMusicLyrics(musicId)

    /**
     * 更新音乐喜爱状态
     * @param musicId 音乐ID
     * @param liked 是否喜欢
     */
    suspend fun updateLikedStatus(musicId: Long, liked: Boolean) {
        musicRepository.updateLikedStatus(musicId, liked)
    }

    /**
     * 获取音乐喜爱状态
     * @param musicId 音乐ID
     * @return 是否喜欢
     */
    suspend fun getLikedStatus(musicId: Long): Boolean = musicRepository.getLikedStatus(musicId)

    /**
     * 根据加权标签获取相似歌曲
     * @param musicId 音乐ID
     * @param limit 返回数量限制
     * @return 相似歌曲列表
     */
    suspend fun getSimilarSongsByWeightedLabels(musicId: Long, limit: Int = 10): List<MusicInfo> = musicRepository.getSimilarSongsByWeightedLabels(musicId, limit)
}
