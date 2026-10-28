package com.hmp.data.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * D2-13 判据（纯函数那半）：父目录切分要能**在任何操作系统上**对反斜杠路径断言。
 *
 * 分隔符是参数而不是 `File.separatorChar`，正是为了这一点 —— 若实现走 `java.io.File.parent`，
 * 这条断言在 Linux / macOS 的 runner 上会退化成"整串当文件名"，看起来绿、其实什么都没验。
 * 修前 Desktop 那份写死 `substringBeforeLast("/")`，Windows 上 `C:\Music\a.mp3` 切出来是
 * "Unknown"，于是「已隐藏文件夹」把不同目录的歌聚成一个错分组，"取消隐藏"一次恢复全部。
 */
class ParentPathTest {

    private val windows = "\\/"   // 两个字符：反斜杠 + 正斜杠（Desktop 声明的就是这个集合）

    @Test
    fun windowsPaths_groupByTheirOwnFolder() {
        assertEquals("""C:\Users\x\Music""", parentPathOf("""C:\Users\x\Music\a.mp3""", windows))
        // 同一盘下不同目录必须是**不同**分组
        assertEquals("""C:\Music\Pop""", parentPathOf("""C:\Music\Pop\a.mp3""", windows))
        assertEquals("""C:\Music\Rock""", parentPathOf("""C:\Music\Rock\b.mp3""", windows))
        // 用户手填或从别处搬来的目录里可能是 `/`，java.io.File 在 Windows 上两种都认，这里也要认
        assertEquals("C:/Music/Pop", parentPathOf("C:/Music/Pop/a.mp3", windows))
    }

    @Test
    fun posixPaths_useSlash() {
        assertEquals("/storage/emulated/0/Music", parentPathOf("/storage/emulated/0/Music/a.mp3", "/"))
        assertEquals("/Users/x/Music", parentPathOf("/Users/x/Music/a.mp3", "/"))
        // POSIX 端**不**把反斜杠当分隔符：它出现在路径里只可能是文件名的一部分
        // （这一条钉住"给 Android/iOS 传了 Desktop 那套分隔符"会切错位置）
        assertEquals("/Music", parentPathOf("/Music/My\\Song.mp3", "/"))
    }

    @Test
    fun edgeCases_matchJavaFileParentSemantics() {
        // 盘根与文件系统根：File("C:\\a.mp3").parent == "C:\\"、File("/a.mp3").parent == "/"
        // （期望值用普通字符串写 —— `"""C:\""""` 里那个尾随的引号会被解析进内容，读起来也费劲）
        assertEquals("C:\\", parentPathOf("""C:\a.mp3""", windows))
        assertEquals("/", parentPathOf("/a.mp3", "/"))
        // 裸文件名没有父目录 → null（调用方显示成 UNKNOWN_FOLDER）
        assertNull(parentPathOf("a.mp3", windows))
        assertNull(parentPathOf("", "/"))
        // UNKNOWN_FOLDER 是「已隐藏文件夹」列表里那个兜底标签，值本身也要钉住
        assertEquals("Unknown", UNKNOWN_FOLDER)
    }
}
