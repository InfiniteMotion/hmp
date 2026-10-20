package com.hmp.domain.backup

/**
 * 把"必须全成或全不成"的一批写收进单个数据库事务（一-2 / D7-02）。
 *
 * 为什么要有这个端口：备份恢复要跨 4 个仓库（settings / musicUserState / listeningStats / playlists），
 * 而用例在领域层拿不到 `RoomDatabase` 句柄 —— 旧写法是四条串行 `restore` 后 `catch → Result.failure`，
 * 第 4 步先 `deleteAll()` 再写，中途失败就留下"前 3 步已改、第 4 步已清空"的半截库。
 * 实现只在数据层（Room）有一份。
 */
interface TransactionRunner {
    suspend fun <R> run(block: suspend () -> R): R
}
