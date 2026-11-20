package com.caliedu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caliedu.core.Track

/**
 * 示例入口。原来在首页，和「粘一道题」抢注意力；
 * 现在挪到记录页——那里本来就是「翻旧账」的地方，示例算一份现成的旧账。
 */
@Composable
internal fun SampleEntry(onClick: () -> Unit) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(skin.radiusCard)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(skin.accentSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = skin.accent,
            modifier = Modifier.size(skin.ui(16f)),
        )
        Spacer(Modifier.width(9.dp))
        Text("示例", fontSize = skin.smallSize, color = skin.accentDeep)
        Spacer(Modifier.weight(1f))
        Text("三份现成的课", fontSize = skin.fs(11f), color = skin.accent.copy(alpha = 0.75f))
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = skin.accent,
            modifier = Modifier.size(skin.ui(16f)),
        )
    }
}

/** 三份示例：一份是闯关，一份是纯数学推导，一份是现成的整本讲义。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SampleSheet(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val skin = LocalSkin.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val rows = listOf(
        Triple(Icons.Rounded.AutoStories, "代码闯关", "鲍鱼年龄预测 · 22 步，每步一道题"),
        Triple(Icons.Rounded.Functions, "数学推导", "数列通项 · 8 步，全是短推导"),
        Triple(Icons.AutoMirrored.Rounded.MenuBook, "整本讲义", "md 教程直接摊开读，不出题"),
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = skin.bg,
        dragHandle = { SheetHandle(skin) },
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("示例", fontSize = skin.stepTitleSize * 0.9f, fontWeight = FontWeight.Bold, color = skin.text)
            Spacer(Modifier.height(10.dp))
            rows.forEachIndexed { index, (icon, title, note) ->
                SheetRow(icon, title, note, badge = "", onClick = { onPick(index) })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 拆题前先问一句「这是什么题」。
 *
 * 自动识别只能靠关键词，代码和数学混在一起时最容易看走眼；走错一条线，
 * 整套 prompt 就白花了。三个选项已经覆盖全部拆法：数学、编程、概念。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TypeSheet(
    suggested: Track,
    onPick: (Track) -> Unit,
    onDismiss: () -> Unit,
) {
    val skin = LocalSkin.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val rows = listOf(
        Triple(Track.MATH, Icons.Rounded.Functions, "先摆条件，再一次只做一个变形，最后回头检验。"),
        Triple(Track.CODE, Icons.Rounded.Code, "先认清程序被切成几块，再逐块看数据变成了什么。"),
        Triple(Track.GENERAL, Icons.AutoMirrored.Rounded.Notes, "先问清在问什么，再找概念之间的接点。"),
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = skin.bg,
        dragHandle = { SheetHandle(skin) },
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("这是什么题？", fontSize = skin.stepTitleSize * 0.9f, fontWeight = FontWeight.Bold, color = skin.text)
            Spacer(Modifier.height(3.dp))
            Text(
                "选错了会走另一套拆法，得重来。",
                fontSize = skin.fs(11.5f),
                color = skin.sub,
            )
            Spacer(Modifier.height(10.dp))
            rows.forEach { (track, icon, note) ->
                SheetRow(
                    icon = icon,
                    title = track.label,
                    note = note,
                    badge = if (track == suggested) "推荐" else "",
                    onClick = { onPick(track) },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SheetHandle(skin: Skin) {
    Box(Modifier.padding(vertical = 10.dp)) {
        Box(
            Modifier
                .width(36.dp)
                .height(4.dp)
                .background(skin.line, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun SheetRow(
    icon: ImageVector,
    title: String,
    note: String,
    badge: String,
    onClick: () -> Unit,
) {
    val skin = LocalSkin.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(skin.radiusChoice))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(skin.ui(34f))
                .background(skin.accentSoft, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = skin.accent, modifier = Modifier.size(skin.ui(17f)))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = skin.smallSize, color = skin.text, fontWeight = FontWeight.Medium)
                if (badge.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        badge,
                        fontSize = skin.fs(9.5f),
                        color = skin.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(skin.accentSoft)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            Text(note, fontSize = skin.fs(11f), lineHeight = skin.fs(16f), color = skin.sub)
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = skin.line,
            modifier = Modifier.size(skin.ui(16f)),
        )
    }
}
