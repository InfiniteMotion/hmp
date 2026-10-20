package com.hmp.test.fakes

import com.hmp.domain.backup.TransactionRunner

/**
 * 直通的事务替身（commonTest 没有 Room）。
 *
 * `runs` 用来钉住 D7-02 的形状：四条 restore 必须**整段**交给 runner 一次，
 * 而不是各自开事务 —— 真的回滚能力由 desktopTest 用真 Room 证明。
 */
class FakeTransactionRunner : TransactionRunner {
    var runs = 0
        private set

    override suspend fun <R> run(block: suspend () -> R): R {
        runs++
        return block()
    }
}
