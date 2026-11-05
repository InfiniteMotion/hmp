package com.hearablemusic.player.ui.common.util

import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.angry
import com.hearablemusic.player.ui.generated.resources.arabic
import com.hearablemusic.player.ui.generated.resources.blues
import com.hearablemusic.player.ui.generated.resources.calm
import com.hearablemusic.player.ui.generated.resources.chinese
import com.hearablemusic.player.ui.generated.resources.classical
import com.hearablemusic.player.ui.generated.resources.country
import com.hearablemusic.player.ui.generated.resources.dark
import com.hearablemusic.player.ui.generated.resources.dinner
import com.hearablemusic.player.ui.generated.resources.driving
import com.hearablemusic.player.ui.generated.resources.eighties
import com.hearablemusic.player.ui.generated.resources.electronic
import com.hearablemusic.player.ui.generated.resources.energetic
import com.hearablemusic.player.ui.generated.resources.english
import com.hearablemusic.player.ui.generated.resources.focus
import com.hearablemusic.player.ui.generated.resources.folk
import com.hearablemusic.player.ui.generated.resources.french
import com.hearablemusic.player.ui.generated.resources.funk
import com.hearablemusic.player.ui.generated.resources.german
import com.hearablemusic.player.ui.generated.resources.happy
import com.hearablemusic.player.ui.generated.resources.hindi
import com.hearablemusic.player.ui.generated.resources.hiphop
import com.hearablemusic.player.ui.generated.resources.hopeful
import com.hearablemusic.player.ui.generated.resources.indie
import com.hearablemusic.player.ui.generated.resources.italian
import com.hearablemusic.player.ui.generated.resources.japanese
import com.hearablemusic.player.ui.generated.resources.jazz
import com.hearablemusic.player.ui.generated.resources.korean
import com.hearablemusic.player.ui.generated.resources.lonely
import com.hearablemusic.player.ui.generated.resources.meditation
import com.hearablemusic.player.ui.generated.resources.melancholy
import com.hearablemusic.player.ui.generated.resources.metal
import com.hearablemusic.player.ui.generated.resources.morning
import com.hearablemusic.player.ui.generated.resources.mysterious
import com.hearablemusic.player.ui.generated.resources.night
import com.hearablemusic.player.ui.generated.resources.nineties
import com.hearablemusic.player.ui.generated.resources.party
import com.hearablemusic.player.ui.generated.resources.pop
import com.hearablemusic.player.ui.generated.resources.punk
import com.hearablemusic.player.ui.generated.resources.reggae
import com.hearablemusic.player.ui.generated.resources.relax
import com.hearablemusic.player.ui.generated.resources.rnb
import com.hearablemusic.player.ui.generated.resources.rock
import com.hearablemusic.player.ui.generated.resources.romantic
import com.hearablemusic.player.ui.generated.resources.russian
import com.hearablemusic.player.ui.generated.resources.sad
import com.hearablemusic.player.ui.generated.resources.seventies
import com.hearablemusic.player.ui.generated.resources.sixties
import com.hearablemusic.player.ui.generated.resources.sleep
import com.hearablemusic.player.ui.generated.resources.soul
import com.hearablemusic.player.ui.generated.resources.spanish
import com.hearablemusic.player.ui.generated.resources.study
import com.hearablemusic.player.ui.generated.resources.travel
import com.hearablemusic.player.ui.generated.resources.twenty_tens
import com.hearablemusic.player.ui.generated.resources.twenty_twenties
import com.hearablemusic.player.ui.generated.resources.two_thousands
import com.hearablemusic.player.ui.generated.resources.unknown
import com.hearablemusic.player.ui.generated.resources.uplifting
import com.hearablemusic.player.ui.generated.resources.workout
import com.hmp.domain.enum.LabelName
import org.jetbrains.compose.resources.DrawableResource

