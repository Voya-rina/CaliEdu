package com.caliedu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 二级确认。删掉的东西找不回来，所以宁可多一次点击。
 * 危险动作用红，普通动作用主色——颜色本身就是那句"想清楚"。
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "删除",
    danger: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val skin = LocalSkin.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = skin.surface,
        titleContentColor = skin.text,
        textContentColor = skin.sub,
        shape = RoundedCornerShape(skin.radiusCard),
        title = {
            Text(title, fontSize = skin.fs(15.5f), fontWeight = FontWeight.Medium, color = skin.text)
        },
        text = {
            Text(message, fontSize = skin.fs(12.5f), lineHeight = skin.fs(19f), color = skin.sub)
        },
        // 两个按钮塞进同一个 Row 手动排：原来「确定」是自绘胶囊、「算了」是 TextButton，
        // 两者的最小高度和内边距不是一套，落不到同一条水平线上。
        confirmButton = {
            DialogActions(
                cancelText = "算了",
                confirmText = confirmText,
                confirmTone = if (danger) skin.wrong else skin.accent,
                onCancel = onDismiss,
                onConfirm = onConfirm,
            )
        },
    )
}

/**
 * 教材整本几百页，全抽一遍既慢又没用——真正要的是某一页上的那道题。
 * 所以先问从哪页起、抽几页。
 */
@Composable
fun PageRangeDialog(
    fileName: String,
    onConfirm: (start: Int, count: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val skin = LocalSkin.current
    var start by remember { mutableStateOf(1) }
    var count by remember { mutableStateOf(10) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = skin.surface,
        shape = RoundedCornerShape(skin.radiusCard),
        title = {
            Column {
                Text("抽哪几页", fontSize = skin.fs(15.5f), fontWeight = FontWeight.Medium, color = skin.text)
                Spacer(Modifier.height(3.dp))
                Text(
                    fileName,
                    fontSize = skin.fs(11f),
                    color = skin.sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Stepper("从第几页起", start) { start = (start + it).coerceIn(1, 2000) }
                Stepper("抽几页", count) { count = (count + it).coerceIn(1, 40) }
                Text(
                    "抽得越多越慢，也越容易被无关内容带偏。",
                    fontSize = skin.fs(10.5f),
                    lineHeight = skin.fs(16f),
                    color = skin.sub,
                )
            }
        },
        confirmButton = {
            DialogActions(
                cancelText = "取消",
                confirmText = "抽取",
                confirmTone = skin.accent,
                onCancel = onDismiss,
                onConfirm = { onConfirm(start, count) },
            )
        },
    )
}

@Composable
private fun DialogActions(
    cancelText: String,
    confirmText: String,
    confirmTone: Color,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val skin = LocalSkin.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DialogButton(cancelText, skin.codeBg, skin.text, onCancel)
        DialogButton(confirmText, confirmTone, skin.onAccent, onConfirm)
    }
}

/** 固定高度 + 居中排版：两个按钮的字号、行高、内边距完全一致，不可能再错行。 */
@Composable
private fun DialogButton(label: String, background: Color, foreground: Color, onClick: () -> Unit) {
    val skin = LocalSkin.current
    Box(
        Modifier
            .height(skin.ui(36f))
            .clip(RoundedCornerShape(100.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = skin.fs(12.5f),
            fontWeight = FontWeight.Medium,
            color = foreground,
            maxLines = 1,
        )
    }
}

@Composable
private fun Stepper(label: String, value: Int, onStep: (Int) -> Unit) {
    val skin = LocalSkin.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = skin.fs(12.5f), color = skin.text, modifier = Modifier.weight(1f))
        StepButton("−") { onStep(-1) }
        Text(
            "$value",
            fontSize = skin.fs(13f),
            color = skin.text,
            modifier = Modifier.width(38.dp),
            textAlign = TextAlign.Center,
        )
        StepButton("+") { onStep(1) }
    }
}

@Composable
private fun StepButton(sign: String, onClick: () -> Unit) {
    val skin = LocalSkin.current
    Box(
        Modifier
            .size(skin.ui(30f))
            .clip(RoundedCornerShape(8.dp))
            .background(skin.codeBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(sign, fontSize = skin.fs(14f), color = skin.accent, textAlign = TextAlign.Center)
    }
}
