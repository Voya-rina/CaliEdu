package com.voyarina.caliedu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 答题时用的符号条。
 *
 * 手机键盘上没有 ≤、α、²、Σ 这些键，数学答案基本写不出来。这里给一条横滑的符号条：
 * 普通按钮点一下塞到光标处；面上写着「+ − ×」这类的是组入口，点开在条上方展开一整组，
 * 再点同一组收起，点开另一组自动收起上一组。
 *
 * 插进去的是 Unicode 字符而不是 LaTeX：`Judge.normalize` 认得 `²`→2、`−`→`-`、`×`→`*`，
 * 却认不出 `\frac{1}{2}`，写 LaTeX 反而会让本地判定判错。
 *
 * 上下标是唯一的例外：插 `^{}` / `_{}`（光标落在花括号里），而不是 `²`。
 * 因为 `x^{n+1}` 这种式子没有现成的 Unicode 上标，而 normalize 会把 `^ { }` 全部丢掉，
 * `x^{2}` 归一成 `x2`，和 `x²` 是同一条判定路径。
 */
data class SymItem(
    /** 按钮上显示的字。可以和 [insert] 不同：`−` 看着是减号，插进去的是 ASCII 的 `-`。 */
    val label: String,
    val insert: String = label,
    /** 插入后光标往回收几个字符。`()` 收 1 格，正好停在括号中间。 */
    val back: Int = 0,
)

sealed interface SymEntry {
    /** 二级入口。[note] 只给读屏用，按钮上不显示。 */
    data class Group(val face: String, val note: String, val items: List<SymItem>) : SymEntry

    /** 点一下直接插入。 */
    data class Key(val item: SymItem) : SymEntry
}

object Symbols {

    private fun key(label: String, insert: String = label, back: Int = 0) = SymItem(label, insert, back)

    val ENTRIES: List<SymEntry> = listOf(
        SymEntry.Group(
            face = "+ − ×",
            note = "加减乘除与正负",
            items = listOf(
                key("+"), key("−", "-"), key("×"), key("÷"), key("="), key("±"),
            ),
        ),
        SymEntry.Group(
            face = "≤ ≥ ≠",
            note = "比较与约等",
            items = listOf(
                key("<"), key(">"), key("≤"), key("≥"), key("≠"), key("≈"),
            ),
        ),
        // 上下标不进二级菜单：就两个模板，插进去光标停在花括号中间，直接打数字。
        SymEntry.Key(key("x²", "^{}", back = 1)),
        SymEntry.Key(key("xₙ", "_{}", back = 1)),
        SymEntry.Group(
            face = "α β γ",
            note = "希腊字母",
            items = listOf(
                key("α"), key("β"), key("γ"), key("δ"), key("ε"), key("θ"),
                key("λ"), key("μ"), key("σ"), key("φ"), key("ω"), key("Δ"),
                key("π"), key("∞"),
            ),
        ),
        SymEntry.Group(
            face = "Σ ∫ √",
            note = "算符与箭头",
            items = listOf(
                key("Σ"), key("Π"), key("∫"), key("√"), key("∂"), key("→"),
                key("⇒"), key("∈"), key("°"), key("∴"), key("∝"), key("⊥"),
            ),
        ),
        SymEntry.Key(key("( )", "()", back = 1)),
        SymEntry.Key(key("| |", "||", back = 1)),
    )

    /** 组面板一行放几个。和符号条一样用固定列数，按钮大小才一致。 */
    const val COLUMNS = 6
}

/**
 * 插到光标处。
 *
 * 输入框没焦点时拿不到真实的选区（`selection` 是 0），这时候插到末尾，
 * 比插到开头更符合「接着往下写」的预期。
 */
fun TextFieldValue.insertSymbol(item: SymItem, focused: Boolean): TextFieldValue {
    val start = if (focused) selection.min else text.length
    val end = if (focused) selection.max else text.length
    val next = text.substring(0, start) + item.insert + text.substring(end)
    val caret = (start + item.insert.length - item.back).coerceIn(0, next.length)
    return TextFieldValue(next, TextRange(caret))
}

@Composable
fun SymbolStrip(
    onInsert: (SymItem) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val skin = LocalSkin.current
    var open by remember { mutableIntStateOf(-1) }
    // 收起的动画要播得出来，就不能在 open 变 -1 的同时把内容也抽掉。
    // 所以面板内容另外记一份，只有点开另一组时才换。
    var items by remember { mutableStateOf<List<SymItem>>(emptyList()) }
    val shape = RoundedCornerShape(skin.radiusBlock)

    Column(modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = open >= 0,
            enter = fadeIn(tween(140)) + expandVertically(tween(190)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(170)),
        ) {
            SymbolPanel(items, enabled, onInsert)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = if (open >= 0) 8.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Symbols.ENTRIES.forEachIndexed { index, entry ->
                when (entry) {
                    is SymEntry.Group -> StripKey(
                        label = entry.face,
                        group = true,
                        active = open == index,
                        enabled = enabled,
                        shape = shape,
                        onClick = {
                            if (open == index) {
                                open = -1
                            } else {
                                items = entry.items
                                open = index
                            }
                        },
                    )

                    is SymEntry.Key -> StripKey(
                        label = entry.item.label,
                        group = false,
                        active = false,
                        enabled = enabled,
                        shape = shape,
                        onClick = { onInsert(entry.item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StripKey(
    label: String,
    group: Boolean,
    active: Boolean,
    enabled: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    val skin = LocalSkin.current
    val border = when {
        active -> skin.accent
        group -> skin.accent.copy(alpha = 0.42f)
        else -> skin.line
    }
    val tint = when {
        !enabled -> skin.sub.copy(alpha = 0.5f)
        active -> skin.accentDeep
        group -> skin.accent
        else -> skin.text
    }
    Box(
        Modifier
            .widthIn(min = skin.ui(40f))
            .heightIn(min = skin.ui(36f))
            .background(if (active) skin.accentSoft else skin.surface, shape)
            .border(0.5.dp, border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = skin.fs(13.5f),
            color = tint,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SymbolPanel(items: List<SymItem>, enabled: Boolean, onPick: (SymItem) -> Unit) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(skin.radiusBlock)
    Column(
        Modifier
            .fillMaxWidth()
            .background(skin.surface, shape)
            .border(0.5.dp, skin.line, shape)
            .padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.chunked(Symbols.COLUMNS).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { item ->
                    PanelKey(item.label, enabled, Modifier.weight(1f)) { onPick(item) }
                }
                // 补齐空位，最后一行才不会被拉宽
                repeat(Symbols.COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PanelKey(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(skin.radiusBlock)
    Box(
        modifier
            .heightIn(min = skin.ui(40f))
            .background(skin.codeBg, shape)
            .border(0.5.dp, skin.line, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = skin.fs(15f),
            color = if (enabled) skin.text else skin.sub.copy(alpha = 0.5f),
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
