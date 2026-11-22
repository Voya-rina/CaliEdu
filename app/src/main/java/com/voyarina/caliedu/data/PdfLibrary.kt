package com.voyarina.caliedu.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.serialization.Serializable

/**
 * 抽 PDF 文字层暂时不可用，先把整条入口藏起来：
 * 扫描跳过 PDF、收文件拒收、列表不显示，已经收过的 PDF 也不再列出来。
 * 引擎修好后把这里改成 true 就全恢复。
 */
const val PDF_ENABLED = false

/** 拒收时说清认哪些类型。PDF 关掉时别把人往 PDF 上引。 */
val SUPPORTED_HINT: String get() = if (PDF_ENABLED) "只认 PDF、md、txt、tex" else "只认 md、txt、tex"

/** 资料夹里的一个文件。uri 是 SAF 的 document uri，带持久授权。 */
@Serializable
data class PdfItem(
    val name: String = "",
    val uri: String = "",
    val size: Long = 0L,
    val modified: Long = 0L,
) {
    /** PDF 只能打开看（Android 上没有可靠的文字层抽取）；纯文本这类能直接拿来出题。 */
    val isText: Boolean get() = TEXT_SUFFIXES.any { name.endsWith(it, ignoreCase = true) }

    val isPdf: Boolean get() = name.endsWith(".pdf", ignoreCase = true)

    /** 有分节结构的 md：能当讲义读，也能当素材出题。 */
    val isMarkdown: Boolean get() = name.endsWith(".md", ignoreCase = true) || name.endsWith(".markdown", ignoreCase = true)

    val supported: Boolean get() = isText || (isPdf && PDF_ENABLED)

    companion object {
        val TEXT_SUFFIXES = listOf(".md", ".markdown", ".txt", ".tex", ".text")
    }
}

/**
 * 用户收进资料夹的文件。
 *
 * [items] 是指定文件夹扫描出来的，[extraItems] 是单独挑进来的几个文件。
 * 两者分开存：重新扫描文件夹时不能把单独挑的那些冲掉。
 */
@Serializable
data class PdfLibrary(
    val treeUri: String = "",
    val folderName: String = "",
    val scannedAt: Long = 0L,
    val items: List<PdfItem> = emptyList(),
    val extraItems: List<PdfItem> = emptyList(),
) {
    val picked: Boolean get() = treeUri.isNotBlank()

    /** 列表里真正要显示的：扫描结果在前，单独挑的在后，同一个文件只留一条。 */
    val allItems: List<PdfItem> get() = (items + extraItems).distinctBy { it.uri }

    /** 界面上看得见的那些。PDF 关掉之后，之前收过的也不会再冒出来。 */
    val visibleItems: List<PdfItem> get() = allItems.filter { it.supported }

    val totalSize: Long get() = visibleItems.sumOf { it.size }

    val usableCount: Int get() = visibleItems.count { it.isText }

    val pdfCount: Int get() = visibleItems.count { it.isPdf }

    val hasAny: Boolean get() = visibleItems.isNotEmpty()

    /** 收一个单独挑的文件。已经在文件夹扫描里出现的，不再重复记一份。 */
    fun plus(item: PdfItem): PdfLibrary {
        if (items.any { it.uri == item.uri }) return this
        if (extraItems.any { it.uri == item.uri }) return this
        return copy(extraItems = extraItems + item)
    }

    /** 移除单独挑的文件。只解除引用，不动磁盘上的源文件。 */
    fun minus(item: PdfItem): PdfLibrary =
        copy(extraItems = extraItems.filterNot { it.uri == item.uri })
}

/**
 * 用 SAF 递归扫一个文件夹。不引 DocumentFile，直接查 DocumentsContract，
 * 少一个依赖、也少一次 IPC 往返。
 */
object PdfScanner {

    private const val MAX_DEPTH = 6
    private const val MAX_FILES = 1000

    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    fun scan(context: Context, tree: Uri): PdfLibrary {
        val resolver = context.contentResolver
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
            ?: return PdfLibrary(treeUri = tree.toString())

        val folderName = runCatching {
            resolver.query(DocumentsContract.buildDocumentUriUsingTree(tree, rootId), arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull().orEmpty()

        val items = mutableListOf<PdfItem>()
        walk(resolver, tree, rootId, 0, items)

        return PdfLibrary(
            treeUri = tree.toString(),
            folderName = folderName.ifBlank { "已选文件夹" },
            scannedAt = System.currentTimeMillis(),
            items = items.sortedBy { it.name.lowercase() },
        )
    }

    private fun walk(
        resolver: android.content.ContentResolver,
        tree: Uri,
        documentId: String,
        depth: Int,
        out: MutableList<PdfItem>,
    ) {
        if (depth > MAX_DEPTH || out.size >= MAX_FILES) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)

        runCatching {
            resolver.query(children, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext() && out.size < MAX_FILES) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val mime = cursor.getString(2).orEmpty()
                    val size = cursor.getLong(3)
                    val modified = cursor.getLong(4)

                    when {
                        // 有些提供方把目录的 mime 报成 application/octet-stream，退一步看有没有扩展名
                        mime == DocumentsContract.Document.MIME_TYPE_DIR ->
                            walk(resolver, tree, id, depth + 1, out)

                        (PDF_ENABLED && (mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true))) ||
                            PdfItem.TEXT_SUFFIXES.any { name.endsWith(it, ignoreCase = true) } ->
                            out += PdfItem(
                                name = name,
                                uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                                size = size,
                                modified = modified,
                            )
                    }
                }
            }
        }
    }
}
