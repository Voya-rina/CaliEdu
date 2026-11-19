package com.caliedu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import android.provider.OpenableColumns
import com.caliedu.UiState
import com.caliedu.core.Category
import com.caliedu.core.Track
import com.caliedu.core.TrackDetector
import com.caliedu.data.LessonRecord
import com.caliedu.data.Review
import com.caliedu.data.Stats

@Composable
fun HomeScreen(
    state: UiState,
    onInput: (String) -> Unit,
    onStart: () -> Unit,
    onSample: (Track) -> Unit,
    onReview: () -> Unit,
    onDocSample: () -> Unit,
    onMarkdown: (String, String) -> Unit,
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
    val mistakeCount = remember(state.records) { state.records.sumOf { r -> r.mistakes.count { !it.fixed } } }
    val dueCount = remember(state.records) { Review.dueCount(state.records) }
    var confirmClear by remember { mutableStateOf(false) }
    var sampleOpen by remember { mutableStateOf(false) }
    val route = remember(state.input) {
        if (state.input.isBlank()) null else TrackDetector.detect(state.input)
    }

    // 一道题的源码动辄几千行，粘进手机很不现实，给一个文件口子。
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull().orEmpty()
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val buffer = CharArray(600_000)
                val read = reader.read(buffer)
                if (read > 0) String(buffer, 0, read) else ""
            }
        }.getOrNull()
        if (text.isNullOrBlank()) return@rememberLauncherForActivityResult
        if (name.endsWith(".md", true) || name.endsWith(".markdown", true)) {
            onMarkdown(text, name.substringBeforeLast('.'))
        } else {
            onInput(text)
        }
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
                fontSize = skin.fs(12f),
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
                        modifier = Modifier.size(skin.ui(13f)),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${stats.streakDays}", fontSize = skin.fs(12f), color = skin.amber, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        // 输入框只占够用的高度：它是入口，不是主界面。题目长的自己在框里滚。
        Box(
            Modifier
                .height(if (recent.isEmpty()) skin.ui(172f) else skin.ui(154f))
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
                    Spacer(Modifier.height(16.dp))
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
                        modifier = Modifier.size(skin.ui(17f)),
                    )
                }
                if (state.input.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .background(skin.codeBg, RoundedCornerShape(100.dp))
                            .clickable { confirmClear = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text("清空", fontSize = skin.fs(11f), color = skin.sub)
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
                    modifier = Modifier.size(skin.ui(21f)),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Chip("文件", Icons.Rounded.FileOpen, skin.sub, skin.codeBg) { picker.launch(arrayOf("*/*")) }
                if (dueCount > 0) {
                    Chip("复习 $dueCount", Icons.Rounded.Refresh, skin.amber, skin.amberSoft, onReview)
                } else if (mistakeCount > 0) {
                    Chip("错题 $mistakeCount", Icons.Rounded.AutoStories, skin.wrong, skin.wrongSoft, onMistakes)
                }
            }
            if (route != null) {
                Spacer(Modifier.width(10.dp))
                // 先说清会走哪条线：认错了题，一整套 prompt 就白花了。
                Text(
                    "${route.label} · ${TrackDetector.why(route)}",
                    fontSize = skin.fs(10.5f),
                    color = skin.sub,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SampleEntry { sampleOpen = true }

        if (recent.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                "最近",
                fontSize = skin.fs(11f),
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

    if (sampleOpen) {
        SampleSheet(
            onPick = { which ->
                sampleOpen = false
                when (which) {
                    0 -> onSample(Track.CODE)
                    1 -> onSample(Track.MATH)
                    else -> onDocSample()
                }
            },
            onDismiss = { sampleOpen = false },
        )
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "清空输入",
            message = "粘进来的题会被删掉。这道题还没开始拆，删了就没了。",
            confirmText = "清空",
            onConfirm = {
                confirmClear = false
                onInput("")
            },
            onDismiss = { confirmClear = false },
        )
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
                modifier = Modifier.size(skin.ui(14f)),
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
                buildString {
                    append(if (record.finished) "已走完" else "第 ${record.cursor + 1} / ${record.total} 步")
                    append(" · ").append(Category.majorOf(record.plan))
                },
                fontSize = skin.fs(10.5f),
                color = skin.sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = if (highlighted) skin.accent else skin.line,
            modifier = Modifier.size(skin.ui(16f)),
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
    val skin = LocalSkin.current
    Row(
        Modifier
            .background(background, RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(skin.ui(14f)))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = skin.fs(12.5f), color = tint)
    }
}

/** 「示例」单独一行：它是入口，不该混在文件、错题那排小胶囊里。 */
@Composable
private fun SampleEntry(onClick: () -> Unit) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(skin.radiusCard)
    Row(
        Modifier
            .fillMaxWidth()
            .background(skin.accentSoft, shape)
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
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SampleSheet(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val skin = LocalSkin.current
    val state = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val rows = listOf(
        Triple(Icons.Rounded.AutoStories, "代码闯关", "鲍鱼年龄预测 · 22 步，每步一道题"),
        Triple(Icons.Rounded.Functions, "数学推导", "数列通项 · 8 步，全是短推导"),
        Triple(Icons.AutoMirrored.Rounded.MenuBook, "整本讲义", "md 教程直接摊开读，不出题"),
    )
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = skin.bg,
        dragHandle = {
            Box(Modifier.padding(vertical = 10.dp)) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .background(skin.line, RoundedCornerShape(2.dp))
                )
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("示例", fontSize = skin.stepTitleSize * 0.9f, fontWeight = FontWeight.Bold, color = skin.text)
            Spacer(Modifier.height(10.dp))
            rows.forEachIndexed { index, (icon, title, note) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(index) }
                        .padding(vertical = 12.dp),
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
                        Text(title, fontSize = skin.smallSize, color = skin.text)
                        Text(note, fontSize = skin.fs(11f), color = skin.sub)
                    }
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = skin.line,
                        modifier = Modifier.size(skin.ui(16f)),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
