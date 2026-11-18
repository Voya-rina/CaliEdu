package com.caliedu.ui

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

            line.startsWith("$$") -> {
                flush()
                val body = StringBuilder(line.removePrefix("$$"))
                while (!body.contains("$$") && i + 1 < lines.size) {
                    i++
                    body.append('\n').append(lines[i].trim())
                }
                val tex = body.toString().removeSuffix("$$").trim()
                out += MdNode.Math(tex)
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
    val text = source.replace("**", "\u0001").replace("`", "\u0002")
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

            c == '$' -> {
                val end = text.indexOf('$', i + 1)
                if (end > i + 1) {
                    out.pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Serif,
                            fontSize = skin.bodySize * 1.02f,
                            color = if (skin.style == UiStyle.PRINT) skin.accentDeep else skin.text,
                        )
                    )
                    out.append(TexLite.convert(text.substring(i + 1, end)))
                    out.pop()
                    i = end + 1
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

@Composable
fun Markdown(source: String, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val nodes = remember(source) { parseMarkdown(source) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (skin.justify) 9.dp else 11.dp)) {
        nodes.forEach { node ->
            when (node) {
                is MdNode.Head -> HeadBlock(node, skin)

                is MdNode.Para -> Text(
                    text = inlineMarkdown(node.text, skin),
                    fontSize = skin.bodySize,
                    lineHeight = skin.bodyLine,
                    color = skin.text,
                    textAlign = if (skin.justify) TextAlign.Justify else TextAlign.Start,
                )

                is MdNode.Bullet -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    node.items.forEach { item ->
                        Row {
                            Text(
                                text = if (node.ordered) "" else "·",
                                fontSize = skin.bodySize,
                                lineHeight = skin.bodyLine,
                                color = skin.accent,
                                modifier = Modifier.width(if (node.ordered) 0.dp else 14.dp),
                            )
                            Text(
                                text = inlineMarkdown(item, skin),
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
                    Text(
                        text = inlineMarkdown(node.text, skin),
                        fontSize = skin.bodySize * 0.97f,
                        lineHeight = skin.bodyLine,
                        color = skin.sub,
                    )
                }

                is MdNode.Table -> TableBlock(node, skin)

                is MdNode.Math -> Text(
                    text = TexLite.convert(node.tex),
                    fontFamily = FontFamily.Serif,
                    fontSize = skin.bodySize * 1.08f,
                    lineHeight = skin.bodyLine * 1.15f,
                    color = if (skin.style == UiStyle.PRINT) skin.accentDeep else skin.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )

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

@Composable
private fun HeadBlock(node: MdNode.Head, skin: Skin) {
    val size = when (node.level) {
        1 -> skin.stepTitleSize * 1.12f
        2 -> skin.stepTitleSize
        else -> skin.bodySize * 1.06f
    }
    val color = if (node.level <= 2) skin.accentDeep else skin.text
    val body: @Composable () -> Unit = {
        Text(
            text = inlineMarkdown(node.text, skin),
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
                    fontSize = 11.sp,
                    color = CodeHi.outputTone(tag ?: "unmeasured", skin),
                )

                lang.isNotBlank() && lang != "text" -> Text(
                    text = lang,
                    fontSize = 11.sp,
                    color = skin.sub,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (copied) "已复制" else "复制",
                fontSize = 10.5.sp,
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
            fontSize = 12.5.sp,
            lineHeight = 20.sp,
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
