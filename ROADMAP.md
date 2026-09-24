# Hearable Music Player 项目演进路线

**文档说明**：本文件为**功能状态与版本历史的单一事实来源**。README 中的功能列表与本文件保持一致；完整变更日志仅在此维护。版本号格式与发版流程遵循 [docs/VERSIONING.md](docs/VERSIONING.md)。其他文档见 [docs/README.md](docs/README.md)。

## 📜 项目历史与版本演进

### v1.0 (2025-03-31)
- 项目初始化
- 创建基础项目结构
- 配置Git仓库

### v2.0 (2025-04-23)
- 实现播放列表功能
- 添加基础播放控制功能
- 完成第一个可运行版本

### v3.0 (2025-05-06)
- 更新README文档
- 优化MusicViewModel逻辑
- 完善播放控制功能

### v4.0 (2025-05-06)
- 重构代码结构
- 优化播放逻辑
- 提升应用稳定性

### v5.0 (2025-11-20)
- 模块化架构迁移完成
- 实现Hilt依赖注入
- 升级数据库版本支持分页查询
- 迁移到Media3 MediaSession
- 优化播放控制流程

### v5.2 (2025-12-03)
- 添加触觉反馈增强用户体验
- 优化播放页面UI及交互体验
- 更新状态沉浸并优化用户播放界面
- 统一调整UI组件颜色适配主题色彩体系
- 修复播放进度调整失败的漏洞
- 添加音频效果屏幕并重构用户界面
- 引入TabScreen模板
- 实现动态主题设置
- 添加按艺术家页面
- 添加自定义主题和AI功能页面
- 添加主题模式切换功能

### v5.3 (2025-12-30)
- 实现多 AI 服务商支持（DeepSeek、OpenAI、Claude、通义千问、文心一言）
- 添加 AI 服务商配置界面，支持自由切换
- 实现 API 密钥加密存储机制
- 添加 API 连接测试功能
- 实现每日推荐刷新策略系统：
  - 支持按时间刷新（自定义小时数）
  - 支持按启动次数刷新
  - 预留智能刷新接口
- 添加每日推荐持久化存储，重启后保持同一首歌
- 优化 AI 批量处理流程，支持暂停、继续、取消操作
- 添加处理进度显示和结果统计

### v5.3.1 (2026-01-08)
- 添加下一阶段架构优化方案文档
- 优化音乐列表布局和交互体验
- 重构引导页和主界面，添加动画和多步骤流程
- 统一页面路由常量管理，调整导航路由和界面排版
- 添加动画效果和优化组件交互

### v5.4 (2026-01-14)
- 实现 Hilt 导航组件集成与音乐控制器重构
- 重构项目模块依赖关系与模型迁移
- 重构并整合域模型
- 统一屏幕布局样式并优化导航逻辑
- 重构列表页面，新增多样化播放列表展示组件
- 优化用户界面和听歌时长热力图展示
- 优化多个页面的组件拆分和状态管理
- 修复音乐列表快速滑动时专辑封面/头像占位符导致的崩溃
- 更新文档

### v5.5 (2026-02-05)
- 实现 UI 字符串国际化并优化组件样式
- 添加动态背景风格选择与改进动画效果
- 统一签名配置，支持无缝安装 APK
- 优化界面组件和交互细节
- 替换智能歌词组件为高级歌词组件，支持歌词显示参数配置及管理
- 移除播放模式功能相关代码

### v5.6 (2026-02-24)
- 重构歌曲详情页面并新增详情视图模型
- 新增独立歌词页面及其设置面板
- 重构组件结构并移除旧音效组件
- 添加智能播放列表生成算法及 UI 支持
- 调整 domain 层包结构
- 优化底部导航和分页指示组件及按钮样式
- 优化音乐库界面及播放列表组件
- 优化播放器界面组件及空状态处理
- 重构主导航和用户界面布局

### v5.7 (2026-03-17)
- **播放列表管理补全**：用户自定义播放列表全流程支持
  - 数据/领域层：getAllPlaylists、重命名、按 ID 删除（仅自定义列表）、列表内单曲排序（reorderPlaylistItems）
  - UI：播放列表页「用户自定义播放列表」区块与入口、新建/重命名/删除（含确认）、列表内拖拽排序、从播放列表移除单曲

### v5.8 (2026-04-01)
- **架构升级与UI优化**：
  - 迁移到 Navigation 3 和 Gradle 9.0
  - 重构导航系统为集中式路由管理
  - 新增专辑页面及功能
  - 添加毛玻璃效果相关设置和优化弹窗功能
  - 优化音乐列表和消息提示的UI布局和交互
  - 添加对话框管理功能并优化UI交互
  - 简化应用名称与开启代码混淆
  - 修复 Navigation3 迁移相关问题，统一配置页面动画效果

### v5.9 (2026-04-17)
- **代码组织与播放器重构**：
  - 拆分 PlayControlViewModel 为多个专用 ViewModel（PlaybackViewModel、PlaylistQueueViewModel等）
  - 重构播放器界面结构，优化播放控制逻辑
  - 按功能模块重新组织包结构，提升代码可维护性
  - 统一空状态和加载状态的UI样式
  - 移除视图模型默认参数并统一依赖注入方式
  - 实现音乐详情弹窗的分享功能（支持分享音频文件）
  - 优化「下一首播放」逻辑：若歌曲已存在于播放列表，先移除再插入到下一首位置

