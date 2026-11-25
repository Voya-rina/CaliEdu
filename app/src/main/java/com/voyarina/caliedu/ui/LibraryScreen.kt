package com.voyarina.caliedu.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voyarina.caliedu.QuizMode
import com.voyarina.caliedu.core.Category
import com.voyarina.caliedu.data.LessonRecord
import com.voyarina.caliedu.data.PdfItem
import com.voyarina.caliedu.data.PdfLibrary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 一份资料。同一个文件既能当「文件」列出来，也能当「讲义」列出来——
 * 分成两块就会同一个东西出现两次，还各自带着不同的时间。
 * 这里合成一条：文件是本体，读到哪儿是它身上的进度。
 */
private data class Entry(val item: PdfItem?, val doc: LessonRecord?) {
    /**
     * 列表上显示的名字。
     *
     * 有文件就用文件名，不替换成讲义里的第一个大标题：资料页是用来找文件的，
     * 认的是自己起的名字（`03_numpy.md`），不是标题里那句长句子。
     */
    val title: String
        get() = item?.name?.takeIf { it.isNotBlank() }
            ?: doc?.headline?.takeIf { it.isNotBlank() }
            ?: "未命名"

    /** 能不能出题：md 有问答，txt/tex 也能整篇拿去出题。 */
    val canQuiz: Boolean get() = item?.isText == true

    /** 能不能「读」：md 才有分节，txt 读成一节没意义。 */
    val canRead: Boolean get() = item?.isMarkdown == true
}

@Composable
fun LibraryScreen(
    library: PdfLibrary,
    docs: List<LessonRecord>,
    busy: Boolean,
    onImport: (Uri) -> Unit,
    onAddFiles: (List<Uri>) -> Unit,
    onRescan: () -> Unit,
    onUse: (PdfItem, Int, Int) -> Unit,
    onDoc: (PdfItem) -> Unit,
    onQuiz: (PdfItem, QuizMode) -> Unit,
    onOpenDoc: (LessonRecord) -> Unit,
    onDeleteDoc: (LessonRecord) -> Unit,
    onRemove: (PdfItem) -> Unit,
    onToast: (String) -> Unit,
) {
    val skin = LocalSkin.current
    val context = LocalContext.current
    var sheetFor by remember { mutableStateOf<Entry?>(null) }
    var quizFor by remember { mutableStateOf<PdfItem?>(null) }
    var removeAsk by remember { mutableStateOf<PdfItem?>(null) }
    var deleteAsk by remember { mutableStateOf<LessonRecord?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onImport(uri)
    }
    // 系统对 md 的 mime 报得很乱（text/markdown、text/plain、octet-stream 都有），
    // 按类型过滤会把该有的文件挡在外面，所以放开选，选完按扩展名分。
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onAddFiles(uris)
    }

    val entries = remember(library, docs) { mergeEntries(library, docs) }

    Column(
        Modifier
            .fillMaxSize()
            .background(skin.bg)
            .padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Text("资料", fontSize = skin.fs(20f), fontWeight = FontWeight.Bold, color = skin.text)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionLabel(
                label = if (busy) "读取中…" else "收文件",
                primary = true,
                enabled = !busy,
            ) { filePicker.launch(arrayOf("*/*")) }
            ActionLabel(
                label = if (library.picked) "换文件夹" else "选文件夹",
                primary = false,
                enabled = !busy,
            ) { folderPicker.launch(null) }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (library.picked) {
                item { FolderRow(library = library, count = entries.count { it.item != null }, busy = busy, onRescan = onRescan) }
            }

            items(entries, key = { it.doc?.id ?: it.item?.uri.orEmpty() }) { entry ->
                EntryCard(
                    entry = entry,
                    busy = busy,
                    onClick = {
                        when {
                            entry.doc != null -> onOpenDoc(entry.doc)
                            entry.canRead -> entry.item?.let(onDoc)
                            entry.item != null -> onUse(entry.item, 1, 10)
                        }
                    },
                    onMore = { sheetFor = entry },
                )
            }

            if (entries.isEmpty() && !busy) {
                item { EmptyLibrary(picked = library.picked) }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    sheetFor?.let { entry ->
        val fileUri = entry.item?.uri.orEmpty()
        EntrySheet(
            entry = entry,
            removable = fileUri.isNotBlank(),
            onRead = {
                sheetFor = null
                entry.item?.let(onDoc)
            },
            onQuiz = {
                sheetFor = null
                quizFor = entry.item
            },
            onOpen = {
                sheetFor = null
                entry.doc?.let(onOpenDoc)
            },
            onExternal = {
                sheetFor = null
                entry.item?.let { item ->
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(item.uri), "text/plain")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        )
                    }.onFailure { onToast("没有能打开它的应用") }
                }
            },
            onRemove = {
                sheetFor = null
                entry.item?.let { removeAsk = it }
            },
            onDeleteDoc = {
                sheetFor = null
                entry.doc?.let { deleteAsk = it }
            },
            onDismiss = { sheetFor = null },
        )
    }

    quizFor?.let { item ->
        QuizModeSheet(
            name = item.name,
            onPick = { mode ->
                quizFor = null
                onQuiz(item, mode)
            },
            onDismiss = { quizFor = null },
        )
    }

    removeAsk?.let { item ->
        ConfirmDialog(
            title = "不再列在资料里",
            message = "《${item.name}》只是从列表里移走，磁盘上的文件不动。换文件夹或重新收一次就能找回来。",
            confirmText = "移走",
            onConfirm = {
                removeAsk = null
                onRemove(item)
            },
            onDismiss = { removeAsk = null },
        )
    }
    deleteAsk?.let { record ->
        ConfirmDialog(
            title = "删除这份讲义的进度",
            message = "《${record.headline}》读到第几节会一起删掉，文件本身还在，可以重新导入。",
            confirmText = "删除",
            onConfirm = {
                deleteAsk = null
                onDeleteDoc(record)
            },
            onDismiss = { deleteAsk = null },
        )
    }
}

