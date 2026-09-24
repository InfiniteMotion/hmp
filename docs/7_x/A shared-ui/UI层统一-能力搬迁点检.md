# UI 层统一 · 平台能力搬迁点检

> 适用范围：两轮「单一实现达成」的 UI 层整合 —— 桌面端（阶段一收尾）与 iOS（方向 A 全面替换）。
> 目的：记录点检发现的**平台能力倒退**（R1–R3）、复核判定为**非倒退**的项（避免重复怀疑）、
> 以及今后搬迁必须走的**能力搬迁检查清单**（§5，新增规范）。

***

## 1. 背景与点检范围

「单一实现达成」是把旧的平台专属 UI 层删掉、统一到 `shared-ui`。两轮各有一次大规模删除：

| 轮次 | 提交 | 日期 | 动作 | 规模 |
|---|---|---|---|---|
| **桌面端**（阶段一收尾） | `d7ae415` | 2026-08-20 | 删除旧桌面 UI 层 `desktop/feature-ui` | 260 文件 / +2 −28172 |
| **iOS**（方向 A 全面替换） | `f42e51c` | 2026-08-26 | iOS 切共享层 Compose UI，删 67 个 SwiftUI 文件 | 129 文件 / +2329 −12374 |

**共同失败模式**：删旧 UI 层时**只搬了「显示」，漏了「写入 / 触发」**。三类具体表现：

1. **调用点搬走了，实现没搬** → R1
2. **代码还在，声明（plist）丢了** → R2
3. **数据层与 UI 都在，连接两者的入口没搬** → R3

## 2. 点检方法

1. **调用链回溯**：从 UI 入口沿 `PlatformServices` 桥 / UseCase 一路查到平台实现，检查每一环是否真有实现。
2. **调用点核查**：对每个平台 API 反问「谁调用它」；零调用点的 `override` 一律视为可疑。
3. **单源核对**：凡由生成器（XcodeGen 的 `info.properties`、Gradle 的 versionName）产出的声明，
   核对**真源**里是否还有该键 —— 手改产物会被下次生成冲掉。
4. **反向核对**：对每个被删文件/能力，确认「改造前是否真的有人用」——避免把死代码清理误判成倒退（见 §4）。

***

## 3. 确认的能力倒退

### R1 · Android 权限申请失效 🔴

| | |
|---|---|
| **现象** | 首启引导点「授予权限」**不弹系统授权框**，界面直接跳到「权限已授予」；随后扫描得到**空曲库且无任何提示**；Android 13+ 播放通知不显示 |
| **根因** | `AndroidPlatformServices.requestIntroPermissions` 是空壳 —— `// 当前无调用点：保留签名，直接回调成功` → `onResult(true)`。该注释写于实现在编时「当时确实没有调用点」（那时 `IntroScreen` 还在 `androidMain` 里用自己的 launcher）。`f42e51c` 把 `IntroScreen.kt` 从 `androidMain` **重命名**到 `commonMain`（git 记为 R087），删掉 `rememberLauncherForActivityResult(RequestMultiplePermissions)` + `launch([READ_MEDIA_AUDIO, POST_NOTIFICATIONS])` 改为调桥 —— **调用点出现了，实现没补** |
| **证据** | 空壳：`shared-ui/src/androidMain/.../platform/AndroidPlatformServices.kt:196-199`<br>调用点：`shared-ui/src/commonMain/.../common/pages/IntroScreen.kt:116-119`、挂载 `:245`<br>被删实现：`git show f42e51c^:shared-ui/src/androidMain/kotlin/com/hearablemusic/player/ui/common/pages/IntroScreen.kt`（含 `permissionLauncher`）<br>`TargetSdk`：`android/app/build.gradle.kts` `minSdk = 33` / `targetSdk = 37` → `READ_MEDIA_AUDIO` 是**必需**的运行时权限<br>全仓另一处 `READ_MEDIA_AUDIO`（`MainActivity.kt:108-110`）只是 `checkSelfPermission` **查询**，不申请 |
| **影响** | Android 端**首启曲库为空**（`loadMusicFromDevice` 把异常 catch 后仅写日志）；通知权限一并缺失 |
| **修法** | 按同文件既有模式（`registry.register(KEY, contract) { dispatch(KEY, result) }` + `callbacks[KEY]`，参考 `pickImage` / `overlay` / `writeRequest`）注册 `ActivityResultContracts.RequestMultiplePermissions()`，在 `requestIntroPermissions` 里 `launch(arrayOf(READ_MEDIA_AUDIO, POST_NOTIFICATIONS))`；并给扫描加「权限未授予」的可见提示，消除静默失败 |