### v5.10 (2026-05-09)
- **跨平台架构迁移**：
  - 实现Kotlin Multiplatform Mobile (KMM) 架构
  - 迁移core-domain和core-data层到shared模块
  - 配置CocoaPods集成，支持iOS平台
  - 创建iOS平台特定实现（11个expect/actual声明）
  - 完成iOS应用的构建和基本UI显示
- **iOS 核心功能**：
  - 播放引擎（AVPlayer + MusicPlayerController 单例 + AudioSessionManager）
  - 锁屏控制与远程命令（NowPlayingInfoManager + RemoteCommandManager）
  - Live Activity 与灵动岛（LiveActivityManager + HMPMediaSession 协调器）
  - 音乐扫描（DeviceMusicScanner + MusicTagParser Bridge）
  - 音乐仓库完整实现（MusicRepositoryImpl：扫描/增量同步/标签/AI/播放历史/分析）
  - 8个 ViewModel 实现完毕（Library/Search/SongDetail/Playlist/Settings/AudioEffect/Recommendation/Dialog）
  - SwiftUI 界面大规模实现：音乐库/播放器/播放列表/设置四大模块
- **发布流程重构**：
  - 版本号集中管理到 `gradle.properties`（`hmp.versionCode` / `hmp.versionName`）
  - 新增 Gradle release 任务（`releaseAndroid` / `releaseIos` / `releaseStorybook` / `release`）
  - Release 构建启用代码混淆和资源压缩
  - 图标加载改为 classpath 资源读取，不依赖 Android Context
- **CI/CD 自动发布**：
  - 新增 `.github/workflows/release.yml`，PR 合并到 master 时自动构建并发布 GitHub Release
  - 基于 git tag 自动生成 changelog
  - 自动构建并部署 Storybook 到 GitHub Pages
  - 移除旧的 `deploy-storybook.yml` 工作流

### v6.10 (2026-05-20)
- **桌面端平台** (Compose Multiplatform)：
  - 新模块 `desktop:app` / `desktop:core-player` / `desktop:feature-ui`
  - 自研音频引擎（FFmpeg 解码 + JNA 绑定）
  - 响应式布局系统（Compact/Expanded 模式、多面板导航）
  - NavigationRail + BottomFusionBar 自适应导航组件
  - 无边框窗口（标题栏、拖拽、圆角阴影、主题集成）
  - 键盘快捷键支持
  - 动态背景重构与主题系统优化
  - 冷启动优化（同步预加载、JNA 预热）
- **Android 增强**：
  - BottomFusionBar 组件（底部融合栏替代 MiniPlayerBar）
  - 动态背景取色算法重构
- **构建与 CI**：
  - `settings.gradle.kts` 条件化模块包含（`HMP_BUILD_TARGET` 环境变量）
  - CI 自动构建桌面三平台安装包（macOS DMG / Windows MSI / Linux DEB+AppImage）
  - GitHub Release 同发 Android + Desktop 六种产物


### v6.11.1 (2026-07-23)
- **R8 优化**：
  - 启用 -repackageclasses，收窄 ProGuard keep 规则
  - 移除 Compose/Koin/Media3/Room/Navigation3 等宽泛库保留规则
  - 替换为精确的 Room Entity、@Serializable、ViewModel 保留规则
- **CI/CD 发版流程优化**：
  - 添加 Gradle 构建缓存，移除 --rerun-tasks
  - 新增构建前校验（单元测试 + 版本号重复检测）
  - Android 与桌面端构建并行化，不再互相阻塞
  - Release Notes 按 commit 类型自动分类展示
  - 新增产物 SHA256 校验文件
  - 支持 workflow_dispatch 手动触发 PATCH 发版

### v6.11.2 (2026-07-23)
- 版本号与构建配置更新（gradle.properties）

### v6.12.0 (2026-08-10)
- **国际化扩展**：
  - 新增 12 种语言（日/韩/西/法/德/葡/俄/阿/印地/泰/越/印尼），每语言 450+ 条字符串
  - 应用合计支持 14 种语言，补齐占位符、换行符与实体转义
- **质量保障**：
  - 补全单元测试 650+ 用例，覆盖 shared（commonTest/desktopTest）、desktop（feature-ui/core-player）、android（feature-ui/core-player）全模块
  - 引入 FakeRepository、FakeAudioEngine、Room in-memory 测试库、Robolectric + MockK 等测试基础设施

### v6.13.0 (2026-08-12)
- **歌词逐字显示（卡拉 OK）**：
  - 新增歌词逐字高亮显示功能，支持逐字跟随当前播放进度
  - 逐字显示平滑优化，歌词默认呈现升级
  - 新增 KaraokeTiming、LyricsPresentationParams 等领域模型与对应单元测试
- **音乐标签编辑**：
  - 支持编辑单曲 ID3 标签（标题、艺术家、专辑、年份、流派、音轨、歌词、封面）
  - 优化标签编辑页 UI 与交互
  - Android / Desktop / iOS 三平台 MusicTagEditor 实现
