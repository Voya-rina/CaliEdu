package com.caliedu.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caliedu.data.LessonRecord
import com.caliedu.data.Mistake
import com.caliedu.data.Stats
import com.caliedu.data.StudyStats

private data class MistakeRow(val record: LessonRecord, val mistake: Mistake)

@Composable
fun RecordsScreen(
    records: List<LessonRecord>,
    tab: Int,
    onTab: (Int) -> Unit,
    onOpen: (LessonRecord) -> Unit,
    onOpenMistake: (LessonRecord, Int) -> Unit,
    onDelete: (String) -> Unit,
    onStart: () -> Unit,
    onClear: () -> Unit,
) {
    val skin = LocalSkin.current
    val stats = remember(records) { Stats.of(records) }
    val mistakes = remember(records) {
        records.flatMap { record -> record.mistakes.map { MistakeRow(record, it) } }
            .sortedByDescending { it.mistake.at }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(skin.bg)
            .padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("记录", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = skin.text)
            Spacer(Modifier.weight(1f))
            if (records.isNotEmpty()) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clickable(onClick = onClear),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("清空", fontSize = 12.sp, color = skin.sub)
                }
            }
        }

        if (records.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            StatsStrip(stats)
            if (stats.weak.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("弱项", fontSize = 10.5.sp, color = skin.sub, modifier = Modifier.padding(top = 4.dp))
                    stats.weak.forEach { point ->
                        Box(
                            Modifier
                                .background(skin.wrongSoft, RoundedCornerShape(100.dp))
                                .padding(horizontal = 9.dp, vertical = 3.dp)
                        ) {
                            Text("${point.name} ×${point.wrongs}", fontSize = 10.5.sp, color = skin.wrong)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Tabs(
                left = "课程 ${records.size}",
                right = "错题 ${mistakes.size}",
                selected = tab,
                onSelect = onTab,
            )
            Spacer(Modifier.height(12.dp))
        }

        when {
            records.isEmpty() -> Box(Modifier.weight(1f)) { EmptyRecords(onStart) }

            tab == 1 && mistakes.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("还没有错题。答错会自动收在这里。", fontSize = skin.smallSize, color = skin.sub)
            }

            tab == 1 -> LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(mistakes.size) { index ->
                    val row = mistakes[index]
                    MistakeCard(
                        row = row,
                        onClick = { onOpenMistake(row.record, row.mistake.step) },
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }

            else -> LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(records, key = { it.id }) { record ->
                    RecordCard(
                        record = record,
                        onOpen = { onOpen(record) },
                        onDelete = { onDelete(record.id) },
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun StatsStrip(stats: StudyStats) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(skin.radiusCard)
    Row(
        Modifier
            .fillMaxWidth()
            .background(skin.surface, shape)
            .let { base -> if (skin.borderWidth > 0.dp) base.border(skin.borderWidth, skin.line, shape) else base }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Number("${stats.lessons}", "门课", skin)
        Number("${stats.solved}", "答对", skin)
        Number(if (stats.solved + stats.wrongs == 0) "—" else "${(stats.accuracy * 100).toInt()}%", "正确率", skin)
        Number("${stats.streakDays}", "连续天", skin)
    }
}

@Composable
private fun Number(value: String, label: String, skin: Skin) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = skin.text)
        Text(label, fontSize = 10.5.sp, color = skin.sub)
    }
}

@Composable
private fun Tabs(left: String, right: String, selected: Int, onSelect: (Int) -> Unit) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(100.dp)
    Row(
        Modifier
            .background(skin.codeBg, shape)
            .padding(3.dp)
    ) {
        listOf(left, right).forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier
                    .background(if (active) skin.surface else androidx.compose.ui.graphics.Color.Transparent, shape)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    color = if (active) skin.text else skin.sub,
                )
            }
        }
    }
}

