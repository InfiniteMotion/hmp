# Hearable Music Player — 项目文档索引

本索引覆盖 `docs/` 目录与仓库根目录下的全部文档，说明各自职责与当前状态。

> **维护约定**：新增 / 移动 / 删除文档时同步本索引。文档与代码冲突时**以代码为准**，改文档。

## 📌 一句话分工

| 想知道什么 | 去哪儿 |
|---|---|
| 项目是什么、怎么装、怎么用 | 根目录 [README.md](../README.md) |
| **版本历史与功能状态（单一事实来源）** | 根目录 [ROADMAP.md](../ROADMAP.md) |
| 现在要做什么、优先级 | 根目录 [TODO.md](../TODO.md)（**v7.3.0 的施工分解**见 [7_3/domain-baseline.md](7_3/domain-baseline.md)） |
| 技术架构、模块划分、开发流程 | 根目录 [DEVELOP.md](../DEVELOP.md) |
| 给 AI 协作者的项目速查（**唯一真源**） | 根目录 [AGENTS.md](../AGENTS.md) |
| 版本号规范 / 发版流程 / 分支策略 / CI 与产物现状 | [spec/hmp-release.md](spec/hmp-release.md) |
| **v7.3.0 施工基线（九域 + 横切线，唯一工作分解入口）** | [7_3/domain-baseline.md](7_3/domain-baseline.md) |
| 架构实现审查（2026-09-28 / 09-30，**历史证据，非施工入口**） | [7_3/review-7.3-architecture.md](7_3/review-7.3-architecture.md) / [7_3/review-7.3-code.md](7_3/review-7.3-code.md) |
| 代码风格门禁（ktlint）落地方案 | [ktlint-integration.md](ktlint-integration.md) |
| 历史版本的开发方案（已完成，备查） | 本目录 `archive/5_x/`、`archive/6_x/`（两层：<家族>/<版本>） |
| **当前版本线（v7.3.0）的域分片与施工条目** | [`7_3/`](7_3/domain-baseline.md) → `7_3/domain/` + `7_3/taskbook/` |
| 已收口版本线的设计资料与推进计划 | [`archive/7_x/`](archive/7_x/)（`7_1` 共享 UI 提取线 / `7_2` Agent 体系） |
| 日志门面与 Tag 规范 | [spec/hmp-log.md](spec/hmp-log.md) |

***

## 📂 活跃文档（随开发同步维护）

位于仓库根目录与 `docs/` 顶层，是日常查阅的一线文档。