- **架构优化（ViewModel 重构）**：
  - ViewModel 生命周期修复、作用域统一与上帝对象拆分
  - SettingsViewModel 拆分为 AiSettingsViewModel、BackupViewModel、LyricsSettingsViewModel、ArtistAlbumViewModel 等
  - 修复导航包单元测试挂起与预存断言缺陷，归档重构方案文档
- **质量保障**：
  - 新增歌词逐字与标签编辑相关单元测试

### v6.13.1 (2026-08-12)
- **Google Play 合规修复（权限策略调整）**：
  - 移除 MANAGE_EXTERNAL_STORAGE（“所有文件访问”）权限，满足 Google Play 政策要求
  - 音乐标签编辑改用 Storage Access Framework（SAF）授权写入：文件不可直接写时通过系统文件选择器授权，经 ContentResolver 读写流完成标签写入并刷新媒体库
  - 文件写入与曲库更新解耦，新增 refreshMusicTags 仅同步本地记录
  - 清理 14 种语言与桌面端资源中的权限引导文案
- **质量保障**：
  - 新增 refreshAfterFileWrite 单元测试

### v7.0.0 (2026-08-21)
- **shared-ui 跨平台架构迁移**：
  - UI 层从单平台实现迁移至 commonMain，Android/Desktop 双端共用一套 UI 代码
  - 118 个 UI 文件迁移至 commonMain，平台差异收口到 androidMain/desktopMain 桥接层
  - 新增平台桥接三件套：PlaybackController / AlbumArtPixelsLoader / PlatformServices
  - 删除旧 desktop/feature-ui 和 android/feature-ui 模块，统一由 shared-ui 承载
- **图标资源治理**：
  - 图标架构从运行时动态加载（jar + SharedIconLoader）迁移至 composeResources 编译期资源
  - 使用 exhaustive when 映射替代异步加载，消除平台样板代码和静默失败风险
  - 58 个 PNG 图标转换为 WebP 格式，删除 19 个无用图标
  - APK 体积从 46.9MB 优化至 13.2MB（下载包 7.1MB）
- **代码清理与质量保障**：
  - 删除 6 个死文件 + 约 180 个冗余资源 + 18 个空目录
  - 移除 72 处迁移相关注释，总代码量减少 2521 行
  - 恢复首启 IntroScreen 引导流程（入口切 AppRoot 时丢失的行为）
  - 删除未接线的 DeepLinkHandler 死代码
  - 单测基建修复（AGP 9 KMP 库插件 host test 接入）
  - 双端编译验证通过，单测 81/81 全绿

### v7.1.0 (2026-08-26)
- **iOS 切换到共享层 Compose UI（方向 A A1–A10）**：
  - 地基（A1–A5）：`shared-ui` 增加 iOS targets（`iosArm64` / `iosSimulatorArm64`；`iosX64` 因 navigation3-ui 无构件排除）；`shared-ios` 聚合框架把 `shared` + `shared-ui` 链接为单一 `sharedIos.framework` 接入 CocoaPods，规避双静态框架 duplicate symbol 与动态框架 Koin 全局分裂
  - `iosMain` 桥接层：`PlaybackController` 双桥（状态汇聚 + 命令闭包）/ `AlbumArtPixelsLoader` / `PlatformServices` / Taptic Engine 触觉 / 状态栏 / 对话框 + `iosUiModule`；Swift 侧 `PlaybackBridge`（Observation 状态镜像）+ `PlatformServicesBridge`（分享 / 选图 / 备份文件）
  - 全面替换（A6–A10）：默认入口切到共享层 Compose `AppRoot`（`ContentView` 承载），全模块以共享 UI 运行；**删除 67 个 SwiftUI 页面 / 组件 / Design / Swift ViewModel**，壳收敛至 17 个原生层文件（AppDelegate / 播放引擎 / MediaSession / LiveActivity / 桥 / 解析桥）
  - `IntroScreen` / `MusicScanDialog` 从 `androidMain` 迁移到 `commonMain`（权限走 `PlatformServices` 桥），iOS 首启引导与 Android 同步
- **依赖升级**：Kotlin 2.2.21 → **2.3.21**（navigation3 全系 iOS klib 为 2.3 ABI）；Koin 4.0.4 → **4.2.2**（修复 nav3 entry VM 创建时 `SavedStateHandle` IrLinkageError）；commonMain 去 JVM 化（`CommonFormat` 替代 `String.format` 等 10 处）
- **工程与真机**：iOS bundle id `com.hmp.HMP` → `com.hearablemusic.HMP`（免费团队唯一标识注册要求）；Podfile 适配 Kotlin 2.3（`linkPodFramework` 任务 + composeResources 打包布局）；真机（iPhone 13）构建 / 安装 / 启动验证通过
- **修复**：底部融合栏 Tab 常驻（移除显示条件对 `isMiniPlayerVisible` 的依赖，无歌曲时导航可见）；shared 模块绝对路径任务（`schemaDirectory` / `copyIconsToIos`）导致的 iOS 任务图校验失败

