# 灵感 / 候选项日志（Ideas & Backlog）

> **这是什么**：一个轻量的灵感收纳处。用于记录「以后可能想做、但现在不排期、不承诺进版本」的想法。
> **不是什么**：不是单一事实源（那是 ROADMAP.md），不是可执行任务表（那是 TODO.md），不是设计文档。
> **约定**：
> - 每条按日期追加，不要删改旧条目，只在末尾续写或更新其「状态」。
> - 状态标记：`💡 灵感`（刚记下）/ `🔶 待评估`（值得 revisit）/ `⏸ 挂起`（已决定暂不排期、整体延期）/ `✅ 已立项`（已升格为方向/任务，移出本文件）。
> - 立项时，把内容提炼进对应设计文档 + TODO 桩，并回头把本条状态改为 `✅ 已立项` 并注明去向。

---

## 2026-09-23 · 让本应用的 Agent 能力可被外部 Agent 调用 / 委派任务

**状态**：💡 灵感（未承诺进任何版本）

**缘起**：方向 B（AI Agent 化）已铺好工具注册表、护栏体系、F11 后台生命周期三块地基，应用的能力「外溢」给本机其他 agent 成本低。想探索「被调用」与「被委派任务」两种集成。

**两条诉求对应两个协议**：
- 「让其他 agent 调用本应用的工具」→ **MCP（Model Context Protocol）**，事实标准，JSON-RPC，`tools/list` + `tools/call`，与现有工具注册表同构。
- 「让其他 agent 委派任务给本应用的 agent」→ **A2A（Agent2Agent, Google 2025）**，agent card 自描述 + task submit + 状态回传，agent 级而非函数级。

**统一生命周期契约（关键结论）**：
```
收到调用意图 → 平台把进程拉起（stdio spawn / Intent / URL Scheme）
            → Gateway 启动 localhost MCP 服务（幂等：已运行则复用）
            → 处理 tools/call
            → 所有 MCP 会话空闲超时 → 服务自关（进程可退出 / 回后台）
```
- 三端**统一「按需启动、用时服务、空闲自关」**，差异只在「用什么拉起」。
- 不依赖 F11 后台生命周期，网关可独立排期。
- 启动触发器：Desktop = 调用方 spawn 无头 `hmp-mcp` 子进程（stdio）或 URL 拉起 GUI；Android = 其他 app 显式 Intent 拉起；iOS = 其他 app `open hmp://mcp/start` URL Scheme 拉起前台。
- 需固化：幂等启动（复用已运行服务）、就绪信令（/health 供调用方轮询）、空闲自关条件 = 无活跃会话 **且** `launchedByAgent`（用户正用着 app 不能自关）、认证 token（走 SecureStorageHelper）。

**安全 / 隐私护栏（纯本地项目的命门）**：
- 仅 bind `127.0.0.1`，绝不开 `0.0.0.0`。
- 本地随机 token，设置页可查看/重置。
- 用户显式开关，**默认关**；能力 allow-list 复用 `TrustTier`。
- 破坏性操作即便远程调用仍弹应用内 ConfirmGate —— 护栏随工具走。
- 守产品边界：不引入云端，保持纯本地。

**分阶段设想（仅灵感，未排期）**：
- P0（Desktop 优先）：`shared` 加 `LocalAgentGateway`（KMP 纯逻辑，工具注册表 → MCP tool schema + task handler）；Desktop 用 Ktor 起 `POST /mcp`（Streamable HTTP），先暴露 `searchLibrary / getNowPlayingContext / controlPlayback`（只读+控制），用 Claude Desktop / mcp CLI 连上验证。
- P1（Android）：bound Service 持有同一 gateway，设置页开关 + token。
- P2（iOS）：on-demand 启动 + 空闲自关，文档写清限制。
- P3（A2A）：`/.well-known/agent.json` 暴露 agent card + task submit 端点，复用现有 agent loop。
- P4（反向，可选）：本应用 agent 也能作为 MCP client 调外部工具，对称能力。

**待决 / 风险**：iOS 不常驻（定位「前台可服务 + Desktop 全功能」）；端口发现（固定 / 随机写文件 / mDNS）；A2A 生态仍新，可后置或仅做最小 agent card。

**备注**：本次仅为脑暴记录，未创建任何设计文档或任务条目。若未来立项，按 `docs/7_x/D agent-interop/` 模式建 design + taskbook，并在 TODO 加「方向 D（候选）」桩。

---

## 2026-09-23 · F10 语音会话（RealtimeVoiceTransport）

