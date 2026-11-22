package com.voyarina.caliedu.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.io.File

/**
 * 把导出的 HTML 交给系统的打印面板，用户选「另存为 PDF」就得到一份 PDF。
 *
 * 为什么要单独一个 Activity：打印适配器要绑定一个活着的 WebView，
 * 在 Compose 页面里临时造一个 WebView 会在返回时被回收，打出来是空白页。
 */
class PrintActivity : ComponentActivity() {

    companion object {
        const val EXTRA_HTML = "html_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_HTML)
        if (path.isNullOrBlank() || !File(path).exists()) {
            finish()
            return
        }

        val root = FrameLayout(this)
        val hint = TextView(this).apply {
            text = "正在准备打印…\n返回键可以退出"
            textSize = 14f
            setPadding(48, 96, 48, 0)
            setTextColor(0xFF6B7280.toInt())
            setBackgroundColor(0xFFFFFFFF.toInt())
        }

        val web = WebView(this)
        web.settings.javaScriptEnabled = false
        web.setBackgroundColor(0xFFFFFFFF.toInt())
        web.webViewClient = object : WebViewClient() {
            private var started = false
            override fun onPageFinished(view: WebView, url: String?) {
                if (started) return
                started = true
                hint.visibility = android.view.View.GONE
                runCatching {
                    val manager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val adapter = view.createPrintDocumentAdapter("CaliEdu")
                    manager.print(
                        "CaliEdu 讲义",
                        adapter,
                        PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .build(),
                    )
                }.onFailure {
                    hint.visibility = android.view.View.VISIBLE
                    hint.text = "没能打开打印面板：${it.message}"
                }
            }
        }

        root.addView(
            web,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        root.addView(
            hint,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        setContentView(root)

        // 不能用 file:// 直接加载 cache 目录：部分机型上 WebView 跑在独立进程里，
        // 读不到 App 私有目录，会报 ERR_ACCESS_DENIED 并打出一张错误页。
        // 直接把 HTML 文本喂给 WebView，CSS 都是内联的，不需要任何外部资源。
        val html = runCatching { File(path).readText(Charsets.UTF_8) }.getOrNull()
        if (html == null) {
            hint.text = "导出文件读不出来，重新导出一次试试。"
            return
        }
        web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        setResult(Activity.RESULT_OK)
    }
}
