package com.hmp.domain.agent.funnel

import com.hmp.domain.agent.port.PlaybackCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CommandLexiconTest {

    @Test
    fun exactCommands_mapToDirect() {
        assertIs<FunnelResult.Direct>(CommandLexicon.classify("暂停")).command.let {
            assertEquals(PlaybackCommand.PAUSE, it)
        }
        assertIs<FunnelResult.Direct>(CommandLexicon.classify("下一首")).command.let {
            assertEquals(PlaybackCommand.NEXT, it)
        }
        assertIs<FunnelResult.Direct>(CommandLexicon.classify("随机播放")).command.let {
            assertEquals(PlaybackCommand.SHUFFLE_ON, it)
        }
        assertIs<FunnelResult.Direct>(CommandLexicon.classify("单曲循环")).command.let {
            assertEquals(PlaybackCommand.REPEAT_ONE_ON, it)
        }
    }

    @Test
    fun politePrefix_isAccepted() {
        val r = CommandLexicon.classify("帮我切歌")
        assertIs<FunnelResult.Direct>(r)
        assertEquals(PlaybackCommand.NEXT, r.command)
    }

    @Test
    fun fuzzyIntents_upgrade() {
        assertIs<FunnelResult.Upgrade>(CommandLexicon.classify("换点安静的"))
        assertIs<FunnelResult.Upgrade>(CommandLexicon.classify("推荐几首好听的"))
        assertIs<FunnelResult.Upgrade>(CommandLexicon.classify("帮我建个深夜歌单"))
        assertIs<FunnelResult.Upgrade>(CommandLexicon.classify("放一首周杰伦的歌"))
    }

    @Test
    fun ordinaryTalk_passes() {
        assertIs<FunnelResult.Pass>(CommandLexicon.classify("今天天气如何"))
        assertIs<FunnelResult.Pass>(CommandLexicon.classify("你好"))
        assertIs<FunnelResult.Pass>(CommandLexicon.classify(""))
    }
}