### R2 · iOS Live Activity 声明丢失 🔴

| | |
|---|---|
| **现象** | iOS 锁屏 / Dynamic Island 的**实时活动不显示** |
| **根因** | `f42e51c` 从 `ios/HMP/HMP/Info.plist` 删除了 `NSSupportsLiveActivities`（该键由 `3eadb77 feat(ios): 实现锁屏控制和Live Activity功能` 引入），且**未**补进 `ios/HMP/project.yml` 的 `info.properties`。而 app 的 Info.plist 由 XcodeGen 按该 properties 维护 → 重新生成也不会恢复。Live Activity 的**代码仍在被调用**（`HMPMediaSession.swift:178` → `LiveActivityManager.shared`），缺的只是声明 |
| **证据** | `git log -S "NSSupportsLiveActivities" --all -- ios/` → `f42e51c`（删）、`3eadb77`（增）<br>`git grep "NSSupportsLiveActivities" f42e51c^ -- ios/` 有命中；`f42e51c` 之后全 `ios/` 树**零命中**<br>真源：`ios/HMP/project.yml` → `targets.HMP.info.path: HMP/Info.plist` + `info.properties`（无该键）<br>扩展侧 `ios/HMP/HMPNowPlaying/Info.plist` 只有 `NSExtension` / `NSExtensionPointIdentifier`（对 widget 扩展是正确的，`NSSupportsLiveActivities` 应声明在**宿主 app**） |
| **影响** | iOS 端锁屏播放控制 / 实时活动能力**对用户不可见** |
| **修法** | 把 `NSSupportsLiveActivities: true` 加回 `ios/HMP/project.yml` 的 `targets.HMP.info.properties`（**单一真源**），并同步 `ios/HMP/HMP/Info.plist` |

### R3 · 桌面端目录管理整块丢失 🔴

| | |
|---|---|
| **现象** | 桌面端**只能扫描 `~/Music` 与 `~/Downloads`**（`getDefaultMusicDirectories()`，目录不存在时静默忽略）；音乐放在其他位置（如 `D:\Music`）**无法入库** |
| **根因** | `d7ae415` 删除整个旧桌面 UI 层 `desktop/feature-ui`，其中包含完整的目录管理（VM 四个 mutation + 两个 UI 区块 + 目录选择器）；`shared-ui` 的新版 `LibrarySettingsScreen` **未搬入**这部分能力 |
| **证据（删除前，`d7ae415^`）** | VM：`desktop/feature-ui/.../library/viewmodel/LibraryViewModel.kt:190-230` —— `addScanDirectory` / `removeScanDirectory` / `addBlockedDirectory` / `removeBlockedDirectory` + `loadScanDirectoryConfig()`<br>UI：`desktop/feature-ui/.../settings/pages/LibrarySettingsScreen.kt:124-130`（单栏）、`:142-149`（双栏）；`ScanDirectoriesSection:173-199`、`BlockedDirectoriesSection:215-244`<br>选择器：`DesktopFilePicker.pickDirectory()`（`:199` / `:244`） |
| **证据（现状）** | `saveScanDirectoryConfig` **零调用点**（仅剩接口声明 `SettingsRepository.kt:47` + 三端实现 + 测试 fake）→ 配置恒为空；`DeviceMusicScanner.addScanDirectory()` 亦零调用点<br>新 `LibrarySettingsScreen` 仅有 `LibraryStatsSection` / `ScanOptionsSection` / `LibraryManagementSection`（后者是**已扫描文件夹展示 + 隐藏/取消隐藏**）<br>字符串 `scan_directories` / `blocked_directories` 连同资源一起消失<br>`JFileChooser` 两处全是 `FILES_ONLY`（`DesktopPlatformServices.kt:94` / `:149`）→ **无目录选择能力** |
| **数据层仍完好（空转）** | `ScanDirectoryConfig` 模型；`SettingsRepositoryImpl.desktop` 真实 DataStore 实现（读 `:127` / 写 `:186`）；`MusicRepositoryImpl.desktop:181-185` **仍会读**配置并 `setScanDirectories` / `setBlockedDirectories` |
| **易误判点** | 新屏幕的「**隐藏文件夹**」**不是** `blockedDirectories`。`hideFolder()` 走 `removeFromLibraryUseCase(ids)` —— **歌曲级**软隐藏（把该文件夹下**已入库**的歌标记移出库），`restoreToLibrary()` 反向恢复；与**目录级屏蔽（扫描时整目录跳过）**是两套机制，前者替代不了后者 |
| **修法** | ① `FilePickerService` 增 `pickDirectory`（Desktop `JFileChooser` + `DIRECTORIES_ONLY`；Android 可走 SAF `OpenDocumentTree`；iOS 沙箱无意义→返回 null）② `LibraryViewModel` 补回 4 个 mutation + 订阅 `scanDirectoryConfig`（旧实现约 40 行，可直接复用）③ `LibrarySettingsScreen` 补两个 section ④ 补字符串 × 14 语言。**显示范围（桌面专属 / 全端可见）待裁决** |

