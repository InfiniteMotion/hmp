package com.hmp.domain.agent.port

import co.touchlab.kermit.Logger
import com.hmp.platform.Volatile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.time.TimeSource

/**
 * 一首歌的结局。
 *
 * 这是**观测事实**，不含任何判断 —— 「不喜欢」是模型的事，这里只记「用户做了什么、播了多久」。
 * 见 `docs/7_x/agent-radio-spec.md` §2.4。
 */
enum class TrackOutcome {
    /** 自然播完 */
    COMPLETED,

    /** 点下一曲切走 */
    SKIPPED_NEXT,

    /** 点上一曲切走 */
    SKIPPED_PREV,

    /** 切去播别的曲目（点播 / 心动模式 / 改播放模式等） */
    SWITCHED_AWAY,

    /** 停止播放 / 播放器释放 */
    STOPPED,
}

/**
 * 一首歌的结算事件 —— 观测面唯一的事件。
 *
 * 「听完」与「跳过」由同一个事件表达，靠 [outcome] 区分；**跳过必须带 [playedMs]**，
 * 否则分不清「刚开就跳」和「快听完才换」，判断会系统性失准（spec §2.2）。
 *
 * @param atMs 单调时钟毫秒，由总线盖戳（各端不必自带时钟）
 */
data class TrackSettledEvent(
    val musicId: Long,
    val title: String,
    val outcome: TrackOutcome,
    val playedMs: Long,
    val totalMs: Long,
    val atMs: Long,
) {
    /** 已播比例 0.0-1.0；总时长未知时按 0 处理。 */
    val playedRatio: Double =
        if (totalMs > 0) (playedMs.toDouble() / totalMs).coerceIn(0.0, 1.0) else 0.0
}

/** 暂停 / 继续。不触发判断，但要进上下文（S5 需要它区分「中途离开」与「不喜欢」）。 */
data class PauseEvent(
    /** true = 继续播放，false = 暂停 */
    val resumed: Boolean,
    val atMs: Long,
)

/**
 * 观测面接收端 —— **由三端播放控制器调用**。
 *
 * 埋点必须下沉到控制器：播放页是直连控制器的，只在 ViewModel 层统计是瞎的（spec §2.2）。
 */
interface PlaybackObservationSink {
    /** 一首歌播完或被切走时结算一次。 */
    fun onTrackSettled(
        musicId: Long,
        title: String,
        outcome: TrackOutcome,
        playedMs: Long,
        totalMs: Long,
    )

    /** 播放/暂停状态变化。 */
    fun onPauseStateChanged(resumed: Boolean)
}

/**
 * 观测总线：`sink` 与 `Flow` 的合体，shared 侧唯一实例（Koin single）。
 *
 * 用 `tryEmit` 而非 `emit`：控制器侧可能在非挂起上下文调用，且不能因为总线慢而阻塞播放。
 * 缓冲区足够大（结算事件最密也就是连跳几首），溢出丢弃优于阻塞。
 */
class PlaybackObservationBus : PlaybackObservationSink {

    private val origin = TimeSource.Monotonic.markNow()
    private fun nowMs(): Long = origin.elapsedNow().inWholeMilliseconds

    private val _trackSettled = MutableSharedFlow<TrackSettledEvent>(extraBufferCapacity = 32)
    val trackSettled: SharedFlow<TrackSettledEvent> = _trackSettled.asSharedFlow()

    private val _pauseEvents = MutableSharedFlow<PauseEvent>(extraBufferCapacity = 8)
    val pauseEvents: SharedFlow<PauseEvent> = _pauseEvents.asSharedFlow()

    @Volatile
    private var mutedUntilMs = 0L

    /**
     * 静默一小段时间：**这是我们自己的编排，不是用户行为**。
     *
     * 电台换批走 `REPLACE_QUEUE` 时，播放器内部会再走一次切歌流程，于是产生一条
     * 「已播 0% 被切走」的假结算 —— 而 0% 恰好是模型眼里最强的负反馈信号，
     * 不滤掉就会污染判断。真机日志里见过：同一首歌 1ms 内被结算两次（11% 与 0%）。
     *
     * 用静默窗口而不是「播放时长过短就丢」，是因为后者会误伤真实用户的一秒内连切。
     * 这里语义是精确的：这段时间内发生的一切都是我们自己造成的。
     *
     * @param durationMs 建议 2000ms —— 覆盖一次 REPLACE_QUEUE 引起的连锁切歌
     */
    fun muteFor(durationMs: Long) {
        val until = nowMs() + durationMs
        if (until > mutedUntilMs) mutedUntilMs = until
    }

    private fun muted(): Boolean = nowMs() < mutedUntilMs

    override fun onTrackSettled(
        musicId: Long,
        title: String,
        outcome: TrackOutcome,
        playedMs: Long,
        totalMs: Long,
    ) {
        if (muted()) {
            Logger.d { "观测静默中，丢弃结算《$title》$outcome（编排副作用）" }
            return
        }
        _trackSettled.tryEmit(
            TrackSettledEvent(
                musicId = musicId,
                title = title,
                outcome = outcome,
                playedMs = playedMs,
                totalMs = totalMs,
                atMs = nowMs(),
            )
        )
    }

    override fun onPauseStateChanged(resumed: Boolean) {
        if (muted()) return   // 编排引起的假暂停/继续，同样丢掉
        _pauseEvents.tryEmit(PauseEvent(resumed = resumed, atMs = nowMs()))
    }
}