### v7.2.0 (2026-09-24)
> 合入前 review 结论见 [docs/7_x/B agent-build/review-7.2.md](docs/7_x/B%20agent-build/review-7.2.md)：4 项 A 级（生命周期锁自锁、alwaysAllow 覆盖 STRONG_CONFIRM、Agent 启用开关不落盘、降级静默清库）+ B3 取消被吞，**已在本分支修完并补回归用例**；余项按 B/C 级归档为 TODO 的 **R1–R29**（v7.2.1 作业）。本条描述按该报告校准，不按 taskbook 的完成口吻照抄。

- **AI 功能 Agent 化落地（方向 B，F1–F14，自 `feature/agent-build` 合入）**：
  - 引擎与工具层：Master + SubAgent 编排；四层组件解耦（`LlmCallExecutor` / `ToolCallExecutor` / `ReActLoop` / `StopSignal`）；工具面 29 基础 + `capability_status`；权限体系 9→6 概念收敛（`TrustTier` → `trustLevel`、`AgentPolicyConfig` 2 字段）+ `PolicyGuard` 与 ConfirmGate 全链路
  - 场景交付：对话页与交互主干、AI 电台（播完基于上下文续队列）、Hello 呈现（设计包 + 引擎落地 + 卡片堆叠）、听歌报告双轴筛选（5 维度 × 5 时段窗口，维度拆分 SQL / 零内存聚合）、伙伴设置页（`AgentConfig(agentRole)` 单路由，**四分区**）
  - 用户认识模块 `UserMemory`：两层画像（侧写层 + 证据层）+ 分级记忆 L1/L2/L3 + 认知准入标准（明确拒绝类型学），遗忘唤醒 `ForgottenDelivery`；`Capability` 接口统一三个 SubAgent 能力面 + `CapabilityStatusTool`，删除 8 个 DJ 遗留工具
  - F11 后台生命周期：**Android 落地**（Desktop/iOS 仍为首次进 UI 懒初始化，`AgentKeepAlivePort` Desktop 无实现），真机核验未做
  - F12 Token 计量：真值入账四路径 + 窗口治理（64K 先压后拒，**Master 主对话不经该守卫**）；Room **v8 → v9** 新增 `token_ledger`。看板分账**仅「按 Agent + 累计总量」两格**，端点/模型与分时两维只有明细落库（四个 DAO 聚合零调用方），`measured` 真假值 UI 不区分
  - F14 界面自适应与多语言：六页断点适配与横屏布局、空态 `DefaultEmpty` / `LocalHMPDimens` / `AgentStatusColors` 基建对齐、字符串资源收拢（14 语言各 **789 键**、键集合一致 + 占位符逐条校验，新增 `UiText` 设施）。口径限定：**新译覆盖本轮 317 键**，泰语存量约 57% 仍为英文；中文残留于电台/问候的状态文案经端口上屏，未达成「清零」
  - **F10 语音会话（RealtimeVoiceTransport）整体挂起**，未开工，不在本版
- **官网改版与站点同步（自 `feature/site-sync` 合入）**：多页面结构与 canvas 动态背景、运行时语言切换与多语种词典、站点元信息单源化（`site/js/config.js`）并接入 Release 流水线自动同步、新增音乐伙伴介绍页与全站事实校准
- **依赖升级**：Compose Multiplatform → **1.11.1**、AGP → **9.1.1**，版本目录整理
- **首启与能力回归**：三端首启引导修复、`IntroScreen` 横屏适配、音乐扫描提速、平台能力搬迁点检回归
- **桌面端修复**：播放队列丢失、FFmpeg 架构错配导致的解码失败
- **日志规范**：Agent 域日志规范推广至全仓三端（全仓非测试 `println` 归零）
- **测试**：`desktopTest` 961 例实跑通过（F13 可见性收敛 + DI 核对 + 测试补齐）

### v7.2.2 (2026-09-24)
> **v7.2.0 从未真正发布**：PR #34 已把代码合进 master，但 CI 三平台构建全部失败、没有 `v7.2.0` tag 与产物。本版是承载方向 B Agent 化的**第一个可安装发布物**，发布说明正文会同时带上下方 v7.2.0 条目。

- **桌面端三平台产物恢复可发布**：
  - `injectFFmpeg` 的目标匹配由「按平台各写一套路径形状」收为一条规则（目录名 `bin` 且路径含 `runtime`），失败时打印真实目录树。macOS 在 CMP 1.11 布局下静默失配，**导致 DMG 一直没带上 FFmpeg**（`d977e41` 新加的断言把它炸了出来）；Windows 本机实测注入落到 `app/HMP/runtime/bin/ffmpeg.exe`
  - CI 的 `run:` 在 Windows 默认走 pwsh，`-Phmp.release-build=true` 在点号处被断词成 `-Phmp` + `.release-build=true`，Gradle 把后半截当任务名 → 三个 job 全部加引号。该 flag 自 `8ba051cc`(2026-09-01) 起就在，推断**此后每个 Windows job 都失败**（最后一次成功的 MSI 待查）
