package com.caliedu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class MdNode {
    data class Head(val level: Int, val text: String) : MdNode()
    data class Para(val text: String) : MdNode()
    data class Bullet(val items: List<String>, val ordered: Boolean) : MdNode()
    data class Code(val lang: String, val code: String) : MdNode()
    data class Quote(val text: String) : MdNode()
    data class Table(val head: List<String>, val rows: List<List<String>>) : MdNode()
    data class Math(val tex: String) : MdNode()
    data object Rule : MdNode()
}

private val tableRule = Regex("""^:?-{2,}:?$""")
private val orderedLine = Regex("""^\d+[.)]\s+""")

fun parseMarkdown(source: String): List<MdNode> {
    val lines = source.replace("\r\n", "\n").split("\n")
    val out = mutableListOf<MdNode>()
    val para = StringBuilder()
    var i = 0

    fun flush() {
        if (para.isNotBlank()) {
            out += MdNode.Para(para.toString().trim())
            para.clear()
        }
    }

    while (i < lines.size) {
        val line = lines[i].trim()
        when {
            line.startsWith("```") -> {
                flush()
                val lang = line.removePrefix("```").trim()
                val body = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    body += lines[i]
                    i++
                }
                out += MdNode.Code(lang, body.joinToString("\n"))
                i++
            }

            line.startsWith("$$") || line.startsWith("\\[") -> {
                flush()
                // 模型偶尔会用 \[ ... \] 写独立公式，一并接住，免得整行当普通文字排出来。
                val (open, close) = if (line.startsWith("\\[")) "\\[" to "\\]" else "$$" to "$$"
                val body = StringBuilder(line.removePrefix(open))
                while (!body.contains(close) && i + 1 < lines.size) {
                    i++
                    body.append('\n').append(lines[i].trim())
                }
                val tex = body.toString().removeSuffix(close).trim()
                if (tex.isNotEmpty()) out += MdNode.Math(tex)
                i++
            }

            line.startsWith("#") -> {
                flush()
                val level = line.takeWhile { it == '#' }.length
                out += MdNode.Head(level, line.drop(level).trim())
                i++
            }

            line.startsWith(">") -> {
                flush()
                val quote = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quote += lines[i].trim().removePrefix(">").trim()
                    i++
                }
                out += MdNode.Quote(quote.joinToString(" "))
            }

            line.startsWith("- ") || line.startsWith("* ") -> {
                flush()
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (t.startsWith("- ") || t.startsWith("* ")) {
                        items += t.drop(2).trim()
                        i++
                    } else break
                }
                out += MdNode.Bullet(items, ordered = false)
            }

            orderedLine.containsMatchIn(line) -> {
                flush()
                val items = mutableListOf<String>()
                while (i < lines.size && orderedLine.containsMatchIn(lines[i].trim())) {
                    items += lines[i].trim()
                    i++
                }
                out += MdNode.Bullet(items, ordered = true)
            }

            line.startsWith("|") -> {
                flush()
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    rows += lines[i].trim().trim('|').split('|').map { it.trim() }
                    i++
                }
                val data = rows.filterNot { row -> row.all { tableRule.matches(it) } }
                when {
                    data.size >= 2 -> out += MdNode.Table(data.first(), data.drop(1))
                    data.size == 1 -> out += MdNode.Para(data.first().joinToString("  "))
                }
            }

            line == "---" || line == "***" -> {
                flush()
                out += MdNode.Rule
                i++
            }

            line.isEmpty() -> {
                flush()
                i++
            }

            else -> {
                para.append(line).append(' ')
                i++
            }
        }
    }
    flush()
    return out
}

