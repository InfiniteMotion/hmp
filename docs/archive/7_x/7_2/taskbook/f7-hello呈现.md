# HMP F7 Hello 呈现

> **上游**：`../design/agent.md`（设计总纲）｜`../design/agent-architecture.md`（架构详解）
> **计划书**：`README.md`（总纲与生命线）
> **轴别**：插叙
> **阶段族**：W0-W2 设计文档 → W0 HelloSubAgent 落地 → HelloSlideCards 落地
> **提交**：`e4bf4da` · `2db3374` · `006ab99`

---

## 1. 阶段定位

**先把设计写全，再一天一个可用件。**

`e4bf4da` 一口气建立 W0–W2 三份设计文档 + HelloSubAgent 独立方案（纯文档，不改代码），接下来两笔按方案先后落地引擎侧（`HelloSubAgent`）与 UI 侧（`HelloSlideCards`）。三笔共享同一套设计文档、同一批新文件，是**「设计包 → 引擎落地 → 呈现落地」**的标准三段。合并为一族。

> **前置**：引擎侧能力已齐（`PresenceBus` 在发事件、`MasterAgent` 有问候语逻辑、三个骨架组件已写好但接线只做一半）。本阶段核心是**把引擎事件接到 UI 消费点上，让用户能真正感知到**。

## 2. W0-W2 设计文档体系（`e4bf4da`）

> 四份文档已于 2026-09-15 合并为一份 [`../design/agent-w.md`](../design/agent-w.md)（W0 / W1 / W2 三节 + 末尾缺口登记表）。

| 文档 | 内容 |
|------|------|
| [`../design/agent-w.md`](../design/agent-w.md) | W 阶段全景：W0 引擎侧 HelloSubAgent 完整实现 → W1 页面级呈现规格（P1–P5）→ W2 组件级规格（C1–C11）+ W 缺口登记 G1-G16 |

**W 阶段页面清单（P1–P5）**：

| 编号 | 页面 | 规格要点 |
|------|------|---------|
| P1 | 主页 | 区域①堆叠卡 + 区域②电台/推荐 + 区域③入口 |
| P2 | 伙伴设置页 | 五分区（全局/Master/Hello/Enrich/Radio）+ 按 agent 信任档位/Temperature |
| P3 | 对话页 | 五类气泡 + 回看滚动 + 问候区 + 打字机 + 正在听卡 + 执行中态 |
| P4 | 看板 | AgentMonitorScreen 概览仪表板（Token/Trust/运行态）+「只看异常」Filter |
| P5 | 报告页 | 图表复用 `ListeningChart` + 伙伴叙事段 |

## 3. W0 HelloSubAgent 落地（`2db3374`）

HelloSubAgent 是第三个 SubAgent（继 Enrich、Radio 骨架之后），职责是**门面问候与主动呈现**——DJ 衔接语的生成者、门面问候句的来源、卡片池的内容提供者。

严格遵守 T 阶段铁则 F1–F6：Master 是唯一大脑，Hello 只执行；独立 `AgentContextBudget`；prompt 由 Master 注入；无状态执行器。

## 4. HelloSlideCards 落地（`006ab99`）

**卡片 Pager + 5 种 Agent 卡 UI + CardPool 体系**——把 W1 的 P1 主页规格（区域①堆叠卡）实现为可交互组件。

| 件 | 内容 |
|----|------|
| `HelloSlideCards` | 卡片 Pager 容器（横滑堆叠） |
| 5 种 Agent 卡 | 按 `render_hint` 家族渲染 |
| `CardPool` | 卡片池体系（内容供给 + 生命周期） |

## 5. 验收结果

| 项 | 结果 |
|----|------|
| 设计文档 | ✅ W0–W2 三份 + 缺口登记建立 |
| HelloSubAgent | ✅ 完整落地 |
| HelloSlideCards | ✅ 卡片 Pager + 5 种卡 + CardPool |
| 编译 | ⚠️ 当前改动在未提交 diff 中积累，提交前须统一跑 `:shared-ui:compileKotlinDesktop :shared:desktopTest` 兜底 |

**退出**：Hello 呈现层可交互；存在感四形态之「门面问候区」有内容源。

## 6. 经验与踩坑

- **设计与落地分两天**：`e4bf4da` 纯文档（3 份 + 独立方案），随后两天各落一个可用件。**先写全设计再动手**的好处是：落地时不需要临时决策，两笔代码提交各自聚焦一个面（引擎 / UI），review 边界清晰。
- **W0 文档与 W0 代码同名不同事**：`e4bf4da` 里的 "W0-W2" 指设计文档批次，`2db3374` 里的 "W0" 指 HelloSubAgent 落地——同一编号在两处的粒度不同，是命名歧义（后续文档已统一到章节制解决）。
- **`e4bf4da` 与 `2db3374` 无文件重叠**：前者只新增 `docs/` 下三份 md，后者只改 `shared/` 下 Kotlin 源码。**这是本族中唯一一对零重叠的提交**，合并的依据是"同一份设计包的实施"，而非文件连续性。
