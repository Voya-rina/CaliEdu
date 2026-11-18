package com.caliedu

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    /** 从别的应用分享/划词带进来的文本，交给 Compose 侧灌进输入框。 */
    private var incoming by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        incoming = extract(intent)
        setContent {
            CaliEduApp(
                sharedText = incoming,
                onSharedConsumed = { incoming = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extract(intent)?.let { incoming = it }
    }

    /**
     * 三条入口：别的 App 分享文本、系统划词菜单、外部用 view 打开一段文本。
     * 只取文本，不碰文件流——文件走首页的文件选择器，那里有完整读权限。
     */
    private fun extract(intent: Intent?): String? {
        if (intent == null) return null
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        return text?.takeIf { it.isNotBlank() }?.take(200_000)
    }
}
