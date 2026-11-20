package com.caliedu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caliedu.AskMessage

/**
 * 追问面板。学生卡住时问一句「为什么」，比连点三次提示更接近真实的学习行为。
 * 回答是流式的：字先出来，人就愿意等。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AskSheet(
    stepTitle: String,
    messages: List<AskMessage>,
    partial: String,
    input: String,
    asking: Boolean,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    showSymbols: Boolean = false,
) {
    val skin = LocalSkin.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    // 和答题框一样：符号要插到光标处，得拿着选区。
    var field by remember { mutableStateOf(TextFieldValue(input)) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(input) {
        if (field.text != input) field = TextFieldValue(input, TextRange(input.length))
    }

    LaunchedEffect(messages.size, partial.length) {
        val total = messages.size + if (partial.isNotEmpty() || asking) 1 else 0
        if (total <= 0) return@LaunchedEffect
        // 只有本来就在底部才跟着滚。正在往上翻旧对话时被拽回底部，比不滚更烦。
        val info = listState.layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        val nearBottom = lastVisible < 0 || lastVisible >= info.totalItemsCount - 2
        if (nearBottom) runCatching { listState.scrollToItem(total - 1) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = skin.bg,
        dragHandle = {
            Box(Modifier.padding(vertical = 10.dp)) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .background(skin.line, CircleShape)
                )
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxHeight(0.82f)
                .imePadding()
                .padding(horizontal = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("追问", fontSize = skin.stepTitleSize * 0.82f, fontWeight = FontWeight.Bold, color = skin.text)
                    Text(stepTitle, fontSize = skin.fs(11f), color = skin.sub, maxLines = 1)
                }
                Box(
                    Modifier
                        .size(32.dp)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = skin.sub, modifier = Modifier.size(skin.ui(17f)))
                }
            }
            Spacer(Modifier.height(10.dp))

            if (messages.isEmpty() && partial.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SUGGESTIONS.forEach { text ->
                        HintChip(text) { onInput(text) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            LazyColumn(
                Modifier.weight(1f),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages) { message -> Bubble(message.fromUser, message.text) }
                if (asking) {
                    item { Bubble(false, partial.ifEmpty { "" }, streaming = true) }
                }
                item { Spacer(Modifier.height(6.dp)) }
            }

            Spacer(Modifier.height(8.dp))
            if (showSymbols) {
                SymbolStrip(
                    enabled = !asking,
                    onInsert = { item ->
                        val next = field.insertSymbol(item, focused)
                        field = next
                        onInput(next.text)
                    },
                )
                Spacer(Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Box(
                    Modifier
                        .weight(1f)
                        .background(skin.codeBg, RoundedCornerShape(skin.radiusBlock))
                        .border(0.5.dp, skin.line, RoundedCornerShape(skin.radiusBlock))
                        .padding(horizontal = 13.dp, vertical = 11.dp)
                ) {
                    if (input.isEmpty()) {
                        Text("问一句，只关于这一步", fontSize = skin.smallSize, color = skin.sub)
                    }
                    BasicTextField(
                        value = field,
                        onValueChange = { value ->
                            field = value
                            onInput(value.text)
                        },
                        textStyle = TextStyle(fontSize = skin.bodySize, lineHeight = skin.bodyLine, color = skin.text),
                        cursorBrush = SolidColor(skin.accent),
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focused = it.isFocused },
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(44.dp)
                        .background(if (input.isNotBlank() && !asking) skin.accent else skin.codeBg, CircleShape)
                        .clickable(enabled = input.isNotBlank() && !asking, onClick = onSend),
                    contentAlignment = Alignment.Center,
                ) {
                    if (asking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(skin.ui(15f)),
                            strokeWidth = 1.6.dp,
                            color = skin.accent,
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "发送",
                            tint = if (input.isNotBlank()) skin.onAccent else skin.sub,
                            modifier = Modifier.size(skin.ui(18f)),
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

private val SUGGESTIONS = listOf(
    "为什么这一步要这么做？",
    "这段代码里有个地方没看懂",
    "如果换成别的方法会怎样？",
)

@Composable
private fun HintChip(text: String, onClick: () -> Unit) {
    val skin = LocalSkin.current
    Text(
        text,
        fontSize = skin.fs(12f),
        color = skin.sub,
        modifier = Modifier
            .background(skin.codeBg, RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun Bubble(fromUser: Boolean, text: String, streaming: Boolean = false) {
    val skin = LocalSkin.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .fillMaxWidth(if (fromUser) 0.86f else 0.94f)
                .background(
                    if (fromUser) skin.accentSoft else skin.surface,
                    RoundedCornerShape(skin.radiusCard),
                )
                .let { base ->
                    if (skin.borderWidth > 0.dp && !fromUser) base.border(0.5.dp, skin.line, RoundedCornerShape(skin.radiusCard))
                    else base
                }
                .padding(horizontal = 13.dp, vertical = 11.dp)
        ) {
            if (text.isEmpty() && streaming) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(skin.ui(12f)),
                        strokeWidth = 1.5.dp,
                        color = skin.accent,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("正在想…", fontSize = skin.smallSize, color = skin.sub)
                }
            } else if (fromUser) {
                Text(text, fontSize = skin.bodySize * 0.97f, lineHeight = skin.bodyLine * 0.95f, color = skin.text)
            } else {
                Markdown(if (streaming) "$text▍" else text)
            }
        }
    }
}
