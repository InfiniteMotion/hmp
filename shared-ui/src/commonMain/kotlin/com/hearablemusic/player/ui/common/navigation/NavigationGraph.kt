package com.hearablemusic.player.ui.common.navigation

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.hearablemusic.player.ui.MainShell
import com.hearablemusic.player.ui.chat.ChatScreen
import com.hearablemusic.player.ui.settings.pages.AIScreen
import com.hearablemusic.player.ui.library.pages.AlbumScreen
import com.hearablemusic.player.ui.library.pages.ArtistScreen
import com.hearablemusic.player.ui.settings.pages.AudioEffectsScreen
import com.hearablemusic.player.ui.library.pages.CustomScreen
import com.hearablemusic.player.ui.library.pages.EditMusicTagsScreen
import com.hearablemusic.player.ui.library.pages.SearchScreen
import com.hearablemusic.player.ui.library.pages.SongDetailScreen
import com.hearablemusic.player.ui.settings.pages.UserUsageDataScreen
import com.hearablemusic.player.ui.player.pages.LyricsScreen
import com.hearablemusic.player.ui.player.pages.PlayerScreen
import com.hearablemusic.player.ui.playlist.pages.PlaylistManageScreen
import com.hearablemusic.player.ui.playlist.pages.PlaylistScreen
import com.hearablemusic.player.ui.settings.pages.BackupSettingsScreen
import com.hearablemusic.player.ui.settings.pages.LyricsSettingsPage
import com.hearablemusic.player.ui.settings.pages.LibrarySettingsScreen
import com.hearablemusic.player.ui.settings.pages.ProfileSettingsScreen
import com.hearablemusic.player.ui.settings.pages.SettingScreen
import com.hmp.domain.setting.usecase.LyricsSettingsUseCase
import org.koin.compose.koinInject

/**
 * 导航图定义
 * 集中管理所有页面的导航配置，包含动画和参数传递
 *
 * 本文件使用 Navigation3 的声明式 API (entryProvider + entry) 定义路由与页面的映射关系，
 * 每个路由可以独立配置转场动画和深层链接。这种集中式管理便于维护和确保类型安全。
 *
 * 使用模式：
 * 1. 使用 `entry<路由类型> { ... }` 注册页面，其中 lambda 接收路由参数（如果是 data class）
 * 2. 可以通过 `deepLinks` 参数为路由添加深层链接支持
 * 3. 可以通过 `metadata` 参数配置转场动画（进入、退出、预测性返回）
 * 4. 页面所需 ViewModel 由页面自行获取（koinViewModel/activityViewModel），导航图不传递 VM 实例
 *
 * @param navController 导航控制器
 * @param pagerState 标签页的Pager状态（由 AppRoot 持有，MainShell 与 BottomFusionBar 共用）
 */
@Composable
fun navigationGraph(
    navController: NavBackStack<NavKey>,
    pagerState: PagerState
) = entryProvider {
    // Main 模块
    entry<Routes.Main.Tabs> {
        MainShell(
            navController = navController,
            pagerState = pagerState
        )
    }

    entry<Routes.Library.SongDetail> { route ->
        SongDetailScreen(
            navController = navController,
            musicId = route.musicId,
        )
    }

    entry<Routes.Library.EditMusicTags> { route ->
        EditMusicTagsScreen(
            navController = navController,
            musicId = route.musicId,
        )
    }

    // Player 模块
    entry<Routes.Player.Player> {
        PlayerScreen(
            navController = navController
        )
    }

    // Settings 模块
    entry<Routes.Settings.Setting> {
        SettingScreen(navController)
    }

    entry<Routes.Settings.ProfileSettings> {
        ProfileSettingsScreen(navController = navController)
    }

    entry<Routes.Settings.BackupSettings> {
        BackupSettingsScreen(navController = navController)
    }

    entry<Routes.Settings.LibrarySettings> {
        LibrarySettingsScreen(navController = navController)
    }

    entry<Routes.Settings.LyricsSettings> {
        val useCase: LyricsSettingsUseCase = koinInject()
        LyricsSettingsPage(
            lyricsSettingsUseCase = useCase,
            onBack = { navController.removeLastOrNull() }
        )
    }

    // Library 模块
    entry<Routes.Library.Search> {
        SearchScreen(
            navController = navController
        )
    }

    entry<Routes.Playlist.Playlist> { route ->
        PlaylistScreen(
            navController = navController,
            playlistName = route.name
        )
    }

    entry<Routes.Playlist.CustomPlaylist> { route ->
        PlaylistScreen(
            navController = navController,
            playlistId = route.playlistId
        )
    }

    entry<Routes.Playlist.UserPlaylistManage> {
        PlaylistManageScreen(navController = navController)
    }

    entry<Routes.Library.Artist> { route ->
        ArtistScreen(
            navController = navController,
            artistName = route.name
        )
    }

    entry<Routes.Library.Album> { route ->
        AlbumScreen(
            navController = navController,
            albumName = route.name
        )
    }

    // Player 模块其他路由
    entry<Routes.Player.AudioEffects> {
        AudioEffectsScreen(navController = navController)
    }

    entry<Routes.Player.Lyrics> {
        LyricsScreen(
            onNavigateToSettings = { navController.add(Routes.Settings.LyricsSettings) }
        )
    }

    // AI 模块
    entry<Routes.AI.AI> {
        AIScreen(
            navController = navController
        )
    }

    // Custom 模块
    entry<Routes.Custom.Custom> {
        CustomScreen(navController = navController)
    }

    // UserData 模块
    entry<Routes.UserData.UserUsageData> {
        UserUsageDataScreen(navController = navController)
    }

    // Companion（听歌伙伴对话）
    entry<Routes.Companion.Chat> {
        ChatScreen(navController = navController)
    }
}
