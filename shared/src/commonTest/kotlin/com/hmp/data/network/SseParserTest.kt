package com.hmp.data.network

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * 手动 SSE 解析器单测（任务书 M2-T2）：事件边界/注释/CRLF/分片写入/流尾无空行/畸形行容忍。
 */
class SseParserTest {

    private suspend fun parseAll(text: String): List<String> {
        val channel = ByteChannel()
        channel.writeStringUtf8(text)
        channel.close()
        val events = mutableListOf<String>()
        SseParser.parse(channel) { events += it }
        return events
    }

    @Test
    fun singleEvent_withMultiDataLines_joinedByNewline() = runTest {
        val events = parseAll("data: {\"a\":1}\ndata: {\"b\":2}\n\n")
        assertEquals(1, events.size)
        assertEquals("{\"a\":1}\n{\"b\":2}\n", events.single())
    }

    @Test
    fun multipleEvents_separatedByBlankLines() = runTest {
        val events = parseAll("data: first\n\ndata: second\n\ndata: third\n\n")
        assertEquals(listOf("first\n", "second\n", "third\n"), events)
    }

    @Test
    fun commentLines_andOtherFields_areIgnored() = runTest {
        val events = parseAll(": keep-alive comment\nevent: message\ndata: payload\nid: 42\n\n")
        assertEquals(listOf("payload\n"), events)
    }

    @Test
    fun leadingSpaceAfterColon_isStripped() = runTest {
        val events = parseAll("data: hello\n\n")
        assertEquals(listOf("hello\n"), events)
    }

    @Test
    fun crlfLineEndings_areNormalized() = runTest {
        val events = parseAll("data: hi\r\ndata: there\r\n\r\n")
        assertEquals(listOf("hi\nthere\n"), events)
    }

    @Test
    fun trailingEvent_withoutBlankLineAtEof_isFlushed() = runTest {
        val events = parseAll("data: last")
        assertEquals(listOf("last\n"), events)
    }

    @Test
    fun fragmentedWrites_areHandled() = runTest {
        // 真实网络分片：三次写入拼接同一事件
        val channel = ByteChannel()
        channel.writeStringUtf8("data: {\"choices\":[{\"delta\":{\"con")
        channel.writeStringUtf8("tent\":\"你\"}}]}")
        channel.writeStringUtf8("\n\n")
        channel.close()
        val events = mutableListOf<String>()
        SseParser.parse(channel) { events += it }
        assertEquals(listOf("{\"choices\":[{\"delta\":{\"content\":\"你\"}}]}\n"), events)
    }

    @Test
    fun doneMarker_isPassedThroughAsPayload() = runTest {
        val events = parseAll("data: [DONE]\n\n")
        assertEquals(listOf("[DONE]\n"), events)
    }

    @Test
    fun malformedFieldLines_areIgnored_butDataContinues() = runTest {
        val events = parseAll("data: ok\nretry: 100\ndata: done\n\n")
        assertEquals(listOf("ok\ndone\n"), events)
    }
}