/**
 * 文件和讲义按「同一个文件」合并。
 *
 * 新记录带 uri，直接对上；老记录没存 uri，退一步按文件名猜——
 * 猜不中也只是多出一条，不会把两份不同的东西并成一条。
 */
private fun mergeEntries(library: PdfLibrary, docs: List<LessonRecord>): List<Entry> {
    val used = mutableSetOf<String>()
    val fromFiles = library.visibleItems.map { item ->
        val doc = docs.firstOrNull { it.uri == item.uri && it.uri.isNotBlank() }
            ?: docs.firstOrNull {
                it.uri.isBlank() && it.headline.equals(item.name.substringBeforeLast('.'), ignoreCase = true)
            }
        doc?.let { used += it.id }
        Entry(item, doc)
    }
    val orphans = docs.filterNot { it.id in used }.map { Entry(null, it) }
    return (fromFiles + orphans).sortedWith(
        // 读过的排前面：资料页的常用动作是「接着读」，不是「浏览文件夹」。
        compareBy<Entry> { if (it.doc == null) 1 else 0 }
            .thenByDescending { it.doc?.updatedAt ?: 0L }
            .thenBy { it.item?.name ?: it.doc?.headline.orEmpty() }
    )
}

@Composable
private fun EmptyLibrary(picked: Boolean) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(skin.radiusCard)
    Column(
        Modifier
            .fillMaxWidth()
            .background(skin.surface, shape)
            .border(if (skin.borderWidth > 0.dp) skin.borderWidth else 0.5.dp, skin.line, shape)
            .padding(vertical = 26.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.FolderOpen,
            contentDescription = null,
            tint = skin.line,
            modifier = Modifier.size(skin.ui(30f)),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (picked) "这个文件夹里没有 md / txt / tex。" else "收一份 md，或者选一个课件文件夹",
            fontSize = skin.fs(11.5f),
            color = skin.sub,
        )
    }
}

@Composable
private fun FolderRow(library: PdfLibrary, count: Int, busy: Boolean, onRescan: () -> Unit) {
    val skin = LocalSkin.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.FolderOpen,
            contentDescription = null,
            tint = skin.sub,
            modifier = Modifier.size(skin.ui(15f)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            library.folderName,
            fontSize = skin.fs(11.5f),
            color = skin.sub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            when {
                busy -> "扫描中…"
                else -> "$count 个 · 重扫"
            },
            fontSize = skin.fs(11f),
            color = if (busy) skin.sub else skin.accentDeep,
            modifier = Modifier
                .clickable(enabled = !busy, onClick = onRescan)
                .padding(vertical = 4.dp, horizontal = 2.dp),
        )
    }
}

