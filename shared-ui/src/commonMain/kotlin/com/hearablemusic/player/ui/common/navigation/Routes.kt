package com.hearablemusic.player.ui.common.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * 集中式路由定义
 * 按功能模块拆分，提供类型安全的路由参数传递
 *
 * 使用说明：
 * 1. 所有路由均为 NavKey 的子类，无参数路由使用 object，带参数路由使用 data class
 * 2. 路由参数必须使用 @Serializable 注解，以便 Navigation3 序列化/反序列化
 * 3. 导航时通过 Routes.模块名.路由名 引用，如 Routes.Library.SongDetail(musicId = 123)
 * 4. 添加新路由时，需同时在 NavigationGraph.kt 注册页面映射，
 *    并在 HmpNavBackStack.kt 的 serializer 注册表补 subclass（漏注册仅运行时报错）
 */
object Routes {
    /**
     * 主标签页模块路由
     * 包含底部导航的四个标签页及其统一容器
     */
    object Main {
        /** Tabs 容器页路由（Home/Gallery/List/User 的统一承载） */
        @Serializable object Tabs : NavKey
        
        /** 主页路由 */
        @Serializable object Home : NavKey
        
        /** 画廊页路由 */
        @Serializable object Gallery : NavKey
        
        /** 列表页路由 */
        @Serializable object List : NavKey
        
        /** 用户页路由 */
        @Serializable object User : NavKey
    }
    
    /**
     * 播放器模块路由
     * 包含播放器界面、歌词显示和音频效果调节
     */
    object Player {
        /** 播放器页路由 */
        @Serializable object Player : NavKey
        
        /** 歌词页路由 */
        @Serializable object Lyrics : NavKey
        
        /** 音频效果页路由 */
        @Serializable object AudioEffects : NavKey
    }
    
    /**
     * 音乐库模块路由
     * 包含搜索、歌曲详情、艺术家和专辑浏览功能
     */
    object Library {
        /** 搜索页路由 */
        @Serializable object Search : NavKey
        
        /**
         * 歌曲详情页路由
         * @param musicId 歌曲的唯一标识符
         */
        @Serializable data class SongDetail(val musicId: Long) : NavKey

        /**
         * 音乐标签编辑页路由
         * @param musicId 歌曲的唯一标识符
         */
        @Serializable data class EditMusicTags(val musicId: Long) : NavKey
        
        /**
         * 艺术家页路由
         * @param name 艺术家名称
         */
        @Serializable data class Artist(val name: String) : NavKey
        
        /**
         * 专辑页路由
         * @param name 专辑名称
         */
        @Serializable data class Album(val name: String) : NavKey
    }
    
    /**
     * 播放列表模块路由
     * 包含按名称的标签播放列表、自定义播放列表（按ID）和用户歌单管理
     */
    object Playlist {
        /**
         * 播放列表页路由（按名称，用于标签列表与默认/红心/最近）
         * @param name 播放列表名称
         */
        @Serializable data class Playlist(val name: String) : NavKey
        
        /**
         * 用户自定义播放列表详情页路由（按 ID）
         * @param playlistId 播放列表的唯一标识符
         */
        @Serializable data class CustomPlaylist(val playlistId: Long) : NavKey
        
        /** 用户歌单管理页路由 */
        @Serializable object UserPlaylistManage : NavKey
    }
    
    /**
     * 设置模块路由
     * 包含应用设置、个人资料、备份和音乐库设置
     */
    object Settings {
        /** 设置页路由 */
        @Serializable object Setting : NavKey

        /** 个人资料设置路由 */
        @Serializable object ProfileSettings : NavKey

        /** 备份设置路由 */
        @Serializable object BackupSettings : NavKey

        /** 音乐库设置路由 */
        @Serializable object LibrarySettings : NavKey

        /** 歌词设置路由 */
        @Serializable object LyricsSettings : NavKey

        /** M6-T4：Agent 操作审计日志页 */
        @Serializable object AuditLog : NavKey
    }
    
    /**
     * AI 模块路由
     * 包含 AI 推荐和智能播放列表生成功能
     */
    object AI {
        /** AI页路由 */
        @Serializable object AI : NavKey
    }
    
    /**
     * 自定义模块路由
     * 包含用户自定义主题和界面配置功能
     */
    object Custom {
        /** 自定义页路由 */
        @Serializable object Custom : NavKey
    }
    
    /**
     * 用户数据模块路由
     * 包含用户使用数据统计和分析功能
     */
    object UserData {
        /** 用户使用数据页路由 */
        @Serializable object UserUsageData : NavKey
    }

    /**
     * 听歌伙伴（AI 对话）模块路由
     * 包含存在感召唤/提问的对话页
     */
    object Companion {
        /** 对话页路由（M5 确认卡片流宿主界面） */
        @Serializable object Chat : NavKey
    }
}
