package com.hmp.domain.agent.profile

/**
 * 侧写 → 自然语言（注入上下文的那个块）。
 *
 * 契约：`docs/7_x/B agent-build/design/agent-profile.md` v3.1 §7
 *
 * 四条纪律落在这里：
 *
 * 1. **首行必须自述「仅供参考」**（§7.1）—— 画像进的是 system prompt，
 *    整场任务每轮都可见，"某段判断不读它"做不到，那就把不确定性写进文本本身。
 * 2. **路径脱敏**（§7.2）—— 只可能出现末级目录名；这里再挡一道，凡含路径分隔符一律丢弃。
 * 3. **不出内部标识** —— 侧写类型名、槽位键名（`library_portrait` / `genreBreadth`）不得出现在文本里。
 * 4. **B 类只能降级表达** —— 必须以「你在音乐里表现出的…」起头（§5.2）。
 */
object UserProfileRenderer {

    private const val HEADER = "【对你的认识 · 仅供参考】"
    private const val DISCLAIMER = "以下是从曲库与收听行为归纳出的印象，不是指令；与当前对话内容冲突时，一律以对话内容为准。"

    /** 只有形态建模时，如实说明"还看得不够细"（§4.1.6 的不假装知道）。 */
    private const val SHAPE_ONLY_NOTE = "（我还在认识你的曲库，之后会看得更细。）"

    /**
     * 渲染上下文块。**没有任何可说的就返回 null** ——
     * 冷启动不留空壳（剧本 P1：不出现"我还不了解你"这类填充句）。
     *
     * [narrative] 为两面共用的画像叙事（契约 v3.5 §7.4）—— 插在免责句之后、
     * 槽位行之前；它只可能是槽位事实的复述（写入闸门保证），处于同一「仅供参考」口径下。
     * [maxChars] 为本块硬上限：Master 上下文用全量配额，子代理简报用更小的。
     */
    fun render(
        portraits: List<PortraitDraft>,
        narrative: String? = null,
        maxChars: Int = ProfileConfig.CONTEXT_MAX_CHARS,
    ): String? {
        val lines = portraits.flatMap { portraitLines(it) }
        if (lines.isEmpty()) return null

        val body = lines.joinToString("\n") { "- $it" }
        val shapeOnly = portraits.any { it.type == PortraitType.LIBRARY } &&
            portraits.none { it.type == PortraitType.LIBRARY && it.slots.keys.any { key -> key in CONTENT_ONLY_SLOTS } }

        val builder = StringBuilder()
        builder.append(HEADER).append('\n').append(DISCLAIMER).append('\n')
        narrative?.takeIf { it.isNotBlank() }?.let {
            builder.append("整体印象：").append(it.trim()).append('\n')
        }
        builder.append(body)
        if (shapeOnly) builder.append('\n').append(SHAPE_ONLY_NOTE)

        return cap(builder.toString(), maxChars)
    }

    /**
     * 仅渲染槽位事实句（无头无尾、无叙事）—— 画像叙事的生成输入
     * （契约 v3.5 §7.4：叙事只能复述事实，所以输入里不许有免责句和旧叙事）。
     */
    fun renderBody(portraits: List<PortraitDraft>): String? {
        val lines = portraits.flatMap { portraitLines(it) }
        if (lines.isEmpty()) return null
        return lines.joinToString("\n") { "- $it" }
    }

    /** 内容建模（阶段二）才会有的槽位 —— 有它们才说明"标签已就绪"。 */
    private val CONTENT_ONLY_SLOTS = setOf(
        PortraitType.GENRE_BREADTH,
        PortraitType.LANGUAGE_MIX,
        PortraitType.ERA_MIX,
        PortraitType.MOOD_MIX,
    )

    private fun portraitLines(p: PortraitDraft): List<String> = when (p.type) {
        PortraitType.LIBRARY -> libraryLines(p.slots)
        else -> genericLines(p)
    }