***

## 4. 复核判定为「非倒退」（避免重复怀疑）

| 项 | 判定依据 |
|---|---|
| **iOS 系统音乐库读取**（`MusicLibraryService` / `MPMediaQuery.songs()`、`MusicScannerService`） | 改造**前**就已无调用点：`git grep "MusicLibraryService\|MusicScannerService" f42e51c^ -- '*.swift'` 只匹配到各自 `static let shared` 一行 → 删的是**死代码**，不是能力。且 iOS 通路靠「文件」App 导入沙箱（`Info.plist` 有 `UIFileSharingEnabled` + `LSSupportsOpeningDocumentsInPlace`），可用 |
| **67 个 SwiftUI 文件删除** | 逐项核对均有对应：7 个对话框在 commonMain 有同名 `.kt`（`AddSongToPlaylistDialog` / `CreatePlaylistDialog` / `MusicDetailDialog` / `MusicPickerDialog` / `PlaylistPickerDialog` / `TimerDialog`；`HMPDialogs` → `ConfirmDialog` + `InputDialog`）；能力型文件亦有替代（`PaletteExtractor` → `PaletteColors.kt`；`IndexStrip` → `MusicListIndexStrip.kt`；`AudioEffectViewModel` → `AudioEffectViewModel.kt` + `AudioEffectsScreen.kt`；背景视图 → `DynamicBackground.kt` + Haze） |
| **iOS 播放能力** | 完好：`UIBackgroundModes: audio`；`PlayerEngine` / `AudioSessionManager` / `MediaSession`（`NowPlayingInfoManager` / `RemoteCommandManager`）/ `LiveActivityManager` 代码**全在位**（缺的只是 R2 的 plist 声明） |
| **`commonMain` 被 `f42e51c` 修改的 9 个文件** | 全是可移植性替换（`Math.pow`、`Dispatchers.IO` → `Default`、DI 签名），最大仅 **+5 / −4**，无逻辑损失；`MusicScanDialog` 迁移只换资源体系（`R.string` → `Res.string`） |
| **`DeviceMusicScanner.android.kt` 死代码** | 自 KMP 重构 `546f6c4` 起零调用（Android 实走 `MusicRepositoryImpl.android.kt` 自己的 MediaStore 扫描）→ 早于两轮 UI 统一，非本主题 |
| **`requestMusicWriteAccess(musicId: Long)` 死 API** | 接口 + 三端实现齐全但全仓零调用（真实调用走 `(filePath)` 重载）→ 早于两轮 UI 统一 |
| **iOS `autoProcess` 桥 TODO 空实现** | `shared/src/iosMain/.../di/KoinHelper.kt:163-168`，由 agent 工作 `e3e3924` 引入 → 非 UI 统一造成 |
| **桌面首启引导（IntroScreen）** | 旧桌面 UI 层虽有一份 `desktop/feature-ui/.../common/pages/IntroScreen.kt`，但 `git grep "IntroScreen" d7ae415^ -- 'desktop/**'` 在 `desktop/**` 全域**只有定义、无调用**，且当时的 `Main.kt` 直接 `AppRoot(darkTheme=…)` → **当时即死代码，从未接线**。故「桌面没有首启引导」属**新功能缺口**，不是本轮点检主题下的能力倒退（补接线见提交 `feat(desktop): 桌面端首启引导`） |

