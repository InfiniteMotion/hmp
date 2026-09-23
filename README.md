# Hearable Music Player

一款现代化的跨平台本地音乐播放器，基于Jetpack Compose、Compose Multiplatform和SwiftUI构建，支持Android、Desktop和iOS三大平台。

## 🎯 项目介绍

Hearable Music Player是我个人开发的一款专注于本地音乐播放的跨平台应用，致力于提供简洁优雅的用户界面和流畅的播放体验。应用采用最新的技术栈开发，支持音乐文件扫描、播放控制、播放列表管理以及AI驱动的音乐推荐功能。

作为个人项目，我希望通过这个应用探索跨平台开发的最佳实践，学习 Jetpack Compose、Compose Multiplatform、Media3 和 Kotlin Multiplatform 等新技术，同时为用户提供一个实用的音乐播放工具。

### 项目现状

- 已完成模块化架构设计
- 已实现核心音乐播放功能
- 已集成 DeepSeek API 实现 AI 推荐
- 已实现 UI 与交互，**三端共用一套 Compose UI**（`shared-ui`，v7.1 起 iOS 亦切换至共享层）
- 已支持 Android、Desktop 和 iOS 三平台
- 进行中：AI 功能 Agent 化（v7.3 方向 B，设计资料见 [docs/7_x/B agent-build](docs/7_x/B%20agent-build/)）

## ✨ 核心功能

### 已实现功能

- **本地音乐扫描**：自动扫描设备中的音乐文件，读取ID3标签信息
- **播放控制**：提供播放、暂停、上一首、下一首、快进、快退等基本控制
- **播放列表管理**：创建、重命名、删除自定义播放列表，列表内拖拽排序与从列表移除单曲，支持智能播放列表生成
- **AI驱动推荐**：支持多个 AI 服务商（DeepSeek、OpenAI、Claude、通义千问、文心一言），提供每日音乐推荐功能
- **推荐刷新策略**：支持按时间、按启动次数或智能刷新，用户可自定义
- **歌词显示**：支持滚动歌词显示，独立歌词页面及参数配置
- **标签展示**：显示音乐文件的详细标签信息
- **用户界面**：个性化用户主页，听歌统计与热力图，歌曲详情页，动态背景风格
- **暗色/亮色主题**：支持系统主题自动切换
- **音频效果**：支持均衡器、低音增强、环绕音等调节
- **睡眠定时**：支持定时关闭音乐播放
- **国际化**：UI 字符串支持多语言
- **专辑浏览**：支持按专辑分类浏览音乐
- **音乐分享**：支持将音乐文件分享到其他应用
- **毛玻璃效果**：支持毛玻璃视觉效果和动态背景

### 计划实现功能

- **桌面小组件**：支持桌面音乐控制小组件
- **音乐标签编辑**：支持编辑音乐文件的ID3标签
- **手势操作**：支持滑动切换歌曲、调节音量等手势操作

## 🛠️ 技术栈

### 核心技术

- **开发语言**：Kotlin（UI + 业务逻辑）, Swift（iOS 原生壳 / 平台桥接）
- **跨平台框架**：Kotlin Multiplatform (KMP) + Compose Multiplatform
- **UI框架**：Compose Multiplatform —— Android / Desktop / iOS 三端共用 `shared-ui` 一套 UI
- **架构模式**：MVVM
- **依赖注入**：Koin (跨平台，已从 Hilt 迁移)
- **数据存储**：
  - Room KMP (跨平台本地数据库，支持 Android/iOS)
  - DataStore KMP (跨平台偏好设置)
  - SQLite Bundled (跨平台 SQLite 驱动)
- **媒体播放**：
  - Android: AndroidX Media3 (ExoPlayer)
  - Desktop: FFmpeg + JNA 自研音频引擎
  - iOS: AVFoundation
- **网络请求**：Ktor Client (跨平台，Android 使用 OkHttp 引擎，iOS 使用 Darwin 引擎)
- **JSON解析**：Kotlinx Serialization (跨平台)
- **音乐标签解析**：Jaudiotagger (Android) / AVAsset (iOS)
- **AI集成**：多服务商支持（DeepSeek、OpenAI、Claude、通义千问、文心一言）
- **安全存储**：API 密钥加密存储
- **导航系统**：Navigation 3 (类型安全导航) — 三端共用（shared-ui）
- **构建工具**：Gradle 9.0, CocoaPods (iOS)

### 模块化架构

项目采用模块化架构，划分为以下核心模块：

- **shared**：跨平台共享模块，包含业务逻辑（domain）与数据层（data）
- **shared-ui**：跨平台共享 UI 模块，Android / Desktop / iOS 三端共用一套 Compose 页面与 ViewModel
- **shared-ios**：iOS 聚合框架，把 `shared` + `shared-ui` 链接为单一 `sharedIos.framework` 接入 CocoaPods
- **android/app**：Android 应用入口模块，包含 MainActivity 和 Application 类
- **android/core-player**：Android 播放核心模块，包含 Media3 服务和播放控制逻辑
- **desktop/app**：Desktop 应用入口模块，包含窗口管理和应用生命周期
- **desktop/core-player**：Desktop 播放核心模块，包含 FFmpeg 音频引擎
- **ios**：iOS 应用模块，SwiftUI 壳（原生层 17 个文件）+ 共享 Compose UI（v7.1 起）
- **storybook**：组件展示与文档模块 (Kotlin/Wasm)，当前已移出构建（源码保留）

