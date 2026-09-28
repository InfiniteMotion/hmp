# 文件迁移表格

## 4.1 通用模块 (ui/common/)

### 4.1.1 通用组件
| 现有位置 | 新位置 | 说明 |
|---------|-------|------|
| `ui/components/Avatar.kt` | `ui/common/components/Avatar.kt` | 头像组件 |
| `ui/components/Capsule.kt` | `ui/common/components/Capsule.kt` | 胶囊组件 |
| `ui/components/DotPager.kt` | `ui/common/components/DotPager.kt` | 点导航组件 |
| `ui/components/ListeningChart.kt` | `ui/common/components/ListeningChart.kt` | 听歌统计图表组件 |
| `ui/components/MyButton.kt` | `ui/common/components/MyButton.kt` | 自定义按钮组件 |
| `ui/components/SegmentedControl.kt` | `ui/common/components/SegmentedControl.kt` | 分段控制组件 |
| `ui/components/SquareCard.kt` | `ui/common/components/SquareCard.kt` | 方形卡片组件 |
| `ui/components/TitleWidget.kt` | `ui/common/components/TitleWidget.kt` | 标题组件 |
| `ui/components/common/` | `ui/common/components/` | 通用组件 |

### 4.1.2 全局对话框
| 现有位置 | 新位置 | 说明 |
|---------|-------|------|
| `ui/dialogs/ConfirmDialog.kt` | `ui/common/dialogs/ConfirmDialog.kt` | 确认对话框（全局） |
| `ui/dialogs/InputDialog.kt` | `ui/common/dialogs/InputDialog.kt` | 输入对话框（全局） |
| `ui/dialogs/MessageToast.kt` | `ui/common/dialogs/MessageToast.kt` | 消息提示对话框（全局） |
| `ui/dialogs/ScrimDialog.kt` | `ui/common/dialogs/ScrimDialog.kt` | 遮罩对话框（全局） |

### 4.1.3 通用页面
| 现有位置 | 新位置 | 说明 |
|---------|-------|------|
| `ui/pages/IntroScreen.kt` | `ui/common/pages/IntroScreen.kt` | 引导页面 |
| `ui/pages/UserScreen.kt` | `ui/common/pages/UserScreen.kt` | 用户页面 |
| `ui/pages/UserUsageDataScreen.kt` | `ui/common/pages/UserUsageDataScreen.kt` | 用户使用数据页面 |
| `ui/pages/base/` | `ui/common/pages/base/` | 基础页面组件 |

### 4.1.4 其他通用文件
| 现有位置 | 新位置 | 说明 |
|---------|-------|------|
| `ui/navigation/` | `ui/common/navigation/` | 导航相关文件 |
| `ui/util/` | `ui/common/util/` | 工具类文件 |
| `ui/design/` | `ui/common/design/` | 设计系统文件 |
| `ui/viewmodel/DialogManagerViewModel.kt` | `ui/common/viewmodel/DialogManagerViewModel.kt` | 对话框管理 ViewModel（全局） |
| `ui/viewmodel/DialogViewModel.kt` | `ui/common/viewmodel/DialogViewModel.kt` | 对话框 ViewModel（全局） |
| `ui/viewmodel/ThemeViewModel.kt` | `ui/common/viewmodel/ThemeViewModel.kt` | 主题 ViewModel |
| `ui/viewmodel/UserUsageDataViewModel.kt` | `ui/common/viewmodel/UserUsageDataViewModel.kt` | 用户使用数据 ViewModel |

## 4.2 播放器模块 (ui/player/)

### 4.2.1 播放器组件
| 现有位置 | 新位置 | 说明 |
|---------|-------|------|
| `ui/components/MiniPlayerBar.kt` | `ui/player/components/MiniPlayerBar.kt` | 迷你播放器栏组件 |
| `ui/components/MiniPlayerSafeSpacer.kt` | `ui/player/components/MiniPlayerSafeSpacer.kt` | 迷你播放器安全间隔组件 |

### 4.2.2 播放器局部对话框
| 现有位置 | 新位置 | 说明 |
|---------|-------|------|
| `ui/dialogs/TimerDialog.kt` | `ui/player/dialogs/TimerDialog.kt` | 定时对话框（播放器局部） |

