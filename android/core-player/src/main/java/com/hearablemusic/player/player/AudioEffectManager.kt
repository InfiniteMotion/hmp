package com.hearablemusic.player.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import com.hmp.log.HmpLog
import com.hmp.log.LogTag

/**
 * 音效管理器
 * 负责管理所有音频效果对象的生命周期和参数设置
 */
class AudioEffectManager {
    
    companion object {        
        // 自定义均衡器预设（毫贝尔单位，范围 -1500 到 1500）
        // 每个预设包含 5 个频段的增益值
        private val CUSTOM_EQUALIZER_PRESETS = arrayOf(
            shortArrayOf(0, 0, 0, 0, 0),           // 0: 正常
            shortArrayOf(800, 400, 200, 400, 800), // 1: 摇滚
            shortArrayOf(400, 200, 0, 200, 400),   // 2: 流行
            shortArrayOf(600, 400, 200, 400, 600), // 3: 古典
            shortArrayOf(400, 200, -200, 200, 600), // 4: 爵士
            shortArrayOf(400, 200, 0, 200, 400),   // 5: 蓝调
            shortArrayOf(600, 400, 0, -200, 600),  // 6: 电子
            shortArrayOf(800, 600, 0, 400, 600),   // 7: 嘻哈
            shortArrayOf(800, 400, 0, 400, 800),   // 8: 金属
            shortArrayOf(400, 200, 200, 400, 400)  // 9: 乡村
        )
    }
    
    // 音效对象
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var presetReverb: PresetReverb? = null
    
    // 状态标志
    private var isInitialized = false
    private var audioSessionId: Int = 0
    
    // 当前音效参数
    private var currentEqualizerPreset: Int = 0
    private var currentBassBoostStrength: Short = 0
    private var isVirtualizerEnabled: Boolean = false
    private var currentReverbPreset: Short = 0
    
    /**
     * 初始化音效管理器
     * @param audioSessionId 音频会话ID
     * @return 是否初始化成功
     */
    fun initialize(audioSessionId: Int): Boolean {
        if (audioSessionId <= 0) {
            HmpLog.w(LogTag.PlayerAudioEffect) { "🎛️ Invalid audio session ID: $audioSessionId" }
            return false
        }
        
        this.audioSessionId = audioSessionId
        
        try {
            // 初始化均衡器
            try {
                equalizer = Equalizer(0, audioSessionId).apply {
                    enabled = true
                }
                HmpLog.d(LogTag.PlayerAudioEffect) { "🎛️ Equalizer initialized successfully" }
            } catch (e: Exception) {
                HmpLog.e(LogTag.PlayerAudioEffect, e) { "🎛️ Failed to initialize Equalizer" }
            }
            
            // 初始化低音增强
            try {
                bassBoost = BassBoost(0, audioSessionId).apply {
                    enabled = true
                }
                HmpLog.d(LogTag.PlayerAudioEffect) { "🎛️ BassBoost initialized successfully" }
            } catch (e: Exception) {
                HmpLog.e(LogTag.PlayerAudioEffect, e) { "🎛️ Failed to initialize BassBoost" }
            }
            
            // 初始化虚拟器（环绕声）
            try {
                virtualizer = Virtualizer(0, audioSessionId).apply {
                    enabled = false // 默认关闭
                }
                HmpLog.d(LogTag.PlayerAudioEffect) { "🎛️ Virtualizer initialized successfully" }
            } catch (e: Exception) {
                HmpLog.e(LogTag.PlayerAudioEffect, e) { "🎛️ Failed to initialize Virtualizer" }
            }
            
            // 初始化预设混响
            try {
                presetReverb = PresetReverb(0, audioSessionId).apply {
                    enabled = false // 默认关闭
                }
                HmpLog.d(LogTag.PlayerAudioEffect) { "🎛️ PresetReverb initialized successfully" }
            } catch (e: Exception) {
                HmpLog.e(LogTag.PlayerAudioEffect, e) { "🎛️ Failed to initialize PresetReverb" }
            }
            
            isInitialized = true
            HmpLog.d(LogTag.PlayerAudioEffect) { "🎛️ AudioEffectManager initialized with session ID: $audioSessionId" }
            return true
            
        } catch (e: Exception) {
            HmpLog.e(LogTag.PlayerAudioEffect, e) { "🎛️ Failed to initialize AudioEffectManager" }
            release()
            return false
        }
    }
    
