package com.hmp.domain.agent.tool
import com.hmp.domain.agent.tool.spec.AgentTool
import com.hmp.domain.agent.tool.spec.ToolResult
import com.hmp.domain.agent.tool.spec.ToolParam
import com.hmp.domain.agent.tool.spec.ToolNames
import com.hmp.domain.agent.tool.spec.ToolArgs
import com.hmp.domain.agent.tool.spec.StringParam
import com.hmp.domain.agent.tool.spec.LongParam
import com.hmp.domain.agent.tool.spec.EnumParam

import com.hmp.domain.agent.port.ToolPermissionLevel

import com.hmp.domain.music.MusicLabel
import kotlinx.coroutines.flow.first

// ---------------- song_tag_user_add (write/confirm) ----------------

class SongTagUserAddTool(
    deps: ToolDependencies,
) : AgentTool {
    private val repo = deps.musicRepository

    override val name = ToolNames.SONG_TAG_USER_ADD
    override val description = "为某首歌添加 USER 源标签（永不被模型覆盖，用于用户主动修正）\n写操作，修改歌曲标签元数据\n区分 source：LLM 模型富化用 song_enrich_llm，用户修正用本工具"
    override val permissionLevel = ToolPermissionLevel.CONFIRM
    override val params = listOf(
        LongParam(name = "music_id", description = "歌曲ID", min = 1),
        StringParam(name = "tag_name", description = "标签名（支持中文别名如 爵士/摇滚/深夜/运动）"),
        EnumParam(
            name = "category",
            allowed = listOf("genre", "mood", "scenario"),
            description = "标签分类（缺省 genre）",
            required = false,
        ),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val musicId = args.requireLong("music_id")
        val tagName = args.requireString("tag_name")
        val category = args.optionalString("category") ?: "genre"
        val matched = labelAliasesFor(tagName)
        if (matched.isEmpty()) return ToolResult.failure("未识别标签「$tagName」，请用中文别名或英文枚举")
        val label = matched.first()
        val cat = when (category) {
            "mood" -> com.hmp.domain.enum.LabelCategory.MOOD
            "scenario" -> com.hmp.domain.enum.LabelCategory.SCENARIO
            else -> com.hmp.domain.enum.LabelCategory.GENRE
        }
        val info = repo.getMusicInfoById(musicId).first()
            ?: return ToolResult.failure("歌曲 $musicId 不存在")
        repo.addUserMusicLabel(MusicLabel(musicId, cat, label), confidence = 1.0)
        return ToolResult.success("已为「${info.music.title}」添加 USER 标签 ${label.displayCn()}（$category）")
    }
}

// ---------------- song_tag_user_remove (write/confirm) ----------------
// 需底层 removeUserMusicLabel / removeMusicLabel DAO 删除方法——批次 B 首次编译时若缺失将报错，届时补。

class SongTagUserRemoveTool(
    deps: ToolDependencies,
) : AgentTool {
    private val repo = deps.musicRepository

    override val name = ToolNames.SONG_TAG_USER_REMOVE
    override val description = "删除某首歌的 USER 源标签\n写操作，修改歌曲标签元数据\n只删除 USER 源标签；LLM 富化标签不受影响"
    override val permissionLevel = ToolPermissionLevel.CONFIRM
    override val params = listOf(
        LongParam(name = "music_id", description = "歌曲ID", min = 1),
        StringParam(name = "tag_name", description = "要删除的标签名"),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val musicId = args.requireLong("music_id")
        val tagName = args.requireString("tag_name")
        val matched = labelAliasesFor(tagName)
        if (matched.isEmpty()) return ToolResult.failure("未识别标签「$tagName」")
        val label = matched.first()
        val info = repo.getMusicInfoById(musicId).first()
            ?: return ToolResult.failure("歌曲 $musicId 不存在")
        // 假设底层已有 removeUserMusicLabel(musicId, label)——编译报错时补
        repo.removeUserMusicLabel(musicId, label)
        return ToolResult.success("已从「${info.music.title}」移除 USER 标签 ${label.displayCn()}")
    }
}

// ═══════════════════════════════════════════════════════════════════════
// T 阶段 enrich_* 工具已移除（重构为 MasterAgent 内建意图路由，走原生生命周期方法）
// 此处保留 song_tag_user_add / song_tag_user_remove 两个通用标签工具。
//
// 注：FloatParam 已移至 `tool/spec/ToolSpec.kt`——它是 ToolParam（sealed）的子类，
// 必须与 sealed 接口同包；且当前无调用点（预留能力）。
// ═══════════════════════════════════════════════════════════════════════
