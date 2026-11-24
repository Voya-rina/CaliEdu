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
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voyarina.caliedu.core.Category
import com.voyarina.caliedu.data.LessonRecord
import com.voyarina.caliedu.data.PdfItem
import com.voyarina.caliedu.data.PdfLibrary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    onQuiz: (PdfItem) -> Unit,
    onOpenDoc: (LessonRecord) -> Unit,
    onDeleteDoc: (LessonRecord) -> Unit,
    onRemove: (PdfItem) -> Unit,
    onToast: (String) -> Unit,
) {
    val skin = LocalSkin.current
    val context = LocalContext.current
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

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (library.picked) {
                item { FolderRow(library = library, busy = busy, onRescan = onRescan) }
                item { Spacer(Modifier.height(10.dp)) }
            }

            if (docs.isNotEmpty()) {
                item { SectionTitle("讲义", docs.size) }
                items(docs, key = { it.id }) { record ->
                    DocRow(
                        record = record,
                        onOpen = { onOpenDoc(record) },
                        onLongPress = { deleteAsk = record },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            if (library.visibleItems.isNotEmpty()) {
                item { SectionTitle("文件", library.visibleItems.size) }
                items(library.visibleItems, key = { it.uri }) { item ->
                    PdfRow(
                        item = item,
                        busy = busy,
                        removable = library.extraItems.any { it.uri == item.uri },
                        onDoc = { onDoc(item) },
                        onQuiz = { onQuiz(item) },
                        onUse = { onUse(item, 1, 10) },
                        onLongPress = { if (library.extraItems.any { it.uri == item.uri }) removeAsk = item },
                    ) {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(item.uri), "text/plain")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            )
                        }.onFailure { onToast("没有能打开它的应用") }
                    }
                }
            }

            if (!library.hasAny && docs.isEmpty() && !library.picked) {
                item { EmptyLibrary(busy) }
            } else if (library.visibleItems.isEmpty() && library.picked && !busy) {
                item {
                    Text(
                        "这个文件夹里没有 md / txt / tex。",
                        fontSize = skin.fs(11.5f),
                        color = skin.sub,
                        modifier = Modifier.padding(start = 2.dp, top = 6.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    removeAsk?.let { item ->
        ConfirmDialog(
            title = "不再列在资料夹里",
            message = "《${item.name}》只是从列表里移走，磁盘上的文件不动。想回来再收一次就行。",
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
            title = "删除这份讲义",
            message = "《${record.headline}》的阅读进度会一起删掉。磁盘上的原文件不动，想再看可以重新导入。",
            confirmText = "删除",
            onConfirm = {
                deleteAsk = null
                onDeleteDoc(record)
            },
            onDismiss = { deleteAsk = null },
        )
    }
}

@Composable
private fun EmptyLibrary(busy: Boolean) {
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
            if (busy) "正在读…" else "收一份 md，或者选一个课件文件夹",
            fontSize = skin.fs(11.5f),
            color = skin.sub,
        )
    }
}

@Composable
private fun FolderRow(library: PdfLibrary, busy: Boolean, onRescan: () -> Unit) {
    val skin = LocalSkin.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 6.dp),
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
                else -> "${library.visibleItems.size} 个 · 重扫"
            },
            fontSize = skin.fs(11f),
            color = if (busy) skin.sub else skin.accentDeep,
            modifier = Modifier
                .clickable(enabled = !busy, onClick = onRescan)
                .padding(vertical = 4.dp, horizontal = 2.dp),
        )
    }
}

@Composable
private fun SectionTitle(text: String, count: Int) {
    val skin = LocalSkin.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, top = 4.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = skin.fs(11.5f), letterSpacing = 0.6.sp, color = skin.sub)
        Spacer(Modifier.width(6.dp))
        Text("$count", fontSize = skin.fs(11f), color = skin.line)
    }
}

/** 讲义卡：和记录卡同一套骨架，只是单位是「节」、没有错题。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocRow(record: LessonRecord, onOpen: () -> Unit, onLongPress: () -> Unit) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(skin.radiusCard)
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
            Spacer(Modifier.width(8.dp))
            Text("${record.total} 节", fontSize = skin.fs(10.5f), color = skin.sub)
        }
        Spacer(Modifier.height(5.dp))
        Text(
            buildString {
                append(if (record.finished) "已读完" else "第 ${record.cursor + 1} / ${record.total} 节")
                append(" · ").append(agoText(record.updatedAt))
                append(" · ").append(Category.majorOf(record.plan))
            },
            fontSize = skin.fs(11.5f),
            color = skin.sub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(9.dp))
        Box(
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PdfRow(
    item: PdfItem,
    busy: Boolean,
    removable: Boolean,
    onDoc: () -> Unit,
    onQuiz: () -> Unit,
    onUse: () -> Unit,
    onLongPress: () -> Unit,
    onClick: () -> Unit,
) {
    val skin = LocalSkin.current
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { if (removable) onLongPress() })
            .padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .background(skin.accentSoft, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.Article,
                contentDescription = null,
                tint = skin.accent,
                modifier = Modifier.size(skin.ui(16f)),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                fontSize = skin.smallSize,
                color = skin.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${formatSize(item.size)} · ${formatTime(item.modified)}",
                fontSize = skin.fs(10.5f),
                color = skin.sub,
            )
        }
        Spacer(Modifier.width(8.dp))
        when {
            busy -> Unit
            item.isMarkdown -> {
                Pill(label = "讲义", filled = true, enabled = !busy, onClick = onDoc)
                Spacer(Modifier.width(6.dp))
                Pill(label = "出题", filled = false, enabled = !busy, onClick = onQuiz)
            }
            else -> Pill(label = "出题", filled = true, enabled = !busy, onClick = onUse)
        }
    }
}

@Composable
private fun Pill(label: String, filled: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val skin = LocalSkin.current
    Text(
        label,
        fontSize = skin.fs(11.5f),
        color = if (filled) skin.onAccent else skin.accentDeep,
        modifier = Modifier
            .background(if (filled) skin.accent else skin.accentSoft, RoundedCornerShape(100.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp),
    )
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