**状态**：⏸ 挂起（独立阶段，整体延期，未开工）

**背景**：方向 B（AI Agent 化）主线 F1–F9、F11–F14 为 Agent 工具/编排/界面主线；F10 语音会话是**独立阶段**，与工具注册表、编排、后台生命周期均解耦，可整体后移而不阻塞主线。当前 `feature/agent-build` 分支上 F10 仍为「未开工」状态。

**内容**：基于 `RealtimeVoiceTransport` 的实时语音会话能力 —— 让 Agent 具备语音输入/输出通道（语音唤醒、流式语音对话等）。具体接口、平台适配（尤其 iOS 实时语音权限与后台限制）、与现有文本对话的融合方式，尚未展开设计。

**决策**：暂不排期，挂起留待后续阶段评估。重新激活时，需回到方向 B 任务书确认 F10 的触发条件与依赖（如是否依赖方向 B 收口后的 Agent 体系稳定性）。

**备注**：此条与上方「Agent 互操作」为两条独立灵感，互不影响、可分别立项。

---

## 2026-09-23 · 应用 Agent 化：既有页面（搜索 / 歌单 / 播放）的 Agent 落地

**状态**：💡 灵感（未承诺进任何版本）

**背景**：方向 B（AI Agent 化）推进中，目前主要完成了**页面级入口的改造**（入口 / 触达层的重构），但**既有具体页面本身尚未做 agent 能力的落地**——用户早已在用的搜索页、歌单页、播放页，还只是传统交互页面，没有把 Agent 的工具 / 智能能力真正缝进这些页面的交互闭环。

**核心认知**：当前是「入口改造先行，页面落地滞后」。Agent 的工具链（工具注册表、护栏、编排）已具备，缺的是把这些能力**落到既有页面**，让用户在使用搜索 / 歌单 / 播放页时自然触发 agent，而非只在独立对话界面里对话。

**待落地方向（示例）**：
- **搜索页**：让 agent 能理解自然语言查询并直接驱动搜索 / 智能筛选（底层 `searchLibrary` 工具已注册，但页面本身未与 agent 联动）。
- **歌单页**：agent 基于对话创建 / 整理歌单（底层 `createPlaylist` / `addToPlaylist` 已注册，但页面侧未做"对话即生成 / 整理歌单"的落地）。
- **播放页**：agent 基于场景 / 心情续播、控播（底层 `controlPlayback` / `getNowPlayingContext` 已有，但页面未提供"对播放页唤起就能控播"的入口）。

**与既有条目的关系**：这是方向 B 内部的深化 / 收尾，区别于「Agent 互操作」（对外暴露现有工具）。若未来做对外暴露，「既有页面的 agent 落地」是其内部前提——页面先具备 agent 能力，才能被外部 agent 可靠调用。

**待决**：各页面 agent 落地的具体形态（内嵌对话气泡？长按唤起？智能建议条？）、与 F10 语音会话的协同、入口是新增组件还是复用现有交互、优先级排序（搜索 / 歌单 / 播放先做哪个）。

---

## 2026-09-23 · 全局设置：应用震动 / 触感调节

**状态**：💡 灵感（未承诺进任何版本）

**背景**：想在设置里加一个**全局开关 / 档位**，让用户统一调节应用的震动（振动 / 触感反馈）强度。例如：关闭 / 轻 / 标准 / 强 四档，或单个总开关 + 强度滑杆。目标是把分散在各处的震动调用收敛到一处用户可控制的策略。

**核心设想**：
- 新增设置项（建议归在「设置 → 通用 / 反馈」或单列「触感反馈」分组），持久化进现有 `dataStore`，与其他偏好同链路。
- 应用内所有会触发震动的地方（切歌 / 跳过 / 通知 / 某些确认反馈等，待排查现有调用点）统一经一个 `HapticPolicy` / `VibrationController` 门户读取当前档位后决策，避免各页面各写各的 `Vibrator`。

**跨平台差异（关键待决）**：
- **Android**：`Vibrator` / `VibrationEffect`（API 26+），可直接控制时长 / 波形 / 振幅，能力最完整，是主要落地平台。
- **iOS**：`UIImpactFeedbackGenerator` / `UINotificationFeedbackGenerator` 等**分级触感**，无"振幅"概念，只能选风格（light / medium / rigid / soft / heavy）；档位需映射为"风格 + 是否启用"，不能精确控强。
- **Desktop**：无原生震动物理通道（除非外接设备），可降级为"无触感"或仅做 UI 视觉反馈（如按钮按压态），档位对其基本无意义——需决定是否在 Desktop 隐藏该设置。

