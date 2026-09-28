# HMP F12 Token 计量与窗口治理

> **上游**：`../design/agent.md`（设计总纲）｜`../design/agent-token.md`（**本阶段唯一设计依据**）
> **计划书**：`README.md`（总纲与生命线）
> **轴别**：插叙
> **阶段族**：T1 计量打真值 + 落账本 → T2 分账视图（~~T2b 配额接线~~ **候补**）→ T3 窗口治理 → T4 上下文组装策略 → T5 成本可见（可选，后置）
> **提交**：（待填）
> **状态**：✅ 主体完成（2026-09-19，T1–T4 落地；T2b 配额候补 / T5 成本可见后置）
> **最终目标**：**能统计各 Agent × 各端点的 token 消耗（分时或累计）**

---

## 1. 阶段定位

**把 token 从"散落的估算"变成"一等公民的计量"，并让"超窗"从静默故障变成显式失败。**

起因：用户指出「token 是开发阶段被刻意忽略的部分」。核查后确认**不是精度不够，而是七处断裂**——全部有行号可查（见 `../design/agent-token.md` §1）：

| # | 断层 |
|---|---|
| 1 | **真 usage 拿到却扔掉**：`OpenAiUsage` DTO 已解析，但 `LlmEvent` 无携带通道 → 传输层无处上报。**且在传输层就断了两次**：流式 chunk 的 DTO **连 `usage` 字段都没有**（`ApiDtos.kt:96-99`），非流式有但**全仓无生产代码读取**（只有 DTO 单测引用）|
| 2 | **记账只覆盖一条路径**：`recordTokens` 全仓仅 `ReActLoop:123` 一处 → Radio / Enrich / Hello / 报告 / 画像全部不记账。**后果不是"数字偏低"，而是配额熔断对自己最大的消费者失明** —— `decideEnrichState()` 用 `tokenCounter.shouldStop(0.9)` 管 Enrich 暂停，而 Enrich 的消耗不入账 → `quotaOk` **恒真**，这道闸门对 Enrich 永远开着（详见 `../design/agent-token.md` §1）|
| 3 | **口径混淆**：一个 `estimatedTokenCount` 同时当"窗口占用"与"日消耗" |
| 4 | **日配额是假控件**：设置页滑块写进 `GlobalAgentConfig`，但 `GlobalTokenCounter.dailyTokenQuota` 是 `val` 且唯一构造点只传 timeProvider → **永远 500K** |
| 5 | **窗口按 Agent 硬编码**（Enrich 32K / Radio 64K / Hello 128K，与所选模型无关）→ 换小窗口模型即**静默故障**：超窗 → API 报错 → 电台判定变 `none`，图上看不出 |
| 6 | **压缩空转**：`buildMessages` 只发 `takeLast(6)` → 窗口占用天然接近不了上限 → 压缩分支几乎永不执行，且摘要不回注 |
| 7 | **无分账维度**：唯一账本是 `GlobalTokenCounter` 的**单个累加数**，无 agent / 端点 / 时间维度 → **"哪个 Agent、哪个端点在什么时候花了多少"答不了**（而这正是本阶段的最终目标）|

本阶段是**基础设施修补**：不改 agent 的决策能力与对外契约，只补"真值计量 + 分账账本 + 窗口护栏"。设计细节见 [`../design/agent-token.md`](../design/agent-token.md)，本文件只记推进与验收。

> **边界（2026-09-19 裁定）**：① **窗口固定 64K、上下文降级不对用户开放配置**（不设"上下文上限"、不设"压缩方式"、不做窗口探测/内置表/端点覆盖）——理由：OpenAI 与 DeepSeek 官方**都不返回窗口**，与其做"探测 + 多级回退"却仍留空洞，不如规定下限 + 固定假设；② **token 明细永久保留**（不做清理、暂不做日桶）；③ **配额（限额）候补** —— 先放开，不作为本阶段交付。

---

## 2. 交付内容（七断层 → 五个交付）

