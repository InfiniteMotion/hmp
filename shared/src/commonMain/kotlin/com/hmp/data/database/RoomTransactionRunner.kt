package com.hmp.data.database

import androidx.room.RoomDatabase
import androidx.room.Transactor
import androidx.room.deferredTransaction
import androidx.room.useWriterConnection
import com.hmp.domain.backup.TransactionRunner

/**
 * [TransactionRunner] 的 Room 实现。
 *
 * 用写连接上的 `deferredTransaction`：块内抛异常即回滚。DAO 上的 `@Transaction` 方法落在
 * 这条外层事务里会被 Room 当作嵌套事务（savepoint），所以一-2 里那些写入口单独调用与
 * 被恢复流程包着调用都成立。
 *
 * 参数收成 `AppDatabase` 而不是 `RoomDatabase`：DI 里注册的是 `single<AppDatabase>`，
 * 按 `RoomDatabase` 取会在运行期 `NoBeanDefFoundException`（Koin 不按父类型匹配）。
 */
class RoomTransactionRunner(private val database: AppDatabase) : TransactionRunner {
    override suspend fun <R> run(block: suspend () -> R): R =
        database.useWriterConnection { transactor: Transactor ->
            transactor.deferredTransaction { block() }
        }
}
