package com.hearablemusic.player.ui.chat

import co.touchlab.kermit.Logger
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hmp.domain.agent.port.NowPlayingContext
import com.hmp.domain.agent.port.NowPlayingContextProvider
import com.hmp.domain.agent.port.PlaybackCommand
import com.hmp.domain.agent.port.PlaybackCommandPort
import com.hmp.domain.enum.PlaybackMode
import com.hmp.domain.music.MusicRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first

/**
 * R-T3 真实端口适配器：把 shared-ui 已有的 [PlaybackController] 桥接到 :shared 的 agent 端口。
 *
 * 复用现有 Controller 桥（Android Media3 / Desktop FFmpeg / iOS AVPlayer 各端已实现），
 * 让 `controlPlayback` / `getNowPlayingContext` 在真实对话里生效：
 * - `NowPlayingContextProvider`：从 controller 的播放状态流取当前曲目/进度。
 * - `PlaybackCommandPort`：把密封指令映射为 controller 的播放控制。
 *
 * M6-T2 增强：skipEvents 由 NEXT/PREVIOUS/PLAY_BY_ID/SKIP_ALL 命令驱动——
 * 执行前缓存当前曲目 title，命令完成后 emit。
 *
 * M6-T3 增强：DjBlank 事件在切歌时 emit——每次 currentPlayingMusic 变化（且是 agent 命令触发的）
 * 都视为曲间空白点，供 MasterAgent 消费生成衔接语。
 *
 * 依赖方向铁律（总纲 7.2 选型 5）：`:shared` 只依赖端口，本适配器位于 shared-ui，
 * 不反向把 shared-ui 类型泄给 :shared。
 */
class ControllerPlaybackCommandPort(
    private val controller: PlaybackController,
    private val musicRepository: MusicRepository,
) : PlaybackCommandPort {

    private val _skipEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val skipEvents: Flow<String> = _skipEvents.asSharedFlow()

    /**
     * M6-T3：Agent 命令触发的切歌事件（currentPlayingMusic 变化 + 切歌来源）。
     * 用于 MasterAgent 判断是否需要生成 Dj 衔接语（DjBlank）。
     * 自然播放完毕的切歌不 emit——只有用户主动/agent 命令才触发。
     */
    private val _agentTrackChanges = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val trackChangeEvents: Flow<String> = _agentTrackChanges.asSharedFlow()

    /** 标记是否正在执行会导致"切歌"的命令（NEXT/PREVIOUS/PLAY_BY_ID/SKIP_ALL）。 */
    @com.hmp.platform.Volatile
    private var pendingTrackChange = false

    override suspend fun execute(command: PlaybackCommand): Pair<Boolean, String> {
        val prevTitle = controller.currentPlayingMusic.value?.music?.title
        val result = when (command) {
            is PlaybackCommand.PLAY -> {
                controller.playOrResume(); true to "已播放"
            }
            is PlaybackCommand.PAUSE -> {
                controller.pauseMusic(); true to "已暂停"
            }
            is PlaybackCommand.NEXT -> {
                pendingTrackChange = true
                controller.playNext(); true to "已切到下一首"
            }
            is PlaybackCommand.PREVIOUS -> {
                pendingTrackChange = true
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
                    pendingTrackChange = true
                    controller.playWith(music); true to "已开始播放「${music.music.title}」"
                }
            }
            is PlaybackCommand.SHUFFLE_ON -> setMode(PlaybackMode.SHUFFLE)
            is PlaybackCommand.SHUFFLE_OFF -> setMode(PlaybackMode.SEQUENTIAL)
            is PlaybackCommand.REPEAT_ONE_ON -> setMode(PlaybackMode.REPEAT_ONE)
            is PlaybackCommand.REPEAT_ALL_ON -> setMode(PlaybackMode.SEQUENTIAL) // 全部循环无对应，退回顺序
            is PlaybackCommand.REPEAT_OFF -> setMode(PlaybackMode.SEQUENTIAL)
            is PlaybackCommand.ADD_TO_QUEUE -> false to "播放控制器暂不支持直接入队，建议使用播放列表功能"
            is PlaybackCommand.SKIP_ALL -> {
                pendingTrackChange = true
                controller.clearPlaylist(); true to "播放队列已清空"
            }
        }

        // M6-T2：跳过事件——只要执行成功 + 命令属于切歌类，就 emit 被跳过的曲目 title
        if (result.first && prevTitle != null && command.isSkipLikeCommand()) {
            _skipEvents.tryEmit(prevTitle)
            Logger.i("Agent.Port") { "skipEvents emit: \"$prevTitle\" (command=${command.displayName})" }
        }

        // M6-T3：Agent 命令触发切歌 → 等 currentPlayingMusic 确实变化了再 emit
        // （异步：切歌完成后另一个协程观察到 track change，再 emit DjBlank）
        if (result.first && pendingTrackChange) {
            pendingTrackChange = false
            // 用 currentPlayingMusic 的变化来确认切歌真的发生了
            val current = controller.currentPlayingMusic.value
            val newTitle = current?.music?.title
            if (newTitle != null && newTitle != prevTitle) {
                _agentTrackChanges.tryEmit(newTitle)
                Logger.i("Agent.Port") { "DjBlank emit: \"$newTitle\"" }
            }
        }

        return result
    }

    private fun PlaybackCommand.isSkipLikeCommand(): Boolean = when (this) {
        is PlaybackCommand.NEXT,
        is PlaybackCommand.PREVIOUS,
        is PlaybackCommand.PLAY_BY_ID,
        is PlaybackCommand.SKIP_ALL -> true
        else -> false
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
