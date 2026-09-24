package com.hearablemusic.player.ui.library.pages.components.musiclist

import com.hearablemusic.player.ui.common.util.epochMillisToYearMonth
import com.hearablemusic.player.ui.common.util.commonFormat
import com.hearablemusic.player.ui.common.util.nowEpochMillis
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hmp.domain.music.MusicInfo
import com.hmp.data.util.stringToPinyinSortKey
import androidx.compose.foundation.layout.PaddingValues

/**
 * 增强版音乐列表的入口配置，驱动头部、单项形态、编辑模式、索引跳转、滚动条与当前播放等行为。
 * 所有子配置在此汇总，调用方通过 copy 或预设方法按场景定制。
 */
data class MusicListConfig(
    val header: HeaderConfig,
    val item: ItemConfig,
    val list: ListConfig,
    val edit: EditConfig,
    val indexJump: IndexJumpConfig,
    val scrollbar: ScrollbarConfig,
    val currentPlaying: CurrentPlayingConfig,
    val callbacks: MusicListCallbacks,
    val contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    val emptyContent: (@Composable () -> Unit)? = null,
    val loadingContent: (@Composable () -> Unit)? = null,
)

// ---------- Header ----------

/**
 * 头部区域配置：不显示 / 简单顺序+随机 / 完整排序筛选+播放 / 自定义内容。
 * 典型场景：None 用于内嵌列表；Simple 用于播放列表页；Full 用于曲库/列表页；Custom 由调用方完全自定义。
 * [Simple.trailing] 在歌曲数量右侧渲染，与其它图标按钮式样一致（如添加歌曲按钮）。
 */
sealed class HeaderConfig {
    data object None : HeaderConfig()
    data class Simple(
        val onOrderPlay: () -> Unit,
        val onShufflePlay: () -> Unit,
        val trailing: (@Composable () -> Unit)? = null,
    ) : HeaderConfig()
    data class Full(
        val selectedGenre: String,
        val selectedOrder: String,
        val onFilterGenreChange: (String) -> Unit,
        val onFilterOrderChange: (String) -> Unit,
        val onOrderPlay: () -> Unit,
        val onShufflePlay: () -> Unit,
        val singleRowFilter: Boolean = false,
    ) : HeaderConfig()
    data class Custom(
        val content: @Composable () -> Unit,
    ) : HeaderConfig()
}

// ---------- Item ----------

/**
 * 单项配置：是否显示序号/选择框、形态（Full/Compact/Gallery/Custom）及各形态选项。
 * - [showIndex] 为 true 时在左侧显示序号，[indexFormat] 控制文案（默认 1-based）。
 * - [showCheckbox] 与编辑模式配合，用于多选。
 * - [variant] 决定行布局：Full/Compact/Gallery 均仅支持三种按钮（置顶、移除、更多）；Custom 使用 [customContent]。
 */
data class ItemConfig(
    val showIndex: Boolean = false,
    val indexFormat: (Int) -> String = { "${it + 1}" },
    val showCheckbox: Boolean = false,
    val variant: ItemVariant = ItemVariant.Full,
    val fullOptions: FullItemOptions? = null,
    val compactOptions: CompactItemOptions? = null,
    val galleryOptions: GalleryItemOptions? = null,
    val customContent: (@Composable (MusicInfo, Int, Boolean) -> Unit)? = null,
    val itemHeight: Dp? = null,
)

sealed class ItemVariant {
    data object Full : ItemVariant()
    data object Compact : ItemVariant()
    data object Gallery : ItemVariant()
    data object Custom : ItemVariant()
}

/** Item 仅支持三种按钮：置顶、移除、更多。showAddToPlaylistInMenu 为 true 时在「更多」下拉中增加「加入播放列表」项。 */
data class FullItemOptions(
    val showPinButton: Boolean = true,
    val showRemoveButton: Boolean = true,
    val showMenuButton: Boolean = true,
    val showAddToPlaylistInMenu: Boolean = false,
    val extraMenuItems: List<Pair<String, () -> Unit>> = emptyList(),
)

