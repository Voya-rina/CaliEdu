package com.caliedu.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.serialization.Serializable

/** 资料夹里的一个 PDF。uri 是 SAF 的 document uri，带持久授权。 */
@Serializable
data class PdfItem(
    val name: String = "",
    val uri: String = "",
    val size: Long = 0L,
    val modified: Long = 0L,
)

/** 用户选过的那一个文件夹，以及上次扫出来的 PDF 清单。 */
@Serializable
data class PdfLibrary(
    val treeUri: String = "",
    val folderName: String = "",
    val scannedAt: Long = 0L,
    val items: List<PdfItem> = emptyList(),
) {
    val picked: Boolean get() = treeUri.isNotBlank()

    val totalSize: Long get() = items.sumOf { it.size }
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

                        mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true) ->
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
