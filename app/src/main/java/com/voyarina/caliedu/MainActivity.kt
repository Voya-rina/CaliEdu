package com.voyarina.caliedu

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
        // auto 会按系统深浅色挑状态栏图标；固定 light 的话深色下会先出现一组黑图标。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // 旋转屏幕时 intent 还是那条 ACTION_SEND。不加这个判断的话，
        // 用户改过的输入会被原始分享文本重新盖掉一遍。
        if (savedInstanceState == null) incoming = extract(intent)
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
        return text?.takeIf { it.isNotBlank() }?.take(MAX_SHARED)
    }

    private companion object {
        /** 分享进来的文本最多收这么多；再长的题面注定要被截，先在这里止损。 */
        const val MAX_SHARED = 20_000
    }
}
