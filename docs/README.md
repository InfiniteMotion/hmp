# Hearable Music Player — 项目文档索引

本索引覆盖 `docs/` 目录与仓库根目录下的全部文档，说明各自职责与当前状态。

> **维护约定**：新增 / 移动 / 删除文档时同步本索引。文档与代码冲突时**以代码为准**，改文档。

## 📌 一句话分工

| 想知道什么 | 去哪儿 |
|---|---|
| 项目是什么、怎么装、怎么用 | 根目录 [README.md](../README.md) |
| **版本历史与功能状态（单一事实来源）** | 根目录 [ROADMAP.md](../ROADMAP.md) |
| 现在要做什么、优先级 | 根目录 [TODO.md](../TODO.md) |
| 技术架构、模块划分、开发流程 | 根目录 [DEVELOP.md](../DEVELOP.md) |
| 给 AI 协作者的项目速查 | 根目录 [CLAUDE.md](../CLAUDE.md) |
| 版本号规范 | [VERSIONING.md](VERSIONING.md) |
| CI/CD 流水线现状与问题清单 | [ci-pipeline-diagnosis.md](ci-pipeline-diagnosis.md) |
| 代码风格门禁（ktlint）落地方案 | [ktlint-integration.md](ktlint-integration.md) |
| 历史版本的开发方案（已完成，备查） | 本目录 `5_9/` `5_10/` `6_1/` `6_12/` |
| **当前在做的设计资料与推进计划** | 本目录 [`7_x/B agent-build/`](7_x/B%20agent-build/) |
| 日志门面与 Tag 规范 | [LOGGING.md](LOGGING.md) |

***

## 📂 活跃文档（随开发同步维护）

位于仓库根目录与 `docs/` 顶层，是日常查阅的一线文档。

| 文档 | 位置 | 用途 |
|------|------|------|
| **README** | [../README.md](../README.md) | 项目概览、功能简介、安装与使用、贡献说明 |
| **ROADMAP** | [../ROADMAP.md](../ROADMAP.md) | **单一事实来源**：版本历史、功能状态（已完成 / 计划中）、技术演进、未来方向 |
| **TODO** | [../TODO.md](../TODO.md) | 可执行任务列表与优先级，按版本阶段组织 |
| **DEVELOP** | [../DEVELOP.md](../DEVELOP.md) | 技术架构、模块划分、开发流程、测试与构建、关键实现说明 |
| **CLAUDE** | [../CLAUDE.md](../CLAUDE.md) | AI 协作者速查：常用命令、目录结构、技术栈版本、包名与分支策略 |
| **VERSIONING** | [VERSIONING.md](VERSIONING.md) | 版本号格式（MAJOR.MINOR.PATCH）、何时升级哪一位、与 ROADMAP / 构建的同步 |
| **CI 流水线诊断** | [ci-pipeline-diagnosis.md](ci-pipeline-diagnosis.md) | `.github/workflows/release.yml` 的结构、已知缺陷与处置建议 |
| **ktlint 融入开发流程** | [ktlint-integration.md](ktlint-integration.md) | 代码风格门禁方案（**暂缓，等 agent 分支线合并后启动**）：`.editorconfig` 成品、包装脚本、三道闸门、存量收敛顺序；含全仓实测数据 |
| **DESIGN_SYSTEM** | [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) | 设计系统：色彩 / 字体 / 间距 / 组件规范 |
| **ROOM_KMP_SETUP** | [ROOM_KMP_SETUP.md](ROOM_KMP_SETUP.md) | Room KMP 跨平台数据库配置经验总结 |
| **LOGGING** | [LOGGING.md](LOGGING.md) | 日志门面（`LogTag` / `HmpLog`）、Tag 层级与 `MemLogWriter` 使用规范 |
| **灵感 / 候选项日志** | [ideas.md](ideas.md) | 不排期、不承诺的灵感与候选项（💡灵感 / 🔶待评估 / ✅已立项），非事实源 |
| **Google Play 上架手册** | [google-play-publish-guide.md](google-play-publish-guide.md) | Android 上架全流程指导 |
| **shared-ios** | [../shared-ios/README.md](../shared-ios/README.md) | iOS 聚合框架模块说明（shared + shared-ui → `sharedIos.framework`） |

***

## 🗂️ 历史版本开发方案（已完成，备查）

按版本号分目录存放，记录每个版本**当时的**设计方案与实施计划。**这些是历史存档，不再更新**——需要了解"某个版本为什么这么设计"时回查，不要据此判断当前状态（以 ROADMAP / 代码为准）。

