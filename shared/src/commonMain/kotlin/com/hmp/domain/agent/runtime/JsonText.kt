package com.hmp.domain.agent.runtime

/**
 * LLM 返回文本的 **JSON 抽取原语** —— 无状态纯函数，各 Agent 共用。
 *
 * 由两处复制品合并而来：`EnrichSubAgent` 的同名 private 方法与 `HelloSubAgent.extractJsonBlock`。
 * 后者注释里当时写着"与 EnrichSubAgent.extractJsonBlock 同逻辑，工具型 Agent 共用"——
 * **写着"共用"，实际是各抄一份**。
 *
 * 放在 `runtime/` 而不是某个 Agent 内部：它不认识任何 Agent 概念，
 * 只认识"一段可能裹着 markdown 围栏的模型输出文本"。
 * 诊断日志留在各 Agent 侧（只有那里才知道 agentId）。
 */
internal object JsonText {

    private val CODE_BLOCK = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""")

    /**
     * 提取 JSON 块：优先 markdown ```json 围栏内的内容，
     * 否则取第一个 `{` 或 `[` 到最后一个 `}` 或 `]` 之间的子串。
     */
    fun extractJsonBlock(text: String): String {
        val match = CODE_BLOCK.find(text)
        if (match != null) return match.groupValues[1].trim()

        val firstBrace = text.indexOfAny(charArrayOf('{', '['))
        val lastBrace = text.lastIndexOfAny(charArrayOf('}', ']'))
        if (firstBrace >= 0 && lastBrace > firstBrace) return text.substring(firstBrace, lastBrace + 1)
        return text.trim()
    }

    /**
     * 把「逗号分隔的 JSON 对象序列」切成一个个对象文本。
     *
     * 逐字符跟踪**字符串态**与**括号深度**，因此字符串内部的 `{` / `}` / `,` 不会被误判；
     * 根级逗号被跳过（它们只分隔对象，不属于任何对象）。
     */
    fun splitJsonObjects(content: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var inString = false
        var escapeNext = false
        val current = StringBuilder()

        for (char in content) {
            when {
                escapeNext -> {
                    current.append(char)
                    escapeNext = false
                }
                char == '\\' && inString -> {
                    current.append(char)
                    escapeNext = true
                }
                char == '"' -> {
                    inString = !inString
                    current.append(char)
                }
                !inString && char == '{' -> {
                    depth++
                    current.append(char)
                }
                !inString && char == '}' -> {
                    depth--
                    current.append(char)
                    if (depth == 0) {
                        result.add(current.toString().trim())
                        current.clear()
                    }
                }
                !inString && depth == 0 && char == ',' -> {
                    // 根级别逗号 —— 跳过
                }
                else -> current.append(char)
            }
        }
        return result
    }
}