***

## 5. 规范 · UI 层统一时的能力搬迁检查清单

> 本节是本次点检最重要的产出。**任何「删除平台专属 UI 层 / 合并到共享层」的改动，提交前必须逐项自查。**

### 5.1 删除前：能力清点

- [ ] **枚举旧层的全部「写入与触发」入口**，而不只是页面渲染：
      `fun` 里带 `save*` / `set*` / `add*` / `remove*` / `request*` / `launch` / `start*` / `stop*` 的成员逐个过一遍。
      （R3 的教训：页面「显示了已扫描目录」很显眼，但「添加目录」的入口在 ViewModel 里，容易被漏掉。）
- [ ] **枚举旧层的全部权限 / 能力申请**：`requestPermissions` / `ActivityResultContracts` / `MPMediaLibrary` /
      `requestAuthorization` / `JFileChooser` 等。
- [ ] **枚举旧层的全部平台声明**：`Info.plist` 键、`UIBackgroundModes`、`AndroidManifest` 权限、
      Gradle 属性 —— 区分「目标平台专属」与「宿主 app 必需」。

### 5.2 迁移时：三处必须同时落地

- [ ] **实现**（平台侧）：桥的每个 `override` 都要有真实行为。**不允许**留 `onResult(true)` 式空壳。
- [ ] **调用点**（共享侧）：确认新共享 UI 真的会调它 —— 若调了而实现是空壳，比不调更危险（静默失败）。
- [ ] **声明**（配置侧）：凡由生成器产出的键（XcodeGen `info.properties`、Gradle 属性），
      必须改**真源**，不能只手改产物。

### 5.3 删除后：反向验证

- [ ] **零调用点扫描**：对所有平台 `override` / `Set*` / `Save*` API 反问「谁调用它」。
      **零调用点的 `override` 一律视为可疑**，逐个说明「为何合法」（如桌面无运行时权限 → 恒授权）。
- [ ] **配置空转扫描**：若某配置有「读」有「写」接口、但写侧零调用点 → 该配置**永远不会被写入**，
      读侧等于空转。（R3 即此类。）
- [ ] **声明存活扫描**：对每个删除时动过的 plist / manifest / gradle 键，在**真源**里复核一遍。
- [ ] **能力对拍**：旧层的「写入与触发」清单（5.1）逐项给出「新实现在哪」或「为何不需要」，写进提交信息。

### 5.4 提交与验证

- [ ] 提交信息里**显式列出能力搬迁对照表**（旧入口 → 新实现 / 不需要的理由）。
- [ ] **真机/实机核验必须覆盖这些路径**（本轮 R1、R2 都逃过了 v7.1 的「真机验证」）：
      首启权限流程、锁屏实时活动、桌面目录选择、后台播放、通知。
- [ ] 平台专属 UI 若在部分端无意义（如 iOS 无「扫描目录」概念），**显式按平台条件渲染**，
      并在代码注释说明理由 —— 不要因为「另一平台用不上」而整体不实现。

### 5.5 教训归档

| 编号 | 迁移轮次 | 漏搬的东西 | 类型 |
|---|---|---|---|
| R1 | iOS（`f42e51c`） | 桥的 Android 实现（旧 launcher 被删） | 调用点搬了、实现没搬 |
| R2 | iOS（`f42e51c`） | plist 声明（未进 XcodeGen 真源） | 代码在、声明丢 |
| R3 | 桌面（`d7ae415`） | 目录管理的 VM + UI + 选择器 | 数据层与 UI 都在、入口没搬 |

