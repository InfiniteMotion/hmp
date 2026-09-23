package com.hmp.domain.agent.port

/**
 * 工具许可级别 —— 四级，与 [com.hmp.domain.agent.policy.TrustLevel] 配合决定「工具能不能自动跑」。
 *
 * **为什么定义在 port 而不是 policy 或 tool**：许可是跨层概念，三处都要用它 ——
 * [com.hmp.domain.agent.policy.PolicyGuard] 靠它判定、`policy/AgentPolicy` 靠它做身份门过滤、
 * `tool/spec/ToolSpec` 的 `AgentTool.permissionLevel` 靠它声明。放 policy 会让 port 反向依赖 policy，
 * 放 tool 会让 policy 反向依赖 tool；下沉到 port 后依赖方向恒为 `policy → port`、`tool → port`。
 *
 * 语义由低到高：
 * - [SILENT]          只读 / 无副作用，直接执行不打扰
 * - [NOTIFY]          有副作用但可逆，执行后通知
 * - [CONFIRM]         有副作用，需要用户确认（高信任档位可豁免）
 * - [STRONG_CONFIRM]  不可逆 / 高危，始终需要确认
 */
enum class ToolPermissionLevel { SILENT, NOTIFY, CONFIRM, STRONG_CONFIRM }