- **发版通道防「绿了不等于对」**：产物齐全硬断言（缺任一平台即拒发）；tag / release 幂等（中途失败可原地重跑，原来必死于 `tag already exists`）；版本校验从两处收成一处并扩为三条不变量（`versionName`↔`versionCode` 自洽、不与 tag 重复、**`versionCode` 严格递增**——回退包在 Android 上装不上而原先无人检查）；新增 `checkReleaseConsistency`，以 `gradle.properties` 为真源核对站点 / iOS 工程 / 发版记录共 9 处声明；Release Notes 正文改取 ROADMAP 条目、commit 分类降为附录；`.gitignore` 补 `*.dmg` / `*.deb` / `*.AppImage`
- **合入前 review 的修复随本包首次交到用户手上**（代码在 v7.2.0 那批）：A1 生命周期锁自锁与「等一个仍在自旋的旧循环」、A2 `alwaysAllow` 可永久静默放行不可逆操作、A3 Agent 启用开关不落盘（关掉后重启自启）、A4 降级静默清库、B3 取消被吞后继续写库；余项归档 TODO **R1–R34**
- **CI 静默挂死处置中（未结案）**：删去 `org.gradle.configureondemand`（与 parallel 组合的配置期锁竞态；实测本仓配置耗时无差别 9.2s vs 9.2s）、validate 加 `timeout-minutes: 20`、`concurrency.cancel-in-progress: true`（原为 false，一个挂死的 run 会堵住之后所有发版尝试）


### v7.2.3 (2026-09-24)
> ⚠️ v7.2.0 / 7.2.1 / 7.2.2 都还没有对应发布物（无 tag、无产物）—— 首次跑通的发布会把这几节一并写进 Release Notes。

- **CI 不再跑单元测试**：`testAll` 在 runner 上静默挂死（最后一行停在某个 `> Task` 之后无输出；plain console 的标题行只代表任务**开始**，所以看到的不是卡住的那个任务）。头号嫌疑是 `:android:core-player` 的三个 Robolectric 用例在测试执行期从 Maven Central 现拉 `android-all` 大 jar —— 默认无超时也不输出，且 `setup-gradle` 只缓存 Gradle home、不缓存 `~/.m2`，本机热缓存故永远复现不出。已从 validate 移除，单元测试改由**本机 `./gradlew preflight`** 守门（写进 VERSIONING §5/§6 与 CLAUDE 发版流程），validate 保留 `timeout-minutes: 10`。成因未结案，恢复路径记在 TODO R31
- **版本号下发脚本**：新增 `syncVersion`，以 `gradle.properties` 为唯一真源，把版本号写入 13 处声明点（站点 `config.js` / JSON-LD、iOS `project.yml` / `Info.plist` / `pbxproj`、`shared-ios` 框架版本、CLAUDE / DEVELOP），**`versionCode` 由 `versionName` 推导**（此前我手写算错过两次）；`checkReleaseConsistency` 与它共用同一张声明点表，ROADMAP 与站点 changelog 仍要求人来写 —— 脚本只查不代笔，缺条目就失败并给出模板
- 版本号 **7.2.3 / versionCode 72003**


## 🛠️ 关键技术演进

### 架构演进
1. **单体架构** (v1.0-v4.0)
   - 传统Android项目结构
   - 所有代码集中在app模块

2. **模块化架构** (v5.0-v5.9)
   - 划分为core-data、core-domain、core-player、feature-ui模块
   - 降低模块耦合度
   - 提升编译速度

3. **跨平台架构** (v5.10+)
   - 实现Kotlin Multiplatform Mobile (KMM) 架构
   - 共享core-domain和core-data层
   - 平台特定UI实现（Android: Jetpack Compose, iOS: SwiftUI）
   - 平台特定播放引擎（Android: Media3, iOS: AVFoundation）
   - Monorepo结构，统一版本管理

4. **桌面端平台** (v6.10+)
   - Compose Multiplatform 桌面端（JVM）
   - 自研音频引擎替代 Media3（桌面环境适配）

### 技术栈升级
1. **播放引擎**
   - 从ExoPlayer迁移到Media3 (Android)
   - 实现MediaSession统一控制 (Android)
   - 集成AVFoundation (iOS)
   - 实现NowPlayingInfoCenter (iOS)

2. **依赖注入**
   - 从Hilt迁移到Koin（跨平台支持）
   - 重构ViewModel初始化流程

3. **数据库**
   - 升级Room数据库版本 (Android)
   - 支持分页查询
   - 优化数据访问性能
   - 迁移到 Room KMP（跨平台，Android + iOS 共享）

4. **网络**
   - 从Retrofit+OkHttp迁移到Ktor Client（跨平台支持）
   - 实现多平台HTTP客户端

5. **UI框架**
   - 全面采用 Jetpack Compose (Android)
   - **共享 UI 层 `shared-ui`**：Compose Multiplatform 三端共用（v7.0 Android/Desktop → v7.1 iOS 全面替换 SwiftUI，旧 SwiftUI 层删除，壳只留原生层 17 个文件）
   - 实现动态主题切换
   - 优化无障碍支持

6. **跨平台开发**
   - 实现Kotlin Multiplatform Mobile (KMM)
   - 聚合框架 `shared-ios`（shared + shared-ui → 单一 `sharedIos.framework`）+ CocoaPods 集成
   - 实现expect/actual平台特定代码
   - 共享核心业务逻辑

