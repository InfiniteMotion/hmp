# iOS vs Android UI 层实现差异对照

本文档记录 HMP 项目 iOS SwiftUI 迁移过程中，Android 端需要大量手写代码但 iOS 原生提供更优方案的组件和模式。供开发时参考，避免不必要的重复造轮子。

---

## 一、可大幅简化的组件（iOS 原生开箱即用）

### 1. SegmentedControl — 分段选择器

| 维度 | Android | iOS |
|------|---------|-----|
| 实现方式 | ~170 行自定义 `Surface` + `Row`/`Column` + `border` + `clip` | 原生 `SegmentedControl` / `Picker(.segmented)` |
| 滑块动画 | ❌ 无，仅颜色/字重切换 | ✅ 原生平滑滑块过渡 |
| 无障碍 | 需手动添加 `semantics` | ✅ 内建 VoiceOver 支持 |
| 动态类型 | 需手动处理 | ✅ 内建 Dynamic Type 适配 |
| 图标支持 | `SegmentedOption` 包含 `Painter?` | iOS 26+ `SegmentedControl` 支持图标 |
| 竖排变体 | `VerticalSegmentedControl` ~60 行 | 无原生竖排，可用 `Picker(.wheel)` 或自定义 |

**迁移策略**：
- 横排分段 → 直接使用 `SegmentedControl`，一行代码
- 需要图标 → iOS 26+ 原生支持，低版本降级为纯文字
- 竖排分段 → 少量自定义，但比 Android 简化很多

**对应 TODO**：P7.7

---

### 2. MusicListIndexStrip — 字母/锚点索引条

| 维度 | Android | iOS |
|------|---------|-----|
| 实现方式 | ~340 行完全自定义：拖拽手势 + 高亮逻辑 + 滚动联动 | `List` + `sectionIndexTitles` 几行配置 |
| 拖拽选择 | 手动 `detectDragGestures` + `onDragStart/onDrag/onDragEnd` | ✅ 内建拖拽 + 高亮 |
| 滚动联动 | 手动 `snapshotFlow { listState.firstVisibleItemIndex }` 计算 | ✅ 内建与列表联动 |
| 触觉反馈 | 手动 `haptic.performLightClick()` | ✅ 内建触觉反馈 |
| 无障碍 | 手动 `semantics { contentDescription }` | ✅ 内建无障碍 |

**迁移策略**：
- 使用 `List` 的 `sectionIndexTitles` 代理方法
- 数据源按拼音首字母分组（复用 `stringToPinyinSortKey`），`sectionIndexTitles` 自动生成 A-Z 索引
- 锚点模式（数值/时间）需少量自定义，但远少于 Android 340 行

**对应 TODO**：P7.17

---

### 3. SearchScreen — 搜索页面

| 维度 | Android | iOS |
|------|---------|-----|
| 搜索栏 | 手写 `TextField` + 去下划线 + 圆角样式 ~60 行 | `.searchable(text:)` 一个 modifier |
| 动画 | 无 | ✅ 原生搜索栏展开/收起动画 |
| 取消按钮 | 手动添加 | ✅ 原生自动显示取消按钮 |
| 结果切换 | 手动管理 UiState 状态 | ✅ 原生搜索结果与主内容分离 |
| 空状态 | 手写居中布局 | 可配合 `UiState` 枚举 + `@ViewBuilder` |

**迁移策略**：
- 使用 `.searchable(text: $searchText)` modifier
- `onChange(of: searchText)` 触发搜索
- 搜索结果与主内容用 `@ViewBuilder` 按 `searchText.isEmpty` 切换

**对应 TODO**：P7.21

---

### 4. PlayerScreen — 下滑关闭

| 维度 | Android | iOS |
|------|---------|-----|
| 实现方式 | 自定义 `nestedScroll` + `Animatable(0f)` + 偏移阈值 ~30 行 | `.presentationDetents` + `.interactiveDismiss` |
| 手势处理 | 手动计算偏移量 + 透明度渐变 | ✅ 原生手势 + 弹性动画 |
| 透明度渐变 | `graphicsLayer { alpha = 1f - (offsetY / dismissThreshold) }` | ✅ 原生半模态交互 |
| 适配性 | 需考虑不同屏幕尺寸阈值 | ✅ 原生适配 |

