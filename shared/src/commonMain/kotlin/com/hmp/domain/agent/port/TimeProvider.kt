package com.hmp.domain.agent.port

/**
 * 时间来源（毫秒）。
 *
 * **为什么是端口**：引擎平面保持纯 domain —— 所有需要"当前时间"的组件
 * 注入此函数，生产走 DI（绑 data 层 `currentTimeMillis`），测试注入脚本化递增时钟。
 *
 * **为什么在 port 而不是 runtime**：它是**跨层的基础设施契约** —— `infra/` 与
 * `runtime/` 都要用它，若定义在 `runtime/` 则 `infra → runtime` 形成反向依赖。
 * 端口层是零内部依赖的叶子，适合承载这类基础类型。
 *
 * 注意与 [WallClock] 的区别：本类型只给**毫秒时间戳**（算间隔、记耗时），
 * 而 [WallClock] 给**本地日历语义**（几点/周几/时段）——后者需要各端系统 API。
 */
typealias TimeProvider = () -> Long