data class CompactItemOptions(
    val showPinButton: Boolean = true,
    val showRemoveButton: Boolean = true,
    val showMenuButton: Boolean = true,
    val showAddToPlaylistInMenu: Boolean = false,
    val extraMenuItems: List<Pair<String, () -> Unit>> = emptyList(),
)

data class GalleryItemOptions(
    val showPinButton: Boolean = true,
    val showRemoveButton: Boolean = true,
    val showMenuButton: Boolean = true,
    val showAddToPlaylistInMenu: Boolean = false,
    val extraMenuItems: List<Pair<String, () -> Unit>> = emptyList(),
)

// ---------- List ----------

/**
 * 列表整体配置：LazyColumn 的 key、底部占位高度、是否支持长按列表项进入编辑模式。
 */
data class ListConfig(
    val key: (Int, MusicInfo) -> Any = { i, m -> "${m.music.id}_$i" },
    val bottomSpacerHeight: Dp = 88.dp,
    val enableLongPressToEnterEdit: Boolean = false,
    val columns: Int = 1,
)

// ---------- Edit ----------

/**
 * 编辑模式配置：是否启用、是否显示工具栏、[toolbarActions] 决定显示的批量操作。
 *
 * 注：原 `selectAllLabel` / `deselectAllLabel` / `confirmLabel` 三个字段**全仓零读取**（只写不读的
 * 死字段），其内联中文默认值是遗留硬编码，T2 已删除；工具栏文案实际由 `EditToolbarAction` 承载。
 */
data class EditConfig(
    val enabled: Boolean = false,
    val showToolbar: Boolean = true,
    val toolbarActions: List<EditToolbarAction> = defaultEditToolbarActions(),
    val selectedCountFormat: (Int) -> String = { it.toString() },
)

enum class EditToolbarAction {
    SelectAll,
    DeselectAll,
    Delete,
    AddToPlaylist,
    RemoveFromPlaylist,
    MoveToTop,
    Share,
}

fun defaultEditToolbarActions(): List<EditToolbarAction> = listOf(
    EditToolbarAction.SelectAll,
    EditToolbarAction.DeselectAll,
    EditToolbarAction.Delete,
    EditToolbarAction.AddToPlaylist,
)

// ---------- IndexJump ----------

/**
 * 索引跳转配置：支持 Letter 模式（A–Z #）与 Anchor 模式（智能锚点）。
 * - Letter 模式：[letters] + [letterToIndex]，降序时 letters 为 Z–A #。
 * - Anchor 模式：[smartAnchor] + [orderType]，降序时锚点条从上到下反转（与列表首项对应）。
 */
data class IndexJumpConfig(
    val enabled: Boolean = false,
    val mode: IndexJumpMode = IndexJumpMode.FirstLetter,
    val letters: List<Char> = ('A'..'Z').toList() + listOf('#'),
    val letterToIndex: (List<MusicInfo>) -> Map<Char, Int>,
    val smartAnchor: ((List<MusicInfo>) -> Pair<List<String>, Map<Int, Int>>)? = null,
    val orderType: String = "ASC",
) {
    val isAnchorMode: Boolean get() = smartAnchor != null
}

enum class IndexJumpMode {
    FirstLetter,
    PinyinInitial,
}

/**
 * 标题首字符对应的索引字母：英文字母取大写；中文取首字默认拼音的首字母；数字及其他归为 '#'。
 */
private fun titleToIndexLetter(title: String): Char {
    val c = title.firstOrNull() ?: return '#'
    return when {
        c in 'a'..'z' -> c.uppercaseChar()
        c in 'A'..'Z' -> c
        c in '0'..'9' -> '#'
        else -> {
            val pinyinKey = stringToPinyinSortKey(title)
            pinyinKey.firstOrNull()?.uppercaseChar() ?: '#'
        }
    }
}

/**
 * 按标题首字母/首字分组，返回「字母 -> 该组第一项在列表中的 index」映射。
 * 英文取首字母；中文取首字默认拼音首字母；数字及其他归为 #。依赖传入 [list] 的当前顺序。
 */
