package com.hearablemusic.player.ui.agent.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hmp.domain.agent.card.AnniversaryContent
import com.hmp.domain.agent.card.AnniversarySubtype
import com.hmp.domain.agent.card.SlideCard



// ═══════════════════════════════════════════════════════════════════
// 家族 B-new：ANNIVERSARY 专用卡——4 种子类型差异化视觉
// ═══════════════════════════════════════════════════════════════════

/** ANNIVERSARY 子类型 → 视觉配置（颜色、emoji、徽章文本、标签）。 */
private data class AnniversaryVisual(
    val themeColor: Color,
    val label: String,        // 顶部小标签（如 "N 年前的今天"）
    val badgeMain: String,    // 徽章主文本（如 "3" / "100" / "50h"）
    val badgeSub: String?,    // 徽章副文本（如 "年前" / "次" / null）
    val footerParts: List<String>,  // 底部数字条分片
)

private fun AnniversaryContent.resolveVisual(): AnniversaryVisual = when (subtype) {
    AnniversarySubtype.FIRST_PLAY -> AnniversaryVisual(
        themeColor = Color(0xFFFFA000),
        label = "$yearsAgo 年前的今天",
        badgeMain = (yearsAgo ?: 1).toString(),
        badgeSub = "年前",
        footerParts = buildList {
            // 只取日期部分，"N 年前" 已由 label + 徽章表达
            specificDate?.substringBefore("·")?.trim()?.let { add(it) }
            if ((thatDayPlays ?: 0) > 0) add("那天听了 $thatDayPlays 遍")
            add("累计 $totalPlays 次")
            totalListenHours?.let { if (it > 0) add("${it}h") }
        },
    )
    AnniversarySubtype.PLAYLIST_CREATE -> AnniversaryVisual(
        themeColor = Color(0xFFAB47BC),
        label = "$yearsAgo 年前建的歌单",
        badgeMain = "歌单",
        badgeSub = null,
        footerParts = buildList {
            specificDate?.substringBefore("·")?.trim()?.let { add(it) }
            add("$totalPlays 次播放")
        },
    )
    AnniversarySubtype.PLAY_MILESTONE -> AnniversaryVisual(
        themeColor = Color(0xFF26A69A),
        label = "里程碑",
        badgeMain = (milestoneValue ?: 100).toString(),
        badgeSub = "次",
        footerParts = buildList {
            // "100 次" 已由徽章表达，放总数供参考
            add("共 $totalPlays 次")
            totalListenHours?.let { if (it > 0) add("${it}h") }
        },
    )
    AnniversarySubtype.DURATION_MILESTONE -> AnniversaryVisual(
        themeColor = Color(0xFF42A5F5),
        label = "里程碑",
        badgeMain = "${milestoneValue ?: 10}h",
        badgeSub = null,
        footerParts = buildList {
            // "50h" 已由徽章表达，放累计总数 + 播放次数
            add("共 $totalPlays 次")
            totalListenHours?.let { if (it > 0) add("${it}h") }
        },
    )
}

@Composable
internal fun FamilyAnniversaryCard(
    card: SlideCard,
    modifier: Modifier = Modifier,
    onCardClick: ((SlideCard) -> Unit)? = null,
    onLongClick: ((SlideCard) -> Unit)? = null,
) {
    val content = card.content as? AnniversaryContent ?: return
    val visual = content.resolveVisual()

    val clickMod = modifier.clip(RoundedCornerShape(25.dp)).let { m ->
        when {
            onLongClick != null -> m.pointerInput(card.cardId) {
                detectTapGestures(
                    onLongPress = { onLongClick.invoke(card) },
                    onTap = { /* 短按留给 Pager 手势 */ },
                )
            }
            onCardClick != null -> m.clickable { onCardClick.invoke(card) }
            else -> m
        }
    }

    Card(
        modifier = clickMod,
        shape = RoundedCornerShape(25.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(visual.themeColor.copy(alpha = 0.95f), visual.themeColor.copy(alpha = 0.75f)),
                    )
                )
                .padding(20.dp)
        ) {
            // 装饰性圆斑（右上角）
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(80.dp)
                    .clip(RoundedCornerShape(40.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(Color.White.copy(alpha = 0.06f))
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // ═══ 左：徽章区（ANNIVERSARY 专属视觉焦点）═══
                AnniversaryBadge(
                    visual = visual,
                    size = 110.dp,
                )

                // ═══ 右：信息区 ═══
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    // 标签
                    Text(
                        text = visual.label,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // 主标题
                    Text(
                        text = content.trackTitle,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )

                    // 分割线
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(vertical = 8.dp)
                    )

                    // 情感文案（Llm 生成的那句话）
                    content.emotionText?.let {
                        Text(
                            text = it,
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // 底部数据点（多行垂直排列）
                    Column(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        visual.footerParts.forEach { part ->
                            Text(
                                text = "· $part",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** ANNIVERSARY 专属徽章：圆形或方形（PLAYLIST_CREATE），内含大数字/emoji。 */
@Composable
private fun AnniversaryBadge(
    visual: AnniversaryVisual,
    size: androidx.compose.ui.unit.Dp,
) {
    val isSquareEmoji = visual.badgeSub == null && visual.badgeMain.length <= 2
    Box(
        modifier = Modifier
            .size(size)
            .clip(if (isSquareEmoji) RoundedCornerShape(20.dp) else RoundedCornerShape(size / 2))
            .background(Color.White.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        if (visual.badgeSub != null) {
            // 徽章主副两行（如 "3" + "年前" / "100" + "次"）
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = visual.badgeMain,
                    color = Color.White,
                    fontSize = when (visual.badgeMain.length) {
                        in 1..2 -> 26.sp
                        3 -> 20.sp
                        else -> 16.sp
                    },
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = visual.badgeSub,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        } else {
            // 单行徽章文本（如 "歌单" / "50h"）
            Text(
                text = visual.badgeMain,
                color = Color.White,
                fontSize = when {
                    visual.badgeMain.length <= 2 -> 22.sp
                    visual.badgeMain.length <= 4 -> 18.sp
                    else -> 16.sp
                },
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