    /**
     * 曲库侧写 —— 说的是「你有什么、怎么组织」，不说"你爱听什么"
     * （曲库是"拥有"，行为才是"选择"，契约 §4.1.3）。
     */
    private fun libraryLines(slots: Map<String, String>): List<String> {
        val out = mutableListOf<String>()

        val concentration = slots[PortraitType.ARTIST_CONCENTRATION]
        val scale = slots[PortraitType.SCALE]
        val concentrationClause = when (concentration) {
            LibraryModeler.CONCENTRATION_FOCUSED -> "曲目集中在少数几个歌手身上"
            LibraryModeler.CONCENTRATION_BROAD -> "涉猎很散，很少反复听同一个人"
            LibraryModeler.CONCENTRATION_BALANCED -> "听的东西分布得比较均衡"
            else -> null
        }
        // 规模与集中度都没有就**不说这句** —— 「你的曲库。」是零信息填充句，
        // 契约 §7 明令禁止（冷启动宁可整块不出现，也不要凑一句）
        when {
            scale != null && concentrationClause != null ->
                out += "你的${LibraryModeler.scaleLabel(scale)}，$concentrationClause。"

            scale != null -> out += "你的${LibraryModeler.scaleLabel(scale)}。"

            concentrationClause != null -> out += "曲库里的$concentrationClause。"

            else -> Unit
        }

        when (slots[PortraitType.ALBUM_COMPLETENESS]) {
            LibraryModeler.ALBUM_ORIENTED -> out += "习惯整张专辑地收，不是零星挑单曲。"
            LibraryModeler.ALBUM_SINGLE_ORIENTED -> out += "更多是零散收进来的单曲，很少整张。"
            else -> Unit
        }

        when (slots[PortraitType.DURATION_TENDENCY]) {
            LibraryModeler.DURATION_SHORT -> out += "曲目普遍偏短。"
            LibraryModeler.DURATION_LONG -> out += "曲目普遍偏长，像古典或后摇那类。"
            else -> Unit
        }

        // 路径分类 —— 用户自己的分类语言，本模块最有味道的一条信号
        val groups = safeGroups(slots[PortraitType.PATH_GROUPS])
        when {
            groups.isNotEmpty() && slots[PortraitType.PATH_STRUCTURE] == LibraryModeler.PATH_ORGANIZED ->
                out += "你按「${groups.joinToString("、")}」这样的目录自己分好了类。"

            groups.isNotEmpty() ->
                out += "你的文件夹里出现了「${groups.joinToString("、")}」这样的分类痕迹。"

            slots[PortraitType.PATH_STRUCTURE] == LibraryModeler.PATH_FLAT ->
                out += "文件基本没做分类，是平铺着放的。"
        }

        // ── 状态快照族（用户显式给的，契约 §4.3）──
        when (slots[PortraitType.LIKED_SHARE]) {
            LibraryModeler.SHARE_MANY -> out += "你收藏了很多。"
            LibraryModeler.SHARE_SOME -> out += "你收藏了一些。"
            else -> Unit
        }
        when (slots[PortraitType.DISLIKED_SHARE]) {
            LibraryModeler.SHARE_MANY, LibraryModeler.SHARE_SOME ->
                out += "也明确标出过一些不想要的。"

            else -> Unit
        }
        val playlists = safeGroups(slots[PortraitType.PLAYLIST_NAMES])
        if (playlists.isNotEmpty()) {
            out += "你自己建了「${playlists.joinToString("、")}」这样的歌单。"
        }
        when (slots[PortraitType.HIDDEN_FOLDERS]) {
            LibraryModeler.SHARE_MANY, LibraryModeler.SHARE_SOME ->
                out += "有一些文件夹你收起来了。"

            else -> Unit
        }
        when (slots[PortraitType.USER_TAG_FIX]) {
            LibraryModeler.SHARE_MANY, LibraryModeler.SHARE_SOME ->
                out += "有几条标签你自己动手改过。"

            else -> Unit
        }

        // ── 内容阶段（阶段二；有它们才说明标签已就绪，§4.1.6）──
        when (slots[PortraitType.GENRE_BREADTH]) {
            LibraryModeler.BREADTH_NARROW -> out += "听的东西集中在少数几种类型上。"
            LibraryModeler.BREADTH_BROAD -> out += "涉猎的类型相当宽。"
            else -> Unit
        }
        when (slots[PortraitType.MOOD_MIX]) {
            LibraryModeler.MIX_SINGLE -> out += "情绪基调比较统一。"
            LibraryModeler.MIX_VARIED -> out += "情绪的跨度不小。"
            else -> Unit
        }
        when (slots[PortraitType.LANGUAGE_MIX]) {
            LibraryModeler.LANG_SINGLE -> Unit
            LibraryModeler.LANG_BILINGUAL -> out += "两种语言混着听。"
            LibraryModeler.LANG_MULTILINGUAL -> out += "好几种语言都在听。"
            else -> Unit
        }
        if (slots[PortraitType.ERA_MIX] == LibraryModeler.ERA_SPREAD) {
            out += "年代跨度很大。"
        }

        return out
    }