fun defaultLetterToIndex(list: List<MusicInfo>): Map<Char, Int> =
    letterToIndexByString(list) { it.music.title }

/**
 * 按 [getKey] 返回的字符串首字母分组，返回「字母 -> 该组第一项在列表中的 index」映射。
 */
fun letterToIndexByString(list: List<MusicInfo>, getKey: (MusicInfo) -> String): Map<Char, Int> {
    val map = mutableMapOf<Char, Int>()
    list.forEachIndexed { index, info ->
        val char = titleToIndexLetter(getKey(info))
        if (!map.containsKey(char)) map[char] = index
    }
    return map
}

/** 智能锚点：数量范围 */
private const val SMART_ANCHOR_MIN = 8
private const val SMART_ANCHOR_MAX = 16

/** 升序时字母条从上到下 A–Z #，降序时 Z–A # */
private fun lettersForOrderType(orderType: String): List<Char> =
    if (orderType.uppercase() == "DESC") ('Z' downTo 'A').toList() + listOf('#')
    else ('A'..'Z').toList() + listOf('#')

/**
 * 根据 [orderBy] 与 [orderType] 返回对应的索引配置。
 * title/artist/album 使用字母索引，降序时字母条为 Z–A #；duration/fileSize/playCount/id 使用锚点索引。
 */
