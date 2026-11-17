package com.caliedu.stepedu.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class MdNode {
    data class Head(val level: Int, val text: String) : MdNode()
    data class Para(val text: String) : MdNode()
    data class Bullet(val items: List<String>, val ordered: Boolean) : MdNode()
    data class Code(val lang: String, val code: String) : MdNode()
    data class Quote(val text: String) : MdNode()
    data class Table(val head: List<String>, val rows: List<List<String>>) : MdNode()
    data object Rule : MdNode()
}

private val tableRule = Regex("""^:?-{2,}:?$""")
private val ordered = Regex("""^\d+[.)]\s+""")

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
        val raw = lines[i]
        val line = raw.trim()
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

            ordered.containsMatchIn(line) -> {
                flush()
                val items = mutableListOf<String>()
                while (i < lines.size && ordered.containsMatchIn(lines[i].trim())) {
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

fun inlineMarkdown(source: String): AnnotatedString {
    val text = source.replace("**", "\u0001").replace("`", "\u0002")
    val out = androidx.compose.ui.text.AnnotatedString.Builder()
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
                            fontSize = 13.sp,
                            background = Ink.Code,
                            color = Ink.Mono,
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
                    out.pushStyle(SpanStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic))
                    out.append(text.substring(i + 1, end))
                    out.pop()
                    i = end + 1
                } else {
                    out.append(c)
                    i++
                }
            }

            else -> {
                if (bold) {
                    out.pushStyle(SpanStyle(fontWeight = FontWeight.Medium, color = Ink.Text))
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
    val nodes = remember(source) { parseMarkdown(source) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        nodes.forEach { node ->
            when (node) {
                is MdNode.Head -> Text(
                    text = inlineMarkdown(node.text),
                    fontSize = when (node.level) {
                        1 -> 21.sp
                        2 -> 17.sp
                        else -> 15.sp
                    },
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = Ink.Text,
                )

                is MdNode.Para -> Text(
                    text = inlineMarkdown(node.text),
                    fontSize = 15.sp,
                    lineHeight = 26.sp,
                    color = Ink.Text,
                )

                is MdNode.Bullet -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    node.items.forEach { item ->
                        Row {
                            Text(
                                text = if (node.ordered) "" else "·",
                                fontSize = 15.sp,
                                lineHeight = 25.sp,
                                color = Ink.Sub,
                                modifier = Modifier.width(if (node.ordered) 0.dp else 14.dp),
                            )
                            Text(
                                text = inlineMarkdown(item),
                                fontSize = 15.sp,
                                lineHeight = 25.sp,
                                color = Ink.Text,
                            )
                        }
                    }
                }

                is MdNode.Code -> CodeBlock(node.lang, node.code)

                is MdNode.Quote -> Row {
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(18.dp)
                            .align(Alignment.CenterVertically)
                            .background(Ink.Line)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = inlineMarkdown(node.text),
                        fontSize = 14.sp,
                        lineHeight = 24.sp,
                        color = Ink.Sub,
                    )
                }

                is MdNode.Table -> TableBlock(node)

                MdNode.Rule -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Ink.Line)
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(lang: String, code: String) {
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
    val tone = when (tag) {
        "measured" -> Ink.Accent
        "inferred" -> Ink.Amber
        else -> Ink.Sub
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Ink.Code, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (label != null) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = tone,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        } else if (lang.isNotBlank() && lang != "text") {
            Text(
                text = lang,
                fontSize = 11.sp,
                color = Ink.Sub,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.5.sp,
            lineHeight = 21.sp,
            color = Ink.Mono,
            softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun TableBlock(node: MdNode.Table) {
    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        Row(Modifier.padding(bottom = 6.dp)) {
            node.head.forEach { cell ->
                Cell(cell, header = true)
            }
        }
        Box(
            Modifier
                .width((132 * node.head.size).dp)
                .height(1.dp)
                .background(Ink.Line)
        )
        node.rows.forEach { row ->
            Row(Modifier.padding(vertical = 6.dp)) {
                row.forEach { cell -> Cell(cell, header = false) }
            }
        }
    }
}

@Composable
private fun Cell(text: String, header: Boolean) {
    Text(
        text = inlineMarkdown(text),
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = if (header) Ink.Text else Ink.Mono,
        fontWeight = if (header) FontWeight.Medium else FontWeight.Normal,
        modifier = Modifier
            .width(132.dp)
            .padding(end = 12.dp),
        textAlign = TextAlign.Start,
    )
}
