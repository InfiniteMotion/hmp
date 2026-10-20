package com.hmp.domain.backup

/**
 * 恢复失败（一-2 / D7-02）。带上"恢复前副本"的路径，让 UI 能直接告诉用户去哪找回原库，
 * 而不是只报一句"恢复失败"——失败时库已被回滚，副本是唯一的手工出路。
 */
class BackupRestoreFailedException(
    val safetyCopyPath: String?,
    cause: Throwable,
) : Exception(
    "恢复失败，本次改动已回滚。" +
        (safetyCopyPath?.let { "恢复前的库已另存为 $it，可手工找回。" } ?: "（恢复前副本生成失败，原库未改动。）"),
    cause,
)
