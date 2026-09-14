package com.hearablemusic.player.ui.chat

import co.touchlab.kermit.Logger
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hmp.domain.agent.port.CommandSource
import com.hmp.domain.agent.port.NowPlayingContext
import com.hmp.domain.agent.port.NowPlayingContextProvider
import com.hmp.domain.agent.port.PauseEvent
import com.hmp.domain.agent.port.PlaybackCommand
import com.hmp.domain.agent.port.PlaybackCommandPort
import com.hmp.domain.agent.port.PlaybackObservationBus
import com.hmp.domain.agent.port.TrackSettledEvent
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
    /**
     * 观测面总线（shared 侧单例）。三端播放控制器在会话结算点往里报事实，
     * 这里只做转发 —— 不走命令路径，因此能覆盖通知栏 / 耳机按键 / UI 直连控制器等来源。
     */
    private val observationBus: PlaybackObservationBus,
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

    // ── 观测面：直接转发总线，不做任何加工（模型要看的是原始事实） ──
    override val trackSettled: Flow<TrackSettledEvent> get() = observationBus.trackSettled
    override val pauseEvents: Flow<PauseEvent> get() = observationBus.pauseEvents

    /** 标记是否正在执行会导致"切歌"的命令（NEXT/PREVIOUS/PLAY_BY_ID/SKIP_ALL）。 */
    @com.hmp.platform.Volatile
    private var pendingTrackChange = false

    override suspend fun execute(
        command: PlaybackCommand,
        source: CommandSource,
    ): Pair<Boolean, String> {
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
            is PlaybackCommand.ADD_TO_QUEUE -> {
                val music = musicRepository.getMusicInfoById(command.musicId).first()
                if (music == null) false to "未找到该曲目"
                else {
                    controller.addToPlaylist(music); true to "已追加到队列"
                }
            }
            is PlaybackCommand.SKIP_ALL -> {
                pendingTrackChange = true
                controller.clearPlaylist(); true to "播放队列已清空"
            }
            is PlaybackCommand.REPLACE_QUEUE -> {
                val appended = replaceQueueWith(command.musicIds)
                val wanted = command.musicIds.size
                if (appended < wanted) {
                    Logger.w("Agent.Port") {
                        "REPLACE_QUEUE 未全量入队：请求 $wanted 首，实际追加 $appended 首（部分 id 解析失败被丢弃）"
                    }
                }
                (appended >= wanted) to "播放队列已替换 ($appended/$wanted 首)"
            }
        }

        // M6-T2：跳过事件——只要执行成功 + 命令属于切歌类，就 emit 被跳过的曲目 title。
        // source=AGENT_INTERNAL（子 Agent 内部原子操作，如电台重排）吞掉事件，
        // 否则 MasterAgent 会把 Agent 自己的操作误判为用户跳过意图 → 触发无意义的重排循环。
        if (source == CommandSource.USER && result.first && prevTitle != null && command.isSkipLikeCommand()) {
            _skipEvents.tryEmit(prevTitle)
            Logger.i("Agent.Port") { "skipEvents emit: \"$prevTitle\" (command=${command.displayName})" }
        }

        // M6-T3：Agent 命令触发切歌 → 等 currentPlayingMusic 确实变化了再 emit
        // （异步：切歌完成后另一个协程观察到 track change，再 emit DjBlank）
        if (source == CommandSource.USER && result.first && pendingTrackChange) {
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

    /**
     * REPLACE_QUEUE 实现：**替换除在播曲之外的整条队列**。
     *
     * 刻意不走 clearPlaylist + 重建 —— 那样会把包括当前曲在内的整条队列拆掉，
     * 中间必然存在打断窗口，之后还得靠 playAt / seekTo 去"恢复"，仍然会顿一下。
     * 这里改为：移除尾部条目 → 清理与预期撞车的前缀条目（addToPlaylist 整队列
     * 去重，见下方注释）→ 追加新列表。当前曲本身不碰，播放与 position 天然不受影响。
     *
     * 当前没有在播曲目时才退化为「整体替换 + 从第一首开始播」。
     *
     * 操作序列：移除 idx+1 之后的条目 → 移除前缀撞车条目 → 逐个 addToPlaylist 追加
     * （**不能用 addAllToPlaylistInOrder**：它会整体替换 + 索引归零 + 从头播，当前曲一样被顶掉）
     */
    private suspend fun replaceQueueWith(musicIds: List<Long>): Int {
        val current = controller.currentPlayingMusic.value
        val idx = controller.currentIndex.value
        val hasCurrent = current != null && idx >= 0

        if (!hasCurrent) {
            // 当前没有在播 → 退化：整体替换并从第一首开始播
            controller.clearPlaylist()
            if (musicIds.isEmpty()) return 0
            val all = musicRepository.getMusicInfoByIds(musicIds)
            if (all.isNotEmpty()) {
                controller.addAllToPlaylistInOrder(all)
                controller.playAt(all.first())
            }
            return all.size
        }

        // ── 只动当前播放曲**之后**的条目 ──
        // 先移除尾部，再追加新列表。当前曲本身不碰，因此播放与 position 天然不受影响。
        val trailing = controller.currentPlaylist.value.drop(idx + 1)
        trailing.forEach { controller.removeFromPlaylist(it) }
        if (musicIds.isEmpty()) return 0

        val currentId = current.music.id
        val idToMusic = musicRepository.getMusicInfoByIds(musicIds).associateBy { it.music.id }
        val wanted = musicIds.filter { it != currentId }.toSet()
        // 追加前先清掉与本次预期撞车的旧条目。addToPlaylist 有**整队列去重**
        // （none { it.music.id == ... }），而尾部刚清完，剩余撞车条目只可能在
        // 当前曲**之前**的旧前缀里 —— 不清的话它们会把同 id 的新条目静默去重掉
        // （真机 00:14 实锤：两次替换分别丢 6/4 首，丢失集合 = 预期 ∩ 旧前缀）。
        // removeFromPlaylist 会按在播曲 id 重算索引，当前曲播放不受影响。
        controller.currentPlaylist.value
            .filter { it.music.id in wanted }
            .forEach { controller.removeFromPlaylist(it) }
        // 必须逐个 addToPlaylist，**不能用 addAllToPlaylistInOrder** ——
        // 那个方法名不副实：它会 `_currentPlaylist.value = playlist` + `currentIndex = 0`
        // + `playCurrentTrack()`，也就是整条替换并从第一首开始播，当前曲照样被顶掉。
        val appended = musicIds
            .filter { it != currentId }
            .mapNotNull { idToMusic[it] }
        appended.forEach { controller.addToPlaylist(it) }   // 真正的追加：不动索引、不触发播放
        // 返回实际追加数：解析不到的 id 会被静默丢弃（真机 22:57「列表好像没换上」的
        // 排查盲区 —— 此前端口报的是请求数，丢弃多少完全不可见）
        return appended.size
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
            // 队列指纹：电台重开时靠它判断"环境有没有被改动过"
            queueIds = controller.currentPlaylist.value.map { it.music.id },
        )
    }
}