**迁移策略**：
- PlayerScreen 用 `.sheet` + `.presentationDetents([.medium, .large])` + `.interactiveDismiss`
- 全屏播放器用 `.fullScreenCover` + 自定义下滑手势（如需更细粒度控制）
- 或使用 iOS 16+ 的 `NavigationStack` + 自定义 `Toolbar` 实现返回

**对应 TODO**：P7.28

---

### 5. 模糊/毛玻璃效果

| 维度 | Android | iOS |
|------|---------|-----|
| 实现方式 | 第三方库 `dev.chrisbanes.haze`（Compose 无原生高性能模糊 API） | 原生 Liquid Glass (iOS 26+) / `.ultraThinMaterial` |
| 性能 | Haze 库优化过但仍是第三方 | ✅ 系统级 GPU 加速 |
| 一致性 | 需与 Material Design 适配 | ✅ 与系统 UI 风格一致 |
| 版本回退 | 不需要 | iOS 26 以下回退 `.regularMaterial` |

**迁移策略**：
- iOS 26+ 使用 Liquid Glass（系统级液态玻璃效果）
- iOS 26 以下使用 `.ultraThinMaterial` / `.regularMaterial`
- 版本判断：`if #available(iOS 26, *)` 切换

**涉及 TODO**：P7.9, P7.11, P7.28

---

### 6. SettingScreen — 设置列表

| 维度 | Android | iOS |
|------|---------|-----|
| 实现方式 | `Column` + `Card` + `Row` 手动模拟设置项 | `Form` / `List(.insetGrouped)` 原生分组样式 |
| 分组样式 | 手写 `surfaceVariant.copy(alpha = 0.5f)` 半透明 + 圆角 | ✅ 内建圆角分组 + 分割线 |
| 右箭头指示 | 手动添加 `Icons.Default.ChevronRight` | ✅ `NavigationLink` 自动显示 |
| 点击反馈 | 手动 `haptic.performClick()` | ✅ 内建触觉反馈 |

**迁移策略**：
- 设置主页用 `Form` 或 `List(.insetGrouped)` 原生样式
- 自定义项（Haze/主题预览）用 `Section` 嵌套自定义视图
- 保留品牌色的自定义修饰

**对应 TODO**：P7.37

---

## 二、可适度简化的组件（iOS 原生提供部分能力）

### 7. MainTabView — 主标签页

| 维度 | Android | iOS |
|------|---------|-----|
| 实现方式 | `HorizontalPager` + 手动 Tab 栏 + `CompositionLocalProvider` | `TabView` 原生支持两种模式 |
| 底部 Tab 栏 | 需手动实现 | ✅ `TabView` + `.tabItem` 开箱即用 |
| 滑动分页 | `HorizontalPager` 原生支持 | `TabView(.page)` 支持，但 Tab 栏样式不同 |
| 页面预加载 | `beyondViewportPageCount = 3` | SwiftUI 无直接对应，依赖系统管理 |
| 触觉反馈 | 手动 `LaunchedEffect` + `haptic.performClick()` | ✅ 内建切换反馈 |

**迁移策略**：
- iOS 26+ 推荐原生 `TabView` + Liquid Glass Tab 栏
- 如需保留 Android 的滑动分页交互，使用 `TabView(.page)` + 自定义 `TabPageIndicator`
- MiniPlayerBar 通过 `.overlay(alignment: .bottom)` 实现

**对应 TODO**：P7.16

---

### 8. AudioEffectsScreen — 音效调节

| 维度 | Android | iOS |
|------|---------|-----|
| 预设选择器 | 手写 `Box` + `background` + `clickable` + `chunked(2)` 分行 | `SegmentedControl` / `Picker` 原生 |
| 垂直滑块 | ~140 行自定义手势 `detectVerticalDragGestures` + 绘制轨道 | **无原生垂直 Slider**，仍需自定义 |
| EQ API | `android.media.audiofx.Equalizer` | `AVAudioEngine` + `AVAudioUnitEQ` 更成熟 |
| 混响预设 | 手写 5 个选择器 | `SegmentedControl` 简化 |

**迁移策略**：
- 预设选择器 → 原生 `SegmentedControl` 大幅简化
- 垂直滑块 → 仍需自定义（`DragGesture` + `GeometryReader`），但实现量少于 Android 140 行
- EQ 底层 API → `AVAudioEngine` 更成熟，UI 层用 SwiftUI Slider 水平变体 + 旋转技巧