| 文档 | 位置 | 用途 |
|------|------|------|
| **README** | [../README.md](../README.md) | 项目概览、功能简介、安装与使用、贡献说明 |
| **ROADMAP** | [../ROADMAP.md](../ROADMAP.md) | **单一事实来源**：版本历史、功能状态（已完成 / 计划中）、技术演进、未来方向 |
| **TODO** | [../TODO.md](../TODO.md) | 可执行任务列表与优先级，按版本阶段组织 |
| **DEVELOP** | [../DEVELOP.md](../DEVELOP.md) | 技术架构、模块划分、开发流程、测试与构建、关键实现说明 |
| **AGENTS** | [../AGENTS.md](../AGENTS.md) | **AI 协作者须知的唯一真源**：硬约束 / 常用命令 / 模块拓扑 / 跨平台机制 / 三端引擎差异 / Agent 子系统 / 已知地雷 / 版本与分支策略。跨工具（Claude Code、Codex、Cursor、Qoder）都读它 |
| **CLAUDE** | [../CLAUDE.md](../CLAUDE.md) | Claude Code 入口指针：`@AGENTS.md` 导入 + Claude 专属差异（Skill 清单、`.claude/settings.local.json`）。**不承载正文**，改动一律落到 `AGENTS.md` |
| **spec/hmp-release** | [spec/hmp-release.md](spec/hmp-release.md) | 版本号格式（MAJOR.MINOR.PATCH）、何时升级哪一位、真源分层与发版检查清单、CI/CD 流水线现状与已知问题（原独立的 `ci-pipeline-diagnosis.md` 已并入本文件） |
| **领域施工基线（2026-09-30）** | [7_3/domain-baseline.md](7_3/domain-baseline.md) | **v7.3.0 的唯一工作分解入口**：覆盖论证、九域（D1–D9）定义、三批施工顺序、编号与判据规则。域分片在 [7_3/domain/](7_3/domain/D1.md)（每域：功能清单×三端矩阵 / 目标状态 / 本轮发现 / 四项横查 / 验证缺口 / 刻意不做），共 109 条发现 |
| **v7.3.0 施工计划（已评审）** | [7_3/plan.md](7_3/plan.md) | **施工顺序的真源**：范围与版本目标、验收定义、批 0 前置、四批共 34 个工作包（含条目归属与判据落点）、实机验证清单、8 条待裁决策、51 条刻意不做汇总、风险与里程碑。只做排序与切包，不改写判据（判据一律指回域章节） |
| **v10 迁移设计** | [7_3/v10-migration.md](7_3/v10-migration.md) | 批一四域（D2/D3/D5/D7）的 schema 需求收口为**一次** `version 9→10` 迁移：新增索引、唯一索引、删列、迁移门禁与降级守卫。施工时 schema 增量的唯一真源 |
| **施工条目 taskbook** | [7_3/taskbook/](7_3/taskbook/README.md) | 可拆成动作的执行条目，一个条目一个文件（`D{n}-{序号}.md` / `X-{序号}.md`），固定四段「现状 → 动作 → 判据 → 验证方式」；做完即删并回填域章节。**当前为空**，待按基线翻译 |
| **架构实现审查（2026-09-28）** | [7_3/review-7.3-architecture.md](7_3/review-7.3-architecture.md) | 全仓 5 路分片审查 + 主审逐条复跑：S1/S2 分级发现、闸门缺口清单、14 条文档失真、误报否决记录与「刻意不做」。**历史证据**：是 TODO §六 R39–R56 的出处，**不是 v7.3.0 的施工入口**（新基线明确声明不继承其编号与结论，对照见 TODO §六 末表） |
| **ktlint 融入开发流程** | [ktlint-integration.md](ktlint-integration.md) | 代码风格门禁方案（**暂缓，等 agent 分支线合并后启动**）：`.editorconfig` 成品、包装脚本、三道闸门、存量收敛顺序；含全仓实测数据 |
| **spec/hmp-design** | [spec/hmp-design.md](spec/hmp-design.md) | 设计系统：色彩 / 字体 / 间距 / 组件规范（2026-09-28 已按统一格式重写并改正 8 处与代码不符的现状描述；token 强制与白名单仍按 TODO **R49** 做） |
| **room-kmp-setup** | [room-kmp-setup.md](room-kmp-setup.md) | Room KMP 跨平台数据库配置经验总结 |
| **spec/hmp-log** | [spec/hmp-log.md](spec/hmp-log.md) | 日志门面（`LogTag` / `HmpLog`）、Tag 层级与 `MemLogWriter` 使用规范 |
| **灵感 / 候选项日志** | [ideas.md](ideas.md) | 不排期、不承诺的灵感与候选项（💡灵感 / 🔶待评估 / ✅已立项），非事实源 |
| **Google Play 上架手册** | [google-play-publish-guide.md](google-play-publish-guide.md) | Android 上架全流程指导 |
| **shared-ios** | [../shared-ios/README.md](../shared-ios/README.md) | iOS 聚合框架模块说明（shared + shared-ui → `sharedIos.framework`） |

***

## 🗂️ 历史版本开发方案（已完成，备查）

按版本号分目录存放，记录每个版本**当时的**设计方案与实施计划。**这些是历史存档，不再更新**——需要了解"某个版本为什么这么设计"时回查，不要据此判断当前状态（以 ROADMAP / 代码为准）。

