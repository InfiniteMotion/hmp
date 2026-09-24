package com.hmp.domain.agent.runtime.sub.shared

/**
 * 电台曲目（跨 agent 共享模型）。
 *
 * 归属 `sub/shared/` 而非 `sub/radio/`：它被 [RadioSubAgent] 与 [RadioSession] 生产，
 * 同时被 HelloSubAgent（RADIO_STATUS 卡渲染）消费——放在任一 agent 包内都会
 * 制造另一方的反向依赖。
 */
data class RadioTrack(
    val musicId: Long,
    val title: String,
    /** 歌手（RADIO_STATUS 卡 ANCHOR 区显示用；缺省空串表示未知） */
    val artist: String = "",
    val why: String,
    val source: RadioTrackSource = RadioTrackSource.LOCAL,
)

enum class RadioTrackSource { LOCAL, CLOUD }
