package com.caliedu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caliedu.UiState

/**
 * 步骤目录。长课听到一半想跳回去看某一节，比一路点「下一步」现实得多。
 * 顺带把「哪几步的正文还没生成」摊开，读的人心里有数。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StepDirectorySheet(
    state: UiState,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val skin = LocalSkin.current
    val plan = state.plan ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = skin.bg,
        dragHandle = {
            Box(Modifier.padding(vertical = 10.dp)) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .background(skin.line, CircleShape)
                )
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("目录", fontSize = skin.stepTitleSize * 0.9f, fontWeight = FontWeight.Bold, color = skin.text)
                Spacer(Modifier.weight(1f))
                Text(
                    "${state.contents.size} / ${plan.steps.size} 已写",
                    fontSize = 11.5.sp,
                    color = skin.sub,
                )
            }
            Spacer(Modifier.height(10.dp))

            LazyColumn(
                Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(plan.steps) { index, brief ->
                    val current = index == state.progress.idx
                    val ready = state.contents.containsKey(brief.n)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (current) skin.accentSoft else skin.bg,
                                RoundedCornerShape(skin.radiusChoice),
                            )
                            .clickable { onPick(index) }
                            .padding(horizontal = 10.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(20.dp)
                                .background(
                                    if (ready) skin.accent else skin.codeBg,
                                    RoundedCornerShape(6.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (ready) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = skin.onAccent,
                                    modifier = Modifier.size(12.dp),
                                )
                            } else {
                                Text("${brief.n}", fontSize = 10.sp, color = skin.sub)
                            }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                brief.title,
                                fontSize = skin.smallSize,
                                fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
                                color = if (current) skin.accentDeep else skin.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (brief.stage.isNotBlank()) {
                                Text(brief.stage, fontSize = 10.5.sp, color = skin.sub)
                            }
                        }
                        if (current) {
                            Text("当前", fontSize = 10.5.sp, color = skin.accent)
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
