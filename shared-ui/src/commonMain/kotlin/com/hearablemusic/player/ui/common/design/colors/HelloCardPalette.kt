package com.hearablemusic.player.ui.common.design.colors

import androidx.compose.ui.graphics.Color

/**
 * 音乐伙伴页七类卡片的固定主题色。
 *
 * 这些颜色**不随亮/暗主题变化**，是卡片自身的"家族色"；渐变透明度由调用点决定。
 * 集中在这里是为了让卡片的视觉身份可被单点修改，而不是散在七张卡的实现里。
 */
object HelloCardPalette {

    /** ANCHOR 与 RADIO_STATUS：封面取色失败时的兜底渐变（两卡共用，保持一致）。 */
    object CoverFallback {
        val Primary = Color(0xFF3D5AFE)
        val Background = Color(0xFF1A237E)
    }

    /** ANNIVERSARY：按子类型取色。 */
    object Anniversary {
        val FirstPlay = Color(0xFFFFA000)
        val PlaylistCreate = Color(0xFFAB47BC)
        val PlayMilestone = Color(0xFF26A69A)
        val DurationMilestone = Color(0xFF42A5F5)
    }

    /** DISCOVER：青色系渐变。 */
    object Discover {
        val Start = Color(0xFF00ACC1)
        val End = Color(0xFF006064)
    }

    /** GREETING：琥珀色系渐变。 */
    object Greeting {
        val Start = Color(0xFFFFB300)
        val End = Color(0xFFFF6F00)
    }

    /** NARRATIVE：紫色系渐变。 */
    object Narrative {
        val Start = Color(0xFF5E35B1)
        val End = Color(0xFF311B92)
    }

    /** SINGLE_TRACK：推荐 / 遗忘唤醒两种子类型的主题色。 */
    object SingleTrack {
        val Recommend = Color(0xFF7C4DFF)
        val Forgotten = Color(0xFF78909C)
    }
}
