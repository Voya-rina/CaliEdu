package com.voyarina.caliedu.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voyarina.caliedu.core.Category
import com.voyarina.caliedu.core.Subject
import com.voyarina.caliedu.core.Track
import com.voyarina.caliedu.data.LessonRecord
import com.voyarina.caliedu.data.Mistake
import com.voyarina.caliedu.data.Stats
import com.voyarina.caliedu.data.StudyStats

private data class MistakeRow(val record: LessonRecord, val mistake: Mistake)

@Composable
fun RecordsScreen(
    records: List<LessonRecord>,
    tab: Int,
    filter: String,
    onTab: (Int) -> Unit,
    onFilter: (String) -> Unit,
    onOpen: (LessonRecord) -> Unit,
    onOpenMistake: (LessonRecord, Int) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteMistake: (String, Mistake) -> Unit,
    onClearMistakes: () -> Unit,
    onRecategorize: (String, String) -> Unit,
    onReview: () -> Unit,
    onSample: (Track) -> Unit,
    onDocSample: () -> Unit,
    onStart: () -> Unit,
    onClear: () -> Unit,
) {
    val skin = LocalSkin.current
    var managing by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmClearMistakes by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<LessonRecord?>(null) }
    var pendingCategory by remember { mutableStateOf<LessonRecord?>(null) }
    var searching by remember { mutableStateOf(false) }
    var sampleOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }

    // 统计里带着「该复习几条」和连续天数，两个都跟当前时间有关。
    // 只按 records 缓存的话，跨天之后这两个数会一直停在旧值。
    val dayBucket = System.currentTimeMillis() / 86_400_000L
    val stats = remember(records, dayBucket) { Stats.of(records) }

    // 一级分类：旧记录没有 tags 也会按题型折进一个大类，筛选栏不会时有时无。
    val categories = remember(records) {
        records.groupingBy { Category.majorOf(it.plan) }.eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }
    }
    // 点卡片上的细分标签也能筛（比如「数列」），此时这一项不在大类列表里，
    // 把它补到最前面并标成选中，否则会出现「筛了但没有任何 chip 亮着」。
    val chips = remember(categories, filter, records) {
        if (filter.isBlank() || categories.any { it.first == filter }) {
            categories
        } else {
            val count = records.count { Category.tagsOf(it.plan).contains(filter) }
            listOf(filter to count) + categories
        }
    }
    val allMistakes = remember(records) {
        records.flatMap { record -> record.mistakes.map { MistakeRow(record, it) } }
            .sortedWith(compareBy<MistakeRow> { it.mistake.fixed }.thenByDescending { it.mistake.at })
    }
    val q = query.trim()
    val shown = remember(records, filter, q) {
        records.filter { record ->
            inCategory(record, filter) && matchesQuery(record, q)
        }
    }
    val mistakes = remember(allMistakes, filter, q) {
        allMistakes.filter { row ->
            inCategory(row.record, filter) &&
                (q.isBlank() ||
                    row.record.headline.contains(q, ignoreCase = true) ||
                    row.mistake.prompt.contains(q, ignoreCase = true) ||
                    row.mistake.title.contains(q, ignoreCase = true))
        }
    }

    LaunchedEffect(searching) { if (searching) runCatching { searchFocus.requestFocus() } }
    // 把最后一条删掉之后，管理和搜索都该退出去：
    // 记录为空时右上角没有图标，搜索框会变成一个关不掉的输入框。
    LaunchedEffect(records.isEmpty()) {
        if (records.isEmpty()) {
            managing = false
            searching = false
            query = ""
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(skin.bg)
            // 搜索框一聚焦键盘就上来，没有它列表下半截会被盖住。
            .imePadding()
            .padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("记录", fontSize = skin.fs(20f), fontWeight = FontWeight.Bold, color = skin.text)
            Spacer(Modifier.weight(1f))
            if (records.isNotEmpty()) {
                if (managing) {
                    // 管理模式下的垃圾桶跟着当前页走：在错题页清错题，别把整门课连根拔掉。
                    HeaderIcon(
                        Icons.Rounded.DeleteOutline,
                        skin.wrong,
                        skin.wrongSoft,
                        if (tab == 1) "清空错题" else "清空记录",
                    ) {
                        if (tab == 1) confirmClearMistakes = true else confirmClear = true
                    }
                    Spacer(Modifier.width(6.dp))
                    HeaderIcon(Icons.Rounded.Check, skin.accent, skin.accentSoft, "退出管理") {
                        managing = false
                    }
                } else {
                    HeaderIcon(Icons.Rounded.AutoAwesome, skin.accent, skin.accentSoft, "示例") {
                        sampleOpen = true
                    }
                    Spacer(Modifier.width(6.dp))
                    HeaderIcon(
                        if (searching) Icons.Rounded.Close else Icons.Rounded.Search,
                        skin.sub,
                        skin.codeBg,
                        if (searching) "收起搜索" else "搜索",
                    ) {
                        searching = !searching
                        if (!searching) query = ""
                    }
                    Spacer(Modifier.width(6.dp))
                    HeaderIcon(Icons.Rounded.Edit, skin.sub, skin.codeBg, "管理") {
                        managing = true
                    }
                }
            }
        }

        if (searching) {
            Spacer(Modifier.height(10.dp))
            SearchField(
                query = query,
                onChange = { query = it },
                onClear = { query = "" },
                focus = searchFocus,
            )
        }

        if (records.isEmpty() && !searching) {
            Spacer(Modifier.height(12.dp))
            SampleEntry { sampleOpen = true }
        }

        if (records.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            StatsStrip(stats)
            if (stats.mastery.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                MasteryBlock(stats)
            }
            val openCount = allMistakes.count { !it.mistake.fixed }
            val reviewCount = if (stats.due > 0) stats.due else openCount
            if (reviewCount > 0) {
                Spacer(Modifier.height(12.dp))
                ReviewBanner(count = reviewCount, due = stats.due > 0, onClick = onReview)
            }
            Spacer(Modifier.height(12.dp))
            CategoryFilter(
                categories = chips,
                selected = filter,
                onSelect = onFilter,
            )
            Spacer(Modifier.height(12.dp))
            Tabs(
                left = "课程 ${shown.size}",
                // 数字和列表里能看到的条数保持一致：订正过的也在列表里，只是排在后面。
                right = "错题 ${mistakes.size}",
                selected = tab,
                onSelect = onTab,
            )
            Spacer(Modifier.height(12.dp))
        }

        when {
            records.isEmpty() -> Box(Modifier.weight(1f)) { EmptyRecords(onStart) }

            tab == 1 && mistakes.isEmpty() -> Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        query.isNotBlank() -> "没有匹配「$query」的错题。"
                        filter.isNotBlank() -> "「$filter」下面没有错题。"
                        else -> "还没有错题。答错会自动收在这里。"
                    },
                    fontSize = skin.smallSize,
                    color = skin.sub,
                )
            }

            tab == 0 && shown.isEmpty() -> Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when {
                            query.isNotBlank() -> "没有匹配「$query」的课。"
                            else -> "「$filter」下面还没有课。"
                        },
                        fontSize = skin.smallSize,
                        color = skin.sub,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "看全部",
                        fontSize = skin.fs(12f),
                        color = skin.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(skin.accentSoft)
                            .clickable {
                                query = ""
                                onFilter("")
                            }
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                    )
                }
            }

            tab == 1 -> LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    count = mistakes.size,
                    key = { index ->
                        val row = mistakes[index]
                        "${row.record.id}-${row.mistake.step}-${row.mistake.at}"
                    },
                ) { index ->
                    val row = mistakes[index]
                    MistakeCard(
                        row = row,
                        onTag = onFilter,
                        showDelete = managing,
                        onDelete = { onDeleteMistake(row.record.id, row.mistake) },
                        onClick = { onOpenMistake(row.record, row.mistake.step) },
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }

            else -> LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shown, key = { it.id }) { record ->
                    RecordCard(
                        record = record,
                        showDelete = managing,
                        onTag = onFilter,
                        onOpen = { onOpen(record) },
                        onLongPress = { pendingCategory = record },
                        onCategory = { pendingCategory = record },
                        onDelete = { pendingDelete = record },
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
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
            title = "清空所有记录",
            message = "课程、错题本、答题统计会一起删掉，删完找不回来。",
            confirmText = "全部清空",
            onConfirm = {
                confirmClear = false
                onClear()
            },
            onDismiss = { confirmClear = false },
        )
    }

    if (confirmClearMistakes) {
        ConfirmDialog(
            title = "清空错题本",
            message = "所有课里的 ${allMistakes.size} 条错题会一起删掉，课程进度和答题数保留。",
            confirmText = "清空错题",
            onConfirm = {
                confirmClearMistakes = false
                onClearMistakes()
            },
            onDismiss = { confirmClearMistakes = false },
        )
    }

    pendingDelete?.let { record ->
        ConfirmDialog(
            title = "删掉这一课",
            message = "《${record.headline}》和它的 ${record.mistakes.size} 条错题会一起消失。",
            onConfirm = {
                val id = record.id
                pendingDelete = null
                onDelete(id)
            },
            onDismiss = { pendingDelete = null },
        )
    }

    pendingCategory?.let { record ->
        CategoryDialog(
            current = Category.majorOf(record.plan),
            onPick = { major ->
                pendingCategory = null
                onRecategorize(record.id, major)
            },
            onDismiss = { pendingCategory = null },
        )
    }
}

