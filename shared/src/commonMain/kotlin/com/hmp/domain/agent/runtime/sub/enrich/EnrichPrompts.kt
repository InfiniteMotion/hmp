package com.hmp.domain.agent.runtime.sub.enrich

import com.hmp.domain.agent.runtime.i18n.resolvePrompt
import com.hmp.domain.music.MusicInfo

/**
 * Enrich 的 **prompt 词表** —— 纯字符串构造，无状态。
 *
 * 为什么要独立成文件：这 290 行原先挤在 `EnrichSubAgent` 的 `companion object` 里
 * （占用全类近 1/4），与"5 轮编排流程"同处一个类。两者变化原因完全不同 ——
 * 改 prompt 措辞不该翻编排代码，改轮次顺序也不该翻词表 —— 却共享同一个文件与上下文。
 *
 * 留在 `companion object` 的只剩 [EnrichSubAgent] 的**流程参数**
 * （`GROUP_KEY_MIXED` / `CHUNK_SPLIT_SIZE`）：那才是编排自身的一部分。
 *
 * 全部函数都是**输入 → 字符串**的纯映射，因此这里的 `private` → 包内可见
 * 不构成可见性回退：**`private` 保护的是不变量，而无状态纯函数没有不变量**。
 */
internal object EnrichPrompts {

    private const val GENRE_CANDIDATES = """ROCK, POP, JAZZ, CLASSICAL, HIPHOP, ELECTRONIC, FOLK, RNB, METAL, COUNTRY, BLUES, REGGAE, PUNK, FUNK, SOUL, INDIE"""
    private const val MOOD_CANDIDATES = """HAPPY, SAD, ENERGETIC, CALM, ROMANTIC, ANGRY, LONELY, UPLIFTING, MYSTERIOUS, DARK, MELANCHOLY, HOPEFUL"""
    private const val SCENARIO_CANDIDATES = """WORKOUT, SLEEP, PARTY, DRIVING, STUDY, RELAX, DINNER, MEDITATION, FOCUS, TRAVEL, MORNING, NIGHT"""
    private const val LANGUAGE_CANDIDATES = """ENGLISH, CHINESE, JAPANESE, KOREAN, OTHERS"""
    private const val ERA_CANDIDATES = """SIXTIES, SEVENTIES, EIGHTIES, NINETIES, TWO_THOUSANDS, TWENTY_TENS, TWENTY_TWENTIES"""

    /**
     * 构建 Enrich system prompt。角色由 Master 注入（F5：Enrich 不自演化角色）。
     *
     * @param targetCoverage 目标覆盖率（注入到 {{target_coverage}} 占位符）
     * @param preferredLang Agent 语言偏好。null 回落旧三引号逻辑。
     * @param globalReplyLanguage 全局语言
     * @param userOverrides 用户覆盖 prompt Map
     */
    fun buildSystemPrompt(
        targetCoverage: Float,
        preferredLang: String? = null,
        globalReplyLanguage: String = "zh",
        userOverrides: Map<String, String> = emptyMap(),
    ): String {
        if (preferredLang != null) {
            val base = resolvePrompt(
                key = "enrich.system",
                preferredLang = preferredLang,
                globalReplyLanguage = globalReplyLanguage,
                userOverrides = userOverrides,
            )
            if (base.isNotBlank()) {
                return base.replace("{{target_coverage}}", (targetCoverage * 100).toInt().toString())
            }
        }
        // Fallback：原有三引号逻辑
        return """
你是一位专业的音乐编辑，精通各类音乐风格、流派发展历史和艺术家背景。

你将对一组歌曲进行 5 轮渐进式富化：枚举标签 → 枚举自检 → 自由文本（易）→ 自由文本（难）→ 总体反思。所有返回的内容都用于 AI 标签生成，可能不完全准确。

⚠️ 核心约束：不确定就不要编造。编造的错误信息比空着更糟糕。
- 对歌手背景/奖项/歌词/创作背景，只有 100% 确定的才能写
- genre/mood 枚举如果拿不准就少标（不超过候选值的一半）
- language/era 拿不准就返回 UNKNOWN

Master 当前任务：
- 目标覆盖率：${(targetCoverage * 100).toInt()}%
""".trimIndent()
    }

    // ---------- Round 0: 预热 ----------

    fun buildPreheatPrompt(artist: String): String = """
请用 2-3 段话介绍一下 "${artist}" 这位歌手/乐队：
- 音乐风格和流派定位
- 主要成就和代表性作品
- 风格演变或标志性的声音元素

这是后面分析 ${artist} 多首歌曲的共同背景，请尽量准确。如果这个歌手你不太熟悉，简短说你知道的就行，不要编造。
""".trimIndent()

    // ---------- Round 1: 枚举批量 ----------

