package com.hmp.log

import co.touchlab.kermit.Logger

/**
 * 统一日志 tag 常量（编译期护栏，配合 docs/LOGGING.md 规范）。
 * 新域仅在枚举追加条目，禁止在调用点硬编码字符串。
 */
enum class LogTag(val v: String) {
    AgentMaster("Agent.Master"),
    AgentSub("Agent.Sub"),
    AgentEnrich("Agent.Enrich"),
    AgentHello("Agent.Hello"),
    AgentRadio("Agent.Radio"),
    AgentReActLoop("Agent.ReActLoop"),
    AgentScheduler("Agent.Scheduler"),
    AgentTool("Agent.Tool"),
    AgentLlmCall("Agent.LlmCall"),
    AgentContext("Agent.ContextBudget"),
    AgentProfile("Agent.Profile"),
    // 后续域占位：UiChat("UI.Chat"), DataRepo("Data.Repository"), PlayerCore("Player.Core"), SystemInit("System.Init")
    ;
}