    /**
     * 释放所有音效资源
     */
    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            presetReverb?.release()
            
            equalizer = null
            bassBoost = null
            virtualizer = null
            presetReverb = null
            
            isInitialized = false
            HmpLog.d(LogTag.PlayerAudioEffect) { "🎛️ AudioEffectManager released" }
        } catch (e: Exception) {
            HmpLog.e(LogTag.PlayerAudioEffect, e) { "🎛️ Error releasing audio effects" }
        }
    }
    
    /**
     * 设置均衡器预设
     * @param preset 预设索引（0-9）
     * @return 是否设置成功
     */
    fun setEqualizerPreset(preset: Int): Boolean {
        if (!isInitialized || equalizer == null) {
            HmpLog.w(LogTag.PlayerAudioEffect) { "🎛️ Equalizer not initialized" }
            return false
        }
        
        if (preset !in 0..9) {
            HmpLog.w(LogTag.PlayerAudioEffect) { "🎛️ Invalid equalizer preset: $preset" }
            return false
        }
        
        try {
            val eq = equalizer ?: return false
            val bandCount = eq.numberOfBands.toInt()
            
            // 使用自定义预设曲线
            if (preset < CUSTOM_EQUALIZER_PRESETS.size) {
                val presetLevels = CUSTOM_EQUALIZER_PRESETS[preset]
                
                // 应用预设到各频段
                for (band in 0 until minOf(bandCount, presetLevels.size)) {
                    eq.setBandLevel(band.toShort(), presetLevels[band])
                }
                
                currentEqualizerPreset = preset
                HmpLog.d(LogTag.PlayerAudioEffect) { "🎛️ Equalizer preset set to: $preset" }
                return true
            }
            
            return false
        } catch (e: Exception) {
            HmpLog.e(LogTag.PlayerAudioEffect, e) { "🎛️ Failed to set equalizer preset" }
            return false
        }
    }
    
    /**
     * 设置均衡器指定频段的增益
     * @param band 频段索引
     * @param level 增益值（毫贝尔）
     * @return 是否设置成功
     */
    fun setEqualizerBandLevel(band: Int, level: Short): Boolean {
        if (!isInitialized || equalizer == null) {
            HmpLog.w(LogTag.PlayerAudioEffect) { "🎛️ Equalizer not initialized" }
            return false
        }
        
        try {
            val eq = equalizer ?: return false
            
            if (band >= 0 && band < eq.numberOfBands.toInt()) {
                eq.setBandLevel(band.toShort(), level)
                HmpLog.d(LogTag.PlayerAudioEffect) { "🎛️ Equalizer band $band set to level: $level" }
                return true
            }
            
            HmpLog.w(LogTag.PlayerAudioEffect) { "🎛️ Invalid band index: $band" }
            return false
        } catch (e: Exception) {
            HmpLog.e(LogTag.PlayerAudioEffect, e) { "🎛️ Failed to set equalizer band level" }
            return false
        }
    }
    
    /**
     * 设置低音增强强度
     * @param strength 强度值（0-1000）
     * @return 是否设置成功
     */
    fun setBassBoostStrength(strength: Short): Boolean {
        if (!isInitialized || bassBoost == null) {
            HmpLog.w(LogTag.PlayerAudioEffect) { "🎛️ BassBoost not initialized" }
            return false
        }
        
        try {
            val validStrength = strength.coerceIn(0, 1000)
            bassBoost?.setStrength(validStrength)
            currentBassBoostStrength = validStrength
            HmpLog.d(LogTag.PlayerAudioEffect) { "🎛️ BassBoost strength set to: $validStrength" }
            return true
        } catch (e: Exception) {
            HmpLog.e(LogTag.PlayerAudioEffect, e) { "🎛️ Failed to set bass boost strength" }
            return false
        }
    }
    
    /**
     * 设置虚拟器（环绕声）
     * @param enabled 是否启用
     * @return 是否设置成功
     */
    fun setVirtualizerEnabled(enabled: Boolean): Boolean {
        if (!isInitialized || virtualizer == null) {
            HmpLog.w(LogTag.PlayerAudioEffect) { "🎛️ Virtualizer not initialized" }
            return false
        }
        
        try {
            val virt = virtualizer ?: return false
            virt.enabled = enabled
            
            if (enabled) {
                // 设置固定强度为最大值
                virt.setStrength(1000)
            }
            
            isVirtualizerEnabled = enabled
            HmpLog.d(LogTag.PlayerAudioEffect) { "🎛️ Virtualizer enabled: $enabled" }
            return true
        } catch (e: Exception) {
            HmpLog.e(LogTag.PlayerAudioEffect, e) { "🎛️ Failed to set virtualizer" }
            return false
        }
    }
    
    /**
     * 设置混响预设
     * @param preset 预设索引（0-4）
     * @return 是否设置成功
     */
    fun setReverbPreset(preset: Short): Boolean {
        if (!isInitialized || presetReverb == null) {
            HmpLog.w(LogTag.PlayerAudioEffect) { "🎛️ PresetReverb not initialized" }
            return false
        }
        
        try {
            val reverb = presetReverb ?: return false
            
            when (preset.toInt()) {
                0 -> {
                    // 关闭混响
                    reverb.enabled = false
                }
                1 -> {
                    reverb.enabled = true
                    reverb.preset = PresetReverb.PRESET_SMALLROOM
                }
                2 -> {
                    reverb.enabled = true
                    reverb.preset = PresetReverb.PRESET_LARGEROOM
                }
                3 -> {
                    reverb.enabled = true
                    reverb.preset = PresetReverb.PRESET_LARGEHALL
                }
                4 -> {
                    reverb.enabled = true
                    reverb.preset = PresetReverb.PRESET_PLATE
                }
                else -> {
                    HmpLog.w(LogTag.PlayerAudioEffect) { "🎛️ Invalid reverb preset: $preset" }
                    return false
                }
            }
            
            currentReverbPreset = preset
            HmpLog.d(LogTag.PlayerAudioEffect) { "🎛️ Reverb preset set to: $preset" }
            return true
        } catch (e: Exception) {
            HmpLog.e(LogTag.PlayerAudioEffect, e) { "🎛️ Failed to set reverb preset" }
            return false
        }
    }
    
    /**
     * 获取均衡器频段数量
     */
    fun getEqualizerBandCount(): Int {
        return equalizer?.numberOfBands?.toInt() ?: 5
    }
    
    /**
     * 获取均衡器增益范围
     */
    fun getEqualizerBandLevelRange(): Pair<Short, Short> {
        return try {
            val range = equalizer?.bandLevelRange
            if (range != null && range.size >= 2) {
                Pair(range[0], range[1])
            } else {
                Pair(-1500, 1500)
            }
        } catch (e: Exception) {
            Pair(-1500, 1500)
        }
    }
    
    /**
     * 获取指定频段的频率范围
     */
    fun getBandFreqRange(band: Int): Pair<Int, Int>? {
        return try {
            val eq = equalizer ?: return null
            if (band >= 0 && band < eq.numberOfBands.toInt()) {
                val range = eq.getBandFreqRange(band.toShort())
                Pair(range[0], range[1])
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 检测设备是否支持均衡器
     */
    fun isEqualizerSupported(): Boolean = equalizer != null
    
    /**
     * 检测设备是否支持低音增强
     */
    fun isBassBoostSupported(): Boolean = bassBoost != null
    
    /**
     * 检测设备是否支持虚拟器
     */
    fun isVirtualizerSupported(): Boolean = virtualizer != null
    
    /**
     * 检测设备是否支持混响
     */
    fun isReverbSupported(): Boolean = presetReverb != null
    
    /**
     * 获取当前均衡器预设
     */
    fun getCurrentEqualizerPreset(): Int = currentEqualizerPreset
    
    /**
     * 获取当前低音增强强度
     */
    fun getCurrentBassBoostStrength(): Short = currentBassBoostStrength
    
    /**
     * 获取虚拟器是否启用
     */
    fun isVirtualizerCurrentlyEnabled(): Boolean = isVirtualizerEnabled
    
    /**
     * 获取当前混响预设
     */
    fun getCurrentReverbPreset(): Short = currentReverbPreset
    
    /**
     * 获取当前均衡器各频段的增益值
     */
    fun getCurrentEqualizerBandLevels(): ShortArray {
        return try {
            val eq = equalizer
            if (eq != null) {
                val bandCount = eq.numberOfBands.toInt()
                ShortArray(bandCount) { band ->
                    eq.getBandLevel(band.toShort())
                }
            } else {
                shortArrayOf()
            }
        } catch (e: Exception) {
            HmpLog.e(LogTag.PlayerAudioEffect, e) { "🎛️ Failed to get equalizer band levels" }
            shortArrayOf()
        }
    }
}
