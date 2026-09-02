package com.hmp.domain.agent.port

import com.hmp.domain.music.MusicInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * 播放控制端口（M3-M4：工具层不反向依赖 shared-ui，接口定义于此，三端适配由各平台完成）。
 *
 * 设计：密封指令集枚举，简化不同服务商返回文本匹配误差，工具解析为明确指令后交付三端实现。
 * 「相似歌单推荐」结果直接由工具基于 MusicRepository 产出，不占用此处指令集；此处只处理即时播放控制。
 */
sealed interface PlaybackCommand {
    val displayName: String

    data object PLAY : PlaybackCommand { override val displayName: String get() = "播放" }
    data object PAUSE : PlaybackCommand { override val displayName: String get() = "暂停" }
    data object NEXT : PlaybackCommand { override val displayName: String get() = "下一首" }
    data object PREVIOUS : PlaybackCommand { override val displayName: String get() = "上一首" }
    data class SEEK_TO(val positionMs: Long) : PlaybackCommand {
        override val displayName: String get() = "跳转至 ${positionMs / 1000} 秒"
    }
    data class PLAY_BY_ID(val musicId: Long) : PlaybackCommand {
        override val displayName: String get() = "播放曲目 $musicId"
    }
    data object SHUFFLE_ON : PlaybackCommand { override val displayName: String get() = "开启随机播放" }
    data object SHUFFLE_OFF : PlaybackCommand { override val displayName: String get() = "关闭随机播放" }
    data object REPEAT_ONE_ON : PlaybackCommand { override val displayName: String get() = "单曲循环开启" }
    data object REPEAT_ALL_ON : PlaybackCommand { override val displayName: String get() = "全部循环开启" }
    data object REPEAT_OFF : PlaybackCommand { override val displayName: String get() = "关闭循环" }
    data class ADD_TO_QUEUE(val musicId: Long) : PlaybackCommand {
        override val displayName: String get() = "追加入队 $musicId"
    }
    /** 跳过当前播放队列的所有曲目（M6-T2 电台重排时清空旧队列）。 */
    data object SKIP_ALL : PlaybackCommand { override val displayName: String get() = "清空播放队列" }
}

/**
 * 工具执行播放控制接口：工具只描述目标，不关心 platform 实现细节。
 * 三端各自注入实现（Android 绑定 Media3，Desktop 绑定 FFmpeg 播放引擎，iOS 绑定 AVPlayer）。
 */
interface PlaybackCommandPort {
    /** 执行给定指令，返回执行结果摘要（true=成功）+ human-readable 文本。 */
    suspend fun execute(command: PlaybackCommand): Pair<Boolean, String>

    /**
     * 用户跳过（NEXT/PREVIOUS/PLAY_BY_ID/SKIP_ALL）事件流：emit 被跳过曲目的 title。
     * M6-T2 MasterAgent 监听此流 → 累积 consecutiveCount → SkipDetected → 重排。
     * 默认 emptyFlow() stub；真实实现由 ControllerPlaybackCommandPort 在 execute() 内部 emit。
     */
    val skipEvents: Flow<String> get() = emptyFlow()

    /**
     * Agent 命令触发的切歌事件：emit 新曲目的 title。
     * M6-T3 MasterAgent 监听此流 → emit PresenceBus.DjBlank → 生成 DJ 衔接语。
     * 默认 emptyFlow() stub；真实实现由 ControllerPlaybackCommandPort 在 execute() 内部 emit。
     * 与 skipEvents 的区别：skipEvents emit"被跳过的旧曲目"，
     * trackChangeEvents emit"刚切过去的新曲目"——两者可能先后到达同一轮切歌。
     */
    val trackChangeEvents: Flow<String> get() = emptyFlow()
}

/**
 * 当前播放上下文提供者：工具「获取当前播放信息 / 相似推荐」需要当前 ID。
 * shared domain 无状态保存，由 UI 层提供当前状态；M3 工具依赖此端口。
 */
data class NowPlayingContext(
    val currentMusicId: Long?,
    val currentMusicInfo: MusicInfo?,
    val isPlaying: Boolean,
    val currentPositionMs: Long,
    val durationMs: Long,
)

interface NowPlayingContextProvider {
    /** 获取当前最新播放上下文。 */
    suspend fun getNowPlaying(): NowPlayingContext
}

/**
 * Fake 实现供测试：固定返回空/默认，不操作真实播放器。
 * M3-M4 单元测试不依赖真实播放器，用此 Fake。
 */
object FakePlaybackCommandPort : PlaybackCommandPort {
    override val skipEvents: Flow<String> = emptyFlow()
    override val trackChangeEvents: Flow<String> = emptyFlow()
    override suspend fun execute(command: PlaybackCommand): Pair<Boolean, String> =
        true to "（测试环境）[${command.displayName}] 已接收指令"
}

object FakeNowPlayingContextProvider : NowPlayingContextProvider {
    override suspend fun getNowPlaying(): NowPlayingContext = NowPlayingContext(
        currentMusicId = null,
        currentMusicInfo = null,
        isPlaying = false,
        currentPositionMs = 0L,
        durationMs = 0L,
    )
}