| 断层 | 交付 |
|---|---|
| 真 usage 无通道 / 记账只一处 / 口径混淆 | **T1 计量打真值**：`LlmEvent.Usage` + 进程级 `TokenMeter`（唯一记账口）+ 估算降级为兜底并打标 |
| **无分账维度** | **T1 落账本**：Room 表 `token_ledger`（每次调用一行明细，含 agent / 端点 / 模型 / 时间 / 实测标记）|
| 无分账视图 | **T2 分账视图**：从明细聚合出**按 Agent / 按端点·模型 / 按时间**三个维度；看板数字改由明细聚合 |
| 窗口按 Agent 硬编码 → 静默故障 | **T3 窗口治理**：窗口 = 固定 64K 常量；超窗**前置拒绝**并走本地保底；失败原因**与"模型判 none"可区分** |
| 压缩空转 + 摘要不回注 | **T4 上下文组装策略**：区分"常驻裁剪"（主机制）与"超窗降级"（安全网）；摘要必须回注；收敛两处保留条数魔数 |
| ~~日配额是假控件~~ | ⏸ **候补**（T2b）：配额先放开；**搬回前必须先修断层 2 的「Enrich 熔断失明」** |

---

## 3. 任务清单（F12-T1..T5；T2b 候补）

| ID | 任务 | 涉及文件 | 验收 |
|----|------|---------|------|
| **F12-T1** | **计量打真值 + 落账本**：`LlmEvent` 增 `Usage(promptTokens, completionTokens, cachedTokens)`；各端点 SSE / 非流式响应把 `OpenAiUsage` 转成该事件发出（**请求侧补 `stream_options.include_usage`；`OpenAiStreamChunk` 目前连 `usage` 字段都没有，须补**）；新增进程级 `TokenMeter`（**唯一记账口，一次调用写三处**：当日累加 + 窗口占用 + 明细）；**新增 Room 表 `token_ledger`**（`AppDatabase` v8→**v9** + `MIGRATION_8_9`，照 `agent_audit_log` 写法）；**出口收口**：`LlmCallExecutor` + `AgentContextBudget.callLlm/callLlmText`，**并把 `RadioSubAgent.askJudge` 那条自建路径（`:1005-1020`）并入**；估算降级为 fallback 并打标 `measured=false` | `port/LlmTransport.kt`、`data/network/*`、`route/dto/ApiDtos.kt`、`runtime/TokenMeter.kt`（新）、`runtime/AgentContextBudget.kt`、`runtime/ReActLoop.kt`、`data/database/TokenLedger.kt`（新）、`data/database/AppDatabase.kt`、`sub/RadioSubAgent.kt` | Fake 发 `Usage` → `GlobalTokenCounter` / budget 窗口占用 / **`token_ledger` 新增一行** 三者**都记到真值**；**master / radio / enrich / hello 四条路径都入账**（现状只有 master）；不返回 usage 时落估算且 `measured=false`；`token_ledger` 里**无 apiKey、无完整 URL**（S1 / S12）|
| **F12-T2** | **分账视图（主交付）**：从 `token_ledger` 聚合三维度 —— **按 Agent** / **按端点·模型** / **按时间**（小时桶 + 天桶），窗口"今天 / 7 天 / 30 天 / 全部"；**看板数字改由明细聚合得出、不再依赖 `GlobalTokenCounter`**；升级 `AgentMonitorScreen.TokenMonitorCard`（现只有一条进度条）为分账表 + 分时图。**不做**清理与日桶固化（明细永久保留）| `agent/AgentMonitorScreen.kt`、`data/database/TokenLedger.kt`、`runtime/TokenMeter.kt` | 按 Agent 聚合 → **四个 Agent 都出现**；按端点聚合 → 换端点后出现两行；聚合总和 = 明细之和；分时图能看出"某小时 Enrich 突增"；`measured=false` 被标出（S10 / S11）|
| ~~**F12-T2b**~~ | ~~**配额接线**（`dailyTokenQuota` 可变 + 热更新 + 熔断读真值）~~ → ⏸ **候补**（用户 2026-09-19："token 限额先放开"）。**搬回前必须先修 §1 断层 2 的「Enrich 熔断失明」** —— 否则闸门仍是摆设 | `runtime/GlobalTokenCounter.kt`、`chat/ChatKoinModule.kt`、`settings/pages/AIScreen.kt` | 候补，暂不验（原 S3）|
| **F12-T3** | **窗口治理（消除静默故障）**：新增 `EngineDefaults.AGENT_CONTEXT_WINDOW = 64_000`，**删除三处 Agent 硬编码**；`callLlm` 组装完 messages（含 system + tools）后估算 → 超 `64K × 0.9` = 57.6K 先降级、仍超则**拒绝发出**；失败原因与"模型判定 none"**可区分**；日志带窗口假设值；设置页加"端点须支持 ≥64K"声明 | `runtime/EngineConfig.kt`、`runtime/MasterAgent.kt`（3 处硬编码）、`runtime/AgentContextBudget.kt`、`settings/pages/AIScreen.kt` | 把窗口常量临时调到 4K → 出现"前置拒绝 + 本地保底"，**无 API 报错**；日志明确写"超窗"而非"模型不想动"；设置页**无**"上下文上限"控件 |
| **F12-T4** | **上下文组装策略**：明确区分**常驻裁剪**（每轮，决定基线）与**超窗降级**（安全网）；`compressHistory` 的摘要**回注**为 summary 消息；收敛 `AgentContextBudget.recentMessagesToKeep`(6) 与 `ChatAgentGateway.buildHistory`(30) 两处魔数为单一内部常量 | `runtime/AgentContextBudget.kt`、`chat/ChatAgentGateway.kt`、`chat/ChatViewModel.kt` | **单测直接构造超长上下文**跑通"降级 → 仍超 → 拒绝"路径（不得依赖真机复现）；摘要出现在后续 messages；两处消费点同值 |
| **F12-T5** | **成本可见（可选，后置）**：模型价目表（内置 + 可覆盖）→ 按 prompt/completion 分别计价 → 看板显示"今日 ≈ ¥x.xx" + 分 Agent 分账 | 新建价目表 + `AgentMonitorScreen.kt` | 见 `../design/agent-token.md` §4 T5。**收益**：把配额从抽象 token 变成钱。**代价**：价目表要维护（改名/调价即失效）→ 故后置 |

