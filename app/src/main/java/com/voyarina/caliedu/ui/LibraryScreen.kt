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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voyarina.caliedu.data.PdfItem
import com.voyarina.caliedu.data.PdfLibrary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(
    library: PdfLibrary,
    busy: Boolean,
    onImport: (Uri) -> Unit,
    onAddFiles: (List<Uri>) -> Unit,
    onRescan: () -> Unit,
    onUse: (PdfItem, Int, Int) -> Unit,
    onDoc: (PdfItem) -> Unit,
    onRemove: (PdfItem) -> Unit,
    onToast: (String) -> Unit,
) {
    val skin = LocalSkin.current
    val context = LocalContext.current
    var removeAsk by remember { mutableStateOf<PdfItem?>(null) }

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
        Text(
            "文件夹和单个文件都在这里。md 按章切节当讲义读，也能拆成题目。",
            fontSize = skin.fs(11.5f),
            lineHeight = skin.fs(18f),
            color = skin.sub,
            modifier = Modifier.padding(start = 2.dp, top = 4.dp),
        )

        Spacer(Modifier.height(16.dp))

        if (!library.hasAny && !library.picked) {
            EmptyLibrary(busy, onPickFiles = { filePicker.launch(arrayOf("*/*")) }, onPickFolder = { folderPicker.launch(null) })
        } else {
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
            Spacer(Modifier.height(14.dp))

            if (library.picked) {
                FolderCard(library = library, busy = busy, onRescan = onRescan)
                Spacer(Modifier.height(12.dp))
            }

            if (library.visibleItems.isEmpty() && !busy) {
                Text(
                    "这里没有 md / txt / tex。换一个文件夹，或者直接收几个文件。",
                    fontSize = skin.smallSize,
                    lineHeight = skin.fs(21f),
                    color = skin.sub,
                )
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(library.visibleItems, key = { it.uri }) { item ->
                    PdfRow(
                        item = item,
                        busy = busy,
                        removable = library.extraItems.any { it.uri == item.uri },
                        onDoc = { onDoc(item) },
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
                item { Spacer(Modifier.height(12.dp)) }
            }
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
}

@Composable
private fun EmptyLibrary(busy: Boolean, onPickFiles: () -> Unit, onPickFolder: () -> Unit) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(skin.radiusCard)
    Column(
        Modifier
            .fillMaxWidth()
            .background(skin.surface, shape)
            .border(if (skin.borderWidth > 0.dp) skin.borderWidth else 0.5.dp, skin.line, shape)
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.FolderOpen,
            contentDescription = null,
            tint = skin.sub,
            modifier = Modifier.size(skin.ui(40f)),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (busy) "正在读…" else "还没有资料",
            fontSize = skin.smallSize,
            color = skin.text,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "收几个 md / txt 进来，或者直接选一个装满课件的文件夹（子文件夹也会读进来）。",
            fontSize = skin.fs(11.5f),
            lineHeight = skin.fs(18f),
            color = skin.sub,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionLabel("选文件", primary = true, enabled = !busy, onClick = onPickFiles)
            ActionLabel("选文件夹", primary = false, enabled = !busy, onClick = onPickFolder)
        }
    }
}

@Composable
private fun FolderCard(library: PdfLibrary, busy: Boolean, onRescan: () -> Unit) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(skin.radiusCard)
    Column(
        Modifier
            .fillMaxWidth()
            .background(skin.surface, shape)
            .border(if (skin.borderWidth > 0.dp) skin.borderWidth else 0.5.dp, skin.line, shape)
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = skin.accent, modifier = Modifier.size(skin.ui(20f)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    library.folderName,
                    fontSize = skin.smallSize,
                    fontWeight = FontWeight.Medium,
                    color = skin.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${library.visibleItems.size} 个文件 · ${formatSize(library.totalSize)} · ${formatTime(library.scannedAt)}",
                    fontSize = skin.fs(11f),
                    color = skin.sub,
                )
            }
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(skin.ui(16f)),
                    strokeWidth = 1.8.dp,
                    color = skin.accent,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        ActionLabel("重新扫描", primary = false, enabled = !busy, onClick = onRescan)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PdfRow(
    item: PdfItem,
    busy: Boolean,
    removable: Boolean,
    onDoc: () -> Unit,
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
                Pill(label = "出题", filled = false, enabled = !busy, onClick = onUse)
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
