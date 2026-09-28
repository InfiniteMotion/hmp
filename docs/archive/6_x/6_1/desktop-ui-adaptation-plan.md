# 桌面端 UI 适配与优化计划

> 版本: v6.1 | 分支: `develop-desktop` | 更新日期: 2026-05-14

## 概述

对 HMP 桌面端 (Compose Desktop) 的各页面组件与布局进行全面适配与优化，覆盖 App Shell、Tab 主页面、播放器、音乐库子页面、设置页面、通用组件及交互体验。

---

## 一、App Shell 与全局布局

### 1.1 MainScreen 响应式布局

- **文件**: `desktop/feature-ui/.../common/pages/MainScreen.kt`
- **现状**: 当前区分 Expanded (>=840dp) 和 Compact (<840dp) 两档
- **目标**: 保持两档方案，优化各档布局细节
  - **Expanded (>=840dp)**: 全宽内容 + BottomFusionBar 底部融合栏
  - **Compact (<840dp)**: TabPageIndicator 顶部指示器 + MiniPlayerBar 浮动播放条

### 1.3 BottomFusionBar 优化

- **文件**: `common/components/BottomFusionBar.kt`
- **现状**: 胶囊式双形态(导航展开/播放展开)已实现，播放胶囊 5 秒自动收起
- **目标**: 优化动画流畅度、交互反馈、状态切换逻辑

### 1.4 CustomTitleBar 适配

- **文件**: `desktop/app/.../CustomTitleBar.kt`
- **现状**: 自定义无边框窗口标题栏，40dp 高度，播放时沉浸式透明
- **目标**: 完善拖拽区域、窗口控制按钮样式、平台差异 (Windows/macOS)

### 1.5 DynamicBackground

- **文件**: `common/pages/base/DynamicBackground.kt`
- **现状**: 三种背景风格 (Fluid/Spots/Blur)，播放时淡入
- **目标**: 视觉效果调优、性能优化、风格切换过渡平滑度

---

## 二、Tab 主页面

### 2.1 HomeScreen 首页

- **文件**: `library/pages/HomeScreen.kt`
- **现状**: 推荐内容、每日精选、FixedMusicList
- **适配要点**:
  - 推荐卡片网格列数随窗口宽度自适应
  - 横向滚动列表的间距与边缘处理
  - 宽屏下内容区最大宽度限制

### 2.2 GalleryScreen 封面

- **文件**: `library/pages/GalleryScreen.kt`
- **现状**: 专辑封面网格 + MusicList (gallery preset)
- **适配要点**:
  - 网格列数响应式 (Compact: 2-3列, Medium: 4-5列, Expanded: 6+列)
  - 封面尺寸与间距优化
  - 瀑布流 vs 固定网格选择

### 2.3 ListScreen 列表

- **文件**: `library/pages/ListScreen.kt`
- **现状**: 用户自定义播放列表、默认列表(收藏/历史)、标签组(流派/心情/场景/语言/年代)
- **适配要点**:
  - 播放列表分组展示布局
  - 标签组横向/网格布局自适应
  - ListBanner 卡片尺寸优化

### 2.4 UserScreen 我的

- **文件**: `settings/pages/UserScreen.kt`
- **现状**: 头像、用户名、听歌时长图表、快捷入口卡片 (主题/音频/AI/设置)
- **适配要点**:
  - 快捷卡片网格列数自适应
  - 图表区域尺寸与标签可读性
  - 头像与信息区域布局

---

## 三、播放器页面

### 3.1 PlayerScreen 全屏播放器

- **文件**: `player/pages/PlayerScreen.kt` / `PlayContent.kt`
- **现状**: 封面、歌词、播放控制、进度条、模式按钮
- **适配要点**:
  - 宽屏: 左右分栏 (封面 | 歌词+控制)
  - 窄屏: 上下布局 (封面 -> 歌词 -> 控制)
  - 下拉关闭手势 (nested scroll)

### 3.2 LyricsScreen 歌词

- **文件**: `player/pages/LyricsScreen.kt` / `AdvancedLyrics.kt`
- **现状**: 全屏歌词显示、设置面板 (字号/对齐/显示模式)
- **适配要点**:
  - 歌词面板与播放器的联动
  - 设置面板布局与交互
  - 歌词动画滚动性能

### 3.3 PlaylistArea 播放列表

- **文件**: `player/pages/PlaylistArea.kt`
- **现状**: 当前播放队列展示
- **适配要点**:
  - 队列列表高度自适应
  - 拖拽排序交互
  - 当前播放高亮

### 3.4 MiniPlayerBar

- **文件**: `player/components/MiniPlayerBar.kt`
- **现状**: Compact 模式浮动播放条，显示封面/标题/歌手/播放控制/进度
- **适配要点**:
  - 浮动位置与 SafeArea 处理
  - 展开/收起动画
  - 进度条交互

### 3.5 AudioEffectsScreen