@Composable
private fun HeaderIcon(
    icon: ImageVector,
    tint: Color,
    background: Color,
    description: String,
    onClick: () -> Unit,
) {
    val skin = LocalSkin.current
    Box(
        Modifier
            .size(skin.ui(36f))
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(skin.ui(18f)))
    }
}

@Composable
private fun SearchField(
    query: String,
    onChange: (String) -> Unit,
    onClear: () -> Unit,
    focus: FocusRequester,
) {
    val skin = LocalSkin.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(skin.ui(38f))
            .clip(RoundedCornerShape(100.dp))
            .background(skin.codeBg)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = skin.sub, modifier = Modifier.size(skin.ui(15f)))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text("搜课名或题目", fontSize = skin.fs(12.5f), color = skin.sub.copy(alpha = 0.75f))
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = skin.fs(12.5f), color = skin.text),
                cursorBrush = SolidColor(skin.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "清空搜索",
                tint = skin.sub,
                modifier = Modifier
                    .size(skin.ui(15f))
                    .clickable(onClick = onClear),
            )
        }
    }
}

/** 到期的错题：不是催你刷题，是提醒「这一条该回来了」。 */
@Composable
private fun ReviewBanner(count: Int, due: Boolean, onClick: () -> Unit) {
    val skin = LocalSkin.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(skin.radiusChoice))
            .background(skin.amberSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Refresh,
            contentDescription = null,
            tint = skin.amber,
            modifier = Modifier.size(skin.ui(15f)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (due) "该复习 $count 条" else "复习错题 $count 条",
            fontSize = skin.fs(12f),
            color = skin.amber,
        )
        Spacer(Modifier.weight(1f))
        Text("开始", fontSize = skin.fs(12f), color = skin.amber)
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = skin.amber,
            modifier = Modifier.size(skin.ui(15f)),
        )
    }
}

