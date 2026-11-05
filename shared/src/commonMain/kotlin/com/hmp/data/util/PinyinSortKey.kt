package com.hmp.data.util

/**
 * 排序键：把字符串按「拼音 → ASCII 小写」折叠，用于中文标题 / 艺人 / 专辑的 A–Z 排序。
 *
 * **为什么不再是 `expect` / `actual`**（X-06 / R47）：三端实现曾逐字相同（iOS 只多一行注释），
 * 而它依赖的 `PinyinLookupTable` 本来就在 commonMain —— 这个 expect 从第一天起就没有平台差异，
 * 只是形态上让三份代码互相抄、并且只能靠"三份都记得改"保持一致。
 * 合并成一份之后，`commonTest` 一次断言就能同时覆盖 desktop / android / iOS 三个目标。
 *
 * 折叠规则：
 * - 汉字 → 全拼（`PinyinLookupTable`，覆盖常用字表）；
 * - ASCII → 小写；
 * - 其余（非 ASCII 非汉字：标点、emoji、假名等）→ **丢弃**；
 * - 全被丢弃时回落到 `lowercase()`，避免空键把所有这类标题排到一起。
 */
fun stringToPinyinSortKey(input: String): String {
    if (input.isEmpty()) return input
    val sb = StringBuilder(input.length)
    for (ch in input) {
        val pinyin = PinyinLookupTable.getPinyin(ch)
        if (pinyin != null) {
            sb.append(pinyin)
        } else if (ch.code in 0..127) {
            sb.append(ch.lowercaseChar())
        }
    }
    return if (sb.isEmpty()) input.lowercase() else sb.toString()
}