## 🎯 核心功能演进

### 已完成功能
- ✅ 本地音乐扫描与管理
- ✅ 完整播放控制功能
- ✅ 播放列表管理（含用户自定义列表的创建/重命名/删除/排序/从列表移除）
- ✅ 智能播放列表生成算法及 UI
- ✅ 音乐搜索功能
- ✅ 歌词显示和滚动
- ✅ 独立歌词页面及设置面板
- ✅ 睡眠定时功能
- ✅ AI音乐推荐（多服务商支持）
- ✅ 个性化用户主页
- ✅ 暗色/亮色主题自动切换
- ✅ 触觉反馈
- ✅ 音频效果调节
- ✅ 按艺术家分类浏览
- ✅ 自定义主题设置
- ✅ 每日推荐刷新策略配置
- ✅ AI 服务商管理和切换
- ✅ UI 字符串国际化
- ✅ 歌曲详情页与动态背景风格
- ✅ 专辑页面浏览
- ✅ 音乐详情弹窗分享功能（支持分享音频文件）
- ✅ Navigation 3 导航系统
- ✅ 代码混淆与包体积优化
- ✅ Kotlin Multiplatform Mobile (KMM) 架构
- ✅ iOS平台基础支持
- ✅ CocoaPods集成
- ✅ 跨平台数据层和业务逻辑
- ✅ iOS端音乐扫描功能
- ✅ iOS端播放控制功能
- ✅ iOS锁屏控制与Live Activity
- ✅ CI/CD自动发布（GitHub Actions）
- ✅ 单元测试覆盖（650+ 用例）
- ✅ AI 音乐 Agent 化（多 SubAgent 编排 + 原子工具 + 权限护栏，v7.2.0）

### 计划中功能（v7.x 三大方向，详见「未来发展方向」）
- 🔄 方向 A：KMP 重写 iOS UI（Compose 取代 SwiftUI）—— **主线已于 v7.1.0 完成**（iOS 已切共享层 Compose UI，SwiftUI 旧层删除）；剩余观感打磨 / 真机交互级核验
- ✅ 方向 B：AI 功能 Agent 化（工具调用 + 编排）—— **F1–F14 已于 v7.2.0 发版**；残留 F10 语音会话（挂起）与 F11 真机核验 / F12 T2b·T5 后置项
- 🔄 方向 C：播放增强（播放速度 / Gapless / ReplayGain / 交叉淡入 / Desktop 音效 / 格式扩展）
- 🔄 桌面小组件 / 手势操作（README 既定承诺）

## 📊 开发里程碑

### 阶段1：基础架构搭建 (2025-03 ~ 2025-04)
- 项目初始化
- 基础播放功能实现
- 数据库设计

### 阶段2：核心功能完善 (2025-04 ~ 2025-05)
- 播放列表管理
- 搜索功能
- 歌词显示

### 阶段3：架构升级 (2025-11 ~ 2025-12)
- 模块化重构
- Media3迁移
- Hilt依赖注入

### 阶段4：功能增强 (2025-12 ~ 2026-01)
- AI推荐功能
- 主题切换
- 用户体验优化

### 阶段5：质量保障 (2026-01 ~ 2026-02)
- 单元测试
- 性能优化
- 发布准备

### 阶段6：架构优化与体验提升 (2026-03 ~ 2026-04)
- Navigation 3 迁移与导航系统重构
- Gradle 9.0 升级
- ViewModel 职责拆分与代码组织优化
- 专辑页面功能
- 音乐分享功能
- UI 细节优化（毛玻璃效果、空状态、加载状态）

### 阶段7：跨平台架构实现 (2026-04 ~ 2026-05)
- Kotlin Multiplatform Mobile (KMM) 架构搭建
- 核心业务逻辑迁移到shared模块
- iOS平台基础功能实现
- CocoaPods集成与Xcode项目配置
- 跨平台数据层和业务逻辑验证
- iOS应用构建与运行测试
- iOS播放引擎与锁屏控制
- iOS Live Activity 与灵动岛
- iOS SwiftUI 界面大规模实现
- 发布流程重构与 CI/CD 自动发布

### 阶段8：iOS 功能补全与双平台对齐 (v6.x，剩余部分并入 v7.x)
- iOS 存根实现替换：拼音排序与备份读写已完成；安全存储现为 XOR 伪加密，待升级 AES-GCM + Keychain
- iOS 设置页面后端与双平台对齐：由 v7.x 方向 A（KMP 重写 iOS）取代，避免在将被删除的 SwiftUI 页面上重复投入
- iOS 真机验证（锁屏控制 + Live Activity）保留（重写后播放引擎仍为 Swift 层）
- Repository 通用逻辑提取到 commonMain 共享基类（T3，持续）

### 阶段9：共享 UI 与 iOS 双实现债务清除 (v7.0 ~ v7.1)
- v7.0.0：shared-ui 跨平台架构迁移 —— UI 层迁入 commonMain，Android/Desktop 共用一套 UI，删除两套旧 feature-ui
- v7.1.0：iOS 切换共享层 Compose UI —— `shared-ui` 增加 iOS targets，删除 67 个 SwiftUI 页面/组件，三端共用同一套 UI
- 收益：v6.x 遗留的「iOS 双实现对齐」债务永久消失，新功能默认三端交付