@Composable
private fun CategoryFilter(
    categories: List<Pair<String, Int>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val skin = LocalSkin.current
    if (categories.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TagChip("全部", "", selected.isBlank(), skin) { onSelect("") }
        categories.forEach { (name, count) ->
            TagChip(name, "$count", selected == name, skin) {
                onSelect(if (selected == name) "" else name)
            }
        }
    }
}

@Composable
private fun TagChip(label: String, count: String, active: Boolean, skin: Skin, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (active) skin.accent else skin.codeBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = skin.fs(11f), color = if (active) skin.onAccent else skin.sub)
        if (count.isNotBlank()) {
            Spacer(Modifier.width(4.dp))
            Text(
                count,
                fontSize = skin.fs(10f),
                color = if (active) skin.onAccent.copy(alpha = 0.8f) else skin.sub.copy(alpha = 0.6f),
            )
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
            .clip(shape)
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
        Text(value, fontSize = skin.fs(17f), fontWeight = FontWeight.Bold, color = skin.text)
        Text(label, fontSize = skin.fs(10.5f), color = skin.sub)
    }
}

/**
 * 知识点掌握度。只列最弱的几条——全列出来是报表，不是复习。
 * 分母是该知识点摊到的答题数，答得少的不算，免得一条答对就显示 100%。
 */