| 目录 | 版本 | 文档 | 内容 |
|------|------|------|------|
| `5_9/` | v5.9 | [code_organization_optimization_plan.md](archive/5_x/5_9/code_organization_optimization_plan.md) | 代码组织优化：多模块拆分（app / feature-ui / core-*）与 Hilt 引入 |
| | | [file_migration_table.md](archive/5_x/5_9/file_migration_table.md) | 文件迁移对照表（旧位置 → 新位置） |
| `5_10/` | v5.10 | [ios-adaptation-design.md](archive/5_x/5_10/ios-adaptation-design.md) | iOS 适配技术设计：KMP 共享核心层 + iOS 原生 UI + Monorepo 双平台维护 |
| | | [ios-adaptation-plan.md](archive/5_x/5_10/ios-adaptation-plan.md) | v5.10 实施计划（P0–P7 全部阶段） |
| | | [ios-android-ui-diff.md](archive/5_x/5_10/ios-android-ui-diff.md) | iOS vs Android UI 层实现差异对照（原生能力可简化处） |
| `6_1/` | v6.1 | [desktop-ui-adaptation-plan.md](archive/6_x/6_1/desktop-ui-adaptation-plan.md) | 桌面端 UI 适配与优化（App Shell / Tab 页 / 播放器 / 子页） |
| | | [desktop-ui-optimization-plan.md](archive/6_x/6_1/desktop-ui-optimization-plan.md) | 桌面端播放页面与子页面优化 |
| `6_12/` | v6.12 | [viewmodel-refactor-plan.md](archive/6_x/6_12/viewmodel-refactor-plan.md) | ViewModel 作用域改造（`single` → 按目的地作用域）与职责划分 |

***

## 📂 版本线资料：活跃区 `7_3/` 与归档区 `archive/`

`docs/` 顶层只放两类东西：**长期规范**（`spec/`）与**当前版本线的工作目录**（顶层那个 `7_<minor>/`）。版本线一收口，整个目录搬进 `archive/7_x/7_<minor>/`。

```
docs/
├── spec/                          长期规范（跨版本，随开发修订）
│   ├── hmp-release.md              版本号格式 / 发版流程 / 分支策略 / CI 与产物现状
│   ├── hmp-log.md                  日志门面与 Tag 层级
│   └── hmp-design.md               设计系统（色彩 / 字体 / 间距 / 组件）
├── 7_3/                           当前版本线 —— v7.3.0 架构加固
│   ├── domain-baseline.md          唯一工作分解入口（覆盖论证 / 九域定义 / 施工批次 / 判据规则）
│   ├── domain/                     D1..D9.md + X.md（十份域/横切线分片，共 109 条发现）
│   ├── plan.md                     v7.3.0 施工计划（工作包 / 判据落点 / 验收 / 决策 / 里程碑）
│   ├── v10-migration.md            批一收敛：v10 迁移设计（schema 增量的唯一真源）
│   ├── taskbook/                   施工条目（一个条目一个文件；当前为空待填）
│   ├── review-7.3-architecture.md  历史证据（TODO §六 R39–R56 出处，非施工入口）
│   └── review-7.3-code.md          历史证据（代码面分片审查）
├── ktlint-integration.md          （暂缓的工程规范，见下方命名约定）
├── room-kmp-setup.md / google-play-publish-guide.md / ideas.md
├── README.md                      本索引
└── archive/                       已收口的版本线，两层：<家族>/<版本>
    ├── 5_x/5_9, 5_x/5_10
    ├── 6_x/6_1, 6_x/6_12
    └── 7_x/
        ├── 7_1/                   共享 UI 提取线（v7.0 → v7.1）
        │   ├── 方案.md             方案定稿（v4，三轮 review 修订）
        │   ├── 接口冻结-调用点映射表.md / 资源A1-映射表.md
        │   ├── 字符串资源取用规范.md / UI层统一-能力搬迁点检.md
        │   └── README.md           阶段一切换前基线（原 `baseline/`，已扁平）
        └── 7_2/                   AI Agent 体系（方向 B，F1–F14）
            ├── review-7.2.md       合入前审查报告 + 处置记录
            ├── design/             设计资料 —— 要建成什么样（`agent.md` 为单一事实来源）
            └── taskbook/           推进计划 —— 做到哪了（f1–f14 一章 + t2 待译清单）
```

**`7_2/` 内部 design 与 taskbook 的分工**：

