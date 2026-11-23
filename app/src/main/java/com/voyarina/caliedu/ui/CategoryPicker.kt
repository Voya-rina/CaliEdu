package com.voyarina.caliedu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voyarina.caliedu.core.Subject

/**
 * 改分类。只改标签，不动进度和错题——
 * 分类是给自己整理用的，删掉重来才是破坏性的。
 *
 * 自动分类只能看题面猜，猜错是常态（讲神经网络的课，满篇 import 就会被当成 Python），
 * 所以这个入口在记录页和课程目录里都能点到。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryPickerDialog(
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val skin = LocalSkin.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = skin.surface,
        shape = RoundedCornerShape(skin.radiusCard),
        title = {
            Column {
                Text("归到哪一类", fontSize = skin.fs(15.5f), fontWeight = FontWeight.Medium, color = skin.text)
                Spacer(Modifier.height(3.dp))
                Text("只改标签，不动已经走过的进度", fontSize = skin.fs(11f), color = skin.sub)
            }
        },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Subject.ALL.forEach { name ->
                    val active = name == current
                    Text(
                        name,
                        fontSize = skin.fs(12f),
                        color = if (active) skin.onAccent else skin.text,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (active) skin.accent else skin.codeBg)
                            .clickable { onPick(name) }
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                    )
                }
            }
        },
        confirmButton = {
            Box(
                Modifier
                    .height(skin.ui(36f))
                    .clip(RoundedCornerShape(100.dp))
                    .background(skin.codeBg)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("取消", fontSize = skin.fs(12.5f), fontWeight = FontWeight.Medium, color = skin.text)
            }
        },
    )
}
