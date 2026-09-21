package com.hmp.domain.agent.runtime.sub.hello

import com.hmp.domain.agent.card.GreetingType
import com.hmp.domain.agent.card.TimePhase

/**
 * 一种问候类型的**全部静态属性**：prompt key、system prompt 兜底、写作要求、温度、兜底文案池。
 *
 * 收拢的动机：这五项原先散成**四个平行的 `when`/分支**——
 * `typePromptRequirement`（写作要求）、`typeSystemPrompt`（角色 + prompt key）、
 * `typeTemperature`（温度）、`fallbackForType`（兜底文案池）。
 * 想加一种问候类型，要四处都改，**漏掉任何一处都不会编译报错**（`when` 有兜底、
 * 池子只是少一项），只在运行时表现为"这个类型温度怪怪的/文案池很空"。
 * 现在只有 [HelloGreetingProfiles.of] 一处需要改，且它是**穷举 `when`** ⇒ 漏了编译不过。
 */
internal data class GreetingProfile(
    /** i18n 词表 key（`hello.greeting.*`）。 */
    val promptKey: String,
    /** 词表未配置时的硬编码角色描述。 */
    val systemFallback: String,
    /** 类型专属写作要求（拼进 user prompt）。 */
    val requirement: String,
    /** 类型专属温度（config 未设 llmTemperature 时生效）。 */
    val temperature: Float,
    /** LLM 不可用时的保底文案池（按 [HelloSubAgent] 的游标轮转取用）。 */
    val fallbacks: List<String>,
)

/**
 * 问候类型的静态画像表 + 路由打分权重表。
 *
 * 从 `HelloSubAgent` 抽出（约 190 行）：这些是**内容定义**，
 * 而 `HelloSubAgent` 剩下的是**内容怎么被生产与投递**。改文案不该翻编排代码。
 *
 * 全部为无状态常量/纯函数 ⇒ 这里的 `private` → 包内可见不构成可见性回退：
 * **`private` 保护的是不变量，而无状态数据没有不变量。**
 */
internal object HelloGreetingProfiles {

    /** 取某类型的画像。**穷举 `when`（无 `else`）** —— 新增枚举成员会在这里编译失败。 */
    fun of(type: GreetingType): GreetingProfile = when (type) {
        GreetingType.QUOTE -> QUOTE
        GreetingType.LYRIC_GOLD -> LYRIC_GOLD
        GreetingType.FACT -> FACT
        GreetingType.STORY -> STORY
        GreetingType.ARTIST -> ARTIST
        GreetingType.LISTEN -> LISTEN
    }

    // ──────────────────── 6 类型画像 ────────────────────

    private val QUOTE = GreetingProfile(
        promptKey = "hello.greeting.quote",
        systemFallback = "你是一个热爱音乐的诗人，擅长写出触动人心的音乐名句。",
        requirement = """
写作要求：
  · 一句话，20-50 字
  · 关于音乐、声音、旋律的哲思或诗意表达
  · 可以引用真实音乐家的话（但不确定就虚构一句自然的）
  · 有画面感，适合当前时段的情绪""",
        temperature = 0.8f,       // 需要文学创造性
        fallbacks = listOf(
            "音乐是情绪的翻译官",
            "好音乐让沉默也变得动听",
            "旋律是通往记忆的捷径",
            "一首歌，一个时刻，一段人生",
            "音乐不会说谎",
            "有些情绪，只能唱出来",
            "耳朵里的世界，心里的远方",
            "一首歌的时间，足够想你",
            "旋律不记得词，但记得你",
            "没有不会过时的歌，只有过时的心情",
        ),
    )

    private val LYRIC_GOLD = GreetingProfile(
        promptKey = "hello.greeting.lyric_gold",
        systemFallback = "你是一个记歌词的音乐达人，对华语流行和经典歌曲的歌词了如指掌。",
        requirement = """
写作要求：
  · 一句或两句歌词金句，20-60 字
  · 如果有当前播放的歌曲，优先从这首歌里挑标志性歌词
  · 没有当前播放就选一句广为人知的华语或经典歌词
  · 附上出处（歌曲名/歌手）""",
        temperature = 0.6f,
        fallbacks = listOf(
            "「故事的小黄花，从出生那年就飘着」——周杰伦《晴天》",
            "「后来，我总算学会了如何去爱」——刘若英《后来》",
            "「对这个世界如果你有太多的抱怨」——周杰伦《稻香》",
            "「我怀念的是无话不说」——孙燕姿《我怀念的》",
            "「朋友一生一起走」——周华健《朋友》",
            "「可惜不是你，陪我到最后」——梁静茹《可惜不是你》",
            "「十年之前，我不认识你」——陈奕迅《十年》",
            "「那些年错过的大雨」——胡夏《那些年》",
            "「如果我们不曾相遇」——五月天《后来的我们》",
            "「阴天快乐」——陈奕迅《阴天快乐》",
        ),
    )