    fun buildEnumPrompt(
        songs: List<MusicInfo>,
        groupKey: String,
        isMixedGroup: Boolean,
    ): String {
        val songsList = songs.mapIndexed { i, song ->
            "${i + 1}. ${song.music.title} — ${song.music.artist}"
        }.joinToString("\n")

        val groupContext = if (isMixedGroup) {
            "这是混合歌手组，每首歌独立分析即可。"
        } else {
            "这批 ${songs.size} 首都是 ${groupKey} 的歌曲（前面你已经介绍过这位歌手的背景），请确保 genre/mood 分布合理——既要有变化又整体符合 ${groupKey} 的风格定位。"
        }

        return """
$groupContext

现在为每首歌标注 5 个枚举字段。候选值：
- genre（选1-3个）：$GENRE_CANDIDATES
- mood（选2-4个）：$MOOD_CANDIDATES
- scenario（选1-3个）：$SCENARIO_CANDIDATES
- language（选1个）：$LANGUAGE_CANDIDATES
- era（选1个）：$ERA_CANDIDATES

规则：
1. genre 不要贪多，不确定的候选值不要加
2. language/era 拿不准就返回 UNKNOWN

歌曲列表：
$songsList

严格返回 JSON array，顺序一致：
[
  {"genre":["..."],"mood":["..."],"scenario":["..."],"language":"...","era":"..."},
  ...（共 ${songs.size} 个元素）
]

只返回 JSON，不要加任何解释或 markdown。
""".trimIndent()
    }

    // ---------- Round 1.5: 枚举自检 ----------

    fun buildEnumSelfCheckPrompt(
        songs: List<MusicInfo>,
        enumMap: Map<Long, EnumOnlyResult>,
        groupKey: String,
        isMixedGroup: Boolean,
    ): String {
        // 把 Round 1 结果以可读形式列出来
        val enumSummary = songs.mapIndexed { i, song ->
            val e = enumMap[song.music.id]
            "${i + 1}. ${song.music.title} → genre=${e?.genre?.joinToString("/") ?: "?"}, mood=${e?.mood?.joinToString("/") ?: "?"}, scenario=${e?.scenario?.joinToString("/") ?: "?"}, language=${e?.language ?: "?"}, era=${e?.era ?: "?"}"
        }.joinToString("\n")

        val checks = if (isMixedGroup) {
            """1. 每首歌的 language 是否跟歌手国籍/歌词语言大致匹配？
2. era 是否跟歌手出道年代大致匹配？"""
        } else {
            """1. genre/mood 分布是否跟你前面介绍的 ${groupKey} 的风格定位矛盾？
2. language 是否跟 ${groupKey} 的主要语言匹配？
3. era 分布是否合理——${groupKey} 活跃年代大致在哪个时期？有没有标到离谱的（比如 60 年代的歌手标了 2020s）？"""
        }

        return """
请回头检查你刚才为这 ${songs.size} 首歌标得枚举，有没有明显错误。

刚才的标注结果：
$enumSummary

请重点检查：
$checks

- 如果某首歌完全没问题，返回跟原来一样的就行
- 如果某首歌有问题，只修有问题的字段（比如 language 从 ENGLISH 改成 CHINESE）
- 不要动没问题的字段

严格返回 JSON array，顺序一致（即使只修了一首也要返回全部 ${songs.size} 个元素）：
[
  {"genre":["..."],"mood":["..."],"scenario":["..."],"language":"...","era":"..."},
  ...
]

只返回 JSON。
""".trimIndent()
    }

    // ---------- Round 2a: 自由文本 Easy ----------

    fun buildFreeTextEasyPrompt(
        songs: List<MusicInfo>,
        enumMap: Map<Long, EnumOnlyResult>,
        groupKey: String,
        isMixedGroup: Boolean,
    ): String {
        val songsWithContext = songs.mapIndexed { i, song ->
            val e = enumMap[song.music.id]
            "${i + 1}. ${song.music.title} — ${song.music.artist} [genre=${e?.genre?.joinToString(",") ?: "待补充"}, mood=${e?.mood?.joinToString(",") ?: "待补充"}]"
        }.joinToString("\n")

        return """
基于刚才的 genre/mood 判断，现在输出两个"相对容易"的自由文本字段。

歌曲列表：
$songsWithContext

字段说明：
- description: 1-2 句话描述歌曲主题和情感，参考上面的 genre/mood 来写
- singerIntroduce: 2-3 句话介绍歌手背景

${if (!isMixedGroup) "（前面你已经介绍过 ${groupKey} 的背景，可以基于那个来写 singerIntroduce）" else "（混合歌手组，每首歌的 singerIntroduce 请基于歌曲标题和风格合理推测）"}

严格返回 JSON array，顺序一致：
[
  {"description":"...","singerIntroduce":"..."},
  ...（共 ${songs.size} 个元素）
]

只返回 JSON，不要加任何解释或 markdown。
""".trimIndent()
    }

