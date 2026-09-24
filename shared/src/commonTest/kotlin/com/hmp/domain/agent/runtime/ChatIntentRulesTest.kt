package com.hmp.domain.agent.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ChatIntentRules] 的确定性规则测试。
 *
 * 这组规则**顺序敏感**（见 [ChatIntentRules] 文件头）：调用方按固定次序逐条试，
 * 例如「停电台」同时命中 `STOP_RADIO` 与强触发词「电台」——
 * 若先判 `isRadioIntent`，用户说「停电台」会被当成**开电台**去重建队列。
 * 本测试把这条约束固化成断言：将来有人重排 `builtinIntent` 的分支顺序，这里会红。
 *
 * 另一处容易踩的接口约定：**判定函数假定输入已 `trim().lowercase()`**
 * （调用方 `builtinIntent` 负责归一化），而 `extractSeed` 自带 `ignoreCase`。
 */
class ChatIntentRulesTest {

    // ═══ 顺序敏感：停电台必须先于电台意图被判定 ═══

    @Test
    fun stopRadioInput_alsoLooksLikeRadioIntent_soOrderIsTheOnlyGuard() {
        // 特征化测试：这不是缺陷，而是"顺序即正确性"的证据。
        // 之所以要写下来，是因为它看起来像冗余判定，容易被后来者"简化"掉。
        assertTrue(ChatIntentRules.isStopRadio("停电台"))
        assertTrue(ChatIntentRules.isRadioIntent("停电台"))
    }

    @Test
    fun stopRadio_matchesStopPhrasesButNotMentions() {
        assertTrue(ChatIntentRules.isStopRadio("停电台"))
        assertTrue(ChatIntentRules.isStopRadio("stop radio"))
        assertFalse(ChatIntentRules.isStopRadio("电台不错"))  // 提到电台 ≠ 要停
    }

    // ═══ 富化生命周期：六态各自命中，且互不误命中 ═══

    @Test
    fun enrichLifecycle_eachIntentMatchesItsOwnPhrases() {
        assertTrue(ChatIntentRules.isStartEnrich("开始富化"))
        assertTrue(ChatIntentRules.isStopEnrich("停止富化"))
        assertTrue(ChatIntentRules.isPauseEnrich("暂停富化"))
        assertTrue(ChatIntentRules.isResumeEnrich("恢复富化"))
        assertTrue(ChatIntentRules.isRescanEnrich("重扫"))
        assertTrue(ChatIntentRules.isEnrichStatus("富化状态"))
    }

    @Test
    fun enrichLifecycle_intentsDoNotOverlap() {
        // 重合会让用户的「停止」被当作「开始」之类 —— 这六个动作都有副作用
        assertFalse(ChatIntentRules.isStopEnrich("开始富化"))
        assertFalse(ChatIntentRules.isStartEnrich("停止富化"))
        assertFalse(ChatIntentRules.isPauseEnrich("恢复富化"))
        assertFalse(ChatIntentRules.isResumeEnrich("暂停富化"))
        assertFalse(ChatIntentRules.isRescanEnrich("富化进度"))
        assertFalse(ChatIntentRules.isEnrichStatus("重扫"))
    }

    // ═══ 电台意图：强触发 / 弱触发+风格词 ═══

    @Test
    fun radioIntent_strongTriggerAloneIsEnough() {
        assertTrue(ChatIntentRules.isRadioIntent("来个电台"))
        assertTrue(ChatIntentRules.isRadioIntent("radio"))
        assertTrue(ChatIntentRules.isRadioIntent("dj 时间"))
    }

    @Test
    fun radioIntent_weakTriggerNeedsAStyleWord() {
        // 「来一首」「放个」是日常说法，只有叠了风格词才算电台意图 ——
        // 否则用户只是想点首歌，也会被拿去开电台
        assertFalse(ChatIntentRules.isRadioIntent("来一首"))
        assertFalse(ChatIntentRules.isRadioIntent("放个"))
        assertTrue(ChatIntentRules.isRadioIntent("来一首摇滚"))
        assertTrue(ChatIntentRules.isRadioIntent("放个爵士"))
    }

    @Test
    fun radioIntent_plainChatIsNotIntent() {
        assertFalse(ChatIntentRules.isRadioIntent("你好"))
        assertFalse(ChatIntentRules.isRadioIntent("这首歌是谁唱的"))
    }

    // ═══ 种子提取的三态语义：风格词 / 空串 / null ═══

    @Test
    fun extractSeed_styleWordWinsOverStripping() {
        assertEquals("摇滚", ChatIntentRules.extractSeed("来点摇滚"))
        assertEquals("jazz", ChatIntentRules.extractSeed("开个 jazz 电台"))
        assertEquals("深夜", ChatIntentRules.extractSeed("深夜放点音乐"))
    }

    @Test
    fun extractSeed_stripsTriggerWordsLeavingFreeText() {
        assertEquals("周杰伦", ChatIntentRules.extractSeed("放个周杰伦"))
    }

    @Test
    fun extractSeed_emptyStringMeansRadioIntentWithoutSeed() {
        // "" = "是电台意图，但用户没给种子" → 上层从 nowPlaying 自动提取
        assertEquals("", ChatIntentRules.extractSeed("开个电台"))
        assertEquals("", ChatIntentRules.extractSeed("来点电台"))
    }

    @Test
    fun extractSeed_nullOnlyWhenNothingLeftAndNotRadioIntent() {
        // null = "根本不是电台意图"。注意：**普通文本会原样返回** ——
        // 它不替调用方判 intent，调用方须先过 isRadioIntent
        assertEquals("你好", ChatIntentRules.extractSeed("你好"))
        assertNull(ChatIntentRules.extractSeed("，。？"))
    }

    // ═══ 大小写契约（两条函数不对称，容易踩） ═══

    @Test
    fun matchers_requireLowerCaseInputWhileExtractSeedIsCaseInsensitive() {
        // 判定函数不做归一化：直接喂原始大写不会命中（接口约定，不是缺陷）
        assertFalse(ChatIntentRules.isStopRadio("STOP RADIO"))
        assertTrue(ChatIntentRules.isStopRadio("stop radio"))
        // 而 extractSeed 自己做 ignoreCase，两种大小写都能抽出种子
        assertEquals("jazz", ChatIntentRules.extractSeed("放个 JAZZ"))
    }
}
