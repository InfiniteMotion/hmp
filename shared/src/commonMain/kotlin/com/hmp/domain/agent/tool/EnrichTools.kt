package com.hmp.domain.agent.tool

import com.hmp.domain.music.MusicLabel
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
// T 阶段：Master 专属 enrich_* 工具（仅 MasterAgent 的 LLM 可见）
// 这些工具不进入 ToolNames.ALL 的完整清单（但作为独立集合注册），
// 也不在 chatbot 模式下创建——只有 ToolDependencies.masterAgentFacade 非空时才注册。
// ═══════════════════════════════════════════════════════════════════════

/**
 * MasterAgent 对工具层暴露的窄接口。
 *
 * 工具层不直接依赖 MasterAgent（避免循环依赖），而是通过这个接口反向调 Master 的生命周期方法。
 * MasterAgent 在初始化时实现此接口并注入 ToolDependencies。
 */
interface MasterAgentFacade {
    /** 当前富化进程是否活跃（已启动未 shutdown） */
    fun isEnrichActive(): Boolean

    /** 富化当前状态的摘要（供 enrich_status 查询；包含 DB 健康度快照，需要 suspend） */
    suspend fun enrichStatusSummary(): Map<String, String>

    /** 启动富化流程；如果已在运行则返回提示 */
    suspend fun startEnrich(targetCoverage: Float?)

    /** 暂停富化（Scheduler pause，进程保活但不处理新批次） */
    suspend fun pauseEnrich()

    /** 恢复富化 */
    suspend fun resumeEnrich()

    /** 重新扫描未覆盖歌曲并重置覆盖率目标 */
    suspend fun rescanEnrich(newTarget: Float?)
}

// ---------------- enrich_start ----------------

class EnrichStartTool(
    private val facade: MasterAgentFacade,
) : AgentTool {
    override val name = ToolNames.ENRICH_START
    override val description = "启动歌曲自动富化流程\nMaster 专属：触发后台 Enrich SubAgent 扫描未覆盖歌曲并调用 LLM 补全 genre/mood/scenario 标签\n成本高：单批次约 500-2000 Token；Scheduler 规则约束（电量≥50% 且 WiFi）"
    override val permissionLevel = ToolPermissionLevel.NOTIFY
    override val params = listOf(
        FloatParam(
            name = "target_coverage",
            description = "目标覆盖率（0.0-1.0），默认 0.9（覆盖 90% 歌曲）",
            required = false,
            min = 0f,
            max = 1f,
        ),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        if (facade.isEnrichActive()) {
            return ToolResult.success("富化进程已在运行中，请用 enrich_status 查看进度")
        }
        val target = args.optionalFloat("target_coverage")
        facade.startEnrich(target)
        return ToolResult.success("已启动富化流程（targetCoverage=${target ?: 0.9f}）；后台 Enrich SubAgent 将开始扫描未覆盖歌曲")
    }
}

// ---------------- enrich_pause ----------------

class EnrichPauseTool(
    private val facade: MasterAgentFacade,
) : AgentTool {
    override val name = ToolNames.ENRICH_PAUSE
    override val description = "暂停富化进程（Scheduler pause，进程保活但不处理新批次）\n可随时用 enrich_resume 恢复\n写入操作，影响后台 Agent 状态"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = emptyList<ToolParam>()

    override suspend fun run(args: ToolArgs): ToolResult {
        if (!facade.isEnrichActive()) {
            return ToolResult.success("富化进程未运行，无需暂停")
        }
        facade.pauseEnrich()
        return ToolResult.success("已暂停富化进程；可用 enrich_resume 恢复")
    }
}

// ---------------- enrich_resume ----------------

class EnrichResumeTool(
    private val facade: MasterAgentFacade,
) : AgentTool {
    override val name = ToolNames.ENRICH_RESUME
    override val description = "恢复富化进程（Scheduler resume，继续处理批次）\n写入操作，影响后台 Agent 状态"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = emptyList<ToolParam>()

    override suspend fun run(args: ToolArgs): ToolResult {
        if (!facade.isEnrichActive()) {
            return ToolResult.success("富化进程未运行；请先用 enrich_start 启动")
        }
        facade.resumeEnrich()
        return ToolResult.success("已恢复富化进程")
    }
}

// ---------------- enrich_status ----------------

class EnrichStatusTool(
    private val facade: MasterAgentFacade,
) : AgentTool {
    override val name = ToolNames.ENRICH_STATUS
    override val description = "查询富化进程当前状态（覆盖率、已处理数、Scheduler 状态、Token 剩余）\n只读操作，无副作用"
    override val permissionLevel = ToolPermissionLevel.SILENT
    override val params = emptyList<ToolParam>()

    override suspend fun run(args: ToolArgs): ToolResult {
        val isActive = facade.isEnrichActive()
        val summary = facade.enrichStatusSummary()
        val header = if (isActive) "富化进程运行中" else "富化进程未运行"
        val detail = summary.entries.joinToString("\n") { "  ${it.key}: ${it.value}" }
        return ToolResult.success("$header\n$detail", detail)
    }
}

// ---------------- enrich_rescan ----------------

class EnrichRescanTool(
    private val facade: MasterAgentFacade,
) : AgentTool {
    override val name = ToolNames.ENRICH_RESCAN
    override val description = "重新扫描所有歌曲并重置覆盖率目标（跳过已富化的歌曲，只处理标签缺失的）\n可选指定新的目标覆盖率（0.0-1.0）\n成本中等：触发 Master Agent 重新拉取未覆盖列表"
    override val permissionLevel = ToolPermissionLevel.NOTIFY
    override val params = listOf(
        FloatParam(
            name = "new_target_coverage",
            description = "新的目标覆盖率（0.0-1.0），保留默认值则沿用之前的目标",
            required = false,
            min = 0f,
            max = 1f,
        ),
    )

    override suspend fun run(args: ToolArgs): ToolResult {
        val newTarget = args.optionalFloat("new_target_coverage")
        facade.rescanEnrich(newTarget)
        return ToolResult.success("已触发富化重扫描${newTarget?.let { "（新目标覆盖率=$it）" } ?: ""}")
    }
}

/** 缺失的 FloatParam——ToolSpec 里没定义，这里补上 */
class FloatParam(
    override val name: String,
    override val required: Boolean = true,
    override val description: String? = null,
    val min: Float? = null,
    val max: Float? = null,
) : ToolParam {
    override val schemaValue: JsonObject = buildJsonObject {
        put("type", "number")
        if (!description.isNullOrBlank()) put("description", description)
        put("title", name)
        if (min != null) put("minimum", min.toDouble())
        if (max != null) put("maximum", max.toDouble())
    }
}
