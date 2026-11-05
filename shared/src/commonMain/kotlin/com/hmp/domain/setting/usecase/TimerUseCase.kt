package com.hmp.domain.setting.usecase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TimerUseCase(
    private val settingsRepository: com.hmp.domain.setting.SettingsRepository,
) {

    private val _timerRemaining = MutableStateFlow<Long?>(null)
    val timerRemaining: StateFlow<Long?> = _timerRemaining.asStateFlow()

    /**
     * 设置定时器剩余时间(毫秒)
     */
    fun setTimerRemaining(milliseconds: Long?) {
        _timerRemaining.value = milliseconds
    }

    /**
     * 减少定时器时间
     */
    fun decrementTimer(decrement: Long) {
        _timerRemaining.value?.let { current ->
            val newValue = (current - decrement).coerceAtLeast(0)
            _timerRemaining.value = if (newValue > 0) newValue else null
        }
    }

    /**
     * 取消定时器
     */
    fun cancelTimer() {
        _timerRemaining.value = null
    }

    /**
     * 检查定时器是否已过期
     */
    fun isTimerExpired(): Boolean = _timerRemaining.value?.let { it <= 0 } ?: false

    /**
     * 检查定时器是否活跃
     */
    fun isTimerActive(): Boolean = _timerRemaining.value != null && _timerRemaining.value!! > 0
}
