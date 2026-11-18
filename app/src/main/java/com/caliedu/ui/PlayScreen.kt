package com.caliedu.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caliedu.core.runner.PyRunner
import com.caliedu.core.runner.RunResult
import kotlinx.coroutines.launch

/**
 * 试跑：把一段 Python 在本机跑一遍，用真实输出替换「推断」。
 * 引擎没装进安装包时，这里老实显示原因，而不是给个点了没反应的按钮。
 */
@Composable
fun PlayScreen(initial: String, title: String, onBack: () -> Unit) {
    val skin = LocalSkin.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val available = remember { PyRunner.available(context) }
    val reason = remember { if (available) "" else PyRunner.reason() }

    var code by remember { mutableStateOf(initial.ifBlank { "print('hello')\n" }) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RunResult?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(skin.bg)
            .imePadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButtonBack(onBack)
            Column(Modifier.weight(1f)) {
                Text(
                    title.ifBlank { "试跑" },
                    fontSize = skin.smallSize,
                    fontWeight = FontWeight.Medium,
                    color = skin.text,
                    maxLines = 1,
                )
                Text(
                    if (available) "本机 Python" else "执行器未启用",
                    fontSize = 11.5.sp,
                    color = skin.sub,
                )
            }
            if (available) {
                Box(
                    Modifier
                        .background(skin.accent, RoundedCornerShape(100.dp))
                        .clickable(enabled = !running) {
                            running = true
                            result = null
                            scope.launch {
                                result = PyRunner.run(context, code)
                                running = false
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (running) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = skin.onAccent,
                            )
                        } else {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = skin.onAccent,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        Spacer(Modifier.width(5.dp))
                        Text(if (running) "跑着" else "运行", fontSize = 12.5.sp, color = skin.onAccent)
                    }
                }
            }
        }

        if (!available) {
            Spacer(Modifier.height(14.dp))
            Text(
                reason + "。这个安装包没有把 Python 编进去，装了带引擎的版本才能在这里真跑。",
                fontSize = skin.smallSize,
                lineHeight = 21.sp,
                color = skin.sub,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .background(skin.codeBg, RoundedCornerShape(skin.radiusCard))
                .border(1.dp, skin.codeBorder.takeIf { it.alpha > 0f } ?: skin.line, RoundedCornerShape(skin.radiusCard))
                .padding(13.dp)
        ) {
            BasicTextField(
                value = code,
                onValueChange = { code = it },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    lineHeight = 20.sp,
                    color = skin.text,
                ),
                cursorBrush = SolidColor(skin.accent),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            )
        }

        Spacer(Modifier.height(10.dp))
        if (result != null) {
            val r = result!!
            val shape = RoundedCornerShape(skin.radiusCard)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(if (r.ok) skin.surface else skin.wrongSoft, shape)
                    .border(0.5.dp, if (r.ok) skin.line else skin.wrong.copy(alpha = 0.3f), shape)
                    .padding(13.dp)
            ) {
                Text(
                    if (r.ok) "输出 · ${r.ms} ms" else "出错 · ${r.ms} ms",
                    fontSize = 11.sp,
                    color = if (r.ok) skin.sub else skin.wrong,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    r.output,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = if (r.ok) skin.text else skin.wrong,
                    modifier = Modifier
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun IconButtonBack(onBack: () -> Unit) {
    val skin = LocalSkin.current
    Box(
        Modifier
            .size(42.dp)
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "返回",
            tint = skin.accent,
            modifier = Modifier.size(21.dp),
        )
    }
}
