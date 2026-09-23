package com.hmp.data.network

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line

/**
 * 手动 SSE（text/event-stream）解析器（设计总纲 7.2 选型 #2）。
 *
 * 理由：不引入 Ktor SSE 插件——避开 Ktor 3.1.1 → 3.2 升级、兼容怪癖端点；
 * [ByteReadChannel] 逐行消费对任意 Ktor engine（OkHttp / Java / Darwin）通用。
 *
 * 语义（SSE 规范子集）：
 * - `data:` 行累积多行 payload（以 \n 连接），空行触发一次 [onData]；流尾无空行也触发；
 * - `: 注释` 与 `event:/id:/retry:` 字段忽略；CRLF/LF 由 readUTF8Line 归一；
 * - `[DONE]` 作为普通 payload 透传，由调用方识别为流结束标记。
 */
object SseParser {

    suspend fun parse(channel: ByteReadChannel, onData: suspend (payload: String) -> Unit) {
        val dataBuilder = StringBuilder()
        var hasData = false

        while (true) {
            val line = channel.readUTF8Line() ?: break
            when {
                line.isEmpty() -> {
                    if (hasData) {
                        onData(dataBuilder.toString())
                        dataBuilder.clear()
                        hasData = false
                    }
                }
                line.startsWith(":") -> Unit // 注释行：忽略
                line.startsWith("data:") -> {
                    val raw = line.removePrefix("data:")
                    val payload = if (raw.startsWith(" ")) raw.drop(1) else raw
                    dataBuilder.append(payload).append('\n')
                    hasData = true
                }
                else -> Unit // event:/id:/retry: 等字段当前协议无需处理
            }
        }

        if (hasData) {
            onData(dataBuilder.toString())
        }
    }
}