    private val FACT = GreetingProfile(
        promptKey = "hello.greeting.fact",
        systemFallback = "你是一个音乐史爱好者，了解关于音乐的各种有趣冷知识，追求准确。",
        requirement = """
写作要求：
  · 一段冷知识，80-150 字
  · 关于音乐、乐器、音乐家、录音技术的有趣事实
  · 要求准确，不确定的内容请说明「根据公开资料」
  · 语言轻松有趣，像朋友分享一个小秘密""",
        temperature = 0.4f,       // 需要事实准确
        fallbacks = listOf(
            "莫扎特 3 岁就能弹钢琴，5 岁开始作曲，是史上最年轻的作曲家之一",
            "贝多芬在听力衰退后，用牙咬木棍贴在钢琴上来感受振动继续创作",
            "史上最畅销专辑是迈克尔·杰克逊的《Thriller》，全球销量超 6600 万张",
            "吉他的六根弦从低到高分别是 EADGBE，这个标准调弦法从 18 世纪沿用至今",
            "古典音乐中，小提琴的四根弦 GDAE 可以追溯到 16 世纪的文艺复兴时期",
            "录音史上第一张商业唱片发行于 1895 年，由爱迪生的留声机录制",
            "巴赫的《平均律钢琴曲集》被称为音乐界的《旧约》，是每个学钢琴的人的必弹曲目",
            "摇滚乐的诞生通常被认为是 1951 年，由 Chuck Berry 的《Johnny B. Goode》开启",
            "爵士音乐起源于 20 世纪初的美国新奥尔良，融合了布鲁斯和拉格泰姆",
            "华语流行史上销量最高的专辑是邓丽君的《Teresa Teng》系列，全球销量超 5000 万张",
        ),
    )

    private val STORY = GreetingProfile(
        promptKey = "hello.greeting.story",
        systemFallback = "你是一个音乐故事讲述者，擅长挖掘歌曲背后不为人知的故事。",
        requirement = """
写作要求：
  · 一段故事，100-200 字
  · 关于某首歌、某张专辑、某次经典演出背后的故事
  · 如果有当前播放的歌曲，优先讲这首歌的故事
  · 有细节、有温度，像在讲一个你亲历的八卦""",
        temperature = 0.7f,
        fallbacks = listOf(
            "周杰伦《晴天》里唱到的秋千，真实存在于他当年就读的淡江中学，现在已经成了歌迷打卡点",
            "陈奕迅《十年》的 MV 里有一个镜头是陈奕迅在便利店门口唱歌，那家店就在香港九龙，至今还在营业",
            "五月天《温柔》是鼓手冠佑在深夜失眠时写的，当时他只是随便弹了几个和弦，没想到成了经典",
            "孙燕姿《遇见》最初是为电影《向左走，向右走》创作的主题曲，电影上映后这首歌反而比电影更红",
            "Beyond《光辉岁月》是黄家驹为纪念南非前总统曼德拉而创作的，表达对平等和自由的追求",
            "王菲《红豆》里「还没为你把红豆，熬成缠绵的伤口」这句歌词，是林夕在失恋期间写的",
            "周杰伦《稻香》的 MV 全部在乡下拍摄，周杰伦说这首歌想表达的是「回归简单的快乐」",
            "梁静茹《勇气》的 MV 女主角是桂纶镁，这是她第一次拍 MV，后来她因为这部 MV 被导演选中拍电影",
            "五月天《倔强》最初不是专辑主打歌，是歌迷在网络上自发投票选出来的热门曲目",
            "邓丽君《月亮代表我的心》并不是她原唱，她把这首歌从一首不为人知的老歌唱成了华语经典",
        ),
    )

    private val ARTIST = GreetingProfile(
        promptKey = "hello.greeting.artist",
        systemFallback = "你是一个音乐圈的八卦大王，知道各种音乐家的有趣轶事。",
        requirement = """
写作要求：
  · 一段轶事，80-150 字
  · 关于某位音乐家不为人知的小故事
  · 可以是古典大师、流行歌手、摇滚传奇
  · 轻松幽默，展现音乐家鲜活有趣的一面""",
        temperature = 0.6f,
        fallbacks = listOf(
            "贝多芬脾气暴躁，有一次演出时他发脾气把钢琴盖砸坏了，但观众们反而更欣赏他的真性情",
            "莫扎特生前曾写过一首《G 大调弦乐四重奏》送给一位理发师，因为那位理发师帮他剪了一个很帅的发型",
            "巴赫每天工作 14 小时，不仅作曲还在教堂担任管风琴师，他一生写了超过 1000 部作品",
            "迈克尔·杰克逊有一次排练时不小心被舞台上的烟火烧伤了头发，但他坚持完成了演出",
            "周杰伦高中时因为成绩不好被老师批评，后来他把这段经历写成了《听妈妈的话》",
            "邓丽君会说五种语言，除了中文和粤语，她还会英语、日语和法语",
            "阿黛尔曾经因为太胖被音乐学院拒绝，后来她用实力证明了自己不需要靠外形",
            "五月天主唱阿信的原名是陈信宏，他「阿信」这个昵称来自日本漫画《阿信》的女主角",
            "陈奕迅有严重的舞台焦虑症，每次上台前都会紧张到手心出汗，但一开口唱歌就完全投入",
            "王菲的女儿窦靖童现在也成了歌手，母女俩曾经同台演出过《因为爱情》",
        ),
    )

