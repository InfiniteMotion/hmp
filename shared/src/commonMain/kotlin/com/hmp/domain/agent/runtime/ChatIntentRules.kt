package com.hmp.domain.agent.runtime

/**
 * 对话里的**确定性意图规则** —— 触发词表 + 匹配判定。
 *
 * 从 `MasterAgent.builtinIntent()` 抽出：原先 8 张触发词表都以
 * `val xxxTriggers = listOf(...)` 的形式**写在函数体里**，于是
 * ① 每条用户消息都会把这几张表重新构造一遍；
 * ② 「支持哪些说法」要读一遍 178 行的 if 链才知道。
 *
 * 另外 `isRadioIntent` 与 `extractSeed` 各自内联了一份**逐字相同**的风格词表
 * （13 行 × 2）—— 改一处漏一处不会编译报错，只会让「判定为电台意图」与
 * 「抽出的种子」对不上。现在只有一份。
 *
 * 全部为无状态常量 + 纯函数 ⇒ 这里的 `private` → 包内可见不构成可见性回退：
 * **`private` 保护的是不变量，而无状态数据没有不变量。**
 *
 * ⚠️ 规则是**顺序敏感**的：`builtinIntent` 按固定次序逐条试，
 * 例如「继续电台」必须先于「电台启动」被判定（否则会被当成重建电台）。
 * 本文件只提供判定，**次序由调用方维持**。
 */
internal object ChatIntentRules {

    // ──────────────────── 触发词表 ────────────────────

    /** 停电台。（原表里 "stop radio" 重复出现两次，已去重） */
    private val STOP_RADIO = listOf("停电台", "关电台", "停止电台", "stop radio", "停止播放电台")

    private val START_ENRICH = listOf("开始富化", "启动富化", "开始识别", "启动识别", "start enrich", "run enrich", "富化一下")

    /** 完全停止（区别于 pause 的调度暂停）。 */
    private val STOP_ENRICH = listOf("停止富化", "关掉富化", "stop enrich", "关闭富化")

    private val PAUSE_ENRICH = listOf("暂停富化", "暂停识别", "pause enrich", "停一下富化")

    private val RESUME_ENRICH = listOf("恢复富化", "继续富化", "恢复识别", "resume enrich", "继续识别")

    private val RESCAN_ENRICH = listOf("重扫", "重新富化", "重扫未覆盖", "rescan enrich", "重置富化", "重新识别")

    private val ENRICH_STATUS = listOf("富化状态", "识别进度", "enrich status", "富化进度", "enrich状态")

    /** 强触发词：出现即认定电台意图。 */
    private val STRONG_TRIGGERS = listOf("电台", "radio", "dj", "打碟")

    /** 弱触发词：必须与风格词同时出现才算电台意图（否则「来一首」会被误判）。 */
    private val WEAK_TRIGGERS = listOf(
        "来一段", "来一首", "开个", "开个电台", "放个", "放一首", "整个", "来个", "放段", "来点",
    )

    /** 风格/场景提示词：既用于弱触发的判定，也用于从原文里**反向剪出**种子。 */
    private val STYLE_HINTS = listOf(
        "摇滚", "爵士", "古典", "民谣", "电子", "流行", "嘻哈", "rnb", "蓝调", "金属",
        "朋克", "雷鬼", "乡村", "灵魂", "放克", "说唱", "edm", "house", "techno",
        "迪斯科", "后摇", "日摇", "韩流", "轻音乐", "纯音乐", "钢琴曲",
        "深夜", "夜晚", "深夜电台", "工作", "学习", "专注", "放松", "运动", "跑步",
        "开车", "通勤", "咖啡馆", "雨天", "清晨", "早晨", "下午茶", "助眠", "睡觉",
        "情歌", "伤感", "治愈", "欢快", "激情", "安静", "冥想",
        "rock", "jazz", "classical", "folk", "pop", "hiphop", "hip-hop", "blues",
        "metal", "punk", "reggae", "soul", "funk", "rap", "study", "focus", "chill",
        "workout", "sleep", "relax", "love songs", "instrumental",
    )

    // ──────────────────── 判定 ────────────────────

    fun isStopRadio(lower: String): Boolean = matches(lower, STOP_RADIO)

    fun isStartEnrich(lower: String): Boolean = matches(lower, START_ENRICH)

    fun isStopEnrich(lower: String): Boolean = matches(lower, STOP_ENRICH)

    fun isPauseEnrich(lower: String): Boolean = matches(lower, PAUSE_ENRICH)

    fun isResumeEnrich(lower: String): Boolean = matches(lower, RESUME_ENRICH)

    fun isRescanEnrich(lower: String): Boolean = matches(lower, RESCAN_ENRICH)

    fun isEnrichStatus(lower: String): Boolean = matches(lower, ENRICH_STATUS)

    /** 电台意图判定（确定性关键词匹配，原 Gateway.extractRadioSeed 搬入 MasterAgent）。 */
    fun isRadioIntent(lower: String): Boolean {
        val hasStrong = matches(lower, STRONG_TRIGGERS)
        val hasWeakWithStyle = matches(lower, WEAK_TRIGGERS) && STYLE_HINTS.any { lower.contains(it.lowercase()) }
        return hasStrong || hasWeakWithStyle
    }

    /** 从电台意图输入中提取种子（风格词优先，空串 = 自动从 nowPlaying 提取，null = 非电台意图）。 */
    fun extractSeed(input: String): String? {
        val lower = input.trim().lowercase()

        val styleInInput = STYLE_HINTS.firstOrNull { input.contains(it, ignoreCase = true) }
        if (styleInInput != null) return styleInInput

        var seed = input.trim()
        (STRONG_TRIGGERS + WEAK_TRIGGERS).forEach { t -> seed = seed.replace(t, "", ignoreCase = true) }
        STYLE_HINTS.forEach { s -> seed = seed.replace(s, "", ignoreCase = true) }
        seed = seed.trim().trim('，', '。', '？', '!', '！', '?', '、', ' ', '　')

        return when {
            seed.isNotBlank() -> seed
            isRadioIntent(lower) -> ""     // 强触发词但无种子 → 自动提取
            else -> null
        }
    }

    private fun matches(lower: String, phrases: List<String>): Boolean = phrases.any { lower.contains(it) }
}
