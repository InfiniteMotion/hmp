package com.hearablemusic.player.ui.common.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.db_too_new_body
import com.hearablemusic.player.ui.generated.resources.db_too_new_title
import com.hmp.data.database.DatabaseTooNew
import org.jetbrains.compose.resources.stringResource

/**
 * 库版本高于本应用时的提示页（一-7 / D7-10）。
 *
 * 存在的理由：三端都没有 destructive 兜底（那是刻意的 —— 降级时静默 drop 整库比打不开更糟），
 * 但"打不开"如果表现为崩在启动路上，用户看到的就是"app 坏了"，而数据其实完好。
 * 这个页面由 [com.hearablemusic.player.ui.AppRoot] 在**解析任何 DAO 之前**分流出来，
 * 它自己只读 [DatabaseTooNew] 的两个版本号，不碰仓库、不碰 Koin 里的数据库。
 */
@Composable
fun DatabaseTooNewScreen(state: DatabaseTooNew) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(metrics.block),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.db_too_new_title),
            style = typo.headline,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            modifier = Modifier.padding(top = metrics.compact),
            text = stringResource(Res.string.db_too_new_body, state.codeVersion, state.dbVersion),
            style = typo.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
