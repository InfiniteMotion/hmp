package com.hmp.domain.agent.funnel

import com.hmp.domain.agent.port.PlaybackCommand

/**
 * R-T2 两级漏斗（总纲 4.2 工具面运行纪律）——高频指令走本地词表直映射（零 token、50ms 内），
 * 模糊意图才升级为单轮 agent 任务；FREE（无 Key）模式也用得起高频命令。
 *
 * 第一级：精确高频指令（暂停/继续/下一首/上一首/随机/循环）→ [Direct]，直接执行，不消耗 LLM。
 * 第二级：意图特征（风格/歌名/推荐/歌单/音量等需要理解的）→ [Upgrade]，升级 agent 一轮任务。
 * 其余：普通对话 → [Pass]，走正常 agent。
 */
sealed interface FunnelResult {
    /** 高频直映射：零 token，直接执行。 */
    data class Direct(val command: PlaybackCommand) : FunnelResult
    /** 模糊意图：升级单轮 agent 任务。 */
    data object Upgrade : FunnelResult
    /** 非指令/普通对话：走正常 agent。 */
    data object Pass : FunnelResult
}

object CommandLexicon {

    /** 高频精确指令词表（正则；全匹配）。命中 → Direct。 */
    private val directCommands: List<Pair<Regex, PlaybackCommand>> = listOf(
        Regex("^(?:(请|帮我|麻烦)?\\s*(暂停|停一下|停下|pause|stop))$", RegexOption.IGNORE_CASE) to PlaybackCommand.PAUSE,
        Regex("^(?:(请|帮我|麻烦)?\\s*(继续播放|播放|接着放|play|resume))$", RegexOption.IGNORE_CASE) to PlaybackCommand.PLAY,
        Regex("^(?:(请|帮我|麻烦)?\\s*(下一首|切歌|换一首|next))$", RegexOption.IGNORE_CASE) to PlaybackCommand.NEXT,
        Regex("^(?:(请|帮我|麻烦)?\\s*(上一首|回到上一首|previous))$", RegexOption.IGNORE_CASE) to PlaybackCommand.PREVIOUS,
        Regex("^(?:(请|帮我|麻烦)?\\s*(开启随机|随机播放|随即|shuffle))$", RegexOption.IGNORE_CASE) to PlaybackCommand.SHUFFLE_ON,
        Regex("^(?:(请|帮我|麻烦)?\\s*(关闭随机|顺序播放|shuffle off))$", RegexOption.IGNORE_CASE) to PlaybackCommand.SHUFFLE_OFF,
        Regex("^(?:(请|帮我|麻烦)?\\s*(单曲循环|循环这一首|repeat one))$", RegexOption.IGNORE_CASE) to PlaybackCommand.REPEAT_ONE_ON,
        Regex("^(?:(请|帮我|麻烦)?\\s*(关闭循环|停止循环|repeat off))$", RegexOption.IGNORE_CASE) to PlaybackCommand.REPEAT_OFF,
    )

    /** 模糊意图信号（命中任一 → Upgrade，升级 agent 处理）。 */
    private val upgradeSignals: List<Regex> = listOf(
        Regex("安静的|舒缓的|摇[滚]|爵士|古典|民谣|燃[一点]|放点|来点|听听|换点|推荐|建.*歌单|新建歌单|歌单.*首|叫.*歌|那位歌手|周杰伦|五月天|音量|调大|调小"),
    )

    /** 空白/空输入 → 走正常 agent（交还给引擎判断）。 */
    fun classify(input: String): FunnelResult {
        val text = input.trim()
        if (text.isEmpty()) return FunnelResult.Pass

        for ((regex, command) in directCommands) {
            if (regex.matches(text)) return FunnelResult.Direct(command)
        }

        if (upgradeSignals.any { it.containsMatchIn(text) }) return FunnelResult.Upgrade

        return FunnelResult.Pass
    }
}