| | `design/` | `taskbook/` |
|---|---|---|
| 回答什么 | 要建成什么样 | 做到哪了 |
| 组织方式 | 按主题（子系统） | 按阶段族 `f1`–`f14` |
| 变更频率 | 决策时才动 | 每阶段推进都动 |
| 冲突时 | `agent.md` 单一事实来源 | 以实际提交与验收为准 |

### 目录与命名约定

> **版本线目录**：活跃线放 `docs/7_<minor>/`（当前是 `7_3/`），收口后整体搬进 `docs/archive/7_x/7_<minor>/`。**活跃区顶层永远只有一条版本线目录**，避免"一半在 `7_x/` 里、一半在外面"。5 与 6 已全部收口，所以只在 `archive/` 下出现。
> **版本目录命名**：`<major>_<minor>`（`7_1`、`7_2`、`7_3`），家族目录 `<major>_x`（`5_x`、`6_x`、`7_x`）。历史上用过 `<字母> <名称>/`（`A shared-ui`、`B agent-build`），**已废弃** —— 项目线代号只留在文字叙述里，不进目录名：代号会随版本线合并而失效，版本号不会。
> **规范文件命名**：`spec/` 下统一 `hmp-<主题>.md`（`hmp-release` / `hmp-log` / `hmp-design`），刻意避开与 `release.toml`、站点 changelog、`docs/` 顶层"变更记录"类文件撞名；`docs/` 顶层的单文件用 kebab-case（`room-kmp-setup.md`、`ktlint-integration.md`）。
> **`archive/` 只进不改**：归档正文是历史记录，**除路径引用外不重写内容**。旧结论事后证明错了，另开新文档说明，不在归档里追改（`ROADMAP.md` 的【未发布】标记是唯一例外，它按发版规则维护）。
> **不留待整理的散稿**：一次性方案稿与实施计划在落地后整合进所属版本线的 `design/` 或 `taskbook/`，原稿删除。

### 搬家清单（新增 / 移动 / 删除文档时逐条过，缺一条就是埋雷）

1. 本索引（三层都要：一句话分工、活跃文档表、版本线目录树）；
2. 根目录 `README.md` / `AGENTS.md` / `CLAUDE.md` / `DEVELOP.md` / `ROADMAP.md` 的文档索引段；
3. **源码与 Swift 注释里的 `docs/...` 路径串** —— 这一项历史上漏过两轮，且**不在任何 md 链接检查的覆盖范围内**，只能靠按 `docs/` 前缀全仓 grep；`CLAUDE.md` / `AGENTS.md` 的字面引用同理（现在是指针关系而非两份正文，漏扫就会指回已被掏空的 `CLAUDE.md`）；
4. `skills/**` 与 `docs/spec/hmp-release.md` 里指向规范的路径（含 `build.gradle.kts` 错误消息里的路径 —— 它会直接显示给用户）；
5. 复跑一遍 md 链接解析 + `docs/` 路径串扫描，确认 0 断链、0 指向不存在文件。

***

## 🔗 推荐阅读顺序

- **新成员 / 贡献者**：README → DEVELOP → ROADMAP
- **查功能是否已做 / 版本历史**：ROADMAP
- **排期与任务**：TODO
- **查架构与实现细节**：DEVELOP
- **AI 协作者 / 新成员速查**：AGENTS（`CLAUDE.md` 只是它的入口指针）
- **了解 Agent 体系设计**：`archive/7_x/7_2/design/README.md`
- **动 v7.3.0 的活**：先读 `7_3/plan.md`（**施工顺序在 §三–§五**，待裁决策在 §九）→ 工作包对应的域章节 `7_3/domain/D{n}.md` / `X.md`（看「目标状态 + 判据」）→ 涉及 schema 时以 `7_3/v10-migration.md` 为准 → 执行条目落 `7_3/taskbook/`。`TODO.md` §一/§六 的 R 条目是**证据台账**而非分解来源（对照表见 §六 末）

***

*文档索引最后更新：2026-09-30（`7_3/` 收录 domain-baseline / domain / v10-migration / taskbook；两份审查报告改标为历史证据，施工入口切到施工基线）*

***

© 2026 Hearable Music Player | Developed by WLYB
