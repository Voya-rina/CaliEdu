package com.caliedu.stepedu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caliedu.stepedu.data.LlmSettings

private data class Preset(val name: String, val base: String, val model: String)

private val presets = listOf(
    Preset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
    Preset("Kimi", "https://api.moonshot.cn/v1", "kimi-latest"),
    Preset("本机", "http://127.0.0.1:1234/v1", "local-model"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: LlmSettings,
    onSave: (LlmSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var base by remember { mutableStateOf(settings.baseUrl) }
    var model by remember { mutableStateOf(settings.model) }
    var key by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Ink.Card,
        contentColor = Ink.Text,
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("模型接口", fontSize = 19.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(
                "任何 OpenAI 兼容接口都能用。Key 只存在这台手机上，不经过任何服务器。",
                fontSize = 13.sp,
                lineHeight = 21.sp,
                color = Ink.Sub,
            )

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    Box(
                        Modifier
                            .background(Ink.Code, RoundedCornerShape(8.dp))
                            .clickable {
                                base = preset.base
                                model = preset.model
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(preset.name, fontSize = 13.sp, color = Ink.Mono)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            SheetField("地址", base) { base = it }
            Spacer(Modifier.height(12.dp))
            SheetField("模型", model) { model = it }
            Spacer(Modifier.height(12.dp))
            SheetField("Key", key, password = true, placeholder = if (settings.apiKey.isNotBlank()) "已保存，留空则不修改" else "sk-…") { key = it }

            Spacer(Modifier.height(22.dp))
            Button(
                onClick = {
                    onSave(
                        LlmSettings(
                            baseUrl = base.trim(),
                            model = model.trim(),
                            apiKey = key.trim().ifBlank { settings.apiKey },
                        )
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink.Accent, contentColor = Ink.Card),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("保存", fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun SheetField(
    label: String,
    value: String,
    password: Boolean = false,
    placeholder: String = "",
    onChange: (String) -> Unit,
) {
    Column {
        Text(label, fontSize = 12.sp, color = Ink.Sub)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .background(Ink.Code, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, fontSize = 14.sp, color = Ink.Sub)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = Ink.Text),
                cursorBrush = SolidColor(Ink.Accent),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
