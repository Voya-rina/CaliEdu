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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caliedu.data.ChannelMode
import com.caliedu.data.LlmSettings

private data class Preset(val name: String, val base: String, val model: String)

private val presets = listOf(
    Preset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
    Preset("Kimi", "https://api.moonshot.cn/v1", "kimi-latest"),
    Preset("通义", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
    Preset("本机", "http://127.0.0.1:1234/v1", "local-model"),
)

@Composable
fun ModelScreen(settings: LlmSettings, onSave: (LlmSettings) -> Unit, onBack: () -> Unit) {
    val skin = LocalSkin.current
    var base by remember { mutableStateOf(settings.baseUrl) }
    var model by remember { mutableStateOf(settings.model) }
    var key by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(settings.mode) }
    var grade by remember { mutableStateOf(settings.autoGrade) }
    var runCode by remember { mutableStateOf(settings.runCode) }

    Column(
        Modifier
            .fillMaxSize()
            .background(skin.bg)
            .padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        PageHeader("模型", onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeCard(
                    mode = ChannelMode.DIRECT,
                    selected = mode == ChannelMode.DIRECT,
                    icon = Icons.Rounded.Cloud,
                    onClick = { mode = ChannelMode.DIRECT },
                    modifier = Modifier.weight(1f),
                )
                ModeCard(
                    mode = ChannelMode.RELAY,
                    selected = mode == ChannelMode.RELAY,
                    icon = Icons.Rounded.ContentCopy,
                    onClick = { mode = ChannelMode.RELAY },
                    modifier = Modifier.weight(1f),
                )
            }

            if (mode == ChannelMode.DIRECT) {
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { preset ->
                        Box(
                            Modifier
                                .background(skin.codeBg, RoundedCornerShape(6.dp))
                                .border(if (skin.borderWidth > 0.dp) skin.borderWidth else 0.5.dp, skin.line, RoundedCornerShape(6.dp))
                                .clickable {
                                    base = preset.base
                                    model = preset.model
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(preset.name, fontSize = 12.sp, color = skin.accentDeep)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Field("地址", base) { base = it }
                Spacer(Modifier.height(12.dp))
                Field("模型", model) { model = it }
                Spacer(Modifier.height(12.dp))
                Field(
                    label = "Key",
                    value = key,
                    password = true,
                    placeholder = if (settings.apiKey.isNotBlank()) "已保存，留空则不修改" else "sk-…",
                ) { key = it }

                Spacer(Modifier.height(10.dp))
                Text("Key 只存在这台手机上，不经过任何服务器。", fontSize = 11.5.sp, lineHeight = 18.sp, color = skin.sub)
            } else {
                Spacer(Modifier.height(16.dp))
                Text(
                    "不用填 Key。拆题时会把整段提示词给你，贴给任何模型，再把 JSON 粘回来。",
                    fontSize = skin.smallSize,
                    lineHeight = 21.sp,
                    color = skin.sub,
                )
                Spacer(Modifier.height(12.dp))
                Field("地址（直连用，可留空）", base) { base = it }
                Spacer(Modifier.height(10.dp))
                Field("模型（直连用，可留空）", model) { model = it }
            }

            Spacer(Modifier.height(20.dp))
            ToggleRow(
                title = "自动批改",
                note = "开放题先本地比对，判不准时让模型复核一次",
                checked = grade,
                enabled = mode == ChannelMode.DIRECT,
                onChange = { grade = it },
            )
            Spacer(Modifier.height(10.dp))
            ToggleRow(
                title = "本机执行 Python",
                note = "在手机上真跑一段代码对输出；只有装了引擎的安装包可用",
                checked = runCode,
                enabled = true,
                onChange = { runCode = it },
            )
            Spacer(Modifier.height(20.dp))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(skin.accent, RoundedCornerShape(if (skin.pillChoices) 100.dp else skin.radiusCard))
                .clickable {
                    onSave(
                        LlmSettings(
                            baseUrl = base.trim(),
                            model = model.trim(),
                            apiKey = key.trim().ifBlank { settings.apiKey },
                            mode = mode,
                            autoGrade = grade,
                            runCode = runCode,
                        )
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("保存", fontSize = skin.bodySize, color = skin.onAccent)
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun ModeCard(
    mode: ChannelMode,
    selected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(skin.radiusCard)
    Column(
        modifier
            .background(if (selected) skin.accentSoft else skin.surface, shape)
            .border(if (selected) 1.5.dp else 0.5.dp, if (selected) skin.accent else skin.line, shape)
            .clickable(onClick = onClick)
            .padding(13.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) skin.accent else skin.sub, modifier = Modifier.size(19.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            mode.label,
            fontSize = skin.smallSize,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) skin.accentDeep else skin.text,
        )
        Text(mode.note, fontSize = 11.sp, lineHeight = 16.sp, color = skin.sub)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    note: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val skin = LocalSkin.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = skin.smallSize, color = if (enabled) skin.text else skin.sub)
            Text(note, fontSize = 11.sp, lineHeight = 16.sp, color = skin.sub)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked && enabled,
            onCheckedChange = { if (enabled) onChange(it) },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = skin.onAccent,
                checkedTrackColor = skin.accent,
                uncheckedThumbColor = skin.sub,
                uncheckedTrackColor = skin.codeBg,
            ),
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    password: Boolean = false,
    placeholder: String = "",
    onChange: (String) -> Unit,
) {
    val skin = LocalSkin.current
    Column {
        Text(label, fontSize = 11.5.sp, color = skin.sub)
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .background(skin.codeBg, RoundedCornerShape(skin.radiusBlock))
                .border(if (skin.borderWidth > 0.dp) skin.borderWidth else 0.5.dp, skin.line, RoundedCornerShape(skin.radiusBlock))
                .padding(horizontal = 13.dp, vertical = 12.dp)
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, fontSize = skin.smallSize, color = skin.sub)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = skin.smallSize, color = skin.text),
                cursorBrush = SolidColor(skin.accent),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
