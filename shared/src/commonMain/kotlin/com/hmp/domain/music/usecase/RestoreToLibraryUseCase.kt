package com.hmp.domain.music.usecase

import com.hmp.domain.music.MusicRepository

/**
 * 恢复已从曲库移除的歌曲：对指定 id 列表执行 markActive，曲库/播放列表等查询将重新返回这些项。
 */
class RestoreToLibraryUseCase(

    private val musicRepository: MusicRepository,
) {
    suspend operator fun invoke(ids: List<Long>) {
        if (ids.isEmpty()) return
        musicRepository.restoreToLibrary(ids)
    }
}