| 目录 | 版本 | 文档 | 内容 |
|------|------|------|------|
| `5_9/` | v5.9 | [code_organization_optimization_plan.md](5_9/code_organization_optimization_plan.md) | 代码组织优化：多模块拆分（app / feature-ui / core-*）与 Hilt 引入 |
| | | [file_migration_table.md](5_9/file_migration_table.md) | 文件迁移对照表（旧位置 → 新位置） |
| `5_10/` | v5.10 | [ios-adaptation-design.md](5_10/ios-adaptation-design.md) | iOS 适配技术设计：KMP 共享核心层 + iOS 原生 UI + Monorepo 双平台维护 |
| | | [ios-adaptation-plan.md](5_10/ios-adaptation-plan.md) | v5.10 实施计划（P0–P7 全部阶段） |
| | | [ios-android-ui-diff.md](5_10/ios-android-ui-diff.md) | iOS vs Android UI 层实现差异对照（原生能力可简化处） |
| `6_1/` | v6.1 | [desktop-ui-adaptation-plan.md](6_1/desktop-ui-adaptation-plan.md) | 桌面端 UI 适配与优化（App Shell / Tab 页 / 播放器 / 子页） |
| | | [desktop-ui-optimization-plan.md](6_1/desktop-ui-optimization-plan.md) | 桌面端播放页面与子页面优化 |
| `6_12/` | v6.12 | [viewmodel-refactor-plan.md](6_12/viewmodel-refactor-plan.md) | ViewModel 作用域改造（`single` → 按目的地作用域）与职责划分 |

***

## 📦 项目线资料：`7_x/`（v7.x 共享 UI 与 Agent）

`7_x/` 按**项目线**分为两个分区，各自是一套完整的设计资料 + 推进计划。
**A 线（共享 UI）与 B 线（Agent）均已完成主体交付**：B 线 F1–F9 已收口，语音会话（原 F9-T3/T4）拆出为后续独立阶段 F10，尚未开工。

```
docs/7_x/
├── A shared-ui/      共享 UI 层提取与跨平台迁移（v7.0 → v7.1，已完成）
│   ├── 方案.md                       方案定稿（v4，三轮 review 修订）
│   ├── 接口冻结-调用点映射表.md        冻结基线：接口成员 → 调用点全量盘点
│   ├── 资源A1-映射表.md               资源搬迁映射（Android res → composeResources）
│   ├── 字符串资源取用规范.md          文案取用规范（非 composable 侧 UiText / 解析时机）
│   ├── UI层统一-能力搬迁点检.md       平台能力倒退点检（R1–R3）+ 能力搬迁检查清单
│   └── baseline/README.md            阶段一切换前基线：旧版 UI 截图存档 + 交互清单
│
└── B agent-build/    AI Agent 体系（方向 B，已随 v7.2.0 交付）
    ├── design/                      设计资料 —— 要建成什么样（按主题，一个子系统一份）
    │   ├── README.md                入口索引
    │   ├── agent.md                 设计总纲（单一事实来源，含 §4.4 语音二档制）
    │   ├── agent-architecture.md    架构详解（铁则 / 两层结构 / 源码索引）
    │   ├── agent-profile.md         用户认识模块唯一依据（两层画像 / 分级记忆 / 认知准入）
    │   ├── agent-radio.md           电台子系统（唯一依据 + 设计演进附录）
    │   ├── agent-w.md               W 阶段全景（引擎侧 + 页面级 + 组件级 + 缺口登记）
    │   └── agent-g6-recommend-design.md  G6 首页双推荐页设计
    └── taskbook/                    推进计划 —— 做到哪了（按阶段族，一族一章）
        ├── README.md                总纲：生命线 / 进度状态 / 章节索引（F10 语音为后续独立阶段）
        └── f1-…f9-*.md              F1–F9 九章（F1–F9 已收口；F10 未开工，暂不建章）
```

**两个目录的分工**（`7_x/B agent-build/` 内部）：

| | `design/` | `taskbook/` |
|---|---|---|
| 回答什么 | 要建成什么样 | 做到哪了 |
| 组织方式 | 按主题（子系统） | 按阶段族 `f1`–`f9` |
| 变更频率 | 决策时才动 | 每阶段推进都动 |
| 冲突时 | `agent.md` 单一事实来源 | 以实际提交与验收为准 |

> 分区约定：`7_x/` 下每个**项目线**一个分区目录（`<字母> <名称>/`）。新增项目线按此约定开新分区，不在 `7_x/` 根下放散文件。
> **`7_x/` 只装项目线的设计资料与推进计划**；跨全仓的工程规范（版本号 `VERSIONING.md`、CI `ci-pipeline-diagnosis.md`、代码风格 `ktlint-integration.md`、日志 `LOGGING.md` 等）一律放本目录顶层，不挂到任何项目线分区下。
> **本目录不留「待整理的散稿」**：一次性方案稿/实施计划在落地后即整合进对应项目线的 `design/`（要建成什么样）或 `taskbook/`（做到哪了），原稿删除。

***

## 🔗 推荐阅读顺序

- **新成员 / 贡献者**：README → DEVELOP → ROADMAP
- **查功能是否已做 / 版本历史**：ROADMAP
- **排期与任务**：TODO
- **查架构与实现细节**：DEVELOP
- **AI 协作者**：CLAUDE
- **了解 Agent 体系设计**：`7_x/B agent-build/design/README.md`

***

*文档索引最后更新：2026-09-18*

***

© 2026 Hearable Music Player | Developed by WLYB
