package com.hearablemusic.player.ui.agent.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hearablemusic.player.ui.common.text.asString
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.card_greeting_phase
import com.hmp.domain.agent.card.GreetingContent
import com.hmp.domain.agent.card.SlideCard
import org.jetbrains.compose.resources.stringResource



// ═══════════════════════════════════════════════════════════════════
// 家族 E：GREETING —— 纯文案（去封面槽）
// ═══════════════════════════════════════════════════════════════════

@Composable
internal fun FamilyGreetingCard(
    card: SlideCard,
    modifier: Modifier = Modifier,
    onCardClick: ((SlideCard) -> Unit)? = null,
    onLongClick: ((SlideCard) -> Unit)? = null,
) {
    val c = card.content as GreetingContent
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
                        colors = listOf(Color(0xFFFFB300).copy(alpha = 0.95f), Color(0xFFFF6F00).copy(alpha = 0.90f))
                    )
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Top
            ) {
                // 时段感知问候
                Text(
                    text = stringResource(Res.string.card_greeting_phase, c.phase.label.asString()),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 10.dp),
                )

                // 主文案（居中，根据内容长度自动适配）
                Text(
                    text = c.text,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 27.sp,
                )
            }
        }
    }
}
