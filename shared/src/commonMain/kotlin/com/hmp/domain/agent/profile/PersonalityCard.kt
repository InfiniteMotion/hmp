package com.hmp.domain.agent.profile

/**
 * 消费面 —— **音乐人格卡**（契约 `agent-profile.md` v3.4 §6）。
 *
 * 认知面给 agent 看槽位原值；本文件把**同一份侧写**换成用户看的包装：
 * 命名 + 3~4 句人话 + 可展开的「凭什么」。**换轴，不是换皮**（§6.2）：
 *
 * | 轴 | 两极 | 来源槽位 |
 * |----|------|---------|
 * | 时段倾向 | 日行 ↔ 夜行 | `time_portrait.primaryPart` |
 * | 口味宽度 | 专精 ↔ 广谱 | `taste/library.genreBreadth`（标签未就绪退化成歌手集中度近似，标"初步"，PF16） |
 * | 听法 | 沉浸 ↔ 筛选 | `habits_portrait.completionRate` |
 *
 * 四条纪律落在这里：
 *
 * 1. **消费面不得回流决策面**（§6.1）—— 本文件是**纯读**：输入侧写、输出文案，
 *    不写任何证据/侧写，也不被任何决策路径引用。称号是他消费的，不该变成他被服务的枷锁。
 * 2. **B 类不进卡正面**（§5.3 / §9.2）—— 弱确证的倾向描述不与可硬确证的结论并列呈现。
 * 3. **不假装知道** —— 轴位槽位缺失就不定轴；三轴不全时**不给命名**（宁可不命名，
 *    也不拿不完整的轴硬凑一个类型），只出有依据的那几句人话。
 * 4. **禁止类型学专名**（§6.3）—— 命名表是自有音乐语境的 8 个名字（PF10），
 *    不得出现 MBTI / 九型 / 大五及其混淆性近似；轴名说的是「你怎么听」，不是「你是谁」。
 */
object PersonalityCardComposer {

    // ── 轴的两极 ──
    const val POLE_NIGHT = "夜行"
    const val POLE_DAY = "日行"
    const val POLE_FOCUSED = "专精"
    const val POLE_BROAD = "广谱"
    const val POLE_IMMERSIVE = "沉浸"
    const val POLE_FILTERING = "筛选"

    /** 已确定的轴。[preliminary] = 该轴由退化近似得出（PF16：标签未就绪时的歌手集中度近似）。 */
    data class CardAxis(val title: String, val pole: String, val preliminary: Boolean = false)

    /**
     * 人格卡（消费面产物）。[typeName] 为 null 表示轴位不全、不给命名 ——
     * 此时卡片只呈现 [sentences] 与 [whys]，不编一个名字出来。
     */
    data class PersonalityCard(
        val typeName: String?,
        val axes: List<CardAxis>,
        /** 3~4 句人话（每句都能反查到侧写槽位；也是叙事不可用时的离线兜底） */
        val sentences: List<String>,
        /** 「凭什么」：逐轴给依据 */
        val whys: List<String>,
        /**
         * LLM 生成的画像叙事（契约 v3.5 §7.4，两面共用）—— null 表示尚未生成或
         * 生成失败，卡片退回 [sentences] 模板句；离线也永远有得看。
         */
        val narrative: String? = null,
    )

    // ── 8 型命名表（PF10：中文 8 个命名 + 对应槽位匹配规则）──
    // 轴序固定为 时段 × 宽度 × 听法；名字都是"听音乐的方式"，不是"你是什么人"。
    private val NAMING_TABLE: Map<Triple<String, String, String>, String> = mapOf(
        Triple(POLE_NIGHT, POLE_FOCUSED, POLE_IMMERSIVE) to "夜航者",      // 深夜 + 那一类 + 听到底
        Triple(POLE_NIGHT, POLE_FOCUSED, POLE_FILTERING) to "夜巡者",      // 深夜 + 那一类 + 边听边挑
        Triple(POLE_NIGHT, POLE_BROAD, POLE_IMMERSIVE) to "月下漫游者",    // 深夜 + 什么都听 + 听到底
        Triple(POLE_NIGHT, POLE_BROAD, POLE_FILTERING) to "午夜调频师",    // 深夜 + 什么都听 + 边听边挑
        Triple(POLE_DAY, POLE_FOCUSED, POLE_IMMERSIVE) to "老唱片",        // 白天 + 那一类 + 听到底
        Triple(POLE_DAY, POLE_FOCUSED, POLE_FILTERING) to "点唱机",        // 白天 + 那一类 + 边听边挑
        Triple(POLE_DAY, POLE_BROAD, POLE_IMMERSIVE) to "拾光者",          // 白天 + 什么都听 + 听到底
        Triple(POLE_DAY, POLE_BROAD, POLE_FILTERING) to "冲浪者",          // 白天 + 什么都听 + 边听边挑
    )

    /**
     * 命名表全量（禁词闸门用）—— 契约 v3.5 §7.4：人格命名**独占消费面**，
     * 画像叙事（两面共用）与认知面上下文不得出现，防称号回流决策面。
     */
    val allNames: List<String> = NAMING_TABLE.values.toList()