### 4.2.3 播放器页面
| 现有位置 | 新位置 | 说明 |
|---------|-------|------|
| `ui/pages/player/PlayerScreen.kt` | `ui/player/pages/PlayerScreen.kt` | 播放器主页面 |
| `ui/pages/player/LyricsScreen.kt` | `ui/player/pages/LyricsScreen.kt` | 歌词页面 |
| `ui/pages/player/AdvancedLyrics.kt` | `ui/player/pages/AdvancedLyrics.kt` | 高级歌词页面 |
| `ui/pages/player/PlayContent.kt` | `ui/player/pages/PlayContent.kt` | 播放内容页面 |
| `ui/pages/player/TechnicalInfoCard.kt` | `ui/player/pages/TechnicalInfoCard.kt` | 技术信息卡片 |
| `ui/pages/AudioEffectsScreen.kt` | `ui/player/pages/AudioEffectsScreen.kt` | 音频效果页面 |

### 4.2.4 播放器 ViewModel
| 现有位置 | 新位置 | 说明 |
|---------|-------|------|
| `ui/viewmodel/AudioEffectViewModel.kt` | `ui/player/viewmodel/AudioEffectViewModel.kt` | 音频效果 ViewModel |
| `ui/viewmodel/PlayControlViewModel.kt` | `ui/player/viewmodel/PlayControlViewModel.kt` | 播放控制 ViewModel |
| `ui/viewmodel/PlaybackViewModel.kt` | `ui/player/viewmodel/PlaybackViewModel.kt` | 播放 ViewModel |
| `ui/viewmodel/PlayerCallbacks.kt` | `ui/player/viewmodel/PlayerCallbacks.kt` | 播放器回调 |
| `ui/viewmodel/PlayerUiState.kt` | `ui/player/viewmodel/PlayerUiState.kt` | 播放器 UI 状态 |
| `ui/viewmodel/PlaylistQueueViewModel.kt` | `ui/player/viewmodel/PlaylistQueueViewModel.kt` | 播放队列 ViewModel |

## 4.3 音乐库模块 (ui/library/)

### 4.3.1 音乐库组件
| 现有位置 | 新位置 | 说明 |
|---------|-------|------|
| `ui/components/AlbumCover.kt` | `ui/library/components/AlbumCover.kt` | 专辑封面组件 |
| `ui/components/ListBanner.kt` | `ui/library/components/ListBanner.kt` | 列表横幅组件 |
| `ui/components/musiclist/` | `ui/library/components/musiclist/` | 音乐列表组件 |

### 4.3.2 音乐库局部对话框
| 现有位置 | 新位置 | 说明 |
|---------|-------|------|
| `ui/dialogs/MusicDetailDialog.kt` | `ui/library/dialogs/MusicDetailDialog.kt` | 音乐详情对话框（音乐库局部） |
| `ui/dialogs/MusicPickerDialog.kt` | `ui/library/dialogs/MusicPickerDialog.kt` | 音乐选择对话框（音乐库局部） |
| `ui/dialogs/MusicScanDialog.kt` | `ui/library/dialogs/MusicScanDialog.kt` | 音乐扫描对话框（音乐库局部） |

### 4.3.3 音乐库页面
| 现有位置 | 新位置 | 说明 |
|---------|-------|------|
| `ui/pages/HomeScreen.kt` | `ui/library/pages/HomeScreen.kt` | 音乐库主页 |
| `ui/pages/ListScreen.kt` | `ui/library/pages/ListScreen.kt` | 音乐列表页面 |
| `ui/pages/AlbumScreen.kt` | `ui/library/pages/AlbumScreen.kt` | 专辑页面 |
| `ui/pages/ArtistScreen.kt` | `ui/library/pages/ArtistScreen.kt` | 艺术家页面 |
| `ui/pages/SearchScreen.kt` | `ui/library/pages/SearchScreen.kt` | 搜索页面 |
| `ui/pages/SongDetailScreen.kt` | `ui/library/pages/SongDetailScreen.kt` | 歌曲详情页面 |
| `ui/pages/CustomScreen.kt` | `ui/library/pages/CustomScreen.kt` | 自定义页面 |
| `ui/pages/GalleryScreen.kt` | `ui/library/pages/GalleryScreen.kt` | 图库页面 |

