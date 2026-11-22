package com.voyarina.caliedu.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * 六色高亮，配色照搬 md2pdf：关键字紫、字符串绿、数字棕、注释灰斜体、函数名蓝。
 * 只处理 Python，其它语言按纯文本渲染 —— 猜错高亮比不高亮更难看。
 */
object CodeHi {

    private val py = Regex(
        "(?<cmt>#[^\\n]*)" +
            "|(?<str>(?:[fFrRbB]{0,2})(?:'{3}[\\s\\S]*?'{3}|\"{3}[\\s\\S]*?\"{3}" +
            "|'(?:[^'\\\\\\n]|\\\\.)*'|\"(?:[^\"\\\\\\n]|\\\\.)*\"))" +
            "|(?<kw>\\b(?:def|return|if|elif|else|for|while|in|not|and|or|None|True|False" +
            "|import|from|with|as|lambda|class|pass|break|continue|try|except|raise|is|del" +
            "|yield|global|assert|finally|async|await)\\b)" +
            "|(?<bi>\\b(?:print|input|range|len|sum|map|list|dict|set|tuple|int|float|str" +
            "|sorted|enumerate|open|abs|max|min|isinstance|type|zip|reversed|repr|format" +
            "|round|super|self|np|pd|paddle)\\b)" +
            "|(?<fn>\\b[A-Za-z_]\\w*(?=\\())" +
            "|(?<num>\\b\\d+\\.?\\d*\\b)"
    )

    fun highlight(code: String, lang: String, skin: Skin): AnnotatedString {
        val plain = SpanStyle(color = skin.codePlain)
        if (lang !in setOf("python", "py", "python3")) {
            return buildAnnotatedString {
                pushStyle(plain)
                append(code)
                pop()
            }
        }
        val kw = SpanStyle(color = skin.codeKw, fontWeight = FontWeight.Bold)
        val str = SpanStyle(color = skin.codeStr)
        val num = SpanStyle(color = skin.codeNum)
        val cmt = SpanStyle(color = skin.codeCmt, fontStyle = FontStyle.Italic)
        val fn = SpanStyle(color = skin.codeFn)
        val bi = SpanStyle(color = skin.codePlain)

        return buildAnnotatedString {
            var cursor = 0
            for (m in py.findAll(code)) {
                if (m.range.first > cursor) {
                    pushStyle(plain)
                    append(code.substring(cursor, m.range.first))
                    pop()
                }
                val style = when {
                    m.groups["cmt"] != null -> cmt
                    m.groups["str"] != null -> str
                    m.groups["kw"] != null -> kw
                    m.groups["bi"] != null -> bi
                    m.groups["fn"] != null -> fn
                    else -> num
                }
                pushStyle(style)
                append(m.value)
                pop()
                cursor = m.range.last + 1
            }
            if (cursor < code.length) {
                pushStyle(plain)
                append(code.substring(cursor))
                pop()
            }
        }
    }

    fun outputTone(tag: String, skin: Skin): Color = when (tag) {
        "measured" -> skin.codeStr
        "inferred" -> skin.codeNum
        else -> skin.codeCmt
    }
}