**与此前灵感的关系**：与 Agent / 语音类灵感无直接耦合，属独立的产品打磨项；但同样是「跨端能力抽象 + 设置页收口」的同类项，落地时可复用既有的 `SettingsRepository` / `dataStore` 与 `expect/actual` 平台桥接范式。

**待决**：现有哪些交互已经在震动（需先 grep 调用点，避免新增策略后老代码绕过）；档位是"总开关 + 强度"还是"预设档位枚举"；Desktop 是否隐藏该设置；是否区分"媒体控制震动"与"通知震动"两套子策略。

---

## 2026-09-23 · Desktop 播放引擎改用 JavaCPP/JavaCV 的 FFmpeg 绑定

**状态**：💡 灵感（未承诺进任何版本）

**缘起**：Desktop 侧目前靠**外部 ffmpeg 可执行文件**解码 —— 构建期从第三方站点下载预编译二进制，运行期 `ProcessBuilder` 起进程、读 stdout 的裸 PCM 喂给 `SourceDataLine`。这条链路在 2026-09 暴露出一整类问题：下载型依赖的**架构必须匹配终端用户**，而构建脚本只判断了操作系统（`isMacOS`），没判断 CPU 架构。Apple Silicon 上因此拿到 x86_64 二进制，未装 Rosetta 时直接 `Exec failed, error: 86 (Bad CPU type in executable)`。

**核心认知**：这不是"某个 URL 选错了"，而是**依赖形态选错了**。预编译二进制是"按 URL 取货"，构建系统看不见它的架构；而按 Maven 坐标解析的依赖，架构信息在构建系统可见的范围内，会被自动匹配。

**设想**：改用 `org.bytedeco:ffmpeg-platform`（JavaCPP presets），原生库按平台分类器由 Gradle 自动解析对应架构 —— 正是"在 macOS 上编译运行应当自动匹配"的语义。同时把 `ProcessBuilder` + stdout 管道改写成 JavaCPP 的 API 调用（`avformat_open_input` / `avcodec_send_packet` 等），省掉一次进程间管道搬运。

**收益**：
- 从根上消灭"下载型依赖架构不匹配"这一整类问题，不再需要维护 (OS × 架构) 的 URL 清单与 SHA256。
- 不再依赖第三方站点可用性；`ffmpeg-platform` 走 Maven Central，随依赖缓存。
- 免去子进程：无 `ffmpeg` 可执行文件的外部依赖，release 包不再需要"把二进制塞进 `runtime/bin`"这套注入逻辑（连带消灭注入时序类 bug）。
- 便于后续做精确 seek / 逐样本处理 / 音效链（与方向 C 的播放增强有协同）。

**代价 / 风险**：
- 依赖体积显著增大（`ffmpeg-platform` 会把各平台原生库都拉下来，需用 `ffmpeg` 而非 `ffmpeg-platform` 或做 classifier 裁剪）。
- 解码链路要重写：现有 `FFmpegAudioEngine` 的 seek、暂停、进度统计、stderr 诊断都基于"进程 + 字节流"模型，改成 API 调用后这些都要重新实现。
- JNI 调用边界上的内存管理（`AVFrame` / `AVPacket` 的 release）容易泄漏，需谨慎。
- 量级接近方向 C 的一个子项，不宜顺手做。

**与既有条目的关系**：与「桌面端 FFmpeg 获取可靠性」是同一问题的两种解法 —— 当前已按"固定 URL + SHA256 + 按架构选择"修好（低成本、保留外部进程模型）；本条是**根治型**替代方案，等方向 C（播放功能增强补齐）启动时一并评估是否值得切换。

**待决**：是否真的需要平台裁剪（`ffmpeg` vs `ffmpeg-platform`）；重写 seek/暂停语义的成本；是否借机把 Desktop 播放引擎与 Android 的 Media3、iOS 的 AVFoundation 做更统一的抽象（三者目前是三套独立实现）。

---

## 2026-09-24 · 统一的 Agent 启动闸门（per-agent 端点可用性 + 有无工作可做）

**状态**：🔶 待评估（已有方案草案，未排期；不进 v7.2）

**缘起**：桌面端实测日志里，`EnrichSubAgent` 在**端点未配置**的情况下持续空转——`hasLLM=true | endpoint= | hasKey=false`，每轮「1 次 DB 查询 + 1 次必然失败的 LLM 调用」，攒满 5 次失败后退避 15s，计数清零后从头再来，只要应用开着就永不停止。有效信息被几十行 `callLlmText failed` 淹没。