@Composable
private fun EmptyRecords(onStart: () -> Unit) {
    val skin = LocalSkin.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.AutoStories,
                contentDescription = null,
                tint = skin.line,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text("还没有记录", fontSize = skin.smallSize, color = skin.sub)
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .background(skin.accentSoft, RoundedCornerShape(100.dp))
                    .clickable(onClick = onStart)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = skin.accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("开始一道", fontSize = 12.5.sp, color = skin.accent)
            }
        }
    }
}

@Composable
private fun RecordCard(record: LessonRecord, onOpen: () -> Unit, onDelete: () -> Unit) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(skin.radiusCard)
    Column(
        Modifier
            .fillMaxWidth()
            .background(skin.surface, shape)
            .border(if (skin.borderWidth > 0.dp) skin.borderWidth else 0.5.dp, skin.line, shape)
            .clickable(onClick = onOpen)
            .padding(horizontal = 15.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                record.headline,
                fontSize = skin.bodySize,
                fontWeight = FontWeight.Medium,
                color = skin.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(26.dp)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "删除", tint = skin.line, modifier = Modifier.size(14.dp))
            }
        }

        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    record.finished -> "已走完"
                    record.total == 0 -> "空"
                    else -> "第 ${record.cursor + 1} / ${record.total} 步"
                },
                fontSize = 11.5.sp,
                color = skin.sub,
            )
            Dot(skin)
            Text(ago(record.updatedAt), fontSize = 11.5.sp, color = skin.sub)
            if (record.mistakes.isNotEmpty()) {
                Dot(skin)
                Text("错 ${record.mistakes.size}", fontSize = 11.5.sp, color = skin.wrong)
            }
            if (record.offline) {
                Dot(skin)
                Text("示例", fontSize = 10.5.sp, color = skin.accent)
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(skin.line, CircleShape)
        ) {
            val ratio = if (record.finished) 1f else record.ratio
            if (ratio > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(ratio)
                        .fillMaxHeight()
                        .background(skin.accent, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun Dot(skin: Skin) {
    Spacer(Modifier.width(7.dp))
    Text("·", fontSize = 11.5.sp, color = skin.line)
    Spacer(Modifier.width(7.dp))
}

@Composable
private fun MistakeCard(row: MistakeRow, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(skin.radiusCard)
    Column(
        Modifier
            .fillMaxWidth()
            .background(skin.surface, shape)
            .border(0.5.dp, skin.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            row.record.headline,
            fontSize = 11.sp,
            color = skin.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            "第 ${row.mistake.step + 1} 步 · ${row.mistake.title.ifBlank { "未命名" }}",
            fontSize = skin.smallSize,
            fontWeight = FontWeight.Medium,
            color = skin.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (row.mistake.prompt.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                row.mistake.prompt,
                fontSize = 11.5.sp,
                lineHeight = 18.sp,
                color = skin.sub,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = inlineMarkdown("你答：${row.mistake.input.ifBlank { "（空）" }}", skin),
            fontSize = 11.5.sp,
            lineHeight = 18.sp,
            color = skin.wrong,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (row.mistake.answer.isNotBlank()) {
            Text(
                text = inlineMarkdown("正解：${row.mistake.answer}", skin),
                fontSize = 11.5.sp,
                lineHeight = 18.sp,
                color = skin.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(ago(row.mistake.at), fontSize = 10.5.sp, color = skin.sub)
    }
}

private fun ago(at: Long): String {
    if (at <= 0) return "未知"
    val diff = System.currentTimeMillis() - at
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff < minute -> "刚刚"
        diff < hour -> "${diff / minute} 分钟前"
        diff < day -> "${diff / hour} 小时前"
        diff < 30 * day -> "${diff / day} 天前"
        else -> {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = at }
            "%d-%02d-%02d".format(
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH) + 1,
                cal.get(java.util.Calendar.DAY_OF_MONTH),
            )
        }
    }
}

@Composable
internal fun PageHeader(title: String, onBack: () -> Unit) {
    val skin = LocalSkin.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(38.dp)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = skin.accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = skin.text)
    }
}