- **文件**: `settings/pages/AudioEffectsScreen.kt`
- **现状**: 均衡器预设、低音增强、环绕声、混响、自定义均衡器
- **适配要点**:
  - 均衡器滑块布局 (宽屏横向展开, 窄屏紧凑)
  - 预设选择器样式
  - 控件间距与触摸区域

---

## 四、音乐库子页面

### 4.1 SearchScreen 搜索

- **文件**: `library/pages/SearchScreen.kt`
- **现状**: 搜索框 + MusicList 结果，使用 SubScreen 基类
- **适配要点**:
  - 搜索框宽度与位置优化
  - 结果列表项高度与信息密度
  - 搜索历史/建议区域

### 4.2 SongDetailScreen 歌曲详情

- **文件**: `library/pages/SongDetailScreen.kt`
- **现状**: 海报、专辑封面、三段式信息 (User/Intro/Lyrics)、统计
- **适配要点**:
  - 宽屏: 左侧封面 + 右侧信息
  - 窄屏: 封面居中 + 下方信息
  - 信息分段切换交互

### 4.3 ArtistScreen 歌手

- **文件**: `library/pages/ArtistScreen.kt`
- **现状**: 歌手头像/Banner + 歌曲列表，使用 SubScreen
- **适配要点**:
  - 歌手页头部区域尺寸
  - 歌曲列表与头部的滚动联动

### 4.4 AlbumScreen 专辑

- **文件**: `library/pages/AlbumScreen.kt`
- **现状**: 专辑封面 + 信息 + 歌曲列表，使用 SubScreen
- **适配要点**:
  - 专辑封面与信息布局
  - 歌曲列表样式统一

### 4.5 PlaylistScreen 播放列表

- **文件**: `playlist/pages/PlaylistScreen.kt`
- **现状**: 列表头部 Banner + 曲目列表
- **适配要点**:
  - Banner 区域高度与封面展示
  - 列表操作栏 (播放全部/随机播放)

---

## 五、设置页面

### 5.1 SettingScreen 设置主页

- **文件**: `settings/pages/SettingScreen.kt`
- **现状**: 设置项列表，链接到子设置页
- **适配要点**: 设置项间距、图标与文字对齐

### 5.2 ProfileSettingsScreen

- **文件**: `settings/pages/ProfileSettingsScreen.kt`
- **现状**: 头像更换 (文件选择器)、用户名修改
- **适配要点**: 头像上传交互、输入框样式

### 5.3 BackupSettingsScreen

- **文件**: `settings/pages/BackupSettingsScreen.kt`
- **现状**: 导出/导入备份、本地备份管理
- **适配要点**: 文件选择器集成、进度反馈

### 5.4 LibrarySettingsScreen

- **文件**: `settings/pages/LibrarySettingsScreen.kt`
- **现状**: 音乐库目录管理、扫描设置
- **适配要点**: 目录列表展示、添加/移除交互

### 5.5 AIScreen AI 推荐

- **文件**: `settings/pages/AIScreen.kt`
- **现状**: API 配置、推荐播放列表生成
- **适配要点**: API Key 输入安全、生成状态反馈

### 5.6 UserUsageDataScreen

- **文件**: `settings/pages/UserUsageDataScreen.kt`
- **现状**: 听歌统计、口味画像、排行榜、历史记录
- **适配要点**: 图表响应式、数据密度、Tab 切换

### 5.7 CustomScreen 主题定制

- **文件**: `library/pages/CustomScreen.kt`
- **现状**: 主题模式 (亮/暗/自动)、背景风格 (Fluid/Spots/Blur)、毛玻璃强度
- **适配要点**: 预览效果实时反馈、选项布局

---

## 六、通用组件与设计系统

### 6.1 MusicList 音乐列表

- **文件**: `library/pages/components/musiclist/*` (10 个文件)
- **组件**: MusicList, MusicListConfig, MusicListContent, MusicListItem, MusicListHeader, MusicListEditToolbar, MusicListIndexStrip, MusicListPreview, MusicListScrollbar, MusicListState
- **适配要点**:
  - 列表项高度与信息密度
  - 宽屏多列 vs 窄屏单列
  - 排序/编辑/索引条的交互
  - 滚动条样式与行为

### 6.2 Dialogs 对话框

- **文件**: `common/dialogs/*` (7 个业务对话框 + 4 个基础对话框)
- **对话框清单**: MusicDetailDialog, CreatePlaylistDialog, MusicPickerDialog, PlaylistPickerDialog, MusicScanDialog, TimerDialog, AddSongToPlaylistDialog
- **适配要点**:
  - 宽屏: 居中固定尺寸
  - 窄屏: 近全屏展示
  - 内容可滚动处理

### 6.3 TabScreen / SubScreen 基类

- **文件**: `common/pages/base/TabScreen.kt` / `SubScreen.kt`
- **现状**: 提供统一的标题栏、返回按钮、响应式 padding
- **适配要点**: padding 策略与窗口尺寸联动

### 6.4 DesignSystem 设计系统

- **文件**: `common/design/*`
- **内容**: ColorTokens, TypographyTokens, AnimationTokens, ThemeExtensions
- **适配要点**: 全局 Token 一致性检查、动态主题色适配

