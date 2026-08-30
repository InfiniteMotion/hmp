package com.hearablemusic.player.ui.chat

import com.hearablemusic.player.ui.platform.PlaybackController
import com.hmp.domain.agent.port.NowPlayingContext
import com.hmp.domain.agent.port.NowPlayingContextProvider
import com.hmp.domain.agent.port.PlaybackCommand
import com.hmp.domain.agent.port.PlaybackCommandPort
import com.hmp.domain.enum.PlaybackMode
import com.hmp.domain.music.MusicRepository
import kotlinx.coroutines.flow.first

/**
 * R-T3 真实端口适配器：把 shared-ui 已有的 [PlaybackController] 桥接到 :shared 的 agent 端口。
 *
 * 复用现有 Controller 桥（Android Media3 / Desktop FFmpeg / iOS AVPlayer 各端已实现），
 * 让 `controlPlayback` / `getNowPlayingContext` 在真实对话里生效：
 * - `NowPlayingContextProvider`：从 controller 的播放状态流取当前曲目/进度。
 * - `PlaybackCommandPort`：把密封指令映射为 controller 的播放控制。
 * 依赖方向铁律（总纲 7.2 选型 5）：`:shared` 只依赖端口，本适配器位于 shared-ui，
 * 不反向把 shared-ui 类型泄给 :shared。
 */
class ControllerPlaybackCommandPort(
    private val controller: PlaybackController,
    private val musicRepository: MusicRepository,
) : PlaybackCommandPort {

    override suspend fun execute(command: PlaybackCommand): Pair<Boolean, String> = when (command) {
        is PlaybackCommand.PLAY -> {
            controller.playOrResume(); true to "已播放"
        }
        is PlaybackCommand.PAUSE -> {
            controller.pauseMusic(); true to "已暂停"
        }
        is PlaybackCommand.NEXT -> {
            controller.playNext(); true to "已切到下一首"
        }
        is PlaybackCommand.PREVIOUS -> {
            controller.playPrevious(); true to "已回到上一首"
        }
        is PlaybackCommand.SEEK_TO -> {
            controller.seekTo(command.positionMs); true to "已跳转"
        }
        is PlaybackCommand.PLAY_BY_ID -> {
            val music = musicRepository.getMusicInfoById(command.musicId).first()
            if (music == null) {
                false to "未找到该曲目"
            } else {
                controller.playWith(music); true to "已开始播放「${music.music.title}」"
            }
        }
        is PlaybackCommand.SHUFFLE_ON -> setMode(PlaybackMode.SHUFFLE)
        is PlaybackCommand.SHUFFLE_OFF -> setMode(PlaybackMode.SEQUENTIAL)
        is PlaybackCommand.REPEAT_ONE_ON -> setMode(PlaybackMode.REPEAT_ONE)
        is PlaybackCommand.REPEAT_ALL_ON -> setMode(PlaybackMode.SEQUENTIAL) // 全部循环无对应，退回顺序
        is PlaybackCommand.REPEAT_OFF -> setMode(PlaybackMode.SEQUENTIAL)
    }

    /** 通过 [PlaybackController.togglePlaybackModeByOrder] 循环切换至目标；3 态循环至多 2 次必达。 */
    private fun setMode(target: PlaybackMode): Pair<Boolean, String> {
        var current = controller.playbackMode.value
        var guard = 0
        while (current != target && guard < 3) {
            controller.togglePlaybackModeByOrder()
            current = controller.playbackMode.value
            guard++
        }
        val ok = current == target
        return ok to if (ok) "已切换播放模式" else "播放模式切换受限"
    }
}

class ControllerNowPlayingProvider(
    private val controller: PlaybackController,
) : NowPlayingContextProvider {

    override suspend fun getNowPlaying(): NowPlayingContext {
        val music = controller.currentPlayingMusic.value
        return NowPlayingContext(
            currentMusicId = music?.music?.id,
            currentMusicInfo = music,
            isPlaying = controller.isPlaying.value,
            currentPositionMs = controller.currentPosition.value,
            durationMs = controller.duration.value,
        )
    }
}
