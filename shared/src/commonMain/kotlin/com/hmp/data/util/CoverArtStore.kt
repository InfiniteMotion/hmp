package com.hmp.data.util

/**
 * 封面缓存：命名规则只有一份，落盘由平台实现（D2-16 / D2-17）。
 *
 * ## 为什么文件名是**内容指纹**而不是路径 hash
 *
 * 修前 `DeviceMusicScanner` 的 private fun 用**音频文件路径**的 hash 当封面文件名，
 * 并且"文件已存在就跳过写"。两条凑在一起就是 D2-17：路径不变、封面换了 →
 * 文件名不变 → 存在 → 跳过 → 用户在「编辑标签」里换封面并保存成功，
 * 回到曲库看到的仍是旧图，直到手工删掉 `~/.hmp/covers/`。
 * 改成内容指纹后"换了封面"与"文件名变了"是同一件事，跳过写也就不再是 bug
 * （同内容命中同名，仍是幂等的）。
 *
 * ## 为什么渲染成无符号十六进制
 *
 * D2-16 记的是同一条规则有两份跨语言实现，且**符号处理不一致**：
 * Kotlin 侧 `hash.toLong()` 可能为负（文件名 `cover_-123.jpg`），Swift 侧用 `abs(...)`（恒非负），
 * 于是任何"用一侧算出的名字去拼另一侧路径"的动作都会错位。
 * 这里改成无符号十六进制，是为了让这一类分歧**在源头消失**，而不是再靠 `abs` 对齐一次。
 *
 * ⚠️ 一次性的升级代价（如实记）：已经发布出去的 Desktop 库里，旧的 `cover_<有符号>.jpg`
 * 会变成没人引用的文件留在磁盘上（下次扫描会按新名重新落一份）。没有清理器，
 * 因为"哪些封面文件还在被引用"要扫全库 `albumArtUri` 才知道，属后续独立一件小事。
 *
 * 注意：曲目 id 用的 [stableIdFor] 仍保持**有符号 Long** —— 那个值已经写进真实库的主键里，
 * 改渲染方式等于让全部曲目换 id（那是数据迁移，不是命名规则）。
 */

/** FNV-1a 64 位。同一份实现同时服务"路径 → 曲目 id"与"字节 → 封面指纹"（原先两份逐字相同地抄在两端）。 */
fun fnv1a64(bytes: ByteArray): ULong {
    var hash = 0xcbf29ce484222325UL
    for (byte in bytes) {
        hash = hash xor byte.toULong()
        hash *= 0x100000001b3UL
    }
    return hash
}

/**
 * 路径 → 曲目 id。**语义与历史值逐字不变**（`Long`，可能为负）：
 * 这个数已经是三端真实库里的 `music.id`，改它等于改主键。
 */
fun stableIdFor(path: String): Long = fnv1a64(path.encodeToByteArray()).toLong()

object CoverArtStore {

    /**
     * 封面文件名 = 封面字节的无符号指纹。
     *
     * 空数组不是一种封面（调用方用它表示"移除封面"），因此在这里就不该走到命名这一步。
     */
    fun fileNameFor(coverBytes: ByteArray): String =
        "cover_" + fnv1a64(coverBytes).toString(16) + ".jpg"

    /**
     * 落盘并返回可存的 uri；写不进（IO 失败）返回 null，调用方保留原 uri 而不是抹成空。
     *
     * @param writer 平台的落盘实现（Android 不在此列：它的封面是 `content://media/.../albumart`，不落盘）
     */
    fun store(coverBytes: ByteArray, writer: CoverArtWriter): String? =
        writer.write(fileNameFor(coverBytes), coverBytes)
}

/** 把已经算好名字的封面字节写到本端该放的地方，返回可直接存进 `music.albumArtUri` 的字符串。 */
interface CoverArtWriter {
    fun write(fileName: String, bytes: ByteArray): String?
}