/**
 * LabelName 扩展属性：获取对应的 composeResources 图标。
 *
 * exhaustive when——新增枚举值漏配图标时编译期报错，
 * 替代旧 SharedIconLoader 按文件名动态查找的运行时静默缺失。
 */
val LabelName.iconRes: DrawableResource
    get() = when (this) {
        // 曲风类型
        LabelName.ROCK -> Res.drawable.rock

        LabelName.POP -> Res.drawable.pop

        LabelName.JAZZ -> Res.drawable.jazz

        LabelName.CLASSICAL -> Res.drawable.classical

        LabelName.HIPHOP -> Res.drawable.hiphop

        LabelName.ELECTRONIC -> Res.drawable.electronic

        LabelName.FOLK -> Res.drawable.folk

        LabelName.RNB -> Res.drawable.rnb

        LabelName.METAL -> Res.drawable.metal

        LabelName.COUNTRY -> Res.drawable.country

        LabelName.BLUES -> Res.drawable.blues

        LabelName.REGGAE -> Res.drawable.reggae

        LabelName.PUNK -> Res.drawable.punk

        LabelName.FUNK -> Res.drawable.funk

        LabelName.SOUL -> Res.drawable.soul

        LabelName.INDIE -> Res.drawable.indie

        // 音乐情绪
        LabelName.HAPPY -> Res.drawable.happy

        LabelName.SAD -> Res.drawable.sad

        LabelName.ENERGETIC -> Res.drawable.energetic

        LabelName.CALM -> Res.drawable.calm

        LabelName.ROMANTIC -> Res.drawable.romantic

        LabelName.ANGRY -> Res.drawable.angry

        LabelName.LONELY -> Res.drawable.lonely

        LabelName.UPLIFTING -> Res.drawable.uplifting

        LabelName.MYSTERIOUS -> Res.drawable.mysterious

        LabelName.DARK -> Res.drawable.dark

        LabelName.MELANCHOLY -> Res.drawable.melancholy

        LabelName.HOPEFUL -> Res.drawable.hopeful

        // 适用场景
        LabelName.WORKOUT -> Res.drawable.workout

        LabelName.SLEEP -> Res.drawable.sleep

        LabelName.PARTY -> Res.drawable.party

        LabelName.DRIVING -> Res.drawable.driving

        LabelName.STUDY -> Res.drawable.study

        LabelName.RELAX -> Res.drawable.relax

        LabelName.DINNER -> Res.drawable.dinner

        LabelName.MEDITATION -> Res.drawable.meditation

        LabelName.FOCUS -> Res.drawable.focus

        LabelName.TRAVEL -> Res.drawable.travel

        LabelName.MORNING -> Res.drawable.morning

        LabelName.NIGHT -> Res.drawable.night

        // 歌曲语言
        LabelName.ENGLISH -> Res.drawable.english

        LabelName.CHINESE -> Res.drawable.chinese

        LabelName.JAPANESE -> Res.drawable.japanese

        LabelName.KOREAN -> Res.drawable.korean

        LabelName.SPANISH -> Res.drawable.spanish

        LabelName.FRENCH -> Res.drawable.french

        LabelName.GERMAN -> Res.drawable.german

        LabelName.ITALIAN -> Res.drawable.italian

        LabelName.ARABIC -> Res.drawable.arabic

        LabelName.HINDI -> Res.drawable.hindi

        LabelName.RUSSIAN -> Res.drawable.russian

        // 年代标签
        LabelName.SIXTIES -> Res.drawable.sixties

        LabelName.SEVENTIES -> Res.drawable.seventies

        LabelName.EIGHTIES -> Res.drawable.eighties

        LabelName.NINETIES -> Res.drawable.nineties

        LabelName.TWO_THOUSANDS -> Res.drawable.two_thousands

        LabelName.TWENTY_TENS -> Res.drawable.twenty_tens

        LabelName.TWENTY_TWENTIES -> Res.drawable.twenty_twenties

        LabelName.UNKNOWN -> Res.drawable.unknown
    }
