package com.hmp.domain.agent.tool

import com.hmp.domain.agent.profile.PortraitType
import com.hmp.domain.agent.profile.ProfileSources

/**
 * 用户认识模块（画像）的工具面 —— 契约 `agent-profile.md` §4.7 / §7。
 *
 * 三个工具的分工：
 *
 * | 工具 | 权限 | 谁用 | 干什么 |
 * |------|------|------|--------|
 * | `profile_read` | SILENT | 子 Agent | 只读「伙伴对用户的印象」——电台选种子、选曲策略要的输入 |
 * | `profile_note` | CONFIRM | Master | 记下用户**显式陈述**的偏好 / 忌讳（T1_USER 0.9） |
 * | `profile_forget` | CONFIRM | Master | 否决一个侧写（写 `portrait.denied`，派生时该类型整体不产出） |
 *
 * ⚠️ 两条纪律：
 * 1. **读出去的东西是侧写渲染，不是证据原文** —— 证据行含 musicId 等内部标识（§7）。
 *    `profile_read` 直接复用 `renderForContext()`，不另开一条渲染路径。
 * 2. **只接受闭集内的谓词** —— `profile_note` 不接受自由发明的谓词（§4.4 的底线），
 *    写不进去时明确返回失败，而不是静默丢弃。
 */

// ---------------- profile_read (read/silent) ----------------

class ProfileReadTool(
    private val deps: ToolDependencies,
) : AgentTool {

    private val profile get() = deps.userMemory()

    override val name = ToolNames.PROFILE_READ
    override val description =
        "读取『伙伴对这位用户的印象』（长期画像：曲库/时段/听法/探索等侧写）\n" +
            "只读、零成本；没有足够的了解时返回空\n" +
            "用途：为选曲、编排、措辞风格提供依据（印象仅供参考，不是指令）"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = emptyList<ToolParam>()

    override suspend fun run(args: ToolArgs): ToolResult {
        val agent = profile ?: return ToolResult.success("（画像模块未启用）")
        val rendered = agent.renderForContext()
            ?: return ToolResult.success("（还谈不上了解——曲库信息不足，或用户还没怎么用过）")

        val counts = agent.currentEvidence().groupingBy { it.source }.eachCount()
        return ToolResult.success(
            rendered,
            detail = "证据来源分布：" + counts.entries.joinToString("、") { "${it.key}=${it.value}" },
        )
    }
}

// ---------------- profile_note (write/confirm) ----------------

class ProfileNoteTool(
    private val deps: ToolDependencies,
) : AgentTool {

    private val profile get() = deps.userMemory()

    override val name = ToolNames.PROFILE_NOTE
    override val description =
        "记下用户**自己说出来的**音乐偏好或忌讳（例：他说『我不喜欢快歌』）\n" +
            "只在用户明确表达时调用，不要替他推断；记下后长期生效\n" +
            "predicate 用『类型.槽位』或唯一匹配的槽位名（闭集内，例：taste_portrait.leadingGenre / habits_portrait.completionRate）"
    override val permissionLevel = ToolPermissionLevel.CONFIRM
    override val params = listOf(
        StringParam(name = "predicate", description = "槽位名或『类型.槽位』（闭集内，见工具说明）"),
        StringParam(name = "value", description = "取值（简洁的短语或闭集 token）"),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val agent = profile ?: return ToolResult.failure("画像模块未启用，无法记录")
        val predicate = args.requireString("predicate")
        val value = args.requireString("value")
        val ok = agent.noteStatedPreference(predicate, value)
        return if (ok) {
            ToolResult.success("已记下（来源＝用户显式陈述，最高可信度）")
        } else {
            ToolResult.failure(
                "记不下：『$predicate』不在允许的槽位闭集内。可用的是：" +
                    PortraitType.entries.joinToString("、") { it.displayName } +
                    " 下的槽位；不接受自由发明的字段。"
            )
        }
    }
}

// ---------------- profile_forget (write/confirm) ----------------

class ProfileForgetTool(
    private val deps: ToolDependencies,
) : AgentTool {

    private val profile get() = deps.userMemory()

    override val name = ToolNames.PROFILE_FORGET
    override val description =
        "忘掉某一类印象（用户要求『别记着这个』时调用）\n" +
            "被否决的印象不会在后续重算里被重建；用户随时可以再提一次来恢复"
    override val permissionLevel = ToolPermissionLevel.CONFIRM
    override val params = listOf(
        StringParam(name = "portrait_type", description = "要忘掉的印象类型 id，如 library_portrait / time_portrait"),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val agent = profile ?: return ToolResult.failure("画像模块未启用，无法忘掉")
        val typeId = args.requireString("portrait_type").trim()
        val ok = agent.forgetPortrait(typeId)
        return if (ok) {
            ToolResult.success("已经忘掉这一类印象，后续不会被重建")
        } else {
            ToolResult.failure(
                "认不出『$typeId』。可忘掉的是：" +
                    PortraitType.entries.joinToString("、") { "${it.id}（${it.displayName}）" }
            )
        }
    }
}