fun indexJumpConfigForOrderBy(orderBy: String, orderType: String = "ASC"): IndexJumpConfig {
    val letters = lettersForOrderType(orderType)
    return when (orderBy) {
        "title" -> IndexJumpConfig(
            enabled = true,
            letters = letters,
            letterToIndex = ::defaultLetterToIndex,
            orderType = orderType,
        )
        "artist" -> IndexJumpConfig(
            enabled = true,
            letters = letters,
            letterToIndex = { list -> letterToIndexByString(list) { it.music.artist } },
            orderType = orderType,
        )
        "album" -> IndexJumpConfig(
            enabled = true,
            letters = letters,
            letterToIndex = { list -> letterToIndexByString(list) { it.music.album } },
            orderType = orderType,
        )
        "duration" -> IndexJumpConfig(
            enabled = true,
            letterToIndex = { emptyMap() },
            smartAnchor = { list ->
                computeSmartAnchors(list, { it.music.duration }, ::formatDurationMs)
            },
            orderType = orderType,
        )
        "fileSize" -> IndexJumpConfig(
            enabled = true,
            letterToIndex = { emptyMap() },
            smartAnchor = { list ->
                computeSmartAnchors(
                    list,
                    { it.extra?.fileSize ?: 0L },
                    ::formatFileSize,
                )
            },
            orderType = orderType,
        )
        "playCount" -> IndexJumpConfig(
            enabled = true,
            letterToIndex = { emptyMap() },
            smartAnchor = { list ->
                computeSmartAnchors(
                    list,
                    { (it.userInfo?.playCount ?: 0).toLong() },
                    { it.toString() },
                )
            },
            orderType = orderType,
        )
        "id" -> IndexJumpConfig(
            enabled = true,
            letterToIndex = { emptyMap() },
            smartAnchor = { list ->
                val dateResult = computeDateSmartAnchors(list, orderType)
                val (labels, _) = dateResult
                if (labels.size <= 1 && labels.getOrNull(0) == "未知") computeDateStylePositionAnchors(list, orderType)
                else dateResult
            },
            orderType = orderType,
        )
        "date" -> IndexJumpConfig(
            enabled = true,
            letterToIndex = { emptyMap() },
            smartAnchor = { list -> computeDateSmartAnchors(list, orderType) },
            orderType = orderType,
        )
        "year" -> IndexJumpConfig(
            enabled = true,
            letterToIndex = { emptyMap() },
            smartAnchor = { list -> computeDateSmartAnchors(list, orderType) },
            orderType = orderType,
        )
        else -> IndexJumpConfig(
            enabled = true,
            letters = letters,
            letterToIndex = ::defaultLetterToIndex,
            orderType = orderType,
        )
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return commonFormat("%d:%02d", m, s)
}

/** 按列表顺序扫描，记录每个锚点桶首次出现的 list index。桶 i 为 [anchorValues[i], anchorValues[i+1])，最后一桶为 [last, ∞)。 */
private fun buildAnchorToIndex(
    list: List<MusicInfo>,
    anchorValues: List<Long>,
    getValue: (MusicInfo) -> Long,
): Map<Int, Int> {
    if (anchorValues.isEmpty()) return emptyMap()
    val result = mutableMapOf<Int, Int>()
    for ((listIndex, info) in list.withIndex()) {
        val v = getValue(info)
        val bucket = when {
            v < anchorValues.first() -> 0
            v >= anchorValues.last() -> anchorValues.lastIndex
            else -> {
                val i = anchorValues.indexOfFirst { it > v }
                if (i <= 0) 0 else i - 1
            }
        }
        if (bucket !in result) result[bucket] = listIndex
    }
    return result
}

/**
 * 智能锚点：根据列表数据分布生成锚点数量与取值，返回 (文案列表, 锚点下标→列表index)。
 * 数值类（duration/fileSize/playCount）使用分位数，使每桶数量大致均匀。
 */
private fun computeSmartAnchors(
    list: List<MusicInfo>,
    getValue: (MusicInfo) -> Long,
    formatLabel: (Long) -> String,
    minAnchors: Int = SMART_ANCHOR_MIN,
    maxAnchors: Int = SMART_ANCHOR_MAX,
): Pair<List<String>, Map<Int, Int>> {
    if (list.isEmpty()) return Pair(emptyList(), emptyMap())
    val values = list.map(getValue).filter { it >= 0 }
    if (values.isEmpty()) return Pair(emptyList(), emptyMap())
    val sorted = values.sorted()
    val n = sorted.size
    if (n == 0) return Pair(emptyList(), emptyMap())
    val anchorCount = if (list.size > 100) maxAnchors else (minAnchors + (list.size / 200).coerceAtMost(maxAnchors - minAnchors)).coerceIn(minAnchors, maxAnchors)
    val anchorValues = if (n == 1) {
        listOf(sorted[0])
    } else {
        (0 until anchorCount).map { i ->
            val idx = (i.toLong() * (n - 1) / (anchorCount - 1).coerceAtLeast(1)).toInt().coerceIn(0, n - 1)
            sorted[idx]
        }.distinct()
    }
    if (anchorValues.isEmpty()) return Pair(emptyList(), emptyMap())
    val labels = anchorValues.map(formatLabel)
    val map = buildAnchorToIndex(list, anchorValues, getValue)
    return Pair(labels, map)
}

/** 文件大小格式化（字节 → 展示文案） */
private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    else -> "${bytes / (1024 * 1024)}MB"
}

/**
 * 智能锚点：id（添加顺序）按位置分位，锚点数量随列表长度调整。
 * [orderType] 为 DESC 时列表为「新→旧」，锚点「早」应对应列表末尾、「末」对应列表头，故反转映射。
 */
/**
 * 智能锚点标签（`早` / `1/4` / `1/2` / `3/4` / `末`）与「未知」哨兵。
 *
 * ⚠️ **已知缺口（f14 §3.13 登记，本轮不抽取）**：这些标签**未接入 strings.xml** —— 它们既是
 * 滚动条的**显示文案**，又充当 [computeDateStylePositionAnchors] 的**哨兵值**
 * （`labels.getOrNull(0) == "未知"` 比较）。本地化需先拆成「内部稳定哨兵 + 显示层文案」两层，
 * 属结构改造，故延后。
 */