### 阶段10：智能化与站点同步 (v7.2，2026-09)
- v7.2.0：方向 B Agent 化自 `feature/agent-build` 并入发版 —— Master + SubAgent 编排、29 工具面、权限护栏、UserMemory 用户认识、Token 计量、界面自适应与 14 语言（残留项见 `docs/7_x/B agent-build/review-7.2.md`）
- v7.2.0：官网改版与站点元信息单源化，版本号由 Release 流水线自动同步
- 遗留：F10 语音会话挂起；F11 后台生命周期真机核验；方向 C 播放增强（C1–C9）未启动

## 🚀 未来发展方向

**产品边界**：坚持纯本地，不做在线/云同步、不引入账号、不做社交；仅保留用户自填 API 的 AI 推荐。

### v7.x 阶段：三大方向（2026-08 制定，任务分解见 TODO.md）

**方向 A — KMP 重写 iOS，Compose 取代 SwiftUI**

> **Phase 1 地基 ✅（2026-08-23，v7.1 载体）**：shared-ui iOS targets + 桥接层 + Swift 引擎双桥 + 设置试点在模拟器跑通。定案：**Kotlin 2.3.21**（navigation3 全系 iOS klib 为 2.3 ABI）；**单一聚合框架 `shared-ios`**（shared+shared-ui 链接为一个 sharedIos pod，规避双静态框架 duplicate symbol / 动态框架 Koin 全局分裂）；不启用 iosX64（navigation3-ui 无该构件）。构建注意：Xcode 26.6 需 iOS 26.5 模拟器运行时；Apple Silicon 模拟器构建加 `ARCHS=arm64 ONLY_ACTIVE_ARCH=YES`。

- shared-ui 增加 iOS targets（iosArm64/iosSimulatorArm64）；现有依赖栈（CMP 1.9.3 / navigation3 KMP / koin-compose-multiplatform / coil3 / haze）均已具备 iOS 构件，v7.0 的 Android/Desktop 迁移已验证同款路径
- 新增 shared-ui iosMain 桥接层：PlaybackController（状态汇聚 + 命令闭包双桥）/ AlbumArtPixelsLoader / PlatformServices / 触觉 / 状态栏 / 对话框
- Swift 播放引擎层保留（PlayerEngine / NowPlayingInfo / RemoteCommand / LiveActivity / 独占能力），由 PlaybackBridge.swift 闭包 + Observation 状态镜像包装
- 逐模块迁移 UI：`2026-08-23 全面替换完成`——默认入口已切到共享层 Compose AppRoot（全模块共享 UI），
  删除 68 个 SwiftUI 页面/组件/Swift ViewModel（A9），壳收敛至 AppDelegate/播放引擎/LiveActivity
  等原生层 17 个 Swift 文件（A10）；无真机环境的交互级核验（播放/歌词/歌单操作、P10.1 锁屏/Live Activity）记录待办
- 收益：P8/P9/P10 一类「iOS 双实现对齐」债务永久消失，新功能默认三端交付
- **已完成（v7.1.0）**：iOS A1–A10 —— 共享层 Compose UI 全面替换，67 个 SwiftUI 文件删除，壳收敛至 17 个原生文件；真机（iPhone 13）构建/安装/启动验证通过
- 残留项：Liquid Glass 观感取舍（Compose Haze 近似 vs 关键页保留 SwiftUI）；无真机环境的交互级核验（播放/歌词/歌单操作、P10.1 锁屏/Live Activity）记录待办