**顺序**：T1 → T2 → T3 → T4（T5 后置）。**T1 是唯一地基且最独立**，T3/T4 都依赖它提供的"真值 + 真占用"。

---

## 4. 待拍板（承接 `../design/agent-token.md` §6）

| 编号 | 决策 | 本阶段取向 |
|---|---|---|
| D1 端点不返回 usage 时 | (a) 估算兜底 + 打标 / (b) 视作 0 / (c) 报错 | **(a)**：打标是底线，否则看板真假混在一起（现状就是混的）|
| D2 日配额口径 | (a) 维持 total（prompt + completion）/ (b) 输入输出分权 | ⏸ **候补**（用户 2026-09-19："token 限额先放开"）。原建议 **(a) 维持**。**D2 与"配额启用"绑定，搬回时一并定** |
| ~~D3 模型窗口来源~~ | ~~探测 / 内置表 / 端点覆盖~~ | ✅ **已定：固定常量 64K**（2026-09-19 二次修订，取代同日早先的"四层取值链"）|
| D4 摘要压缩是否用 LLM | (a) 只做规则化摘要回注（不调 LLM）/ (b) 用轻量 LLM 摘要 | ✅ **(a) 已定**：避免"省 token 花更多 token"的递归问题 |
| ~~D6 上下文上限默认值与形态~~ | ~~默认 100K + 用户可设~~ | ❌ **已作废**（2026-09-19）：改为"固定 64K + 降级不开放配置" |
| D5 实施范围 | (a) T1+T2 / (b) T1–T3 / (c) 全做 | **(b) 建议**：T1 是地基；**T3 已无外部依赖**（窗口是常量），消除静默故障价值最高。**配额那一半（T2b）已候补** |
| D7 分账账本形态 | (a) **明细表** / (b) 日桶累计 / (c) 只有全局累计一个数 | ✅ **(a) 已定**（2026-09-19）：**明细表 + 永久保留**（不做清理、暂不做日桶）。理由：**明细是累计的超集，只存累计不可逆**；且项目有"只有分时能回答"的问题（Enrich 何时在烧）。**只存 host，不存 apiKey / 完整 URL** |

> ⚠️ **实现注意（R2）**：`estimateMessageTokens` 用 `length × 0.7`，对**英文/混合文本高估约 2.8 倍**（英语约 4 字符/token），而曲库标题/艺术家正是英文密集；且**两侧都不计工具 schema**（27 个 schema 每次调用都发）。**估算偏高 + 阈值偏紧 = 误杀** —— 所以 T1（真值）必须早于 T3（护栏），否则会把本来塞得下的请求拒掉。

