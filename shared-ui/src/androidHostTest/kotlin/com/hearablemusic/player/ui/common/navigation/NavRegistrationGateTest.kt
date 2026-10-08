package com.hearablemusic.player.ui.common.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

/**
 * 导航注册闸门（D8-01 / D8-02 / X-05）。
 *
 * 加一条路由要同时动三处：`Routes` 声明、`HmpNavBackStack` 的 subclass 注册、`NavigationGraph` 的
 * `entry<>`。此前这三处只靠人记 —— 漏 serializer **没有编译期报错**，只在该 key 参与保存/恢复
 * （进程重建、配置变更）时抛 `SerializationException`，表现为"进那个页面转一圈就崩"。
 *
 * 本类把这件事变成红：
 * - [everyNavKey_roundTripsThroughTheRegisteredModule]：遍历反射出来的每个 NavKey 做真实多态往返，
 *   漏注册即红；**新增路由会自动进入这道关**，不需要有人记得改测试。
 * - [everyNavKeyIsEitherEntryOrPagerCarried]：每个 NavKey 要么有 `entry<>`，要么在明确的 Tab 豁免名单里。
 */
class NavRegistrationGateTest {

    @Test
    fun everyNavKey_roundTripsThroughTheRegisteredModule() {
        val json = Json { serializersModule = HMP_NAV_KEY_SERIALIZERS }
        val failures = mutableListOf<String>()

        declaredNavKeys().forEach { (path, type) ->
            val run = runCatching {
                val original = instantiate(type)
                val restored = json.decodeFromString<NavKey>(json.encodeToString<NavKey>(original))
                assertEquals(original, restored, "$path 往返后应等值")
            }
            run.exceptionOrNull()?.let { failures += "$path → ${it.message?.take(120)}" }
        }

        assertTrue(
            failures.isEmpty(),
            "以下路由未在 HMP_NAV_KEY_SERIALIZERS 注册（或注册了但序列化不通），" +
                "参与保存/恢复时会在运行期崩：\n" + failures.joinToString("\n"),
        )
    }

    @Test
    fun everyNavKeyIsEitherEntryOrPagerCarried() {
        val declared = declaredNavKeys().keys
        val withEntry = sourceOf(NAVIGATION_GRAPH).readLines().mapNotNull { line ->
            ENTRY_PATTERN.find(line)?.groupValues?.get(1)?.removePrefix("Routes.")
        }.toSet()

        val uncovered = declared - withEntry - pagerCarriedTabs
        val staleEntries = withEntry - declared
        val staleExemptions = pagerCarriedTabs - declared

        assertTrue(
            uncovered.isEmpty() && staleEntries.isEmpty() && staleExemptions.isEmpty(),
            buildString {
                if (uncovered.isNotEmpty()) {
                    appendLine("这些路由在 NavigationGraph 没有 entry<>，也不在 Tab 豁免名单里：$uncovered")
                    appendLine("（若它确实由 MainShell 的 HorizontalPager 承载，就把它加进 pagerCarriedTabs 并说明理由）")
                }
                if (staleEntries.isNotEmpty()) appendLine("这些 entry<> 指向已不存在的路由：$staleEntries")
                if (staleExemptions.isNotEmpty()) appendLine("Tab 豁免名单里有过期项：$staleExemptions")
            },
        )
    }

    /** 由 `Routes.Main.Tabs` 的 entry 渲染 `MainShell`，四个 Tab 页走 HorizontalPager 而非独立 entry。 */
    private val pagerCarriedTabs = setOf("Main.Home", "Main.Gallery", "Main.List", "Main.User")

    /**
     * 权威路由集合：从编译产物反射，而不是在测试里手抄一份名单。
     * 返回 `相对 Routes 的点分路径 => KClass`，路径与两处源文件里的写法（`Main.Tabs`）对齐。
     */
    private fun declaredNavKeys(): Map<String, KClass<out NavKey>> =
        navKeyTypes(Routes::class)
            .associateBy {
                it.qualifiedName?.removePrefix(ROUTES_QUALIFIED_PREFIX)
                    ?: error("${it.qualifiedName} 没有 qualifiedName")
            }
            .also {
                // 反射取空 = 闸门自己失效，这种绿不能算数（宁可红在这里）
                assertTrue(it.isNotEmpty(), "没从 Routes 反射出任何 NavKey —— 闸门失效，检查 Routes 结构")
                assertTrue(
                    it.values.any { type -> type.objectInstance == null },
                    "反射只取到 object 路由、没取到任何带参数的 data class 路由 —— 遍历漏了，" +
                        "而漏掉的恰好是最容易忘记注册的那批",
                )
            }

    /**
     * 递归收集所有实现 NavKey 的嵌套类。
     *
     * 判据不能是「没有嵌套类的才算叶子」：`@Serializable` 的 data class 会被插件生成一个嵌套
     * `$$serializer` 类，那样会把带参数的路由整批判成非叶子并跳过 —— 实测漏掉 7 条，
     * 其中就有本次要补注册的 `Routes.AI.AgentConfig`。所以自身命中也要收，命中后继续下钻。
     */
    private fun navKeyTypes(owner: KClass<*>): List<KClass<out NavKey>> =
        owner.nestedClasses.flatMap { nested ->
            val self = if (NavKey::class.java.isAssignableFrom(nested.java)) {
                listOf(nested as KClass<out NavKey>)
            } else {
                emptyList()
            }
            self + navKeyTypes(nested)
        }

    /** object 路由取单例；带参数的 data class 路由用主构造器灌测试值。 */
    private fun instantiate(type: KClass<out NavKey>): NavKey {
        type.objectInstance?.let { return it as NavKey }
        // 取参数最少的那条构造器：`@Serializable` 的 data class 除主构造器外还带编译器生成的
        // 合成构造器（多一个 marker 参数），要求 singleOrNull 会把每条带参路由都判成"有多个构造器"。
        val constructor = type.java.declaredConstructors.minByOrNull { it.parameterCount }
            ?: error("${type.qualifiedName} 没有可用的构造器")
        val args: List<Any?> = constructor.parameterTypes.map { testValueFor(type, it) }
        return constructor.newInstance(*args.toTypedArray()) as NavKey
    }

    /** 返回类型显式写成 `Any?`：让 `when` 的分支结果 upcast，否则 `toTypedArray()` 会摊到交叉类型上。 */
    private fun testValueFor(type: KClass<*>, parameterType: Class<*>): Any? =
        when (parameterType) {
            Long::class.javaPrimitiveType -> 0L
            Int::class.javaPrimitiveType -> 0
            String::class.java -> "gate"
            else -> error("${type.qualifiedName} 的参数类型 $parameterType 不在闸门支持列表里，请扩展 testValueFor()")
        }

    private fun sourceOf(relativePath: String): File =
        sequenceOf(relativePath, "shared-ui/$relativePath")
            .map(::File)
            .firstOrNull { it.isFile }
            ?: error("找不到 $relativePath —— 闸门要读源文件，测试工作目录应为 shared-ui/ 或仓库根")

    private companion object {
        const val ROUTES_QUALIFIED_PREFIX = "com.hearablemusic.player.ui.common.navigation.Routes."
        const val NAVIGATION_GRAPH =
            "src/commonMain/kotlin/com/hearablemusic/player/ui/common/navigation/NavigationGraph.kt"

        val ENTRY_PATTERN = Regex("""entry<(Routes\.[A-Za-z0-9_.]+)>""")
    }
}