**方向 B — AI 功能 Agent 化**
> **进度（2026-09-18）**：**F1–F9 全部完成并收口**（B0-B6 + R/S/T/U/V/W/A0 各横切阶段）。F9 三笔交付：**T0 用户认识模块（画像，2026-09-16，契约 v3.8）** —— 归属 Master 定名 `UserMemory`，认领 `agent-architecture.md` 长期悬空的两处承诺（`偏好画像` / `AgentMemory`）与 `f5` 未闭环的「Feedback → Recall → 推荐闭环」，定稿为**两层画像**（侧写层 + 证据层，Room v6 两张表）+ **分级记忆 L1/L2/L3** + **认知准入标准**，据此**明确拒绝 MBTI 等类型学**；**A0 Capability 统一化**（2026-09-16）—— `Capability` 接口统一三个 SubAgent 能力面 + `CapabilityStatusTool` + 删除 8 个 DJ 遗留工具；**T1 听歌报告双轴筛选重构（2026-09-17）** —— `UserUsageDataScreen` 5 维度 × 5 时段窗口查询（方案 B：维度拆分 SQL、零内存聚合、零 Room 版本升级）+ `ForgottenDelivery` 遗忘唤醒；**T2 伙伴设置页（2026-09-18）** —— AI 页收拢为参数化 `AgentConfig(agentRole)` 单路由六分区。**语音会话（原 F9-T3/T4）已拆出为后续独立阶段 F10**：B6 里唯一真正新增的传输层，需真实端点验证，与报告/设置页无耦合，**独立 gate**（端点不可用即整体延期，v1 完整性不依赖语音）。契约见 [docs/7_x/B agent-build/design/agent-profile.md](docs/7_x/B%20agent-build/design/agent-profile.md)，推进记录见 [taskbook/README.md](docs/7_x/B%20agent-build/taskbook/README.md)。
> **进度（2026-09-23 更新）**：方向 B 已推进至 **F14**（承载于 `feature/agent-build` 分支，相对 master 领先 40 个提交）。**F11 后台生命周期**（L1–L3/L5 主体完成，真机核验待手动）、**F12 Token 计量与窗口治理**（T1–T4 落地；T2b 配额候补 / T5 成本可见后置）、**F13 Agent 工作收尾**（可见性收敛 + DI 核对 + 测试补齐，`desktopTest` 961 例全绿）全部完成。**F14 界面自适应与多语言 ✅ 全部完成**（执行序 T1→T3→T2）：**T1 UI 自适应**（2026-09-22）、**T3 组件基建对齐**、**T2 字符串收拢 + 14 语言**（2026-09-23 收口 —— 累计 **305 新键**、14 语言各 **779 键**、键集合一致 + 占位符逐条校验、**agent 子树 UI 面向中文清零**，新增 `UiText` 设施）。**F10 语音会话（RealtimeVoiceTransport）整体挂起**：独立阶段、与主线解耦，未开工，可整体延期。设计总纲见 [docs/7_x/B agent-build/design/agent.md](docs/7_x/B%20agent-build/design/agent.md)，推进计划见 [taskbook/README.md](docs/7_x/B%20agent-build/taskbook/README.md)。
> **进度（2026-09-01）**：B0-B4 + R + S + **T** 代码层全完成（M0-M4 + R 债务清零 + S 工具层终局 27 原子工具 + T 阶段 Master 内核/Enrich SubAgent/权限体系/四层组件解耦）。**T 阶段代码层 8/8 退出条件达成**（E1/E2 待手动冒烟，E4 开发阶段放开额度）——四层组件 LlmCallExecutor/ToolCallExecutor/ReActLoop/StopSignal 彻底解耦，权限体系简化 9→6 概念（TrustTier→trustLevel Int、AgentPolicyConfig 2 字段、TrustLedger 复活），AgentPolicyConfig DataStore 三端持久化闭环，EnrichSubAgent 接入 ToolCallExecutor + PolicyGuard（不再 DirectToolExecutor 裸跑），ConfirmGate "总是允许" UI 全链路。审计页/撤销、本地化横切待跟进。
- OpenAiCompatibleAdapter 扩展 tools（function-calling）与 SSE 流式；现有 5 家服务商均走 OpenAI 兼容协议，协议层只改一处
- shared domain 层新增 AgentOrchestrator：本地工具注册表（曲库检索/听歌统计/歌单管理/播放控制）+ agent loop + 护栏（破坏性操作 UI 确认、工具白名单、步数上限）
- 场景：自然语言曲库操作与歌单生成、曲库问答、AI 电台（播完基于上下文自动续队列）
- 排在方向 A 之后：对话 UI 届时三端共享，只写一次

**方向 C — 播放功能增强补齐**（引擎层工作，与方向 A 正交可并行）
- 三端空白补齐：播放速度、Gapless 无缝、ReplayGain 音量均衡、交叉淡入淡出
- Desktop 音效系统：FFmpeg avfilter（EQ/低音/环绕），复用 shared-ui AudioEffectViewModel 共享 UI
- 格式支持：三端白名单统一到 commonMain 常量；Desktop 放行 DSD/APE/WV（FFmpeg 原生可解）；iOS 补 opus
- 进阶（待评估）：bit-perfect 输出（WASAPI 独占等，发烧友向）

**版本编排**：**7.1.0 已发布**（方向 A 主线全量完成 + C 批 1）→ **7.2.0 代码已合入 master 但未产出发布物**（CI 三平台构建失败，无 tag）→ **7.2.2 = 本版**（修好发版通道，作为方向 B 的首个可安装包，正文发布说明会连带覆盖 7.2.0）→ 后续版本承载方向 C 播放增强（C1–C9，号未定）与遗留清理；F10 语音会话独立挂起

### 中期目标
1. 桌面小组件与手势操作（README 既定承诺，穿插于 v7.x）
2. 优化电池续航
3. 完善单元测试覆盖（shared-ui / desktop 为薄弱区）

### 长期目标
1. 发布到应用商店（可选）
2. 车载/穿戴、播客等扩展（可选）

---

**最后更新时间**: 2026-09-24
**当前版本**: v7.2.3（`release/7.2.3` → master，待发布）
**已发布至 master 但未产出发布物**: v7.2.0 / v7.2.1 / v7.2.2（代码在 master，CI 未跑通，无 tag 与产物）
**最新可下载安装包**: v7.1.0
**开发中（未发布）**: 方向 C 播放增强（C1–C9 未启动）；方向 B 残留 —— F10 语音会话挂起、F11 真机核验、F12 T2b 配额候补 / T5 成本可见后置

---

© 2026 Hearable Music Player | Developed by WLYB