***

## 6. 状态跟踪

| 编号 | 能力 | 平台 | 状态 |
|---|---|---|---|
| R1 | 引导页运行时权限申请（音频读取 + 通知） | Android | ✅ 已修 —— `requestIntroPermissions` 补真实 `RequestMultiplePermissions` launcher（回调以**音频读取**为准，通知被拒不阻断引导）+ `hasMusicReadAccess()` / `openAppSettings()`；引导页加「稍后再说」逃生口；设置页加权限提示条 |
| R2 | Live Activity 声明（`NSSupportsLiveActivities`） | iOS | ✅ 已修 —— 加回 `project.yml` 真源 + 同步 `Info.plist`（plist/YAML 解析校验通过） |
| R3 | 扫描目录 / 屏蔽目录管理 | **全端**（iOS 不渲染） | ✅ 已实现 —— 见下方施工记录 |

### R3 施工记录（Android 侧并非「补个选择器」）

裁决为**全端显示 + Android 一并实现**。施工前核实到 Android 侧有三处缺口，逐项补齐：

| # | 缺口（施工前） | 处理 |
|---|---|---|
| 1 | `SettingsRepositoryImpl.android.saveScanDirectoryConfig` 是**空实现 `{ }`**；`scanDirectoryConfig` 恒为 `flowOf(ScanDirectoryConfig())` | 补真实 DataStore 持久化（与 desktop 同构，新增 `SCAN_DIRECTORY_CONFIG` 键） |
| 2 | `MusicRepositoryImpl.android` **完全不读该配置**；其 MediaStore selection 仅 `IS_MUSIC != 0 AND DURATION > ?` | 读配置并把两个列表作为 **include / exclude 过滤**施加到查询（`DATA` 前缀 + `LIKE ... ESCAPE '\'`，转义路径中的 `_` / `%`） |
| 3 | **SAF 的 tree Uri 无法转成文件系统路径**，而 `scanDirectories` 存的就是路径 | 不用 SAF。引入 `DirectorySelectionMode`（`ARBITRARY_PATH` / `KNOWN_FOLDERS` / `UNSUPPORTED`），Android 走**从媒体库已知文件夹中挑**（应用本就从曲目路径算出了「已扫描文件夹」） |

**平台落地差异**（由 `DirectorySelectionMode` 决定）：

| 平台 | 模式 | 添加方式 | 语义 |
|---|---|---|---|
| Desktop | `ARBITRARY_PATH` | `JFileChooser(DIRECTORIES_ONLY)` | `scanDirectories` = 文件系统扫描根 |
| Android | `KNOWN_FOLDERS` | 从「已扫描文件夹」中挑选 | 两个列表 = MediaStore 查询的 include / exclude 过滤 |
| iOS | `UNSUPPORTED` | 区块不渲染 | 沙箱内音乐来自 Documents，无此概念 |

**设计取舍**：目录变更后自动触发 `fullRescan()`（避免「改了设置却没反应」）；`BlockedDirectories`
的语义在 Android 上是「过滤掉该目录下的曲目」，与「隐藏文件夹」（歌曲级软隐藏 `removeFromLibraryUseCase`）
是两套机制，未合并。

> R1、R2 由 v7.1 的 `f42e51c` 引入，R3 由 `d7ae415` 引入 —— 三项均在 `release/7.2.0` 内，
> 属发布前必修项，现已全部修复。

---

## 附：点检记录的提交来源

```
d7ae415  refactor: 删除旧桌面 UI 层 desktop/feature-ui（阶段一收尾，单一实现达成）   2026-08-20
f42e51c  feat(ios): 方向 A 全面替换——iOS 切换到共享层 Compose UI（v7.1 A1–A10 + 真机验证）  2026-08-26
0cef382  feat(desktop): 桌面端音乐库管理重构 — 扫描目录与屏蔽目录支持               （R3 原始功能）
3eadb77  feat(ios): 实现锁屏控制和Live Activity功能                                （R2 原始声明）
546f6c4  feat: 完成 KMP 架构重构 (P2/P3/P4/P5)                                    （Android scanner 死代码起点）
```
