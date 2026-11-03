package com.hmp.domain.setting.usecase

import com.hmp.domain.config.LyricsConfig
import com.hmp.domain.lyrics.LyricsComponent
import com.hmp.domain.lyrics.LyricsComponentConfig
import com.hmp.domain.lyrics.LyricsConfigResolver
import com.hmp.domain.setting.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class LyricsSettingsUseCase(
    private val settingsRepository: SettingsRepository
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val updateMutex = Mutex()

    // ==================== Per-component config ====================

    fun getComponentConfig(component: LyricsComponent): Flow<LyricsComponentConfig> {
        val flow = when (component) {
            LyricsComponent.PLAYER -> settingsRepository.lyricsPlayerConfig
            LyricsComponent.FULLSCREEN -> settingsRepository.lyricsFullscreenConfig
            LyricsComponent.FLOATING -> settingsRepository.lyricsFloatingConfig
        }
        return flow.map { parseConfig(it) }
    }

    suspend fun saveComponentConfig(component: LyricsComponent, config: LyricsComponentConfig) {
        val jsonStr = json.encodeToString(LyricsComponentConfig.serializer(), config)
        when (component) {
            LyricsComponent.PLAYER -> settingsRepository.saveLyricsPlayerConfig(jsonStr)
            LyricsComponent.FULLSCREEN -> settingsRepository.saveLyricsFullscreenConfig(jsonStr)
            LyricsComponent.FLOATING -> settingsRepository.saveLyricsFloatingConfig(jsonStr)
        }
    }

    /**
     * 获取所有组件配置的 Map（用于解析 linkedTo 链）
     */
    suspend fun getAllComponentConfigs(): Map<LyricsComponent, LyricsComponentConfig> {
        return LyricsComponent.entries.associateWith { component ->
            val jsonStr = when (component) {
                LyricsComponent.PLAYER -> settingsRepository.getLyricsPlayerConfig()
                LyricsComponent.FULLSCREEN -> settingsRepository.getLyricsFullscreenConfig()
                LyricsComponent.FLOATING -> settingsRepository.getLyricsFloatingConfig()
            }
            parseConfig(jsonStr)
        }
    }

    /**
     * 解析 linkedTo 链，返回最终生效的 LyricsConfig
     */
    suspend fun resolveConfig(component: LyricsComponent): LyricsConfig {
        val allConfigs = getAllComponentConfigs()
        return LyricsConfigResolver.resolve(component, allConfigs)
    }

    /**
     * **三端渲染器的唯一读取入口**（D4-01）。
     *
     * 读的是 per-component 配置并现场解析 `linkedTo` 链，所以设置页改字号 / 行距 / 显示模式 /
     * 对齐 / 逐字开关后，播放页与独立歌词页会立刻跟着变。此前的读取走的是 legacy 扁平键
     * （`settingsRepository.lyricsOriginalTextSize` 等），设置页写的是新模型 —— 两边各存各的，
     * 用户改了设置看不到任何变化。
     */
    fun configFlow(component: LyricsComponent): Flow<LyricsConfig> =
        combine(
            settingsRepository.lyricsPlayerConfig,
            settingsRepository.lyricsFullscreenConfig,
            settingsRepository.lyricsFloatingConfig,
        ) { player, fullscreen, floating ->
            LyricsConfigResolver.resolve(
                component,
                mapOf(
                    LyricsComponent.PLAYER to parseConfig(player),
                    LyricsComponent.FULLSCREEN to parseConfig(fullscreen),
                    LyricsComponent.FLOATING to parseConfig(floating),
                )
            )
        }

    /**
     * 读-改-写单个组件的配置（页内快捷面板用）。
     *
     * 走「先取当前值再 copy」而不是整份覆盖：面板只改一个字段，不能让同组件上
     * 用户刚在设置页配好的 `linkedTo` / 其它字段被默认值抹掉。
     *
     * 加锁是必要的：面板在拖动滑杆时会连发多次修改，`getAllComponentConfigs()` 与
     * `saveComponentConfig()` 之间有挂起点，两次调用交错就会丢掉前一次的值。
     */
    suspend fun updateComponentConfig(
        component: LyricsComponent,
        transform: (LyricsComponentConfig) -> LyricsComponentConfig,
    ) = updateMutex.withLock {
        val current = getAllComponentConfigs()[component] ?: LyricsComponentConfig.DEFAULT
        saveComponentConfig(component, transform(current))
    }

    /**
     * 重置指定组件为默认配置
     */
    suspend fun resetComponentToDefault(component: LyricsComponent) {
        saveComponentConfig(component, LyricsComponentConfig.DEFAULT)
    }

    private fun parseConfig(jsonStr: String): LyricsComponentConfig {
        return try {
            json.decodeFromString(LyricsComponentConfig.serializer(), jsonStr)
        } catch (e: Exception) {
            LyricsComponentConfig.DEFAULT
        }
    }

    // Floating lyrics enabled toggle
    val floatingLyricsEnabled = settingsRepository.floatingLyricsEnabled

    suspend fun saveFloatingLyricsEnabled(enabled: Boolean) {
        settingsRepository.saveFloatingLyricsEnabled(enabled)
    }
}