    /**
     * 侧写 → 人格卡。**没有任何可说的就返回 null** —— 与上下文渲染同一纪律：
     * 冷启动不留空壳，没有依据就不出卡。
     *
     * [narrative] 为两面共用的画像叙事（已过写入闸门），null 时卡片退回模板句。
     */
    fun compose(portraits: List<PortraitDraft>, narrative: String? = null): PersonalityCard? {
        // 纪律 2：B 类不进卡正面 —— 弱确证倾向不与硬确证结论并列
        val strong = portraits.filter { !it.type.weakEvidence }
        if (strong.isEmpty()) return null
        val slots = strong.associate { it.type to it.slots }

        val axes = mutableListOf<CardAxis>()
        val sentences = mutableListOf<String>()
        val whys = mutableListOf<String>()

        // ── 轴一：时段倾向（time_portrait.primaryPart）──
        val primaryPart = slots[PortraitType.TIME]?.get(PortraitType.PRIMARY_PART)
        val timePole = when (primaryPart) {
            BehaviorModeler.PART_NIGHT, BehaviorModeler.PART_DAWN -> POLE_NIGHT
            BehaviorModeler.PART_MORNING, BehaviorModeler.PART_NOON, BehaviorModeler.PART_EVENING -> POLE_DAY
            else -> null
        }
        if (timePole != null) {
            axes += CardAxis("时段倾向", timePole)
            if (timePole == POLE_NIGHT) {
                sentences += "你常在天黑之后打开播放器。"
                whys += "时段——播放记录里你听得最多的时段是夜里。"
            } else {
                sentences += "你的听歌时间多半落在白天。"
                whys += "时段——播放记录里你听得最多的时段在白天。"
            }
        }

        // ── 轴二：口味宽度（PF16：优先标签结论，未就绪退化为歌手集中度近似并标"初步"）──
        val library = slots[PortraitType.LIBRARY]
        val taste = slots[PortraitType.TASTE]
        var breadthPreliminary = false
        var breadthPole: String? = when ((taste ?: library)?.get(PortraitType.GENRE_BREADTH)) {
            LibraryModeler.BREADTH_NARROW -> POLE_FOCUSED
            LibraryModeler.BREADTH_BROAD -> POLE_BROAD
            else -> null
        }
        if (breadthPole == null) {
            // 退化近似：标签未就绪，用歌手集中度猜 —— 可以说，但要标"初步"（PF16）
            when (library?.get(PortraitType.ARTIST_CONCENTRATION)) {
                LibraryModeler.CONCENTRATION_FOCUSED -> {
                    breadthPole = POLE_FOCUSED; breadthPreliminary = true
                }
                LibraryModeler.CONCENTRATION_BROAD -> {
                    breadthPole = POLE_BROAD; breadthPreliminary = true
                }
                else -> Unit
            }
        }
        if (breadthPole != null) {
            axes += CardAxis("口味宽度", breadthPole, preliminary = breadthPreliminary)
            if (breadthPreliminary) {
                whys += "宽度——标签还没就绪，先用歌手集中度做了个初步近似。"
            }
            when (breadthPole) {
                POLE_FOCUSED -> {
                    sentences += "听的东西不杂，来来回回就是那一类。"
                    if (!breadthPreliminary) whys += "宽度——你的曲库标签显示类型很集中。"
                }
                POLE_BROAD -> {
                    sentences += "你听得杂，什么类型都能装进歌单。"
                    if (!breadthPreliminary) whys += "宽度——你的曲库标签显示类型相当宽。"
                }
            }
        }

        // ── 轴三：听法（habits_portrait.completionRate）──
        val completion = slots[PortraitType.HABITS]?.get(PortraitType.COMPLETION_RATE)
        val listenPole = when (completion) {
            BehaviorModeler.RATE_HIGH -> POLE_IMMERSIVE
            BehaviorModeler.RATE_LOW -> POLE_FILTERING
            else -> null
        }
        if (listenPole != null) {
            axes += CardAxis("听法", listenPole)
            if (listenPole == POLE_IMMERSIVE) {
                sentences += "歌一开就听到底，很少中途切走。"
                whys += "听法——你的完播率一直很高。"
            } else {
                sentences += "你像在筛歌，常常听一段就跳。"
                whys += "听法——你经常听到一半就切走。"
            }
        }

        // ── 佐料：口味侧写的具体内容（有才有，不凑数）──
        taste?.get(PortraitType.LEADING_GENRE)?.takeIf { it.isNotBlank() }?.let {
            sentences += "常听的是${sanitizeShort(it)}这一类。"
        }
        taste?.get(PortraitType.TOP_ARTISTS)?.takeIf { it.isNotBlank() }?.let {
            sentences += "翻来覆去听的是${sanitizeShort(it)}的歌。"
        }

        if (sentences.isEmpty()) return null

        // 纪律 3：三轴不全不给命名 —— 8 型的每格都由三轴定义，缺轴硬凑就是编
        val typeName = if (axes.size == 3 && timePole != null && breadthPole != null && listenPole != null) {
            NAMING_TABLE[Triple(timePole, breadthPole, listenPole)]
        } else {
            null
        }

        return PersonalityCard(
            typeName = typeName,
            axes = axes.toList(),
            sentences = sentences.take(4), // §6.3：命名 + 3~4 句人话，多了就删
            whys = whys.toList(),
            narrative = narrative?.takeIf { it.isNotBlank() },
        )
    }

    /** 佐料值的安全闸门：短句化，防内部标识或长串漏进消费面。 */
    private fun sanitizeShort(raw: String): String = raw
        .lines().first().trim()
        .take(12)
}