    private val LISTEN = GreetingProfile(
        promptKey = "hello.greeting.listen",
        systemFallback = "你是一个懂场景的音乐 DJ，总能在对的时间推荐对的歌。",
        requirement = """
写作要求：
  · 一段听歌引导，50-100 字
  · 结合当前时段和场景，给一个具体的听歌建议
  · 比如「深夜戴上耳机听 XXX」「开车时放 XXX 很搭」
  · 语气亲切自然，像朋友推荐""",
        temperature = 0.5f,
        fallbacks = listOf(
            "深夜戴上耳机听一首轻音乐，会让你发现白天没注意到的细节",
            "开车跑长途时试试放 jazz，比摇滚更能让你保持清醒又放松",
            "下雨天适合听带点忧伤的歌，窗外的雨声和旋律会形成奇妙的和声",
            "加班累了，放一首 80 年代的老歌，旋律里藏着你小时候的温度",
            "清晨起床后听一首节奏明快的歌，比咖啡更能唤醒你",
            "散步时试试听纯音乐，没有歌词的干扰，你会更注意周围的声音",
            "写代码或看书时放 Lo-fi 或 ambient，有背景声但不打扰思考",
            "洗澡时听一首你喜欢的歌，浴室的回声会让它听起来像演唱会现场",
            "做饭时放一首轻快的歌，切菜都会变得更有节奏感",
            "睡前 10 分钟听一首慢歌，比刷手机更能帮你放松入睡",
        ),
    )

    // ──────────────────── 路由打分权重表 ────────────────────
    //
    // 信号 → 类型偏好（权重 0-1，由调用方乘上信号强度）。
    // ⚠️ 这几张表是**稀疏**的（不列 = 0 分），因此加类型时不会编译报错 ——
    // 若要让新类型吃到某个信号的加分，必须手动补进对应表。

    /** 当前播放歌曲（强信号）。 */
    val TYPE_PREFS_NOW_PLAYING: Map<GreetingType, Double> = mapOf(
        GreetingType.LYRIC_GOLD to 0.9, GreetingType.STORY to 0.8, GreetingType.ARTIST to 0.7,
        GreetingType.FACT to 0.5, GreetingType.LISTEN to 0.4, GreetingType.QUOTE to 0.3,
    )

    /** 音乐纪念日（强信号）。 */
    val TYPE_PREFS_ANNIV: Map<GreetingType, Double> = mapOf(
        GreetingType.FACT to 0.9, GreetingType.STORY to 0.8, GreetingType.ARTIST to 0.7,
        GreetingType.QUOTE to 0.5, GreetingType.LISTEN to 0.2, GreetingType.LYRIC_GOLD to 0.1,
    )

    /** 时段（稳定信号）。 */
    val TYPE_PREFS_PHASE: Map<TimePhase, Map<GreetingType, Double>> = mapOf(
        TimePhase.NIGHT to mapOf(
            GreetingType.QUOTE to 0.9, GreetingType.STORY to 0.7, GreetingType.LISTEN to 0.7,
            GreetingType.FACT to 0.6, GreetingType.LYRIC_GOLD to 0.4, GreetingType.ARTIST to 0.3,
        ),
        TimePhase.MORNING_COMMUTE to mapOf(
            GreetingType.LISTEN to 0.9, GreetingType.QUOTE to 0.6, GreetingType.FACT to 0.5,
        ),
        TimePhase.WORK to mapOf(
            GreetingType.FACT to 0.8, GreetingType.QUOTE to 0.6, GreetingType.LISTEN to 0.5,
        ),
        TimePhase.LUNCH to mapOf(
            GreetingType.LYRIC_GOLD to 0.8, GreetingType.QUOTE to 0.7, GreetingType.LISTEN to 0.6,
        ),
        TimePhase.EVENING_COMMUTE to mapOf(
            GreetingType.LISTEN to 0.9, GreetingType.STORY to 0.6, GreetingType.FACT to 0.5,
        ),
        TimePhase.EVENING_LEISURE to mapOf(
            GreetingType.STORY to 0.9, GreetingType.ARTIST to 0.7, GreetingType.LYRIC_GOLD to 0.6,
        ),
    )
}