private fun computeIdSmartAnchors(list: List<MusicInfo>, orderType: String = "ASC"): Pair<List<String>, Map<Int, Int>> {
    if (list.isEmpty()) return Pair(emptyList(), emptyMap())
    val n = list.size
    if (n <= 1) return Pair(listOf("早"), mapOf(0 to 0))
    val anchorCount = if (n > 100) SMART_ANCHOR_MAX else when {
        n < 20 -> 8
        n < 40 -> 10
        n < 60 -> 12
        else -> SMART_ANCHOR_MAX
    }.coerceIn(SMART_ANCHOR_MIN, SMART_ANCHOR_MAX)
    val positionLabels = listOf("早", "1/4", "1/2", "3/4", "末")
    val labels = (0 until anchorCount).map { i ->
        positionLabels.getOrElse(i) { "${i + 1}/${anchorCount}" }
    }
    val desc = orderType.uppercase() == "DESC"
    val map = (0 until anchorCount).associate { i ->
        val pos = (i.toLong() * (n - 1) / (anchorCount - 1).coerceAtLeast(1)).toInt().coerceIn(0, n - 1)
        val listIndex = if (desc) n - 1 - pos else pos
        i to listIndex
    }
    return Pair(labels, map)
}

/**
 * 年月混合索引：单一信息源 [MusicExtra.date]（app 首次读取歌曲的时间戳）。
 * 从时间戳解析 (年, 月)；有有效日期时按年月分档；仅「未知」时用 [computeDateStylePositionAnchors]。
 */
private fun computeDateSmartAnchors(
    list: List<MusicInfo>,
    orderType: String,
): Pair<List<String>, Map<Int, Int>> {
    if (list.isEmpty()) return Pair(emptyList(), emptyMap())
    fun getYearMonth(info: MusicInfo): Pair<Int, Int> {
        val ts = info.extra?.date ?: return -1 to 1
        return epochMillisToYearMonth(ts)
    }
    val pairs = list.map(::getYearMonth).distinct()
    if (pairs.isEmpty()) return Pair(emptyList(), emptyMap())
    val sorted = if (orderType.uppercase() == "DESC") {
        pairs.sortedWith(compareBy({ -it.first }, { -it.second }))
    } else {
        pairs.sortedWith(compareBy({ it.first }, { it.second }))
    }
    val ordered = sorted.filter { it.first >= 0 }.let { known ->
        if (sorted.any { it.first == -1 }) known + (-1 to 1) else known
    }
    if (ordered.isEmpty()) return Pair(emptyList(), emptyMap())
    val labels = ordered.map { (y, m) ->
        if (m == 1) (if (y == -1) "未知" else y.toString()) else commonFormat("%02d", m)
    }
    if (labels.size <= 1 && labels.getOrNull(0) == "未知") {
        return computeDateStylePositionAnchors(list, orderType)
    }
    val map = ordered.mapIndexed { index, pair ->
        index to list.indexOfFirst { getYearMonth(it) == pair }
    }.filter { it.second >= 0 }.toMap()
    return Pair(labels, map)
}

/**
 * 按添加时间排序所用的顺序，将列表位置映射到一条时间线（从过去到当前），再按虚拟年月做索引。
 * 列表已按 id 排好：ASC = 早→新，DESC = 新→早。将 [0..n-1] 线性映射到 startTime..endTime，
 * 得到每个位置对应的 (年, 月)，去重后生成 11、12、2026、01、02、03 风格锚点。
 */
