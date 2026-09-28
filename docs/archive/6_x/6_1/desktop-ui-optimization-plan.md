# 桌面端 UI 优化计划 — 播放页面与子页面

> 基于 develop-desktop 分支现状调查 (2026/05/20)

---

## 概述

当前 develop-desktop 分支已完成 Tab 页面适配（Home/Gallery/List/User）、底部融合栏（BottomFusionBar）、播放核心链路以及首页布局优化。本计划覆盖剩余的播放器页面和所有子页面的桌面端 UI 优化工作。

---

## 第一阶段：播放页面核心优化（P1）

### 1.1 PlayContent 响应式布局完善

**文件**: `desktop/feature-ui/.../player/pages/PlayContent.kt`

| 项目 | 说明 |
|------|------|
| Medium 布局实现 | 当前只有 Compact（单栏）和 Expanded（双栏 4:6）。新增 600-840dp Medium 适配：单栏但右栏 Tab 以底部浮动方式呈现 |
| Compact Tab 切换 | 窄屏下右侧的歌词/播放列表/推荐生成/文件信息 4 个 Tab 不可见，需在控制栏添加 Tab 切换入口 |
| MusicInfo 字体响应 | `displayMedium` 在大标题+小窗口下溢出，改为根据 `WindowWidthSizeClass` 动态选择 `displaySmall`/`displayMedium`/`displayLarge` |
| 封面尺寸响应 | Compact 280dp / Expanded 220dp → 替换为 `windowWidthDp * 0.35` 等比例计算 |
| 分隔线交互 | 双栏分隔线增加拖拽调整比例（`draggableSplitPane` 模式）；或至少加厚/hover 区域 |

### 1.2 键盘快捷键

**文件**: `desktop/feature-ui/.../player/pages/PlayerScreen.kt`

| 项目 | 说明 |
|------|------|
| Space | 播放/暂停 |
| ← / → | 上一首 / 下一首 |
| ↑ / ↓ | 音量调节（如系统未拦截） |
| L | 跳转歌词页 |
| Esc / ↓ 拖拽 | 关闭播放器（已有手势下滑） |

使用 `onKeyEvent` / `KeyEventType.KeyDown` 在 `PlayerScreen` 级别添加。

### 1.3 AdvancedLyrics 窗口响应

**文件**: `desktop/feature-ui/.../player/pages/AdvancedLyrics.kt`

| 项目 | 说明 |
|------|------|
| contentPadding 动态化 | `containerHeight / 2` 兜底值 300.dp 替换为 `maxHeight * 0.3` |
| 字体范围扩展 | 10-32sp 改为根据 `windowHeightSizeClass` 扩展（Compact 维持，Expanded 放大范围到 14-40sp） |
| 视觉反馈替代触觉 | 桌面端点击歌词跳转使用 `Ripple` / 高亮动画替代 `HapticFeedback.LongPress` |

### 1.4 LyricsScreen 设置面板优化

**文件**: `desktop/feature-ui/.../player/pages/LyricsScreen.kt`

| 项目 | 说明 |
|------|------|
| 面板尺寸响应 | 固定 400x176dp → 根据窗口宽度计算（min 320dp，max 500dp） |
| SizeControl 布局 | 5 列在 400dp 中太挤，改为 2-3 行 + 可折叠分类（显示模式/对齐方式/字体大小） |
| 设置面板动画 | 添加垂直展开动画，配合位置记忆 |

### 1.5 PlayerHeader 增强

**文件**: `desktop/feature-ui/.../player/pages/PlayerHeader.kt`

| 项目 | 说明 |
|------|------|
| 添加当前歌名 | 在返回按钮旁显示当前播放歌曲标题 |
| 添加菜单入口 | 三点菜单（更多操作：查看详情、添加到歌单、分享等） |
| 宽度适配 | Compact 居中 / Expanded 左对齐 |

---

## 第二阶段：子页面响应式适配（P2）

### 2.1 设置页面 Expanded 多列布局

**文件**: `desktop/feature-ui/.../settings/pages/SettingScreen.kt`

| 项目 | 说明 |
|------|------|
| Expanded 网格 | 3 个设置卡片改为 3 列网格 (`Modifier.fillMaxWidth()` + `weight(1f)`) |
| Medium 双列 | 中等窗口 2 列 + 1 列换行 |

### 2.2 AIScreen 排版优化

**文件**: `desktop/feature-ui/.../settings/pages/AIScreen.kt`

| 项目 | 说明 |
|------|------|
| 三区域并行布局 | Expanded 下：服务商配置 + 批量处理 + 刷新策略 三栏并排（目前垂直堆叠） |
| Medium 两栏 | 配置+处理并排，刷新策略跨行 |
| Button 宽度响应 | 300dp 按钮在 Compact 下溢出，改为 `fillMaxWidth` 或 `200.dp` |

### 2.3 AudioEffectsScreen 均衡器重构

**文件**: `desktop/feature-ui/.../settings/pages/AudioEffectsScreen.kt`

| 项目 | 说明 |
|------|------|
| 水平 Slider 替代 | 目前使用 `detectVerticalDragGestures` 自定义垂直滑块。桌面端默认水平 Slider 更直观，提供选项切换 |
| 频段自适应 | 5 个频段在 `Row` 中用 `SpaceEvenly`，Compact 改为 `LazyRow` 可滚 |
| 预设展开 | Expanded 下 5 个预设一行铺开，不再分 2 行 |
| 混响布局 | 同预设，宽屏下 5 个一行 |

### 2.4 SongDetailScreen 统计卡片响应