---

## 5. 严格不做什么

- ❌ 不动铁则 **F2**（每 Agent 独立 budget + 独立 transport 物理隔离）——只把"估算"换成"实测"、把"窗口"变成固定假设，**不动隔离结构**
- ❌ 不动 `AgentScheduler` 的仲裁逻辑与阈值（`SCHEDULER_PAUSE_THRESHOLD` 0.9 / `REACT_STOP_THRESHOLD` 0.95）——配额搬回时只让它读到**真实**的 `usedToday()`
- ❌ **不引入本地 tokenizer**（多端一致性 + 体积代价高；实测 + 保守估算已足够）
- ❌ **不做窗口探测 / 内置模型表 / 端点级窗口覆盖**（事实核查留档在 `../design/agent-token.md` T3）
- ❌ **不向用户暴露"上下文上限"或"压缩方式"** —— 前者在 64K 假设下永不触发，后者是兜底机制而非用户偏好
- ❌ **不做"每 Agent 一个窗口"**（实测三个 Agent 峰值都远低于 32K，分档无依据）
- ❌ 不做多模态 / 图片 token 计量（当前无多模态输入）
- ❌ **不做明细清理、不做日桶固化**（明细永久保留，§3.1 体积估算 ~16 MB/年 可承受）
- ❌ **不把 `token_ledger` 当长期记忆用**：它**不喂给任何 LLM、不进 `UserMemory`**（与 F11「电台不落盘」的裁定不冲突 —— 那条管的是**喂给模型的状态**，这条是**给用户看的本地诊断数据**）
- ❌ **`token_ledger` 不存 apiKey、不存完整 URL**（只存 `endpoint_host`）
- ❌ **配额（限额）不在本阶段范围**（候补，T2b）；搬回前必须先修断层 2 的「Enrich 熔断失明」
- ❌ 不做 F10 语音会话、不碰 F11 的生命周期与保活（独立阶段）

---

## 6. 验收结果

> 2026-09-19 落地。T1–T4 全部完成并通过桌面 / 安卓编译 + 单元单测；T2b（配额）候补、T5（成本可见）后置。

| 项 | 验收标准 | 状态 |
|----|---------|------|
| `:shared:compileKotlinDesktop` / `:shared-ui:compileKotlinDesktop` | 编译通过 | ✅ |
| `:android:app:compileDebugKotlin` | 编译通过 | ✅ |
| `:shared:desktopTest` | 全绿（含新增计量 / 超窗 / 压缩用例）| ✅ |
| **S1 真值入账** | Fake 发 usage → `GlobalTokenCounter` / budget 窗口占用 / **`token_ledger` 新增一行** 三处都记到真值；**master / radio / enrich / hello 四条路径都入账** | ✅ |
| **S2 无 usage 兜底** | 不返回 usage → 落估算且 `measured=false`，看板可见区分 | ✅ |
| ~~**S3 配额真生效**~~ | ⏸ **候补** —— 配额启用时再验；搬回前必须先修断层 2（Enrich 熔断失明）| ⏸ |
| **S4 超窗不炸** | 极小窗口 + 长上下文 → 前置拒绝 + 本地保底出声，**无 API 报错** | ✅ |
| **S5 压缩有回注** | 超长历史 → 触发压缩 → 摘要出现在后续 messages 中 | ✅ |
| **S6 电台仍出声** | 上述任一降级路径下，电台不冷场（本地保底）| ✅ |
| **S7 窗口是常量、不是设置项** | 设置页**无**"上下文上限" / "压缩方式"；窗口取自单一常量；三处硬编码已删 | ✅ |
| **S8 超窗可辨（最关键）** | 失败原因明确是"**超窗**（含窗口假设值 64K）"，**而非**"模型判定 none" | ✅ |
| **S9 安全网被验证过** | 超窗降级路径有**单测直接覆盖**（不依赖真机复现）；常驻裁剪两处同值 | ✅ |
| **S10 分账维度完整** | 按 Agent 聚合 → **四个 Agent 都出现**；按端点·模型聚合 → 换端点后出现两行；聚合总和 = 明细之和 | ✅ |
| **S11 分时可查** | 小时桶 / 天桶序列能看出"**某小时 Enrich 突增**"；`measured=false` 条目被标出 | ✅ |
| **S12 账本不泄密** | `token_ledger` 中**无** apiKey、无完整 URL（只有 `endpoint_host`）| ✅ |
| **S13 永久保留可承受** | 模拟连续写入后查询仍快；体积与 §3.1 估算同量级（若远超则启用"行数软上限"后手）| ✅ |
| **S14 Room 迁移** | v8→v9 迁移代码就位 + `exportSchema` 一致；编译期 Room 校验通过 | ✅ |

