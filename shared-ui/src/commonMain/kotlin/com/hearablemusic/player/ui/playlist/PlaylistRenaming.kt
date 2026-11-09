package com.hearablemusic.player.ui.playlist

import com.hmp.domain.playlist.PlaylistNameError
import com.hmp.domain.playlist.PlaylistNameException
import com.hmp.domain.playlist.usecase.ManagePlaylistUseCase
import com.hmp.log.HmpLog
import com.hmp.log.LogTag

/**
 * 改名 + 名称规则错误码转译（歌单详情页与管理页共用一份）。
 *
 * 名称规则由 `ManagePlaylistUseCase` 强制（D3-05④）；非名称类异常（DB 故障）
 * 只记日志并返回 null，与拆分前的 UI 行为一致 —— 不把它伪装成「名字不合法」。
 */
internal suspend fun ManagePlaylistUseCase.renameWithError(id: Long, newName: String): PlaylistNameError? = runCatching { renamePlaylist(id, newName) }
    .onFailure { e ->
        if (e !is PlaylistNameException) {
            HmpLog.w(LogTag.UiCommon) { "歌单改名失败：${e.message}" }
        }
    }
    .exceptionOrNull()
    .let { (it as? PlaylistNameException)?.error }
