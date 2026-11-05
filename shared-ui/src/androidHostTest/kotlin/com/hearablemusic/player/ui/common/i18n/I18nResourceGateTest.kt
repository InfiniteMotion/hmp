package com.hearablemusic.player.ui.common.i18n

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D8-06 判据：14 个 locale 的键集合与占位符集合必须逐条一致。
 *
 * 故意删掉任一语言的一个键、或把 `%1$s` 写坏 → 本测试红。
 *
 * 未译率**只登记不设阈值**（与 D8-06 的口径一致）：把每个语言与默认英文
 * 同键同值的条数打印出来，让数字可见；合理同形（`ok` / `cancel`）混在其中，
 * 所以那个数只是量级，不是"未译条数"。
 */
class I18nResourceGateTest {

    /** 新增语种时同步这里 —— 少一个目录也要红，否则"某语言整包丢失"无人发现。 */
    private val expectedLocales = listOf(
        "values", "values-zh", "values-ar", "values-de", "values-es", "values-fr",
        "values-hi", "values-id", "values-ja", "values-ko", "values-pt", "values-ru",
        "values-th", "values-vi",
    )

    @Test
    fun allLocales_haveSameKeySet() {
        val resources = resourcesRoot()
        val catalogs = loadCatalogs(resources)

        assertEquals(
            expectedLocales.toSet(),
            catalogs.keys,
            "locale 目录集合变了：新增/删除语种时同步 expectedLocales",
        )

        val base = catalogs.getValue("values")
        val problems = mutableListOf<String>()
        catalogs.forEach { (locale, entries) ->
            val missing = (base.keys - entries.keys).sorted()
            val extra = (entries.keys - base.keys).sorted()
            if (missing.isNotEmpty()) problems += "$locale 缺 ${missing.size} 键：${missing.joinToString()}"
            if (extra.isNotEmpty()) problems += "$locale 多 ${extra.size} 键：${extra.joinToString()}"
        }
        assertTrue(problems.isEmpty(), "键集合不一致：\n" + problems.joinToString("\n"))

        // 未译率：只打印，不断言
        catalogs.filterKeys { it != "values" }.forEach { (locale, entries) ->
            val same = entries.count { (k, v) -> base[k] == v }
            println("[i18n] $locale 与英文同值 $same/${entries.size} 条（含 ok/cancel 等合理同形）")
        }
    }

    @Test
    fun allLocales_haveSamePlaceholdersPerKey() {
        val resources = resourcesRoot()
        val catalogs = loadCatalogs(resources)
        val base = catalogs.getValue("values")

        val problems = mutableListOf<String>()
        catalogs.forEach { (locale, entries) ->
            base.forEach { (key, baseValue) ->
                val expected = placeholders(baseValue)
                val actual = placeholders(entries[key] ?: "")
                if (expected != actual) {
                    problems += "$locale / $key：期望 $expected，实际 $actual"
                }
            }
        }
        assertTrue(problems.isEmpty(), "占位符不一致：\n" + problems.joinToString("\n"))
    }

    // ── 读取 ──────────────────────────────────────────────────────────────

    private fun resourcesRoot(): File {
        File("src/commonMain/composeResources").takeIf { it.isDirectory }?.let { return it }
        File("shared-ui/src/commonMain/composeResources").takeIf { it.isDirectory }?.let { return it }

        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "shared-ui/src/commonMain/composeResources")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        error("找不到 shared-ui/src/commonMain/composeResources（user.dir=${System.getProperty("user.dir")}）")
    }

    private fun loadCatalogs(resources: File): Map<String, Map<String, String>> = resources.listFiles()
        .orEmpty()
        .filter { it.isDirectory && it.name.startsWith("values") }
        .associate { dir ->
            dir.name to parseStrings(File(dir, "strings.xml"))
        }

    private fun parseStrings(file: File): Map<String, String> {
        assertTrue(file.isFile, "缺少字符串资源：${file.path}")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val out = LinkedHashMap<String, String>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            out[el.getAttribute("name")] = el.textContent
        }
        return out
    }

    /** 占位符按多重集比较：`%1$s` 与 `%1$d` 不同，出现两次就要对两次；`%%` 是转义不算。 */
    private fun placeholders(text: String): List<String> = Regex("""%(?:\d+\$)?[a-zA-Z]""")
        .findAll(text.replace("%%", ""))
        .map { it.value }
        .sorted()
        .toList()
}