    // ---------- Round 2b: 自由文本 Facts ----------

    fun buildFreeTextFactsPrompt(
        songs: List<MusicInfo>,
        enumMap: Map<Long, EnumOnlyResult>,
        groupKey: String,
        isMixedGroup: Boolean,
    ): String {
        val songsList = songs.mapIndexed { i, song ->
            "${i + 1}. ${song.music.title} — ${song.music.artist}"
        }.joinToString("\n")

        return """
现在输出 4 个"容易编造"的事实字段。

⚠️ 核心规则：编造比空着更糟糕。只有你 100% 确定的内容才能写，否则返回 -暂无。

歌曲列表：
$songsList

字段说明：
- rewards: 歌手或歌曲获得的重要奖项，不确定返回 -暂无
- lyric: 1-2 句最广为人知的歌词，不确定返回 -暂无（不要编造歌词！）
- backgroundIntroduce: 创作背景灵感来源，不确定返回 -暂无
- relevantMusic: 相似歌曲（最多 3 首），逗号分隔字符串，不确定返回 -暂无

判断标准：
- rewards/lyric 你基本不可能 100% 确定 → 大概率全部返回 -暂无 是对的
- backgroundIntroduce/relevantMusic 如果能基于风格合理推测可以写，但不确定就 -暂无

严格返回 JSON array，顺序一致：
[
  {"rewards":"...","lyric":"...","backgroundIntroduce":"...","relevantMusic":"..."},
  ...（共 ${songs.size} 个元素）
]

只返回 JSON，不要加任何解释或 markdown。
""".trimIndent()
    }

    // ---------- Round 3: 总体反思 ----------

    fun buildReflectionPrompt(
        songs: List<MusicInfo>,
        draftResults: Map<Long, EnrichDraft>,
        groupKey: String,
        isMixedGroup: Boolean,
    ): String {
        val enumSummary = songs.mapIndexed { i, song ->
            val d = draftResults[song.music.id]
            "${i + 1}. ${song.music.title} — ${song.music.artist} → genre=${d?.genre?.joinToString("/") ?: "?"}, mood=${d?.mood?.joinToString("/") ?: "?"}, language=${d?.language ?: "?"}, era=${d?.era ?: "?"}"
        }.joinToString("\n")

        val textSummary = songs.mapIndexed { i, song ->
            val d = draftResults[song.music.id]
            "${i + 1}. description=\"${d?.description?.take(40) ?: ""}...\", singerIntroduce=\"${d?.singerIntroduce?.take(40) ?: ""}...\", lyric=${d?.lyric?.take(30) ?: "-暂无"}, rewards=${d?.rewards?.take(30) ?: "-暂无"}"
        }.joinToString("\n")

        val contextIntro = if (!isMixedGroup) {
            "前面你已经介绍过 ${groupKey} 的背景，请对比背景和标注是否矛盾。"
        } else {
            "这是混合歌手组，请独立检查每首歌。"
        }

        return """
最后做一次总体反思。$contextIntro

你为这 ${songs.size} 首歌完成了全部富化，下面是中间结果（枚举 + 自由文本）：

【枚举】
$enumSummary

【自由文本片段】
$textSummary

检查 5 类问题：
1. **枚举 vs 自由文本矛盾**：比如 mood=SAD 但 description 写"欢快的派对歌曲"
2. **预热知识 vs 枚举矛盾**：比如你说这是华语歌手但 language=ENGLISH；或者 era 标到歌手出道前
3. **同歌手分布异常**：genre/era 分布是否整体符合歌手风格（不要 5 首都标 POP 完全没变化）
4. **自由文本内部矛盾**：singerIntroduce 说"2018 出道新人"但 backgroundIntroduce 说"80 年代民歌运动"
5. **编造检测**：rewards/lyric/backgroundIntroduce 是不是看起来像编造？如果不确定应返回 -暂无

⚠️ 只返回需要修正的 patch，**没问题的字段别写**。patch 格式：
- index: 歌曲序号（从 1 开始）
- field: 要修的字段名（genre/mood/scenario/language/era/description/singerIntroduce/rewards/lyric/backgroundIntroduce/relevantMusic）
- fix: 修正后的值（枚举列表用逗号分隔字符串；自由文本直接文本；置空用 "-暂无"）

如果全部没问题，返回空数组 []

严格返回 JSON array：
[
  {"index":2,"field":"language","fix":"CHINESE"},
  {"index":4,"field":"description","fix":"修正后的描述文本..."},
  {"index":5,"field":"lyric","fix":"-暂无"}
]

只返回 JSON，不要加任何解释。
""".trimIndent()
    }
}
