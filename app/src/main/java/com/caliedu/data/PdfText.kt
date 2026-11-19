package com.caliedu.data

import android.content.Context
import android.net.Uri
import com.caliedu.core.runner.PyRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF 抽文字。
 *
 * SAF 的 content:// 交给 Python 打不开，所以先落到缓存再读。
 * 只取前十几页——教材整本几千页，抽完既塞不进上下文也没人会看。
 */
object PdfText {

    /** 不叫 Result，免得和 kotlin.Result 在 runCatching 里搅在一起。 */
    data class Outcome(val ok: Boolean, val text: String)

    suspend fun extract(
        context: Context,
        uri: Uri,
        startPage: Int = 1,
        pages: Int = 10,
    ): Outcome =
        withContext(Dispatchers.IO) {
            val tmp = File(context.cacheDir, "import-${System.currentTimeMillis()}.pdf")
            val written = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
            }.getOrNull()
            if (written == null) {
                tmp.delete()
                return@withContext Outcome(false, "读不了这个文件。")
            }
            val reply = runCatching {
                PyRunner.call(
                    context,
                    "pdf_text",
                    "extract",
                    tmp.absolutePath,
                    startPage.coerceAtLeast(1),
                    pages.coerceAtLeast(1),
                    timeoutMs = 60_000,
                )
            }.getOrNull()
            tmp.delete()
            if (reply == null) return@withContext Outcome(false, "抽取失败，执行器没有响应。")
            Outcome(reply.ok, reply.output.trim())
        }
}