**文件**: `desktop/feature-ui/.../library/pages/SongDetailScreen.kt`

| 项目 | 说明 |
|------|------|
| StatItem 布局 | 3+3 行目前固定 `aspectRatio(1f)`，Compact 下改为 2 列 x 3 行 |
| 歌曲详情 SegmentedControl | 三段切换在窄屏下文本过长截断，改为图标+文字或纯图标 |

### 2.5 PlaylistScreen 封面尺寸响应

**文件**: `desktop/feature-ui/.../playlist/pages/PlaylistScreen.kt`

| 项目 | 说明 |
|------|------|
| Header 封面 | 280dp 固定 → `min(maxWidth * 0.3, 280.dp)` |
| 折叠头阈值 | `maxHeaderCollapsePx = 160.dp` 在不同高度窗口行为不一致，改为相对值 |

---

## 第三阶段：详情页增强与全局优化（P3）

### 3.1 AlbumScreen / ArtistScreen 添加元数据展示

**文件**: `desktop/feature-ui/.../library/pages/AlbumScreen.kt`, `ArtistScreen.kt`

| 项目 | 说明 |
|------|------|
| 专辑封面和信息 | 添加专辑封面对头图，展示专辑名/艺术家/年份/曲目数（类似 PlaylistHeader） |
| Artist 同理 | 艺术家头图 + 简介 |

### 3.2 PlayContent 双栏右侧增强

**文件**: `desktop/feature-ui/.../player/pages/PlayContent.kt`

| 项目 | 说明 |
|------|------|
| 当前歌单封面墙 | 播放列表 Tab 中增加封面网格缩略图，提高视觉动感 |
| "正在播放"指示器 | 当前播放曲目增加红/蓝进度条或发光指示器（目前仅有颜色高亮） |

### 3.3 全局悬停效果

| 文件范围 | 说明 |
|---------|------|
| 所有 clickable 元素 | 添加 `.hoverable()` + 透明度/缩放变化 |
| 列表项 | `MusicListItem` 悬停背景色变化 |
| 按钮和 IconButton | 悬停时放大 1.05x 或背景色变化 |
| 滑块 Knob | 悬停时尺寸增大 |

### 3.4 全局滚动条美化

| 文件范围 | 说明 |
|---------|------|
| MusicListScrollbar | 桌面端滚动条加粗 + 悬停可见 |
| LazyColumn | 自定义 `ScrollState` 滚动条 |

### 3.5 固定 dp 值清理

系统性扫描 `desktop/feature-ui` 中的所有 `*.kt` 文件，查找：

```regex
(\d+)\.dp
```

替换策略：
- `padding(16.dp)` / `padding(24.dp)` → 基于 `WindowSizeInfo` 动态计算（`horizontalPadding` 已部分实现）
- 硬编码尺寸 → 基于窗口 `Dp` 比例计算
- 卡片固定高度 → `weight(1f)` / `fillMaxHeight`

---

## 技术方案

### 响应式尺寸工具（已有基础设施）

```kotlin
// WindowSizeClass.kt 已定义
sealed class WindowWidthSizeClass { Compact, Medium, Expanded }

// SubScreen.kt 已有水平 padding 计算
val horizontalPadding = when (sizeClass) {
    Expanded -> 32.dp; Medium -> 24.dp; Compact -> 16.dp
}
```

新增跨页面动态 dp 计算工具：

| 工具 | 说明 |
|------|------|
| `dynamicHorizontalPadding()` | 基于宽度统一返回 padding |
| `responsiveFontSize(compact, medium, expanded)` | 根据尺寸类选择字体 |
| `responsiveValue(compact, medium, expanded)` | 泛型响应值选择器 |

### 键盘快捷键方案

使用 Compose Desktop `onPreviewKeyEvent` 在 `HmpDesktopApplication.kt` 或 `MainScreen` 统一注册：

```kotlin
Modifier.onPreviewKeyEvent { event ->
    when {
        event.key == Key.Space && event.type == KeyEventType.KeyDown -> {
            playbackViewModel.playOrResume(); true
        }
        // ...
        else -> false
    }
}
```

---

## 文件变更清单

| 阶段 | 文件 | 变更类型 |
|------|------|---------|
| P1 | `player/pages/PlayContent.kt` | 重写 |
| P1 | `player/pages/PlayerScreen.kt` | 增补 |
| P1 | `player/pages/AdvancedLyrics.kt` | 修改 |
| P1 | `player/pages/LyricsScreen.kt` | 修改 |
| P1 | `player/pages/PlayerHeader.kt` | 重写 |
| P2 | `settings/pages/SettingScreen.kt` | 重写 |
| P2 | `settings/pages/AIScreen.kt` | 修改 |
| P2 | `settings/pages/AudioEffectsScreen.kt` | 重写 |
| P2 | `library/pages/SongDetailScreen.kt` | 修改 |
| P2 | `playlist/pages/PlaylistScreen.kt` | 修改 |
| P3 | `library/pages/AlbumScreen.kt` | 重写 |
| P3 | `library/pages/ArtistScreen.kt` | 重写 |
| P3 | `common/util/ResponsiveUtils.kt` | **新建** |
| P3 | 全局 `MusicListItem` / `Scrollbar` | 修改 |

---

## 注意事项

- 所有改动保持与 Android 端共享的 domain 和 viewmodel 层不变，只改 UI 表现层
- 新增响应式工具不破坏现有调用
- 键盘快捷键设计避免与 OS 快捷键冲突（可配置或使用 Modifier）