### 4.3.4 音乐库 ViewModel
| 现有位置 | 新位置 | 说明 |
|---------|-------|------|
| `ui/viewmodel/LibraryViewModel.kt` | `ui/library/viewmodel/LibraryViewModel.kt` | 音乐库 ViewModel |
| `ui/viewmodel/SearchViewModel.kt` | `ui/library/viewmodel/SearchViewModel.kt` | 搜索 ViewModel |
| `ui/viewmodel/SongDetailViewModel.kt` | `ui/library/viewmodel/SongDetailViewModel.kt` | 歌曲详情 ViewModel |

## 4.4 播放列表模块 (ui/playlist/)

### 4.4.1 播放列表组件
| 现有位置 | 新位置 | 说明 |
|---------|-------|------|
| `ui/components/PlaylistArea.kt` | `ui/playlist/components/PlaylistArea.kt` | 播放列表区域组件 |

### 4.4.2 播放列表局部对话框
| 现有位置 | 新位置 | 说明 |
|---------|-------|------|
| `ui/dialogs/AddSongToPlaylistDialog.kt` | `ui/playlist/dialogs/AddSongToPlaylistDialog.kt` | 添加歌曲到播放列表对话框（播放列表局部） |
| `ui/dialogs/CreatePlaylistDialog.kt` | `ui/playlist/dialogs/CreatePlaylistDialog.kt` | 创建播放列表对话框（播放列表局部） |
| `ui/dialogs/PlaylistPickerDialog.kt` | `ui/playlist/dialogs/PlaylistPickerDialog.kt` | 播放列表选择对话框（播放列表局部） |

### 4.4.3 播放列表页面
| 现有位置 | 新位置 | 说明 |
|---------|-------|------|
| `ui/pages/playlist/PlaylistScreen.kt` | `ui/playlist/pages/PlaylistScreen.kt` | 播放列表页面 |
| `ui/pages/playlist/PlaylistManageScreen.kt` | `ui/playlist/pages/PlaylistManageScreen.kt` | 播放列表管理页面 |

### 4.4.4 播放列表 ViewModel
| 现有位置 | 新位置 | 说明 |
|---------|-------|------|
| `ui/viewmodel/PlaylistViewModel.kt` | `ui/playlist/viewmodel/PlaylistViewModel.kt` | 播放列表 ViewModel |

## 4.5 设置模块 (ui/settings/)

### 4.5.1 设置页面
| 现有位置 | 新位置 | 说明 |
|---------|-------|------|
| `ui/pages/settings/SettingScreen.kt` | `ui/settings/pages/SettingScreen.kt` | 设置主页面 |
| `ui/pages/settings/ProfileSettingsScreen.kt` | `ui/settings/pages/ProfileSettingsScreen.kt` | 个人资料设置页面 |
| `ui/pages/settings/BackupSettingsScreen.kt` | `ui/settings/pages/BackupSettingsScreen.kt` | 备份设置页面 |
| `ui/pages/settings/LibrarySettingsScreen.kt` | `ui/settings/pages/LibrarySettingsScreen.kt` | 库设置页面 |

### 4.5.2 设置 ViewModel
| 现有位置 | 新位置 | 说明 |
|---------|-------|------|
| `ui/viewmodel/SettingsViewModel.kt` | `ui/settings/viewmodel/SettingsViewModel.kt` | 设置 ViewModel |

## 4.6 AI 功能模块 (ui/ai/)

### 4.6.1 AI 页面
| 现有位置 | 新位置 | 说明 |
|---------|-------|------|
| `ui/pages/AIScreen.kt` | `ui/ai/pages/AIScreen.kt` | AI 推荐页面 |

### 4.6.2 AI ViewModel
| 现有位置 | 新位置 | 说明 |
|---------|-------|------|
| `ui/viewmodel/RecommendationViewModel.kt` | `ui/ai/viewmodel/RecommendationViewModel.kt` | 推荐 ViewModel |