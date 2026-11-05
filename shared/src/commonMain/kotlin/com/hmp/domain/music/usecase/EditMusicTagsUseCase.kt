package com.hmp.domain.music.usecase

import com.hmp.domain.music.EditableMusicTags
import com.hmp.domain.music.MusicRepository

/**
 * 编辑单曲标签（ID3 元数据：标题/艺术家/专辑）。
 * 写入音乐文件成功后同步更新本地曲库记录。
 */
class EditMusicTagsUseCase(
    private val musicRepository: MusicRepository,
) {
    suspend operator fun invoke(musicId: Long, tags: EditableMusicTags): Result<Unit> = musicRepository.updateMusicTags(musicId, tags)

    /**
     * 文件已通过 SAF 等途径写入，仅刷新本地曲库中的标题/艺术家/专辑/歌词。
     */
    suspend fun refreshAfterFileWrite(musicId: Long, tags: EditableMusicTags): Result<Unit> = musicRepository.refreshMusicTags(musicId, tags)
}
