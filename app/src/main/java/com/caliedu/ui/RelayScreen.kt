package com.caliedu.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caliedu.llm.RelayBroker

/**
 * 手动接力页。
 *
 * 这一页决定 App 能不能零成本用起来：不填 Key，把提示词贴给任何模型，
 * 把 JSON 粘回来就行。所以三件事必须做到位——复制是一键的、预览不遮住提示词、
 * 粘回来的脏内容也算数（围栏、客套话由 JsonRepair 负责）。
 */
@Composable
fun RelayScreen(
    pending: RelayBroker.Pending,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val skin = LocalSkin.current
    val clipboard = LocalClipboardManager.current
    var pasted by remember(pending.id) { mutableStateOf("") }
    var expanded by remember(pending.id) { mutableStateOf(false) }
    var copied by remember(pending.id) { mutableStateOf(false) }

    val cardShape = remember(skin) { RoundedCornerShape(skin.radiusCard) }

    Column(
        Modifier
            .fillMaxSize()
            .background(skin.bg)
            .imePadding()
            .padding(horizontal = 20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.height(6.dp))
            Text(
                "第 1 步",
                fontSize = 11.sp,
                color = skin.accent,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(34.dp)
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "取消", tint = skin.sub, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            pending.label.ifBlank { "这一步" },
            fontSize = skin.stepTitleSize * 0.9f,
            fontWeight = FontWeight.Bold,
            color = skin.text,
        )

        Spacer(Modifier.height(16.dp))

        // 提示词预览：默认三行，点开看全
        Column(
            Modifier
                .fillMaxWidth()
                .background(skin.codeBg, cardShape)
                .border(if (skin.borderWidth > 0.dp) skin.borderWidth else 0.5.dp, skin.codeBorder.takeIf { it.alpha > 0f } ?: skin.line, cardShape)
                .clickable { expanded = !expanded }
                .padding(13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (expanded) "收起" else "看提示词",
                    fontSize = 12.sp,
                    color = skin.accent,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${pending.packet.length} 字",
                    fontSize = 11.sp,
                    color = skin.sub,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = pending.packet,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp,
                lineHeight = 18.sp,
                color = skin.sub,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                modifier = Modifier
                    .heightIn(max = if (expanded) 420.dp else 90.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionChip(
                label = if (copied) "已复制" else "复制提示词",
                icon = Icons.Rounded.ContentCopy,
                primary = true,
            ) {
                clipboard.setText(AnnotatedString(pending.packet))
                copied = true
            }
            ActionChip(label = "粘贴", icon = Icons.Rounded.ContentPaste) {
                val text = clipboard.getText()?.text.orEmpty()
                if (text.isNotBlank()) pasted = text
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("第 2 步 · 把模型吐回来的内容粘在这里", fontSize = 11.sp, color = skin.sub)
        Spacer(Modifier.height(8.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(skin.surface, cardShape)
                .border(if (skin.borderWidth > 0.dp) skin.borderWidth else 0.5.dp, skin.line, cardShape)
                .padding(13.dp)
        ) {
            if (pasted.isEmpty()) {
                Text(
                    "整段贴进来就行，多带了说明文字也没关系。",
                    fontSize = skin.smallSize,
                    lineHeight = 20.sp,
                    color = skin.sub.copy(alpha = 0.8f),
                )
            }
            BasicTextField(
                value = pasted,
                onValueChange = { pasted = it },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = skin.text,
                ),
                cursorBrush = SolidColor(skin.accent),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            )
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onSubmit(pasted) },
            enabled = pasted.isNotBlank(),
            shape = RoundedCornerShape(if (skin.pillChoices) 100.dp else skin.radiusCard),
            colors = ButtonDefaults.buttonColors(
                containerColor = skin.accent,
                contentColor = skin.onAccent,
                disabledContainerColor = skin.codeBg,
                disabledContentColor = skin.sub,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text("收下，继续", fontSize = skin.bodySize)
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun ActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val skin = LocalSkin.current
    Row(
        Modifier
            .background(if (primary) skin.accentSoft else skin.surface, RoundedCornerShape(100.dp))
            .border(0.5.dp, if (primary) skin.accent.copy(alpha = 0.35f) else skin.line, RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (primary) skin.accent else skin.sub, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.5.sp, color = if (primary) skin.accent else skin.sub)
    }
}