---

## 7. 经验与踩坑

> 立项时已积累（来自 2026-09-19 的诊断），落地后继续补。

- **"设置项提供了" ≠ "设置项生效了"**。判据只有一条：**改了它，行为会不会变？** 「日配额」写进了配置但运行时读常量；「压缩方式」会被"上限的 85%"卡住——两者都不是笔误，是**设置的作用域没接上**。**新增任何设置项，验收必须包含"改它 → 观察到一个可断言的差异"，否则它只是个装饰。**
- **"用户可设"不是免费的好设计**：当一个值在正常工作区间内**永不生效**时，它就是一个假控件。窗口"默认 100K"对比实测峰值 1–3 万即属此类（永不触发）。**一条配置项的合理性，取决于它能否在真实工作区间被触发；触发不了的，应删而非留。**
- **"规定一个前提"必须配一条"前提被违反时的可观测信号"**：本项目规定"端点须支持 ≥64K"，但**拿不到窗口 → 无法在配置时校验** → "超窗显式化"不是可选项。没有它，"规定最小 64K"只是文档里的一句话，线上出问题时依旧表现为"AI 不灵了"。
- **"协议兼容" ≠ "字段兼容"**：OpenAI 兼容端点里只有 `chat/completions` 的语义是强约束的，`/models` 是各家自由发挥的接口（OpenAI / DeepSeek 官方**不返回窗口**，OpenRouter / vLLM 返回）。判断某字段能否自动拿到，**必须逐家看文档，不能从"兼容 OpenAI"推**。
- **安全网 ≠ 死代码，但需要证据**：超窗路径在 64K 假设下几乎不执行 → 它实际上是**未验证代码**。必须用单测直接构造触发条件跑通，**不允许靠"真机上应该不会发生"来兜**。
- **"压缩"与"裁剪"是两件事**：常驻裁剪（`takeLast`）每轮都跑、决定基线大小，是**主机制**；超窗压缩只在逼近窗口时触发，是**安全网**。项目现状把后者挂在窗口百分比上，于是它几乎永不执行——**根因不是实现写错，是把安全网当成了主机制**。
- **多个派生自同一 `dataStore.data` 的热监听必须各自 `distinctUntilChanged`**，否则被任意无关写入（播放进度每约 5s 写一次）反复唤醒。T2 的配额热更新复用该链路，须沿用此结论。
- **"熔断看不见自己"——最危险的一类计量缺陷**：`decideEnrichState()`（`AgentScheduler.kt:169-174`）用 `tokenCounter.shouldStop(0.9)` 管 Enrich 运行，而 **Enrich 的 token 不入账** → `quotaOk` **恒真**，闸门对自己的最大消费者永远开着。这比"数字偏低"严重一个量级：**数字不准只是看不清，闸门失明是不设防。** 通用判据 —— **凡是"用 A 控制 B"的机制，先验证"B 的行为是否真的回流到 A 里"**；不成立则该机制等于不存在。
- **同一件事多套实现，是计量漏收的典型形态**：四条 LLM 调用路径各不相同 —— Master 走 `ReActLoop`（入账）、Enrich / Hello 走 `contextBudget.callLlmText`、**Radio 自建 `LlmCallExecutor()` 并直接取 `contextBudget.llmClient`**（`RadioSubAgent.kt:1005-1020`，连 budget 包装都绕过）。这是"接线要单独维护"在计量上的翻版：**收口点不唯一，就必然漏。** T1 的意义正在于把出口收敛为两个（`LlmCallExecutor` + `AgentContextBudget.callLlm/callLlmText`）。
- **估算的两个坑同时存在**：① 它是估算而非实测；② 它**不含工具 schema** —— 而 `tools` 每次调用全量发出（27 个原子工具），量级不小却完全不在账上。**"高估"与"低估"会出现在同一次调用里**（英文内容高估 ~2.8×、工具 schema 完全不计），所以**别指望调系数修准，必须换真值**。
