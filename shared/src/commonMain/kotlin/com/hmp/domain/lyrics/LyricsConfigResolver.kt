package com.hmp.domain.lyrics

import com.hmp.domain.config.LyricsConfig

object LyricsConfigResolver {

    /**
     * 根据 linkedTo 链解析每个组件的最终配置。
     * 仅允许一层跳转（A→B 后 B 必须为 null），防止循环。
     */
    fun resolveAll(
        configs: Map<LyricsComponent, LyricsComponentConfig>,
    ): Map<LyricsComponent, LyricsConfig> = LyricsComponent.entries.associateWith { component ->
        resolve(component, configs)
    }

    fun resolve(
        component: LyricsComponent,
        configs: Map<LyricsComponent, LyricsComponentConfig>,
    ): LyricsConfig {
        val own = configs[component] ?: LyricsComponentConfig.DEFAULT

        // 解析 linkedTo 链（仅一层）
        val target = own.linkedTo?.let { key ->
            LyricsComponent.fromKey(key)
        }
        if (target != null && target != component) {
            val source = configs[target] ?: LyricsComponentConfig.DEFAULT
            // 目标不能再链向别的东西（防止环）
            if (source.linkedTo != null) {
                // 形成链 A→B→C，直接返回自身配置，忽略链路
                return own.toLyricsConfig()
            }
            return source.toLyricsConfig()
        }
        return own.toLyricsConfig()
    }
}
