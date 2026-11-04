package com.hearablemusic.player.ui.common.design.metrics

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 对象外尺寸常量（规范：docs/spec/hmp-design.md §10.5，taskbook D8-03 阶段 3）。
 *
 * 三类值**不进统一刻度**，统一集中在本文件、由业务引用具名常量；守门只豁免本文件，
 * 保证「业务代码零裸 .dp」成立，也不会出现「写 200.dp 自动合法」的后门。
 *
 * 划分依据是**用途而非数值大小**（曾按「>48dp」切分被代码抽样证伪，见 §10.5）：
 * 1. 布局断点 / 窗口限宽——它们定义了「档」，不能自己也从档里取值（循环依赖）；
 * 2. 几何 / 手势参数——参与 px 计算、插值基准与容器上限，归并会改交互手感；
 * 3. 顶层常量与 data class 默认参数——非 Composable 作用域，且原本就不随窗口变化。
 *
 * 能落回 12 档的节奏值**不在此列**：它们直接引用 [HMPMetricTokens.Compact] 的档位，
 * 不为每个历史值再造一个常量，避免常量表膨胀成第二套数列。
 *
 * 同一个值被多处不同用途复用时，取主要用途命名，值本身是唯一真源；
 * 若某处需要独立语义（后续要单独调整），再拆分常量，不要在业务里写回裸值。
 */
object LayoutConstraints {

    // ── 1. 布局断点（WindowSizeClass 判据 / 内容限宽共用同一批量级）──

    /** 宽度 Medium 起点；亦作表单最大宽度 */
    val WidthBreakpointMedium: Dp = 600.dp

    /** 宽度 Expanded 起点；亦作横屏聊天页最大宽度 */
    val WidthBreakpointExpanded: Dp = 840.dp

    /** 高度 Medium 起点；亦作底栏 Compact 限宽与弹窗 / 歌词容器上限 */
    val HeightBreakpointMedium: Dp = 480.dp

    /** 高度 Expanded 起点 */
    val HeightBreakpointExpanded: Dp = 900.dp

    // ── 2. 窗口限宽与内容上限 ──

    /** 底栏 Medium / 内容页 / 审计列表 / Intro 页共用 */
    val ContentMaxWidthMedium: Dp = 640.dp

    /** 宽屏内容 / 表单宽行上限 */
    val ContentMaxWidthWide: Dp = 720.dp

    /** 监控看板 Expanded 上限 */
    val BoardMaxWidth: Dp = 1080.dp

    /** 通知条横屏 / Medium 上限 */
    val NoticeBarMaxWidth: Dp = 520.dp

    /** 编辑页 / 侧栏 / 封面卡上限 */
    val PanelMaxWidth: Dp = 280.dp

    /** 宽屏封面卡上限 */
    val CoverMaxWidthWide: Dp = 260.dp

    /** 弹窗 / 半宽封面上限 */
    val DialogMaxWidth: Dp = 240.dp

    /** Toast 收窄宽度 */
    val ToastMaxWidth: Dp = 150.dp

    // ── 3. 容器高度与面板尺寸 ──

    /** 资料面板 / 播放列表选择器 / 高级歌词留白 */
    val PanelHeight: Dp = 300.dp

    val PanelHeightLarge: Dp = 220.dp

    val CardHeight: Dp = 180.dp

    val CoverHeightSmall: Dp = 160.dp

    val ListMaxHeight: Dp = 130.dp

    val PlaylistAreaHeight: Dp = 500.dp

    val PlaylistSheetHeight: Dp = 560.dp

    val DialogMaxHeight: Dp = 400.dp

    val DialogMaxHeightLarge: Dp = 420.dp

    // ── 4. 几何 / 手势 / 动画基准（不可就近归并，否则改交互手感）──

    /** 均衡器基准偏移 */
    val EqualizerBaseOffset: Dp = 100.dp

    /** 嵌套滚动目标位移 */
    val NestedScrollTarget: Dp = 1000.dp

    /** 周年徽章尺寸 */
    val AnniversaryBadgeSize: Dp = 110.dp

    /** 紧凑图标尺寸（迷你播放条 / 首页窄窗） */
    val CompactIconSize: Dp = 26.dp

    /** 对话页图标尺寸 */
    val ChatIconSize: Dp = 42.dp

    /** 状态点尺寸 */
    val StatusDotSize: Dp = 7.dp

    /** 环形复选框描边 */
    val RingCheckboxStroke: Dp = 1.5.dp

    /** 卡片阴影高度（视觉约束，不随窗口档变化） */
    val ShadowElevation: Dp = 15.dp

    // ── 5. Desktop 壳层窗口约束 ──

    val WindowInitialWidth: Dp = 1200.dp

    val WindowInitialHeight: Dp = 800.dp

    // ── 6. 平台壳层排版对象外 ──
    // 悬浮歌词 / 自绘标题栏叠加在共享 Theme 之外，是独立小部件的固有字号，
    // 不适合依赖 LocalHmpTypo；集中在此，待壳层纳入 Theme 装配后再迁回字阶 9 角色。

    /** 悬浮歌词顶栏文字 */
    val OverlayFontSizeBody: TextUnit = 12.sp

    /** 悬浮歌词锁定图标 */
    val OverlayFontSizeSmall: TextUnit = 10.sp

    /** 悬浮歌词关闭图标 */
    val OverlayFontSizeIcon: TextUnit = 14.sp

    /** 悬浮歌词音符 */
    val OverlayFontSizeNote: TextUnit = 24.sp

    /** 悬浮歌词播放指示点 */
    val OverlayFontSizeDot: TextUnit = 6.sp

    /** 桌面标题栏应用名 */
    val TitleBarFontSize: TextUnit = 13.sp

    val TitleBarLetterSpacing: TextUnit = 0.5.sp

    /** 桌面标题栏按钮符号 */
    val TitleBarButtonFontSize: TextUnit = 11.sp
}