---

## 七、交互与体验优化

### 7.1 键盘快捷键

| 快捷键 | 功能 | 状态 |
|--------|------|------|
| Escape | 返回上一页 | 已实现 |
| Space | 播放/暂停 | 待实现 |
| Ctrl+F | 打开搜索 | 待实现 |
| 左/右箭头 | 上/下一首 | 待实现 |
| 上/下箭头 | 音量调节 | 待实现 |

### 7.2 鼠标手势

- 播放器下拉关闭手势 (nested scroll)
- 列表横向滑动操作 (删除/收藏)
- 滚轮行为优化 (平滑滚动、加速)

### 7.3 系统托盘

- **文件**: `desktop/app/.../SystemTrayManager.kt`
- **现状**: 托盘图标 + 播放控制 (播放/暂停/上一首/下一首)
- **适配要点**: 菜单完整性、图标适配、通知集成

### 7.4 IntroScreen 引导

- **文件**: `common/pages/IntroScreen.kt`
- **现状**: 3 步引导 (权限 -> 音乐扫描 -> 开始体验)
- **适配要点**: 桌面端权限模型适配、扫描进度展示

---

## 优先级

| 优先级 | 范围 | 内容 |
|--------|------|------|
| **P0 核心体验** | 一 (App Shell) + 三 (播放器) + 六.1 (MusicList) | #1-5, #10-14, #27 |
| **P1 主要页面** | 二 (Tab页面) + 四 (音乐库子页) | #6-9, #15-19 |
| **P2 完善** | 五 (设置) + 六.2-4 (对话框/基类/设计系统) + 七 (交互) | #20-26, #28-34 |

---

## 文件索引

### App Shell
| 文件 | 路径 |
|------|------|
| Main.kt | `desktop/app/src/desktopMain/kotlin/com/hmp/desktop/Main.kt` |
| MainScreen.kt | `desktop/feature-ui/.../common/pages/MainScreen.kt` |
| CustomTitleBar.kt | `desktop/app/.../CustomTitleBar.kt` |
| NavigationRail.kt | `common/components/NavigationRail.kt` |
| BottomFusionBar.kt | `common/components/BottomFusionBar.kt` |
| TabPageIndicator.kt | `common/components/TabPageIndicator.kt` |
| DynamicBackground.kt | `common/pages/base/DynamicBackground.kt` |
| WindowSizeClass.kt | `common/layout/WindowSizeClass.kt` |

### Tab 页面
| 文件 | 路径 |
|------|------|
| TabsHost.kt | `common/pages/TabsHost.kt` |
| HomeScreen.kt | `library/pages/HomeScreen.kt` |
| GalleryScreen.kt | `library/pages/GalleryScreen.kt` |
| ListScreen.kt | `library/pages/ListScreen.kt` |
| UserScreen.kt | `settings/pages/UserScreen.kt` |

### 播放器
| 文件 | 路径 |
|------|------|
| PlayerScreen.kt | `player/pages/PlayerScreen.kt` |
| PlayContent.kt | `player/pages/PlayContent.kt` |
| LyricsScreen.kt | `player/pages/LyricsScreen.kt` |
| AdvancedLyrics.kt | `player/pages/AdvancedLyrics.kt` |
| PlaylistArea.kt | `player/pages/PlaylistArea.kt` |
| MiniPlayerBar.kt | `player/components/MiniPlayerBar.kt` |
| AudioEffectsScreen.kt | `settings/pages/AudioEffectsScreen.kt` |

### 音乐库子页面
| 文件 | 路径 |
|------|------|
| SearchScreen.kt | `library/pages/SearchScreen.kt` |
| SongDetailScreen.kt | `library/pages/SongDetailScreen.kt` |
| ArtistScreen.kt | `library/pages/ArtistScreen.kt` |
| AlbumScreen.kt | `library/pages/AlbumScreen.kt` |
| PlaylistScreen.kt | `playlist/pages/PlaylistScreen.kt` |

### 设置页面
| 文件 | 路径 |
|------|------|
| SettingScreen.kt | `settings/pages/SettingScreen.kt` |
| ProfileSettingsScreen.kt | `settings/pages/ProfileSettingsScreen.kt` |
| BackupSettingsScreen.kt | `settings/pages/BackupSettingsScreen.kt` |
| LibrarySettingsScreen.kt | `settings/pages/LibrarySettingsScreen.kt` |
| AIScreen.kt | `settings/pages/AIScreen.kt` |
| UserUsageDataScreen.kt | `settings/pages/UserUsageDataScreen.kt` |
| CustomScreen.kt | `library/pages/CustomScreen.kt` |

### 通用组件
| 文件 | 路径 |
|------|------|
| MusicList 系列 | `library/pages/components/musiclist/*` (10 个文件) |
| 对话框系列 | `common/dialogs/*` (11 个文件) |
| TabScreen.kt | `common/pages/base/TabScreen.kt` |
| SubScreen.kt | `common/pages/base/SubScreen.kt` |
| DesignSystem | `common/design/*` (6 个文件) |
