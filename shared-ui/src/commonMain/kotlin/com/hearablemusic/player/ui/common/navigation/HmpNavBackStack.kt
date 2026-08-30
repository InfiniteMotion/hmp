package com.hearablemusic.player.ui.common.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * 跨平台 NavBackStack 工厂。
 *
 * nav3 的 rememberNavBackStack(vararg)（无 SavedStateConfiguration 的重载）是
 * Android 专属反射实现；非 Android 平台必须走显式注册了 NavKey 多态
 * serializer 的版本。本函数统一两侧都走显式注册版，避免平台行为分叉。
 *
 * 维护约束：新增路由时需在此补 subclass 注册——漏注册无编译期报错，
 * 仅在该 key 参与保存/恢复（进程重建/配置变更）时运行时报错。
 */
private val HMP_NAV_KEY_SERIALIZERS = SerializersModule {
    polymorphic(baseClass = NavKey::class) {
        // Main
        subclass(serializer = Routes.Main.Tabs.serializer())
        subclass(serializer = Routes.Main.Home.serializer())
        subclass(serializer = Routes.Main.Gallery.serializer())
        subclass(serializer = Routes.Main.List.serializer())
        subclass(serializer = Routes.Main.User.serializer())
        // Player
        subclass(serializer = Routes.Player.Player.serializer())
        subclass(serializer = Routes.Player.Lyrics.serializer())
        subclass(serializer = Routes.Player.AudioEffects.serializer())
        // Library
        subclass(serializer = Routes.Library.Search.serializer())
        subclass(serializer = Routes.Library.SongDetail.serializer())
        subclass(serializer = Routes.Library.EditMusicTags.serializer())
        subclass(serializer = Routes.Library.Artist.serializer())
        subclass(serializer = Routes.Library.Album.serializer())
        // Playlist
        subclass(serializer = Routes.Playlist.Playlist.serializer())
        subclass(serializer = Routes.Playlist.CustomPlaylist.serializer())
        subclass(serializer = Routes.Playlist.UserPlaylistManage.serializer())
        // Settings
        subclass(serializer = Routes.Settings.Setting.serializer())
        subclass(serializer = Routes.Settings.ProfileSettings.serializer())
        subclass(serializer = Routes.Settings.BackupSettings.serializer())
        subclass(serializer = Routes.Settings.LibrarySettings.serializer())
        subclass(serializer = Routes.Settings.LyricsSettings.serializer())
        // AI / Custom / UserData
        subclass(serializer = Routes.AI.AI.serializer())
        subclass(serializer = Routes.Custom.Custom.serializer())
        subclass(serializer = Routes.UserData.UserUsageData.serializer())
        // Companion（听歌伙伴对话）
        subclass(serializer = Routes.Companion.Chat.serializer())
    }
}

/** HMP 统一 NavBackStack 入口：显式注册版 rememberNavBackStack 的包装。 */
@Composable
fun rememberHmpNavBackStack(vararg initialStack: NavKey): NavBackStack<NavKey> {
    val configuration = remember {
        SavedStateConfiguration {
            serializersModule = HMP_NAV_KEY_SERIALIZERS
        }
    }
    return rememberNavBackStack(configuration, *initialStack)
}
