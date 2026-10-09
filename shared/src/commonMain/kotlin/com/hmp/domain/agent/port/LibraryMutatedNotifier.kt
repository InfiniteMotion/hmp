package com.hmp.domain.agent.port

/**
 * 「曲库变了」这一件事的接收端（一-2 / D2-03 的前置端口）。
 *
 * 为什么要有它：`LibraryViewModel` 原先直接依赖具体类 `MasterAgent`（约 1700 行、20 个构造依赖），
 * 于是这个 ViewModel **在测试里根本构造不出来** —— D2-03 要的"目录变更不再触发破坏性重扫"
 * 就没有可执行的判据。UI 需要的只是发一个命令，画像重建的协调本来就在 Master 内部，
 * 所以这里只开一个方法；装配时把 `MasterAgent` 绑到本接口即可。
 */
fun interface LibraryMutatedNotifier {
    fun onLibraryMutated()
}