**对应 TODO**：P7.41

---

### 9. UiState 体系

| 维度 | Android | iOS |
|------|---------|-----|
| 状态定义 | `sealed class UiState<T>` | `enum UiState<T>` 更简洁 |
| 视图切换 | 需独立 `UiStateContent<T>` 组件 | `@ViewBuilder` + `switch` 天然支持 |
| 代码量 | 2 个文件 | 1 个枚举 + ViewBuilder 内联 |

**迁移策略**：
- 定义 `enum UiState<T>` 枚举
- 视图切换直接用 `@ViewBuilder` 函数，无需独立组件
- `switch state { case .loading: ... case .success(let data): ... }` 一行搞定

**对应 TODO**：P7.5

---

## 三、需保持自定义的组件（iOS 无直接原生替代）

| 组件 | 原因 | 预估工作量 |
|------|------|-----------|
| **ColorTokens / TypographyTokens / AnimationTokens** | 品牌设计规范，必须自定义 | 中 |
| **DynamicBackground** | 专辑封面取色需 CoreImage 自实现，iOS 无 Palette 对应 | 中 |
| **业务弹窗** (7个) | MusicDetail/CreatePlaylist 等无原生对应 | 高 |
| **MiniPlayerBar** | 全局悬浮播放器需 overlay 自定义 | 中 |
| **CustomEqualizer** | 垂直滑块无原生对应 | 中 |
| **LyricsScreen** | 逐行高亮+译文+时间轴完全自定义 | 高 |
| **ThemeViewModel** | 动态取色逻辑需 CoreImage 重写 | 中 |

---

## 四、技术栈映射总表

| 能力 | Android 方案 | iOS 方案 | 优势方 |
|------|-------------|---------|--------|
| 分段选择器 | 自定义 Surface+Row (~170行) | 原生 `SegmentedControl` | 🍎 iOS |
| 字母索引条 | 自定义拖拽+高亮 (~340行) | `sectionIndexTitles` | 🍎 iOS |
| 搜索控制器 | 手写 TextField+状态 (~60行) | `.searchable()` modifier | 🍎 iOS |
| 模态页下滑关闭 | 自定义 nestedScroll (~30行) | `.presentationDetents` | 🍎 iOS |
| 毛玻璃效果 | 第三方 Haze 库 | Liquid Glass / Material | 🍎 iOS |
| 设置列表 | 手写 Card+Row | `Form` / `List(.insetGrouped)` | 🍎 iOS |
| 确认对话框 | AlertDialog 封装 | `.alert()` modifier | 🍎 iOS |
| 触觉反馈 | 自定义 3 级 | `UIImpactFeedbackGenerator` | 🟰 持平 |
| 播放器手势 | 自定义 nestedScroll | `.interactiveDismiss` | 🍎 iOS |
| 垂直滑块 | 自定义 DragGesture (~140行) | 自定义 DragGesture (~80行) | 🟰 持平 |
| 专辑封面取色 | Palette 库 | CoreImage 自实现 | 🤖 Android |
| 歌词解析 | JAudiotagger 库 | 无直接替代 | 🤖 Android |

---

## 五、代码量预估对比

| 模块 | Android 代码量 | iOS 预估代码量 | 节省比例 |
|------|---------------|-------------|---------|
| SegmentedControl | ~170 行 | ~10 行 | **-94%** |
| MusicListIndexStrip | ~340 行 | ~20 行 | **-94%** |
| SearchScreen | ~200 行 | ~80 行 | **-60%** |
| SettingScreen | ~150 行 | ~60 行 | **-60%** |
| PlayerScreen 手势 | ~30 行 | ~5 行 | **-83%** |
| 模糊效果 | 依赖第三方库 | 0 行（原生） | **-100%** |
| ConfirmDialog | ~40 行 | ~10 行 | **-75%** |
| AudioEffectsScreen | ~580 行 | ~350 行 | **-40%** |
| **P7 总计** | **~3000+ 行** | **~1500 行** | **~-50%** |

> 注：以上为 UI 层手动实现代码对比，不含 ViewModel 和数据层逻辑（两平台共享 KMP shared 层，ViewModel 逻辑量基本一致）。

---

© 2026 Hearable Music Player | Developed by WLYB
