package com.voyarina.caliedu.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voyarina.caliedu.UiState
import com.voyarina.caliedu.ReviewItem

/**
 * 复习：把到期的错题一条条摊开，先想，再看答案，然后自评。
 *
 * 刻意不做「再判一次对错」——错题本来就答错过，机器再判一次也只是重复当时的判断。
 * 这里要的是回想：想起来了就往后推一档，没想起来就打回一天后。
 */
@Composable
fun ReviewScreen(
    state: UiState,
    onReveal: () -> Unit,
    onGrade: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val skin = LocalSkin.current
    val total = state.reviewQueue.size

    Column(
        Modifier
            .fillMaxSize()
            .background(skin.bg)
            .padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = skin.accent,
                    modifier = Modifier.size(skin.ui(20f)),
                )
            }
            Spacer(Modifier.width(4.dp))
            Text("复习", fontSize = skin.fs(18f), fontWeight = FontWeight.Bold, color = skin.text)
            Spacer(Modifier.weight(1f))
            if (total > 0) {
                Text(
                    if (state.reviewDone) "$total / $total" else "${state.reviewIdx + 1} / $total",
                    fontSize = skin.fs(12f),
                    color = skin.sub,
                )
            }
        }

        if (total > 0) {
            Spacer(Modifier.height(10.dp))
            ProgressLine(
                ratio = if (state.reviewDone) 1f else (state.reviewIdx + 1f) / total,
                skin = skin,
            )
        }

        if (state.reviewDone) {
            DoneBody(
                total = total,
                known = state.reviewKnown,
                lost = state.reviewLost,
                onBack = onBack,
            )
            return@Column
        }

        val item = state.reviewQueue.getOrNull(state.reviewIdx)
        if (item == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("没有要复习的错题。", fontSize = skin.smallSize, color = skin.sub)
            }
            return@Column
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp, bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.major,
                    fontSize = skin.fs(10f),
                    color = skin.accentDeep,
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(skin.accentSoft)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    item.course,
                    fontSize = skin.fs(11.5f),
                    color = skin.sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "第 ${item.step + 1} 步 · ${item.title.ifBlank { "这一步" }}",
                fontSize = skin.stepTitleSize * 0.86f,
                fontWeight = skin.stepTitleWeight,
                color = skin.text,
            )
            Spacer(Modifier.height(10.dp))
            Markdown(item.prompt.ifBlank { item.title })

            if (state.reviewRevealed) {
                Spacer(Modifier.height(20.dp))
                AnswerBlock(item, skin)
            }
        }

        // 底部操作条：先看答案，再自评。两个动作不会同时出现。
        Box(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp)
        ) {
            if (!state.reviewRevealed) {
                BigButton(
                    label = "看答案",
                    background = skin.accent,
                    foreground = skin.onAccent,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onReveal,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton(
                        label = "还是不会",
                        background = skin.wrongSoft,
                        foreground = skin.wrong,
                        modifier = Modifier.weight(1f),
                        onClick = { onGrade(false) },
                    )
                    BigButton(
                        label = "记住了",
                        background = skin.accent,
                        foreground = skin.onAccent,
                        modifier = Modifier.weight(1f),
                        onClick = { onGrade(true) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressLine(ratio: Float, skin: Skin) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(skin.line, CircleShape)
    ) {
        Box(
            Modifier
                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                .height(3.dp)
                .background(skin.accent, CircleShape)
        )
    }
}

@Composable
private fun AnswerBlock(item: ReviewItem, skin: Skin) {
    val shape = RoundedCornerShape(skin.radiusBlock)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(skin.accentSoft.copy(alpha = if (skin.style == UiStyle.MATERIAL) 1f else 0.55f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text("正解", fontSize = skin.fs(10.5f), color = skin.accentDeep)
        Spacer(Modifier.height(5.dp))
        if (item.answer.isBlank()) {
            Text("这一步没有留下标准答案。", fontSize = skin.smallSize, color = skin.sub)
        } else {
            Markdown(item.answer)
        }
        if (item.input.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text("你当时答", fontSize = skin.fs(10.5f), color = skin.wrong)
            Spacer(Modifier.height(4.dp))
            Text(
                item.input,
                fontSize = skin.smallSize,
                lineHeight = skin.fs(21f),
                color = skin.wrong,
            )
        }
    }
}

@Composable
private fun DoneBody(total: Int, known: Int, lost: Int, onBack: () -> Unit) {
    val skin = LocalSkin.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(skin.ui(56f))
                    .clip(CircleShape)
                    .background(skin.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = skin.accent,
                    modifier = Modifier.size(skin.ui(26f)),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("复习完了", fontSize = skin.stepTitleSize, fontWeight = FontWeight.Bold, color = skin.text)
            Spacer(Modifier.height(6.dp))
            Text(
                "复习 $total 条 · 记住 $known · 没记住 $lost",
                fontSize = skin.smallSize,
                color = skin.sub,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (lost == 0) "都记住了，明天不会再来找你。" else "没记住的那几条，明天会再出现。",
                fontSize = skin.fs(11.5f),
                color = skin.sub,
            )
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(skin.accentSoft)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = skin.accent,
                    modifier = Modifier.size(skin.ui(15f)),
                )
                Spacer(Modifier.width(6.dp))
                Text("回记录", fontSize = skin.fs(12.5f), color = skin.accent)
            }
        }
    }
}

@Composable
private fun BigButton(
    label: String,
    background: androidx.compose.ui.graphics.Color,
    foreground: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val skin = LocalSkin.current
    Box(
        modifier
            .height(skin.ui(48f))
            .clip(RoundedCornerShape(if (skin.pillChoices) 100.dp else skin.radiusCard))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = skin.fs(14f), fontWeight = FontWeight.Medium, color = foreground)
    }
}