private fun computeAddOrderYearMonthAnchors(
    list: List<MusicInfo>,
    orderType: String,
): Pair<List<String>, Map<Int, Int>> {
    if (list.isEmpty()) return Pair(emptyList(), emptyMap())
    val n = list.size
    // 虚拟时间线跨度 ≈ 2 年（原 Calendar.add(YEAR,-2)，此处用近似值；仅用于锚点标签，无闰日精确性要求）
    val endMs = nowEpochMillis()
    val startMs = endMs - 2L * 365 * 24 * 3600 * 1000
    val spanMs = (endMs - startMs).toDouble().coerceAtLeast(1.0)
    val desc = orderType.uppercase() == "DESC"
    fun virtualYearMonth(listIndex: Int): Pair<Int, Int> {
        val ratio = if (n <= 1) 1.0 else {
            val r = listIndex.toDouble() / (n - 1).coerceAtLeast(1)
            if (desc) 1.0 - r else r
        }
        val ts = (startMs + ratio * spanMs).toLong()
        return epochMillisToYearMonth(ts)
    }
    val indexToYm = (0 until n).map { i -> i to virtualYearMonth(i) }
    val ordered = indexToYm.map { it.second }.distinct().let { distinct ->
        if (desc) distinct.sortedWith(compareBy({ -it.first }, { -it.second }))
        else distinct.sortedWith(compareBy({ it.first }, { it.second }))
    }
    if (ordered.isEmpty()) return Pair(emptyList(), emptyMap())
    val labels = ordered.map { (y, m) -> if (m == 1) y.toString() else commonFormat("%02d", m) }
    val map = ordered.mapIndexed { anchorIndex, pair ->
        anchorIndex to indexToYm.indexOfFirst { it.second == pair }
    }.filter { it.second >= 0 }.toMap()
    return Pair(labels, map)
}

/**
 * 年月风格的位置索引：分段算法与 [computeIdSmartAnchors] 一致，但标签为「当年 + 01～12」。
 * 仅在 [computeDateSmartAnchors] 仅得「未知」时作为 fallback 使用。
 */
private fun computeDateStylePositionAnchors(
    list: List<MusicInfo>,
    orderType: String,
): Pair<List<String>, Map<Int, Int>> {
    if (list.isEmpty()) return Pair(emptyList(), emptyMap())
    val n = list.size
    if (n <= 1) return Pair(listOf(epochMillisToYearMonth(nowEpochMillis()).first.toString()), mapOf(0 to 0))
    val anchorCount = 13
    val currentYear = epochMillisToYearMonth(nowEpochMillis()).first
    val labels = listOf(currentYear.toString()) + (1..12).map { commonFormat("%02d", it) }
    val desc = orderType.uppercase() == "DESC"
    val map = (0 until anchorCount).associate { i ->
        val pos = (i.toLong() * (n - 1) / (anchorCount - 1).coerceAtLeast(1)).toInt().coerceIn(0, n - 1)
        val listIndex = if (desc) n - 1 - pos else pos
        i to listIndex
    }
    return Pair(labels, map)
}

/**
 * 垂直滚动条样式与显隐；颜色为 null 时使用主题或半透明默认色。
 */
data class ScrollbarConfig(
    val enabled: Boolean = false,
    val width: Dp = 4.dp,
    val thumbMinHeight: Dp = 24.dp,
    val cornerRadius: Dp = 2.dp,
    val trackColor: Color? = null,
    val thumbColor: Color? = null,
)

// ---------- CurrentPlaying ----------

/**
 * 当前播放项：索引、是否自动滚动、滚动偏移。
 * 当前播放项始终高亮背景（不可配置）。列表排序后，调用方应将 [index] 更新为当前播放曲目在新列表中的下标，以便高亮与自动滚动正确。
 */
data class CurrentPlayingConfig(
    val index: Int? = null,
    val autoScrollToCurrent: Boolean = true,
    val scrollOffsetForCenter: Dp? = null,
)

// ---------- Callbacks ----------

/**
 * 单项与批量操作回调汇总。使用 [MusicListCallbacksAdapter] 可只重写需要的回调。
 */
interface MusicListCallbacks {
    fun onItemClick(musicInfo: MusicInfo, index: Int) {}
    fun onAddToPlaylist(musicInfo: MusicInfo) {}
    fun onMenuClick(musicInfo: MusicInfo) {}
    fun onRemoveFromPlaylist(musicInfo: MusicInfo) {}
    fun onMoveUp(index: Int) {}
    fun onMoveDown(index: Int) {}
    fun onPinToTop(musicInfo: MusicInfo) {}
    fun onRemove(musicInfo: MusicInfo) {}
    fun onEnterEditMode() {}
    fun onExitEditMode() {}
    fun onSelectionChange(selectedIds: Set<Long>) {}
    fun onBatchDelete(selectedIds: Set<Long>) {}
    fun onBatchAddToPlaylist(selectedIds: Set<Long>) {}
    fun onBatchRemoveFromPlaylist(selectedIds: Set<Long>) {}
    fun onBatchMoveToTop(selectedIds: Set<Long>) {}
    fun onBatchShare(selectedIds: Set<Long>) {}
}

