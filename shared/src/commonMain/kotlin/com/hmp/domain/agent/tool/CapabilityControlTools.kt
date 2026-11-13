package com.hmp.domain.agent.tool

import com.hmp.domain.agent.port.ToolPermissionLevel
import com.hmp.domain.agent.tool.spec.AgentTool
import com.hmp.domain.agent.tool.spec.FloatParam
import com.hmp.domain.agent.tool.spec.StringParam
import com.hmp.domain.agent.tool.spec.ToolArgs
import com.hmp.domain.agent.tool.spec.ToolNames
import com.hmp.domain.agent.tool.spec.ToolParam
import com.hmp.domain.agent.tool.spec.ToolRegistry
import com.hmp.domain.agent.tool.spec.ToolResult

/**
 * 能力控制工具（2026-11-11）。
 *
 * 背景：`MasterAgent.builtinIntents` 短路已删除，电台 / 富化的启停不再有本地规则兜底，
 * 只能由 LLM 在对话中调用这些工具完成。
 *
 * 为什么用回调而不是直接依赖 `runtime/`：与 [bindCapabilityTools] 同一套做法 ——
 * 依赖方向恒定为 `tool → tool/spec`，真实方法由 `MasterAgent.initialize()` 绑定。
 */
fun ToolRegistry.bindCapabilityControlTools(
    radioStart: suspend (seed: String?) -> String,
    radioStop: suspend () -> String,
    radioResume: suspend () -> String,
    enrichStart: suspend (targetCoverage: Float?) -> String,
    enrichStop: suspend () -> String,
    enrichPause: suspend () -> String,
    enrichResume: suspend () -> String,
    enrichRescan: suspend (targetCoverage: Float?) -> String,
) {
    registerIfAbsent(
        controlTool(
            name = ToolNames.RADIO_START,
            description = "开启电台：按风格、歌手或曲目种子生成连续播放队列。" +
                "用户想听某种风格 / 某人的歌时调用；已在播时会复用当前电台。" +
                "单纯切歌或暂停用 playback_control，不要用本工具。",
            params = listOf(
                StringParam(
                    name = "seed",
                    required = false,
                    description = "风格或曲目种子（如「爵士」「周杰伦」）；不传则按当前播放与画像自动选",
                ),
            ),
        ) { args -> radioStart(args.optionalString("seed")) },
    )
    registerIfAbsent(
        controlTool(
            name = ToolNames.RADIO_STOP,
            description = "关闭电台（终态）。只在用户明确说「关电台」「停电台」时调用；" +
                "只是想让声音停下请用 playback_control(pause)。",
            permissionLevel = ToolPermissionLevel.NOTIFY,
        ) { radioStop() },
    )
    registerIfAbsent(
        controlTool(
            name = ToolNames.RADIO_RESUME,
            description = "继续播放已暂停的电台。用户说「继续电台」时调用；普通播放用 playback_control(play)。",
            permissionLevel = ToolPermissionLevel.NOTIFY,
        ) { radioResume() },
    )
    registerIfAbsent(
        controlTool(
            name = ToolNames.ENRICH_START,
            description = "启动曲库富化（补齐标签与画像）。用户说「开始富化」「补一下标签」时调用。" +
                "返回只代表已受理，进度用 capability_status 查。",
            params = listOf(
                FloatParam(
                    name = "targetCoverage",
                    required = false,
                    description = "目标覆盖率 0.5–1.0；不传则用当前设置",
                    min = 0.5f,
                    max = 1.0f,
                ),
            ),
        ) { args -> enrichStart(args.optionalFloat("targetCoverage")) },
    )
    registerIfAbsent(
        controlTool(
            name = ToolNames.ENRICH_STOP,
            description = "停止曲库富化。用户说「停止富化」时调用",
            permissionLevel = ToolPermissionLevel.NOTIFY,
        ) { enrichStop() },
    )
    registerIfAbsent(
        controlTool(
            name = ToolNames.ENRICH_PAUSE,
            description = "暂停曲库富化（保留进度）。用户说「暂停富化」时调用",
            permissionLevel = ToolPermissionLevel.NOTIFY,
        ) { enrichPause() },
    )
    registerIfAbsent(
        controlTool(
            name = ToolNames.ENRICH_RESUME,
            description = "恢复已暂停的曲库富化。用户说「继续富化」时调用",
            permissionLevel = ToolPermissionLevel.NOTIFY,
        ) { enrichResume() },
    )
    registerIfAbsent(
        controlTool(
            name = ToolNames.ENRICH_RESCAN,
            description = "重扫未覆盖的曲库并重置覆盖率目标；富化没在跑时会直接启动。用户说「重扫」「重新补标签」时调用",
            params = listOf(
                FloatParam(
                    name = "targetCoverage",
                    required = false,
                    description = "目标覆盖率 0.5–1.0；不传则沿用当前设置",
                    min = 0.5f,
                    max = 1.0f,
                ),
            ),
        ) { args -> enrichRescan(args.optionalFloat("targetCoverage")) },
    )
}

private fun controlTool(
    name: String,
    description: String,
    params: List<ToolParam> = emptyList(),
    permissionLevel: ToolPermissionLevel = ToolPermissionLevel.CONFIRM,
    run: suspend (ToolArgs) -> String,
): AgentTool = object : AgentTool {
    override val name = name
    override val description = description
    override val params = params
    override val permissionLevel = permissionLevel

    override suspend fun run(args: ToolArgs): ToolResult = ToolResult.success(run(args))
}