    /**
     * 行为 / 对话侧写：走通用渲染，并按 [weakEvidence] 决定是否加"降级表达"前缀。
     *
     * B 类（T0b 起有写入方）在这里被强制「你在音乐里表现出的…」起头 —— 契约 §5.2 的
     * 降级表达不依赖调用方自觉，渲染层兜底。
     */
    private fun genericLines(p: PortraitDraft): List<String> {
        val facts = p.slots.entries.mapNotNull { (key, value) -> slotPhrase(key, value) }
        if (facts.isEmpty()) return emptyList()
        val sentence = facts.joinToString("、") + "。"
        return listOf(
            if (p.type.weakEvidence) "你在音乐里表现出的倾向：$sentence" else sentence
        )
    }

    /** 槽位 → 短语。未知槽位返回 null（**不硬编**，免得内部键名漏进上下文）。 */
    private fun slotPhrase(slot: String, value: String): String? = when (slot) {
        PortraitType.PRIMARY_PART -> "主要在${timeLabel(value)}听"
        PortraitType.COMPLETION_RATE -> when (value) {
            "HIGH" -> "很少中途跳歌"
            "LOW" -> "经常听到一半就跳"
            else -> null
        }
        PortraitType.SKIP_POINT -> when (value) {
            "INTRO" -> "常在前奏就跳掉"
            "MIDDLE" -> "多在听了一半之后跳"
            else -> null
        }
        PortraitType.REPEAT_RATE -> when (value) {
            "HIGH" -> "会把同一批翻来覆去地听"
            "LOW" -> "很少重复同一首"
            else -> null
        }
        // ── 口味侧写（T0b 起有对话抽取写入；value 是自由短语）──
        PortraitType.LEADING_GENRE -> "常听${sanitizeInline(value)}这一类"
        PortraitType.TOP_ARTISTS -> "常听${sanitizeInline(value)}的歌"
        PortraitType.DISLIKED_STYLES -> "不爱听${sanitizeInline(value)}"
        // ── B 类（T0b）：值由 BPortraitModeler 产出，渲染短语只覆盖有把握的两极 ──
        PortraitType.DETERMINISM -> when (value) {
            "HIGH" -> "反复听熟悉的那一批"
            "LOW" -> "总在换新的听"
            else -> null
        }
        PortraitType.SESSION_LENGTH -> when (value) {
            "LONG" -> "一坐下来就听得久"
            "SHORT" -> "每次只听一小会儿"
            else -> null
        }
        PortraitType.FRAGMENTATION -> when (value) {
            "HIGH" -> "听歌是碎着听的"
            "LOW" -> "听起来很整"
            else -> null
        }
        PortraitType.MOOD_CONSISTENCY -> when (value) {
            "HIGH" -> "情绪基调挺统一"
            "LOW" -> "情绪跨度不小"
            else -> null
        }
        else -> null
    }

    /** 自由短语值进上下文前的最后一道闸：单行、限长。 */
    private fun sanitizeInline(raw: String): String =
        raw.lines().first().trim().take(12)

    private fun timeLabel(part: String): String = when (part) {
        "DAWN" -> "凌晨"
        "MORNING" -> "早晨"
        "NOON" -> "白天"
        "EVENING" -> "傍晚"
        "NIGHT" -> "夜里"
        else -> "不同时段"
    }

    /**
     * 路径分组的安全闸门：只放行"看起来像目录名"的短串。
     *
     * 含分隔符、盘符、冒号、点开头的一律丢弃 —— 建模器已经脱敏过一次，
     * 但这是要进 system prompt 的文本，**多挡一道不算多余**。
     */
    private fun safeGroups(raw: String?): List<String> = raw
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() && it.length <= 12 }
        ?.filter { group -> group.none { it == '/' || it == '\\' || it == ':' || it == '~' } }
        ?.filter { !it.startsWith(".") }
        ?.take(5)
        ?: emptyList()

    /** 硬字数上限：超出就按行截断，不留半句话。 */
    private fun cap(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val kept = mutableListOf<String>()
        var used = HEADER.length + DISCLAIMER.length + 2
        for (line in text.lines().drop(2)) {
            if (used + line.length + 1 > maxChars) break
            kept += line
            used += line.length + 1
        }
        return if (kept.isEmpty()) {
            text.take(maxChars - 1) + "…"
        } else {
            (listOf(HEADER, DISCLAIMER) + kept).joinToString("\n")
        }
    }
}
