package com.hmp.domain.backup

/**
 * 备份文件的格式版本高于本应用能读的版本（D7-12）。
 *
 * 为什么会发生：用户在旧版本上装新版本、或把新版本导出的备份喂给旧版本。旧行为是
 * `ignoreUnknownKeys = true` 直接忽略不认识的字段、静默少还原一批数据；现在改成明确拒绝 ——
 * 恢复是破坏性操作（先清空再重建），"读不懂就拒绝"比"猜着还原"安全。
 */
class BackupVersionTooNewException(
    val backupVersion: Int,
    val supportedVersion: Int,
) : Exception(
    "备份格式版本过新（v$backupVersion），当前应用最高支持 v$supportedVersion。" +
        "请先升级应用再恢复；本次未改动任何数据。"
)
