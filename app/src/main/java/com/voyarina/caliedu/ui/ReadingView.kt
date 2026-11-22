package com.voyarina.caliedu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voyarina.caliedu.UiState

/**
 * 通读模式：不再一步一步卡关，把已生成的讲义按顺序连成一篇。
 * 没写的步骤留一个入口，点一下就补写——而不是整篇重来。
 */
@Composable
fun ReadingView(
    state: UiState,
    onGenerate: (Int) -> Unit,
    onFocusStep: (Int) -> Unit,
) {
    val skin = LocalSkin.current
    val plan = state.plan ?: return

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            if (plan.knowledge.isNotEmpty()) {
                Text(
                    plan.knowledge.joinToString("、"),
                    fontSize = skin.fs(11.5f),
                    color = skin.sub,
                )
                Spacer(Modifier.height(14.dp))
            }
        }

        itemsIndexed(plan.steps) { index, brief ->
            val content = state.contents[brief.n]
            Column(Modifier.padding(top = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .background(skin.accentSoft, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            brief.n.toString(),
                            fontSize = skin.fs(11f),
                            fontWeight = FontWeight.Bold,
                            color = skin.accent,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        brief.title,
                        fontSize = skin.stepTitleSize * 0.85f,
                        fontWeight = skin.stepTitleWeight,
                        color = if (skin.style == UiStyle.PRINT) skin.accentDeep else skin.text,
                        modifier = Modifier.weight(1f),
                    )
                    if (content != null) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clickable { onFocusStep(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = "回到这一步做题",
                                tint = skin.line,
                                modifier = Modifier.size(skin.ui(15f)),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                if (content == null) {
                    MissingBlock(
                        waiting = state.waitingStep == brief.n,
                        enabled = !state.offline,
                        onClick = { onGenerate(index) },
                    )
                } else {
                    // 通读模式把 deep 块也放出来——想深看的时候正好在这儿。
                    Markdown(dropDuplicateTitle(content.blocks.joinToString("\n\n") { it.md }, brief.title))
                    if (content.summary.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(skin.amberSoft, RoundedCornerShape(skin.radiusChoice))
                                .padding(horizontal = 13.dp, vertical = 11.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            content.summary.forEach { line ->
                                Text(line, fontSize = skin.fs(12.5f), lineHeight = skin.fs(20f), color = skin.text)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(skin.line.copy(alpha = 0.5f))
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        item { Spacer(Modifier.height(50.dp)) }
    }
}

@Composable
private fun MissingBlock(waiting: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(skin.radiusCard)
    Row(
        Modifier
            .fillMaxWidth()
            .background(skin.codeBg.copy(alpha = 0.55f), shape)
            .border(0.5.dp, skin.line, shape)
            .clickable(enabled = enabled && !waiting, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (waiting) {
            CircularProgressIndicator(
                modifier = Modifier.size(skin.ui(15f)),
                strokeWidth = 1.6.dp,
                color = skin.accent,
            )
        } else {
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = null,
                tint = if (enabled) skin.accent else skin.sub,
                modifier = Modifier.size(skin.ui(15f)),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            when {
                waiting -> "正在写…"
                enabled -> "写这一步"
                else -> "示例课没有这一步的正文"
            },
            fontSize = skin.fs(12.5f),
            color = if (enabled) skin.accent else skin.sub,
        )
    }
}
