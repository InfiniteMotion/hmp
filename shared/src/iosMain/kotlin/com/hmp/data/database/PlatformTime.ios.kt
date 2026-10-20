package com.hmp.data.database

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/**
 * iOS 墙上时钟（Unix epoch 毫秒）。
 *
 * 一-3 / D3-01：这里原本写的是 `timeIntervalSinceReferenceDate`（自 **2001-01-01** 起算的秒），
 * 于是 iOS 落库的所有时间戳比真实时间少约 31 年（落在 1995 年前后）。后果不是"显示不对"这么简单：
 * - `strftime('%m-%d', createdAt / 1000, 'unixepoch')` 与 `getAnniversaryPlaylists` 永不命中（纪念日功能在 iOS 上静默失效）；
 * - 跨端备份恢复后，Android/Desktop 写的 Unix 毫秒与 iOS 写的参考纪元毫秒混在同一列里，`ORDER BY updatedAt DESC` 把新数据排到最末。
 *
 * 存量数据**不做修正**：iOS 从未上线（v7.2.x 的 iOS 只在本机 Archive，不在 CI 产物里），
 * 没有需要救的真实用户库 —— 为一份不存在的历史写迁移，是把风险发给未来。
 */
@OptIn(ExperimentalForeignApi::class)
actual fun currentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()
