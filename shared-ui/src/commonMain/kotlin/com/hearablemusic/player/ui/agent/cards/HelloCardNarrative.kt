package com.hearablemusic.player.ui.agent.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hmp.domain.agent.card.NarrativeContent
import com.hmp.domain.agent.card.SlideCard
import com.hmp.domain.agent.card.zhName



// ═══════════════════════════════════════════════════════════════════
// 家族 F：NARRATIVE —— 听歌报告叙事段（常驻卡，长文案）
// ═══════════════════════════════════════════════════════════════════

/**
 * 报告叙事卡：展示 HelloSubAgent 生成的散文式听歌总结。
 *
 * 与 GREETING 卡的区别：文案更长（一二百字）、常驻不轮播、带时间维度标签。
 * 点击落点（进报告页）暂空——P5 报告页改造后再接。
 */
@Composable
internal fun FamilyNarrativeCard(
    card: SlideCard,
    modifier: Modifier = Modifier,
    onCardClick: ((SlideCard) -> Unit)? = null,
    onLongClick: ((SlideCard) -> Unit)? = null,
) {
    val c = card.content as NarrativeContent
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
                        colors = listOf(Color(0xFF5E35B1).copy(alpha = 0.95f), Color(0xFF311B92).copy(alpha = 0.90f))
                    )
                )
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            // `SpaceBetween` → `Center`：本卡是"顶部标签 + 一段正文 + 可选脚注"的三段式，
            // 但**中间那段并不存在**。SpaceBetween 会把首尾两段硬推到上下两端，
            // 于是在横屏 420dp 高的卡里留下一条贯穿整卡的空白带（截图里"一大坨死紫"的主因）。
            // 改成 Center 后，内容作为一个整体垂直居中；间距靠显式 spacedBy 控制，
            // 不会再出现"内容少 → 空白多"的反比关系。
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // ① 顶部标签：时间维度 + 生成时间
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "${c.timeRange.zhName()}听歌报告",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = formatGeneratedAgo(c.generatedAt),
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 10.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.10f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }

                    // ② 叙事正文（长文案，最多 6 行，超出省略）
                    // 字号/行高按横屏大卡上调：卡高可达 420dp，13sp 的正文在大卡里会显得"飘"。
                    Text(
                        text = c.narrative,
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 25.sp,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // ③ 底部：日均听歌时长（有值才显示）
                val avg = c.avgDailyMinutes
                if (avg != null && avg > 0f) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "日均听歌 ${formatAvgMinutes(avg)}",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

/** 生成时间 → "今天 / N 天前 / N 个月前" 相对表述 */