**核心认知（这也是本条值得单独立项的原因）**：这不是 Enrich 一个 agent 的 bug，而是**「Agent 生命周期缺少统一的启动前置条件」**。体系里每个 agent（master / chat / enrich / radio / hello）都可以有**自己的端点覆盖**（`SettingsRepository.getAgentEndpointConfig(agentId)`），那么「能不能发请求」就必然是**每个 agent 各自一份**的判据——现在却是各写各的：
- `HelloSubAgent` 自发做了 `enableLlm = helloTransport != null && helloConfig?.isConfigured == true`（局部解，恰好对了）；
- `MasterAgent.updateAiConfig()` 里 `perAgent[..]?.takeIf { it.isConfigured } ?: globalConfig` 的**兜底分支没过闸**，于是拿到一个"存在但发不出去"的配置；
- `isConfigured` 本身只是设置页写下的标记，`true` 时 endpoint/apiKey 仍可能为空，不能当作"能发请求"的依据；
- 失败重试策略也是各写各的（Enrich 只有「连续 5 次 → 固定 15s」）。

**设想**：抽一层通用闸门，让"该不该启动、该不该发请求"只有一处定义。

1. **领域层单一判据**：`AiEndpointConfig.isUsable = endpoint.isNotBlank() && apiKey.isNotBlank()`，替代散落的 `isConfigured` / `!= null` 判据。
2. **Master 侧统一闸门**（对齐 F1：Master 决策外部生命周期），per-agent 求三道闸：
   ```
   ① 端点闸  resolveEndpoint(agentId).isUsable
   ② 工作闸  agent.hasWork()          // enrich=有待富化项；radio=有队列可续；hello=有卡片可生成
   ③ 策略闸  policy.enabled           // 如 enrichPolicyConfig.enabled
   三者全过 → 创建/启动；任一不过 → 不创建（或 stop）
   ```
3. **生命周期闭环**：端点「不可用→可用」或工作「无→有」→ 启动；反向 → `stop`。触发点现成的有 `updateAiConfig()`（设置页保存 / 启动），曲库侧可用 `musicRepository.getMusicCount()` 订阅补「扫完才开工」。
4. **agent 内部只留兜底护栏**，不重复实现主判据：端点运行期被清空 → 挂起不发请求；连续失败 → **指数退避（15s→30s→…→上限）**，成功或配置变更即重置。
5. **可观测**：`CapabilityState.detail` 区分「未配置端点」/「无待处理项」/「已达标」——现在三态都显示 IDLE，看不出是没配还是做完了。

**收益**：
- 从根上消灭「端点不可用时空转」这一整类问题，且**对新增 agent 自动生效**（接入闸门即获得，不用每个 agent 自己记得判）。
- per-agent 端点覆盖的语义真正落地：A 配了 key、B 没配，就只有 A 跑。
- 日志信噪比恢复；Scheduler 的 token 预算不再被必然失败的调用挤占（对 F12 token 治理是正相关的）。
- Hello 的 `enableLlm` 这类局部解收敛进统一闸门，减少"两个 agent 两种判据"的漂移。

**代价 / 风险**：
- **最大风险是"收紧后没人启动"**：闸门一收，触发点必须逐个补齐（`updateAiConfig` / 扫描完成 / 设置页开关），漏一个就变成某个 agent 永不启动。**这条不闭环，不如不改。**
- 每个 agent 的「有工作可做」谓词不同，需要各自定义，是本次设计的主要工作量。
- 启动/停止变频繁后要确认幂等（`startEnrich` 已有 mutex + 先 stop 再建的写法，可复用）。

**与既有条目的关系**：与 Agent 类灵感同源，但与「MCP / A2A 外溢」无耦合。若落地，与方向 B 的 A0（Capability 统一化）、F12（token 计量）天然衔接——`hasWork()` 可以考虑直接作为 `Capability` 接口的一个成员，与 `CapabilityStatusTool` 同一层。Enrich 那一处的**最小修复**（只加端点闸 + 闭环）是通用框架的一个特例，两者不冲突，可以先修后再评估是否升格本条。

**待决**：`hasWork()` 放哪里（扩 `Capability` 接口 vs Master 内 `when` 分支表）；是否所有 agent 都允许端点覆盖（UI 侧现状需核对）；触发点清单是否还有遗漏（如备份还原、语言切换后重算）；UI 是否要把「未配置端点」做成可点击跳转设置页的提示。
