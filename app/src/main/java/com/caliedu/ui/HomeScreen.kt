package com.caliedu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caliedu.UiState
import com.caliedu.data.LessonRecord
import com.caliedu.data.Stats

@Composable
fun HomeScreen(
    state: UiState,
    onInput: (String) -> Unit,
    onStart: () -> Unit,
    onSample: () -> Unit,
    onResume: (LessonRecord) -> Unit,
    onMistakes: () -> Unit,
) {
    val skin = LocalSkin.current
    val cardShape = remember(skin) { RoundedCornerShape(skin.radiusCard) }
    val ready = state.input.isNotBlank()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val stats = remember(state.records) { Stats.of(state.records) }
    val recent = remember(state.records) {
        state.records.filter { it.total > 0 }.sortedByDescending { it.updatedAt }.take(3)
    }
    val mistakeCount = remember(state.records) { state.records.sumOf { it.mistakes.size } }

    // 一道题的源码动辄几千行，粘进手机很不现实，给一个文件口子。
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val buffer = CharArray(200_000)
                val read = reader.read(buffer)
                if (read > 0) String(buffer, 0, read) else ""
            }
        }.getOrNull()
        if (!text.isNullOrBlank()) onInput(text)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(skin.bg)
            .imePadding()
            .padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "CaliEdu",
                fontSize = 12.sp,
                letterSpacing = 0.8.sp,
                color = skin.sub,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            if (stats.streakDays > 0) {
                Row(
                    Modifier
                        .background(skin.amberSoft, RoundedCornerShape(100.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.LocalFireDepartment,
                        contentDescription = null,
                        tint = skin.amber,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${stats.streakDays}", fontSize = 12.sp, color = skin.amber, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        // 有历史时不再让输入框吃满整屏，否则中间就是一整块空白。
        Box(
            (if (recent.size >= 3) Modifier.height(236.dp) else Modifier.weight(1f))
                .fillMaxWidth()
                .background(skin.surface, cardShape)
                .let { base ->
                    if (skin.borderWidth > 0.dp) base.border(skin.borderWidth, skin.line, cardShape) else base
                }
        ) {
            if (state.input.isEmpty()) {
                // 空态不放废话：三个台阶，说明这 App 干的是「把题拆成台阶」。
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    StepGlyph(skin.sub.copy(alpha = 0.35f))
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "粘一道题，\n或一段代码。",
                        fontSize = skin.bodySize,
                        lineHeight = skin.bodyLine,
                        color = skin.sub.copy(alpha = 0.85f),
                    )
                }
            }

            BasicTextField(
                value = state.input,
                onValueChange = onInput,
                textStyle = TextStyle(fontSize = skin.bodySize, lineHeight = skin.bodyLine, color = skin.text),
                cursorBrush = SolidColor(skin.accent),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 58.dp)
                    .verticalScroll(rememberScrollState()),
            )

            // 左下角：粘贴 / 清空。不占视觉，但省掉一次「长按—粘贴」。
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .background(skin.codeBg, CircleShape)
                        .clickable {
                            val text = clipboard.getText()?.text
                            if (!text.isNullOrBlank()) onInput(text)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.ContentPaste,
                        contentDescription = "从剪贴板粘贴",
                        tint = skin.sub,
                        modifier = Modifier.size(17.dp),
                    )
                }
                if (state.input.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .background(skin.codeBg, RoundedCornerShape(100.dp))
                            .clickable { onInput("") }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text("清空", fontSize = 11.sp, color = skin.sub)
                    }
                }
            }

            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp)
                    .size(48.dp)
                    .background(if (ready) skin.accent else skin.codeBg, CircleShape)
                    .clickable(onClick = onStart),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "拆解",
                    tint = if (ready) skin.onAccent else skin.sub,
                    modifier = Modifier.size(21.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip("示例", Icons.Rounded.AutoAwesome, skin.accent, skin.accentSoft, onSample)
            Spacer(Modifier.width(8.dp))
            Chip("文件", Icons.Rounded.FileOpen, skin.sub, skin.codeBg) { picker.launch(arrayOf("*/*")) }
            if (mistakeCount > 0) {
                Spacer(Modifier.width(8.dp))
                Chip("错题 $mistakeCount", Icons.Rounded.AutoStories, skin.wrong, skin.wrongSoft, onMistakes)
            }
            Spacer(Modifier.weight(1f))
            if (state.input.isNotBlank()) {
                Text("${state.input.length} 字", fontSize = 11.sp, color = skin.sub)
            }
        }

        if (recent.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text(
                "最近",
                fontSize = 11.sp,
                letterSpacing = 0.6.sp,
                color = skin.sub,
                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
            )
            recent.forEachIndexed { index, record ->
                RecentRow(
                    record = record,
                    highlighted = index == 0,
                    onClick = { onResume(record) },
                )
                if (index != recent.lastIndex) Spacer(Modifier.height(2.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun RecentRow(record: LessonRecord, highlighted: Boolean, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(skin.radiusChoice)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (highlighted) skin.accentSoft else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .background(if (highlighted) skin.accent else skin.codeBg, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.AutoStories,
                contentDescription = null,
                tint = if (highlighted) skin.onAccent else skin.sub,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                record.headline,
                fontSize = skin.smallSize,
                color = if (highlighted) skin.accentDeep else skin.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (record.finished) "已走完" else "第 ${record.cursor + 1} / ${record.total} 步",
                fontSize = 10.5.sp,
                color = skin.sub,
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = if (highlighted) skin.accent else skin.line,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 三个渐宽的台阶：低到只是纹理，不抢内容。 */
@Composable
private fun StepGlyph(color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        listOf(26.dp, 42.dp, 58.dp).forEach { w ->
            Box(
                Modifier
                    .width(w)
                    .height(7.dp)
                    .background(color, RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun Chip(
    label: String,
    icon: ImageVector,
    tint: Color,
    background: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .background(background, RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 12.5.sp, color = tint)
    }
}