fun inlineMarkdown(source: String, skin: Skin): AnnotatedString {
    // \u0003 是转义后的字面美元号：\$5 应该原样显示 $5，不能和公式的 $ 配对。
    val text = repairNewlines(source)
        .replace("\\$", "\u0003")
        .replace("**", "\u0001")
        .replace("`", "\u0002")
    val out = AnnotatedString.Builder()
    var bold = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '\u0001' -> {
                bold = !bold
                i++
            }

            c == '\u0002' -> {
                val end = text.indexOf('\u0002', i + 1)
                if (end > i) {
                    out.pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = skin.bodySize * 0.9f,
                            background = skin.codeBg,
                            color = skin.text,
                        )
                    )
                    out.append(text.substring(i + 1, end))
                    out.pop()
                    i = end + 1
                } else {
                    out.append(c)
                    i++
                }
            }

            c == '\u0003' -> {
                out.append('$')
                i++
            }

            // 模型也常写 \( ... \) 和 \[ ... \]，一并当公式接住，别把反斜杠印出来。
            c == '\\' && i + 1 < text.length && (text[i + 1] == '(' || text[i + 1] == '[') -> {
                val close = if (text[i + 1] == '(') "\\)" else "\\]"
                val end = text.indexOf(close, i + 2)
                if (end > i + 2) {
                    out.pushStyle(mathStyle(skin))
                    out.append(TexLite.convert(text.substring(i + 2, end)))
                    out.pop()
                    i = end + 2
                } else {
                    out.append(c)
                    i++
                }
            }

            // 段落中间夹的 $$...$$：块级解析只在行首认它，这里兜住剩下的。
            text.startsWith("$$", i) -> {
                val end = text.indexOf("$$", i + 2)
                if (end > i + 2) {
                    out.pushStyle(mathStyle(skin))
                    out.append(TexLite.convert(text.substring(i + 2, end)))
                    out.pop()
                    i = end + 2
                } else {
                    out.append('$')
                    i++
                }
            }

            c == '$' -> {
                // 只在这一行里找闭合的 $。跨行的「公式」几乎都是模型忘了关，
                // 真按跨行配对，整段中文都会被塞进 mathStyle 里。
                val lineEnd = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                val close = text.indexOf('$', i + 1)
                if (close in (i + 2) until lineEnd && looksLikeMath(text.substring(i + 1, close))) {
                    out.pushStyle(mathStyle(skin))
                    out.append(TexLite.convert(text.substring(i + 1, close)))
                    out.pop()
                    i = close + 1
                } else if (close < 0 || close >= lineEnd) {
                    // 没闭合就认到行尾为止：宁可把这一行按公式渲染，也好过印出 `$f(a)=\frac{..}`。
                    val rest = text.substring(i + 1, lineEnd).trim()
                    if (looksLikeMathLine(rest)) {
                        out.pushStyle(mathStyle(skin))
                        out.append(TexLite.convert(rest))
                        out.pop()
                        i = lineEnd
                    } else {
                        out.append(c)
                        i++
                    }
                } else {
                    out.append(c)
                    i++
                }
            }

            else -> {
                if (bold) {
                    out.pushStyle(SpanStyle(fontWeight = FontWeight.Medium, color = skin.text))
                    out.append(c)
                    out.pop()
                } else {
                    out.append(c)
                }
                i++
            }
        }
    }
    return out.toAnnotatedString()
}

/**
 * 一对 `$` 之间的内容到底是不是公式。
 *
 * 中文段落里美元号更常见的是钱：`价格 $5，另一件 $8` 如果当公式算，
 * 中间那一段会被塞进公式字体，读起来像排版坏了。宁可漏排一个公式，
 * 也不要把正常句子切开——所以这里只做保守判断。
 */
private fun looksLikeMath(body: String): Boolean {
    if (body.isEmpty() || body.length > 90) return false
    if (body.contains('\n')) return false
    if (body.any { it in '\u4e00'..'\u9fff' } && !body.contains('\\')) return false
    return true
}

/**
 * 未闭合 `$` 之后的那一段：能按公式排就一定比印出反斜杠强，但不能把中文、
 * 也不能把「$8」「$5.5」这种价格吞进公式。长度上限比 [looksLikeMath] 宽，推导式本来就长。
 */
private fun looksLikeMathLine(body: String): Boolean {
    if (body.length < 2 || body.length > 200) return false
    if (body.any { it in '\u4e00'..'\u9fff' } && !body.contains('\\')) return false
    if (body.all { it.isDigit() || it in ".," }) return false
    return true
}

/**
 * 模型经常把换行写成字面量 `\n`（反斜杠 + n），正文于是整段粘成一行。
 *
 * 不能只看「n 后面是不是字母」——`$f(a)=0\nf'(x)>0` 里 `\n` 后面就是 f，
 * 但那是换行，不是命令。所以按完整命令名判断：只有 `\nabla`、`\neq`、`\nu`
 * 这些真实存在的命令才保留，其余一律当换行。
 */