/**
 * 一条资料。读过和没读过的区别只在副标题和那条进度线上——
 * 两种卡片、两套骨架，看着像两份东西。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryCard(
    entry: Entry,
    busy: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(skin.radiusCard)
    val doc = entry.doc
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(skin.surface, shape)
            .border(if (skin.borderWidth > 0.dp) skin.borderWidth else 1.dp, skin.line, shape)
            .combinedClickable(
                onClickLabel = null,
                role = Role.Button,
                onClick = { if (!busy) onClick() },
                onLongClick = { if (!busy) onMore() },
            )
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp)
                    .background(skin.accentSoft, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (doc != null) Icons.Rounded.AutoStories else Icons.AutoMirrored.Rounded.Article,
                    contentDescription = null,
                    tint = skin.accent,
                    modifier = Modifier.size(skin.ui(15f)),
                )
            }
            Spacer(Modifier.width(11.dp))
            Text(
                entry.title,
                fontSize = skin.bodySize,
                fontWeight = FontWeight.Medium,
                color = skin.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(skin.ui(14f)).padding(horizontal = 4.dp),
                    strokeWidth = 1.5.dp,
                    color = skin.accent,
                )
            } else {
                Box(
                    Modifier
                        .size(skin.ui(34f))
                        .clip(CircleShape)
                        .clickable(onClick = onMore),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.MoreHoriz,
                        contentDescription = "更多",
                        tint = skin.sub,
                        modifier = Modifier.size(skin.ui(17f)),
                    )
                }
            }
        }

        Spacer(Modifier.height(5.dp))
        Text(
            text = subtitleOf(entry),
            fontSize = skin.fs(11.5f),
            color = skin.sub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 39.dp, end = 8.dp),
        )

        if (doc != null) {
            Spacer(Modifier.height(9.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 39.dp, end = 8.dp)
                    .height(3.dp)
                    .background(skin.line, CircleShape)
            ) {
                val ratio = doc.ratio
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
}

private fun subtitleOf(entry: Entry): String {
    val doc = entry.doc
    val item = entry.item
    return if (doc != null) {
        buildString {
            append(if (doc.finished) "已读完" else "第 ${doc.cursor + 1} / ${doc.total} 节")
            append(" · ").append(agoText(doc.updatedAt))
            append(" · ").append(Category.majorOf(doc.plan))
        }
    } else {
        buildString {
            append(if (item != null) formatSize(item.size) else "—")
            item?.let { append(" · ").append(formatTime(it.modified)) }
            if (item?.isMarkdown == true) append(" · 点开读")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntrySheet(
    entry: Entry,
    removable: Boolean,
    onRead: () -> Unit,
    onQuiz: () -> Unit,
    onOpen: () -> Unit,
    onExternal: () -> Unit,
    onRemove: () -> Unit,
    onDeleteDoc: () -> Unit,
    onDismiss: () -> Unit,
) {
    val skin = LocalSkin.current
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
            Text(
                entry.title,
                fontSize = skin.bodySize,
                fontWeight = FontWeight.Medium,
                color = skin.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            if (entry.doc != null) {
                SheetAction("继续读", Icons.Rounded.AutoStories, onOpen)
            } else if (entry.canRead) {
                SheetAction("打开读", Icons.Rounded.AutoStories, onRead)
            }
            if (entry.canQuiz) {
                SheetAction("出题自测", Icons.Rounded.AutoAwesome, onQuiz)
            }
            if (entry.item != null) {
                SheetAction("用别的应用打开", Icons.AutoMirrored.Rounded.Article, onExternal)
            }
            if (removable) {
                SheetAction("移出资料列表", Icons.Rounded.DeleteOutline, onRemove)
            }
            if (entry.doc != null) {
                SheetAction("删除阅读进度", Icons.Rounded.DeleteOutline, onDeleteDoc, danger = true)
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}

/** 出题方式让用户自己挑：遮罩不用模型，剩下三种都交给模型。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuizModeSheet(
    name: String,
    onPick: (QuizMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val skin = LocalSkin.current
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
            Text(
                name,
                fontSize = skin.bodySize,
                fontWeight = FontWeight.Medium,
                color = skin.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            SheetAction("遮罩自测 · 不用模型", Icons.Rounded.VisibilityOff, { onPick(QuizMode.MASK) })
            SheetAction("AI · 选择题", Icons.Rounded.CheckCircle, { onPick(QuizMode.CHOICE) })
            SheetAction("AI · 填空题", Icons.Rounded.Edit, { onPick(QuizMode.FILL) })
            SheetAction("AI · 变式题", Icons.Rounded.AutoAwesome, { onPick(QuizMode.VARIANT) })
            Spacer(Modifier.height(26.dp))
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val skin = LocalSkin.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (danger) skin.wrong else skin.accent,
            modifier = Modifier.size(skin.ui(17f)),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            fontSize = skin.smallSize,
            color = if (danger) skin.wrong else skin.text,
        )
    }
}

@Composable
private fun ActionLabel(label: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val skin = LocalSkin.current
    Text(
        label,
        fontSize = if (primary) skin.smallSize else skin.fs(11.5f),
        color = when {
            primary && enabled -> skin.onAccent
            !enabled -> skin.sub
            primary -> skin.sub
            else -> skin.accentDeep
        },
        modifier = Modifier
            .background(
                if (primary && enabled) skin.accent else skin.codeBg,
                RoundedCornerShape(100.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (primary) 18.dp else 12.dp, vertical = if (primary) 9.dp else 6.dp),
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "—"
    bytes >= 1L * 1024 * 1024 -> "%.1f MB".format(bytes / 1024f / 1024f)
    else -> "%.0f KB".format(bytes / 1024f)
}

private fun formatTime(millis: Long): String {
    if (millis <= 0L) return "—"
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
}

private fun agoText(at: Long): String {
    if (at <= 0L) return "未知"
    val diff = System.currentTimeMillis() - at
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff < minute -> "刚刚"
        diff < hour -> "${diff / minute} 分钟前"
        diff < day -> "${diff / hour} 小时前"
        diff < 30 * day -> "${diff / day} 天前"
        else -> formatTime(at)
    }
}