/**
 * 默认空实现的适配器，调用方仅 override 需要的回调。
 */
open class MusicListCallbacksAdapter : MusicListCallbacks

// ---------- 默认与预设 ----------

/**
 * 返回默认 [MusicListConfig]：无头部、Full 单项、无编辑/索引/滚动条、[defaultLetterToIndex] 用于索引跳转。
 * 调用方可通过 [MusicListConfig.copy] 按需覆盖。
 */
fun defaultMusicListConfig(
    callbacks: MusicListCallbacks = MusicListCallbacksAdapter(),
): MusicListConfig = MusicListConfig(
    header = HeaderConfig.None,
    item = ItemConfig(),
    list = ListConfig(),
    edit = EditConfig(),
    indexJump = IndexJumpConfig(letterToIndex = ::defaultLetterToIndex),
    scrollbar = ScrollbarConfig(),
    currentPlaying = CurrentPlayingConfig(),
    callbacks = callbacks,
)

/**
 * 播放列表场景预设：Simple 头部、Full 单项、可编辑、可显示序号与选择框、支持长按进入编辑。
 * [headerTrailing] 可选，在头部歌曲数量右侧渲染（如添加歌曲图标按钮）。
 */
fun playlistPresetMusicListConfig(
    onOrderPlay: () -> Unit,
    onShufflePlay: () -> Unit,
    callbacks: MusicListCallbacks,
    headerTrailing: (@Composable () -> Unit)? = null,
): MusicListConfig = defaultMusicListConfig(callbacks).copy(
    header = HeaderConfig.Simple(onOrderPlay = onOrderPlay, onShufflePlay = onShufflePlay, trailing = headerTrailing),
    item = ItemConfig(
        showIndex = true,
        showCheckbox = true,
        variant = ItemVariant.Full,
        fullOptions = FullItemOptions(showPinButton = true, showRemoveButton = true, showMenuButton = true),
    ),
    list = ListConfig(enableLongPressToEnterEdit = true),
    edit = EditConfig(enabled = true),
)

/**
 * 图库/简洁列表预设：无头部、Gallery 单项、无编辑。
 */
fun galleryPresetMusicListConfig(callbacks: MusicListCallbacks): MusicListConfig =
    defaultMusicListConfig(callbacks).copy(
        header = HeaderConfig.None,
        item = ItemConfig(variant = ItemVariant.Gallery, galleryOptions = GalleryItemOptions()),
    )

/**
 * 曲库/列表页预设：Full 头部（排序筛选）、Full 单项、可编辑、可选索引与滚动条。
 */
fun libraryPresetMusicListConfig(
    selectedGenre: String,
    selectedOrder: String,
    onFilterGenreChange: (String) -> Unit,
    onFilterOrderChange: (String) -> Unit,
    onOrderPlay: () -> Unit,
    onShufflePlay: () -> Unit,
    callbacks: MusicListCallbacks,
): MusicListConfig = defaultMusicListConfig(callbacks).copy(
    header = HeaderConfig.Full(
        selectedGenre = selectedGenre,
        selectedOrder = selectedOrder,
        onFilterGenreChange = onFilterGenreChange,
        onFilterOrderChange = onFilterOrderChange,
        onOrderPlay = onOrderPlay,
        onShufflePlay = onShufflePlay,
    ),
    item = ItemConfig(
        showCheckbox = true,
        variant = ItemVariant.Full,
        fullOptions = FullItemOptions(),
    ),
    list = ListConfig(enableLongPressToEnterEdit = true),
    edit = EditConfig(enabled = true),
    indexJump = IndexJumpConfig(enabled = true, letterToIndex = ::defaultLetterToIndex),
    scrollbar = ScrollbarConfig(enabled = true),
)