internal fun repairNewlines(source: String): String {
    if (!source.contains("\\n")) return source
    val out = StringBuilder(source.length + 8)
    var i = 0
    while (i < source.length) {
        if (source[i] == '\\' && source.getOrNull(i + 1) == 'n') {
            var j = i + 2
            while (j < source.length && (source[j] in 'a'..'z' || source[j] in 'A'..'Z')) j++
            if (source.substring(i + 1, j) !in LATEX_N_COMMANDS) {
                out.append('\n')
                i += 2
                continue
            }
        }
        out.append(source[i])
        i++
    }
    return out.toString()
}

/** 真正以 `\n` 开头的 LaTeX 命令。除这些以外，`\n` 都是模型想换行。 */
private val LATEX_N_COMMANDS = setOf(
    "nabla", "natural", "ncong", "ne", "nearrow", "neg", "neq", "newline",
    "ngeq", "nleq", "nmid", "nonumber", "nolimits", "normalsize", "not", "notin",
    "nparallel", "nrightarrow", "nsim", "nsubseteq", "nsupseteq", "nu", "nvdash",
    "nwarrow",
)

/**
 * 正文第一行常常是一个和步骤同名的小标题：MATH_TEACH 要求「### 动作名」，
 * 模型基本照抄步骤标题，于是同一个名字上下连着出现两次。标题已经显示过了，这里去掉。
 */
internal fun dropDuplicateTitle(md: String, title: String): String {
    val lines = md.trimStart().lines()
    val first = lines.firstOrNull()?.trim().orEmpty()
    if (!first.startsWith("#")) return md
    if (first.trimStart('#').trim() != title.trim()) return md
    return lines.drop(1).joinToString("\n").trimStart()
}

private fun mathStyle(skin: Skin) = SpanStyle(
    fontFamily = FontFamily.Serif,
    fontSize = skin.bodySize * 1.02f,
    color = if (skin.style == UiStyle.PRINT) skin.accentDeep else skin.text,
)

@Composable
fun Markdown(source: String, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val nodes = remember(source) { parseMarkdown(source) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (skin.justify) 9.dp else 11.dp)) {
        nodes.forEach { node ->
            when (node) {
                is MdNode.Head -> HeadBlock(node, skin)

                is MdNode.Para -> {
                    val styled = remember(node.text, skin) { inlineMarkdown(node.text, skin) }
                    Text(
                        text = styled,
                        fontSize = skin.bodySize,
                        lineHeight = skin.bodyLine,
                        color = skin.text,
                        textAlign = if (skin.justify) TextAlign.Justify else TextAlign.Start,
                    )
                }

                is MdNode.Bullet -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    node.items.forEach { item ->
                        val styled = remember(item, skin) { inlineMarkdown(item, skin) }
                        Row {
                            Text(
                                text = if (node.ordered) "" else "·",
                                fontSize = skin.bodySize,
                                lineHeight = skin.bodyLine,
                                color = skin.accent,
                                modifier = Modifier.width(if (node.ordered) 0.dp else 14.dp),
                            )
                            Text(
                                text = styled,
                                fontSize = skin.bodySize,
                                lineHeight = skin.bodyLine,
                                color = skin.text,
                            )
                        }
                    }
                }

                is MdNode.Code -> CodeBlock(node.lang, node.code, skin)

                is MdNode.Quote -> Row(Modifier.heightIn(min = 20.dp)) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .heightIn(min = 20.dp)
                            .align(Alignment.CenterVertically)
                            .background(if (skin.style == UiStyle.PRINT) skin.amber else skin.line)
                    )
                    Spacer(Modifier.width(12.dp))
                    val styled = remember(node.text, skin) { inlineMarkdown(node.text, skin) }
                    Text(
                        text = styled,
                        fontSize = skin.bodySize * 0.97f,
                        lineHeight = skin.bodyLine,
                        color = skin.sub,
                    )
                }

                is MdNode.Table -> TableBlock(node, skin)

                is MdNode.Math -> Derivation(node.tex, skin)

                MdNode.Rule -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(skin.line)
                )
            }
        }
    }
}

/**
 * 推导行：左边一道竖线，式子按 TeX 排出来。
 * 竖线用 drawBehind 画，跟着内容高度走——分式一高，线也跟着高。
 */