## 📱 系统要求

### Android
- Android 13 (API 33) 及以上
- 存储空间权限
- 网络权限 (用于推荐功能)

### Desktop
- macOS 14+ / Windows 10+ / Ubuntu 22.04+
- 存储空间权限
- 网络权限 (用于推荐功能)

### iOS
- iOS 26.0 及以上
- 存储空间权限
- 网络权限 (用于推荐功能)

## 🚀 安装与使用

### 环境要求

#### Android
- Android Studio Ladybug | 2024.2.1 或更高版本
- Kotlin 2.2.21 或更高版本
- Gradle 9.0 或更高版本
- Android SDK 36

#### iOS
- Xcode 17.0 或更高版本
- Swift 5.0 或更高版本
- CocoaPods 1.16.0 或更高版本
- macOS 14.0 或更高版本

#### Desktop
- JDK 21 或更高版本
- Gradle 9.0 或更高版本
- FFmpeg（构建时自动下载）

### 安装步骤

#### Android
1. 克隆项目代码
   ```bash
   git clone https://github.com/InfiniteMotion/HMP.git
   ```

2. 使用Android Studio打开项目

3. 构建并运行到您的设备或模拟器

4. 首次启动时，应用会请求存储权限用于扫描本地音乐文件

5. 扫描完成后，您可以在主界面浏览和播放音乐

#### iOS
1. 克隆项目代码（如果尚未克隆）
   ```bash
   git clone https://github.com/InfiniteMotion/HMP.git
   ```

2. 生成共享Kotlin框架
   ```bash
   cd HMP && ./gradlew :shared:generateDummyFramework
   ```

3. 安装CocoaPods依赖
   ```bash
   cd ios && pod install
   ```

4. 使用Xcode打开工作空间
   ```bash
   open HMP.xcworkspace
   ```

5. 构建并运行到您的iOS设备或模拟器

6. 首次启动时，应用会请求存储权限用于扫描本地音乐文件

7. 扫描完成后，您可以在主界面浏览和播放音乐

#### Desktop
1. 克隆项目代码
   ```bash
   git clone https://github.com/InfiniteMotion/HMP.git
   ```

2. 运行桌面应用
   ```bash
   cd HMP && ./gradlew :desktop:app:run
   ```

3. 首次启动时，在设置中选择音乐扫描目录

4. 扫描完成后，您可以在主界面浏览和播放音乐

## 🎨 界面展示

- **主界面**：音乐分类和推荐内容
- **列表界面**：显示所有歌曲和播放列表
- **播放界面**：专辑封面、歌词和播放控制
- **个人中心**：用户听歌统计和设置

## 📖 使用指南

### 基本操作

- **播放音乐**：点击歌曲列表中的任意歌曲即可开始播放
- **控制播放**：在播放界面或通知栏中使用播放、暂停、上一首、下一首按钮
- **创建播放列表**：在列表界面点击"新建播放列表"按钮，输入名称后添加歌曲
- **查看歌词**：在播放界面点击歌词区域即可显示滚动歌词

### 高级功能

- **AI推荐**：基于AI生成的标签进行推荐，支持多个 AI 服务商
- **服务商管理**：在 AI 配置页面中选择和配置不同的 AI 服务商
- **刷新策略**：在设置页面配置每日推荐的刷新规则（按时间/按启动次数/智能）
- **主题切换**：在设置界面中选择暗色或亮色主题
- **听歌统计**：在个人中心查看您的听歌时长和偏好统计

## 📚 项目文档

- **[ROADMAP](ROADMAP.md)** — 版本历史、功能状态（已完成/计划中）、技术演进与变更日志（**以 ROADMAP 为准**）
- **[DEVELOP](DEVELOP.md)** — 技术架构、模块划分、开发流程与关键实现
- **[TODO](TODO.md)** — 可执行任务列表与优先级
- **[CLAUDE](CLAUDE.md)** — AI 协作者速查：常用命令、目录结构、技术栈版本、包名与分支策略
- **[docs/README](docs/README.md)** — **文档索引**与各文档职责说明（含历史版本方案与当前 Agent 设计资料）
- **[docs/DESIGN_SYSTEM](docs/DESIGN_SYSTEM.md)** — 设计系统：色彩 / 字体 / 间距 / 组件规范
- **[Room KMP 配置](docs/ROOM_KMP_SETUP.md)** — Room 跨平台数据库配置经验总结

## 📝 开发日志

完整版本历史与变更日志见 **[ROADMAP](ROADMAP.md)**。当前版本：v7.1.0。

## 🤝 贡献指南

作为个人项目，我欢迎任何形式的贡献和反馈。如果您有任何建议或问题，请随时联系我。

### 贡献方式

1. 提交Issue报告bug或提出功能建议
2. 提交Pull Request修复bug或添加新功能
3. 提供使用反馈和改进建议

### 代码规范

- 遵循Kotlin官方代码风格指南
- 使用Jetpack Compose的最佳实践
- 保持代码简洁、可读性强
- 添加必要的注释和文档

## 📄 许可证

该项目使用MIT许可证 - 详情请查看LICENSE文件

---

© 2026 Hearable Music Player | Developed by WLYB