@Composable
private fun MasteryBlock(stats: StudyStats) {
    val skin = LocalSkin.current
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("掌握度", fontSize = skin.fs(10.5f), color = skin.sub, modifier = Modifier.padding(start = 2.dp))
        stats.mastery.forEach { point ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    point.name,
                    fontSize = skin.fs(11.5f),
                    color = skin.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(76.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(skin.line)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(point.ratio.coerceIn(0.02f, 1f))
                            .height(5.dp)
                            .background(
                                when {
                                    point.ratio >= 0.75f -> skin.accent
                                    point.ratio >= 0.4f -> skin.amber
                                    else -> skin.wrong
                                },
                                RoundedCornerShape(100.dp),
                            )
                    )
                }
                Text(
                    "${(point.ratio * 100).toInt()}",
                    fontSize = skin.fs(10.5f),
                    color = skin.sub,
                    modifier = Modifier
                        .width(30.dp)
                        .padding(start = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun Tabs(left: String, right: String, selected: Int, onSelect: (Int) -> Unit) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(100.dp)
    Row(
        Modifier
            .clip(shape)
            .background(skin.codeBg)
            .padding(3.dp)
    ) {
        listOf(left, right).forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier
                    .clip(shape)
                    .background(if (active) skin.surface else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    label,
                    fontSize = skin.fs(12f),
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
                modifier = Modifier.size(skin.ui(44f)),
            )
            Spacer(Modifier.height(10.dp))
            Text("还没有记录", fontSize = skin.smallSize, color = skin.sub)
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(skin.accentSoft)
                    .clickable(onClick = onStart)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = skin.accent, modifier = Modifier.size(skin.ui(15f)))
                Spacer(Modifier.width(5.dp))
                Text("开始一道", fontSize = skin.fs(12.5f), color = skin.accent)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun RecordCard(
    record: LessonRecord,
    showDelete: Boolean,
    onTag: (String) -> Unit,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onCategory: () -> Unit,
    onDelete: () -> Unit,
) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(skin.radiusCard)
    val tags = Category.tagsOf(record.plan)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(skin.surface, shape)
            .border(if (skin.borderWidth > 0.dp) skin.borderWidth else 1.dp, skin.line, shape)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
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
            // 管理态才露出动作：平时它们只是误触陷阱。
            if (showDelete) {
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(skin.ui(26f))
                        .clip(CircleShape)
                        .background(skin.codeBg)
                        .clickable(onClick = onCategory),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Sell,
                        contentDescription = "改分类",
                        tint = skin.sub,
                        modifier = Modifier.size(skin.ui(13f)),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(skin.ui(26f))
                        .clip(CircleShape)
                        .background(skin.wrongSoft)
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "删除",
                        tint = skin.wrong,
                        modifier = Modifier.size(skin.ui(13f)),
                    )
                }
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
                fontSize = skin.fs(11.5f),
                color = skin.sub,
            )
            Dot(skin)
            Text(ago(record.updatedAt), fontSize = skin.fs(11.5f), color = skin.sub)
            if (record.mistakes.isNotEmpty()) {
                Dot(skin)
                Text("错 ${record.mistakes.size}", fontSize = skin.fs(11.5f), color = skin.wrong)
            }
            if (record.offline) {
                Dot(skin)
                Text("示例", fontSize = skin.fs(10.5f), color = skin.accent)
            }
        }

        Spacer(Modifier.height(7.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            tags.forEachIndexed { index, tag ->
                Text(
                    tag,
                    fontSize = skin.fs(10f),
                    color = if (index == 0) skin.accentDeep else skin.sub,
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (index == 0) skin.accentSoft else skin.codeBg)
                        .clickable { onTag(tag) }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(skin.line, CircleShape)
        ) {
            val ratio = record.ratio
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
    Text("·", fontSize = skin.fs(11.5f), color = skin.line)
    Spacer(Modifier.width(7.dp))
}

@Composable
private fun MistakeCard(
    row: MistakeRow,
    onTag: (String) -> Unit,
    showDelete: Boolean,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(skin.radiusCard)
    val fixed = row.mistake.fixed
    val major = Category.majorOf(row.record.plan)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (fixed) skin.bg else skin.surface, shape)
            .border(1.dp, skin.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.record.headline,
                fontSize = skin.fs(11f),
                color = if (fixed) skin.sub else skin.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                major,
                fontSize = skin.fs(9.5f),
                color = skin.sub,
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(skin.codeBg)
                    .clickable { onTag(major) }
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
            if (fixed) {
                Spacer(Modifier.width(5.dp))
                Text(
                    "已订正",
                    fontSize = skin.fs(10f),
                    color = skin.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(skin.okSoft)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            "第 ${row.mistake.step + 1} 步 · ${row.mistake.title.ifBlank { "未命名" }}",
            fontSize = skin.smallSize,
            fontWeight = FontWeight.Medium,
            color = if (fixed) skin.sub else skin.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (row.mistake.prompt.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                row.mistake.prompt,
                fontSize = skin.fs(11.5f),
                lineHeight = skin.fs(18f),
                color = skin.sub,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = inlineMarkdown("你答：${row.mistake.input.ifBlank { "（空）" }}", skin),
            fontSize = skin.fs(11.5f),
            lineHeight = skin.fs(18f),
            color = skin.wrong,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (row.mistake.answer.isNotBlank()) {
            Text(
                text = inlineMarkdown("正解：${row.mistake.answer}", skin),
                fontSize = skin.fs(11.5f),
                lineHeight = skin.fs(18f),
                color = skin.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ago(row.mistake.at), fontSize = skin.fs(10.5f), color = skin.sub)
            Spacer(Modifier.weight(1f))
            if (showDelete) {
                Box(
                    Modifier
                        .size(skin.ui(34f))
                        .clip(CircleShape)
                        .background(skin.wrongSoft)
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "删除这条错题",
                        tint = skin.wrong,
                        modifier = Modifier.size(skin.ui(17f)),
                    )
                }
            }
        }
    }
}

/**
 * 改分类。只改标签，不动进度和错题——
 * 分类是给自己整理用的，删掉重来才是破坏性的。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
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

private fun inCategory(record: LessonRecord, filter: String): Boolean =
    filter.isBlank() || Category.tagsOf(record.plan).contains(filter)

private fun matchesQuery(record: LessonRecord, q: String): Boolean =
    q.isBlank() ||
        record.headline.contains(q, ignoreCase = true) ||
        record.oneLine.contains(q, ignoreCase = true)

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
                modifier = Modifier.size(skin.ui(22f)),
            )
        }
        Text(title, fontSize = skin.fs(18f), fontWeight = FontWeight.Bold, color = skin.text)
    }
}
