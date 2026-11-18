package com.caliedu.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object Exporter {

    fun share(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "导出"))
    }

    fun print(context: Context, file: File) {
        context.startActivity(
            Intent(context, PrintActivity::class.java)
                .putExtra(PrintActivity.EXTRA_HTML, file.absolutePath)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun uriOf(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
