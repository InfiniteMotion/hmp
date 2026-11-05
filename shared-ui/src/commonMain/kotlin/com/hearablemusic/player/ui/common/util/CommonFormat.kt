package com.hearablemusic.player.ui.common.util

import kotlin.math.pow
import kotlin.math.round

/**
 * JVM `String.format` 的 commonMain 等价物。
 *
 * Kotlin/Native 无 java.util.Formatter，而 shared-ui commonMain 原先仅面向
 * Android/Desktop（JVM）编译，故散落少量 `"...".format(...)` 调用；本函数覆盖
 * 工程内用到的子集：
 * - 转换符：`d`（整数）/ `s`（字符串）/ `f`（浮点）
 * - 标志：`0` 零填充 + 宽度（`%02d`）
 * - 精度：`.N`（`%.0f`）
 * - 位置索引：`N$`（`%1$d`）
 * - 字面量：`%%` → `%`
 *
 * 其余语法按原样输出（不抛异常），语义与 Formatter 基本一致（不依赖 Locale）。
 */
fun commonFormat(pattern: String, vararg args: Any?): String {
    val sb = StringBuilder()
    var i = 0
    var nextSequential = 0
    while (i < pattern.length) {
        val c = pattern[i]
        if (c != '%') {
            sb.append(c)
            i++
            continue
        }
        if (i + 1 < pattern.length && pattern[i + 1] == '%') {
            sb.append('%')
            i += 2
            continue
        }

        var j = i + 1

        // 位置索引 %N$
        var argIndex: Int? = null
        val digitsStart = j
        while (j < pattern.length && pattern[j].isDigit()) j++
        if (j < pattern.length && pattern[j] == '$') {
            argIndex = pattern.substring(digitsStart, j).toIntOrNull()?.minus(1)
            j++
        }

        // 零填充标志
        var zeroPad = false
        if (j < pattern.length && pattern[j] == '0') {
            zeroPad = true
            j++
        }

        // 宽度
        var width = 0
        while (j < pattern.length && pattern[j].isDigit()) {
            width = width * 10 + (pattern[j] - '0')
            j++
        }

        // 精度
        var precision = -1
        if (j < pattern.length && pattern[j] == '.') {
            j++
            precision = 0
            while (j < pattern.length && pattern[j].isDigit()) {
                precision = precision * 10 + (pattern[j] - '0')
                j++
            }
        }

        if (j >= pattern.length) {
            sb.append(pattern.substring(i))
            break
        }

        val conv = pattern[j]
        j++

        val idx = argIndex ?: nextSequential
        if (argIndex == null) nextSequential++
        val arg = args.getOrNull(idx)

        val text = when (conv) {
            'd' -> {
                val v = (arg as? Number)?.toLong() ?: 0L
                val raw = v.toString()
                if (zeroPad && raw.length < width) "0".repeat(width - raw.length) + raw else raw
            }

            's' -> arg?.toString() ?: "null"

            'f' -> {
                val v = (arg as? Number)?.toDouble() ?: 0.0
                formatFixed(v, if (precision >= 0) precision else 0)
            }

            else -> "%$conv"
        }
        sb.append(text)
        i = j
    }
    return sb.toString()
}

private fun formatFixed(value: Double, precision: Int): String {
    if (precision == 0) return round(value).toLong().toString()
    val factor = 10.0.pow(precision)
    val scaled = round(value * factor).toLong()
    val intPart = scaled / factor.toLong()
    val fracPart = (scaled % factor.toLong()).toString().padStart(precision, '0')
    return "$intPart.$fracPart"
}