@Composable
private fun Derivation(tex: String, skin: Skin) {
    val tone = if (skin.style == UiStyle.PRINT) skin.accentDeep else skin.text
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    color = skin.accent.copy(alpha = 0.32f),
                    size = androidx.compose.ui.geometry.Size(1.5.dp.toPx(), size.height),
                )
            }
            .padding(start = 15.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Tex.MathLine(
            tex = tex,
            size = skin.bodySize * 1.14f,
            color = tone,
        )
    }
}

@Composable
private fun HeadBlock(node: MdNode.Head, skin: Skin) {
    val size = when (node.level) {
        1 -> skin.stepTitleSize * 1.12f
        2 -> skin.stepTitleSize
        else -> skin.bodySize * 1.06f
    }
    val color = if (node.level <= 2) skin.accentDeep else skin.text
    val styled = remember(node.text, skin) { inlineMarkdown(node.text, skin) }
    val body: @Composable () -> Unit = {
        Text(
            text = styled,
            fontSize = size,
            lineHeight = size * 1.45f,
            fontWeight = if (node.level <= 2) FontWeight.Bold else skin.stepTitleWeight,
            color = color,
        )
    }
    if (skin.sectionBar && node.level <= 2) {
        Row {
            Box(
                Modifier
                    .width(5.dp)
                    .height((size.value * 1.35f).dp)
                    .background(skin.accent, RoundedCornerShape(1.dp))
            )
            Spacer(Modifier.width(11.dp))
            body()
        }
    } else {
        body()
    }
}

@Composable
private fun CodeBlock(lang: String, code: String, skin: Skin) {
    val tag = when {
        lang.startsWith("output:") -> lang.substringAfter(':')
        lang == "output" -> "unmeasured"
        else -> null
    }
    val label = when (tag) {
        "measured" -> "实测输出"
        "inferred" -> "推断输出"
        "unmeasured" -> "未实测"
        else -> null
    }
    val codeLang = if (tag != null) "text" else lang
    val shape = RoundedCornerShape(skin.radiusBlock)
    val decorated = Modifier
        .fillMaxWidth()
        .background(skin.codeBg, shape)
        .let { base ->
            if (skin.borderWidth > 0.dp && skin.codeBorder != Color.Transparent) {
                base.border(skin.borderWidth, skin.codeBorder, shape)
            } else base
        }
        .padding(horizontal = 13.dp, vertical = 11.dp)

    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }

    Column(decorated) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                label != null -> Text(
                    text = label,
                    fontSize = skin.fs(11f),
                    color = CodeHi.outputTone(tag ?: "unmeasured", skin),
                )

                lang.isNotBlank() && lang != "text" -> Text(
                    text = lang,
                    fontSize = skin.fs(11f),
                    color = skin.sub,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (copied) "已复制" else "复制",
                fontSize = skin.fs(10.5f),
                color = if (copied) skin.accent else skin.sub,
                modifier = Modifier
                    .background(skin.bg.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .clickable {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    }
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = CodeHi.highlight(code, codeLang, skin),
            fontFamily = skin.mono,
            fontSize = skin.fs(12.5f),
            lineHeight = skin.fs(20f),
            softWrap = true,
        )
    }
}

@Composable
private fun TableBlock(node: MdNode.Table, skin: Skin) {
    val columnWidth = if (node.head.size <= 2) 150.dp else 120.dp
    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        Row(
            Modifier
                .background(skin.accentSoft)
                .padding(vertical = 6.dp)
        ) {
            node.head.forEach { cell -> Cell(cell, true, columnWidth, skin) }
        }
        node.rows.forEach { row ->
            Row(
                Modifier
                    .padding(vertical = 6.dp)
                    .background(Color.Transparent)
            ) {
                row.forEach { cell -> Cell(cell, false, columnWidth, skin) }
            }
        }
    }
}

@Composable
private fun Cell(text: String, header: Boolean, width: Dp, skin: Skin) {
    Text(
        text = inlineMarkdown(text, skin),
        fontSize = skin.smallSize,
        lineHeight = skin.smallSize * 1.55f,
        color = if (header) skin.accentDeep else skin.text,
        fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 8.dp),
        textAlign = TextAlign.Start,
    )
}
