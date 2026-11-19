package com.caliedu.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.rounded.PictureAsPdf
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
import androidx.compose.ui.unit.sp
import com.caliedu.data.PdfItem
import com.caliedu.data.PdfLibrary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(
    library: PdfLibrary,
    busy: Boolean,
    onImport: (Uri) -> Unit,
    onRescan: () -> Unit,
    onUse: (PdfItem, Int, Int) -> Unit,
    onToast: (String) -> Unit,
    onBack: () -> Unit,
) {
    val skin = LocalSkin.current
    val context = LocalContext.current
    var pdfPick by remember { mutableStateOf<PdfItem?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onImport(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(skin.bg)
            .padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        PageHeader("资料夹", onBack)
        Text(
            "选一次就记住，子文件夹也会读进来",
            fontSize = skin.fs(11.5f),
            color = skin.sub,
            modifier = Modifier.padding(start = 2.dp, top = 2.dp),
        )

        Spacer(Modifier.height(16.dp))

        if (!library.picked) {
            EmptyLibrary(busy) { picker.launch(null) }
        } else {
            FolderCard(
                library = library,
                busy = busy,
                onRescan = onRescan,
                onChange = { picker.launch(null) },
            )
            Spacer(Modifier.height(14.dp))

            if (library.items.isEmpty() && !busy) {
                Text(
                    "这个文件夹里没有 PDF，也没有 md / txt / tex。换一个，或者确认文件没有藏在压缩包里。",
                    fontSize = skin.smallSize,
                    lineHeight = skin.fs(21f),
                    color = skin.sub,
                )
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(library.items, key = { it.uri }) { item ->
                    PdfRow(item, busy = busy, onUse = {
                        if (item.isText) onUse(item, 1, 10) else pdfPick = item
                    }) {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(
                                        Uri.parse(item.uri),
                                        if (item.isPdf) "application/pdf" else "text/plain",
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            )
                        }.onFailure { onToast(if (item.isPdf) "没有能打开 PDF 的应用" else "没有能打开它的应用") }
                    }
                }
            }
        }
    }

    pdfPick?.let { item ->
        PageRangeDialog(
            fileName = item.name,
            onConfirm = { start, count ->
                pdfPick = null
                onUse(item, start, count)
            },
            onDismiss = { pdfPick = null },
        )
    }
}

@Composable
private fun EmptyLibrary(busy: Boolean, onPick: () -> Unit) {
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
            if (busy) "正在读文件夹…" else "还没有选文件夹",
            fontSize = skin.smallSize,
            color = skin.text,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "选一个装满课件的目录。PDF 能抽文字出题，md / txt / tex 直接读。",
            fontSize = skin.fs(11.5f),
            lineHeight = skin.fs(18f),
            color = skin.sub,
        )
        Spacer(Modifier.height(16.dp))
        PillButton(
            label = if (busy) "读取中…" else "选择文件夹",
            enabled = !busy,
            onClick = onPick,
        )
    }
}

@Composable
private fun FolderCard(library: PdfLibrary, busy: Boolean, onRescan: () -> Unit, onChange: () -> Unit) {
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
                    "${library.items.size} 个文件 · ${formatSize(library.totalSize)} · ${formatTime(library.scannedAt)}",
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction("重新扫描", enabled = !busy, onClick = onRescan)
            SmallAction("换一个文件夹", enabled = !busy, onClick = onChange)
        }
    }
}

@Composable
private fun PdfRow(item: PdfItem, busy: Boolean, onUse: () -> Unit, onClick: () -> Unit) {
    val skin = LocalSkin.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .background(if (item.isText) skin.accentSoft else skin.codeBg, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (item.isText) Icons.AutoMirrored.Rounded.Article else Icons.Rounded.PictureAsPdf,
                contentDescription = null,
                tint = if (item.isText) skin.accent else skin.sub,
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
        if (item.isText || item.isPdf) {
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    busy -> "…"
                    item.isText -> "出题"
                    else -> "抽文字"
                },
                fontSize = skin.fs(11.5f),
                color = skin.onAccent,
                modifier = Modifier
                    .background(if (busy) skin.sub else skin.accent, RoundedCornerShape(100.dp))
                    .clickable(enabled = !busy, onClick = onUse)
                    .padding(horizontal = 11.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun SmallAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    val skin = LocalSkin.current
    Text(
        label,
        fontSize = skin.fs(11.5f),
        color = if (enabled) skin.accentDeep else skin.sub,
        modifier = Modifier
            .background(skin.codeBg, RoundedCornerShape(100.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

@Composable
private fun PillButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val skin = LocalSkin.current
    Text(
        label,
        fontSize = skin.smallSize,
        color = skin.onAccent,
        modifier = Modifier
            .background(if (enabled) skin.accent else skin.sub, RoundedCornerShape(100.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
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
