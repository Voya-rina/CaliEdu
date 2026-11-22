package com.voyarina.caliedu.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * 六色高亮，配色照搬 md2pdf：关键字紫、字符串绿、数字棕、注释灰斜体、函数名蓝。
 * 只处理 Python 与 C 系（C / C++），其它语言按纯文本渲染 —— 猜错高亮比不高亮更难看。
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

    /** C 与 C++ 共用一套：C++ 那几个词在 C 里出现的概率太低，多认几个不会看错。 */
    private val cWords = listOf(
        "auto", "break", "case", "char", "const", "continue", "default", "do", "double",
        "else", "enum", "extern", "float", "for", "goto", "if", "inline", "int", "long",
        "register", "restrict", "return", "short", "signed", "sizeof", "static", "struct",
        "switch", "typedef", "union", "unsigned", "void", "volatile", "while",
        "bool", "true", "false", "NULL", "size_t",
        "class", "namespace", "template", "typename", "public", "private", "protected",
        "virtual", "using", "new", "delete", "nullptr", "this", "operator", "friend",
        "explicit", "override", "final", "try", "catch", "throw", "constexpr", "noexcept",
        "static_cast", "dynamic_cast", "const_cast", "reinterpret_cast",
    )

    private val c = Regex(
        "(?<cmt>//[^\\n]*|/\\*[\\s\\S]*?\\*/)" +
            // 预处理指令整行算一个记号：后面的 <stdio.h> 单独标色只会更乱。
            "|(?<pre>^[ \\t]*#[^\\n]*)" +
            "|(?<str>\"(?:[^\"\\\\\\n]|\\\\.)*\"|'(?:[^'\\\\\\n]|\\\\.)*')" +
            "|(?<kw>\\b(?:${cWords.joinToString("|")})\\b)" +
            "|(?<fn>\\b[A-Za-z_]\\w*(?=\\s*\\())" +
            "|(?<num>\\b(?:0[xX][0-9a-fA-F]+|\\d+\\.?\\d*(?:[eE][+-]?\\d+)?)[fFuUlL]*\\b)",
        RegexOption.MULTILINE,
    )

    /** 一套词法：正则 + 它真实拥有的命名组（取不存在的组会直接抛异常）。 */
    private class Lexer(val pattern: Regex, val groups: List<String>)

    private val pyLexer = Lexer(py, listOf("cmt", "str", "kw", "bi", "fn", "num"))
    private val cLexer = Lexer(c, listOf("cmt", "pre", "str", "kw", "fn", "num"))

    private fun lexerOf(lang: String): Lexer? = when (lang.trim().lowercase()) {
        "python", "py", "python3" -> pyLexer
        "c", "h", "cpp", "c++", "cc", "cxx", "hpp", "hxx" -> cLexer
        else -> null
    }

    fun highlight(code: String, lang: String, skin: Skin): AnnotatedString {
        val plain = SpanStyle(color = skin.codePlain)
        val lexer = lexerOf(lang) ?: return buildAnnotatedString {
            pushStyle(plain)
            append(code)
            pop()
        }

        val kw = SpanStyle(color = skin.codeKw, fontWeight = FontWeight.Bold)
        val str = SpanStyle(color = skin.codeStr)
        val num = SpanStyle(color = skin.codeNum)
        val cmt = SpanStyle(color = skin.codeCmt, fontStyle = FontStyle.Italic)
        val fn = SpanStyle(color = skin.codeFn)
        val bi = SpanStyle(color = skin.codePlain)
        val styles = mapOf(
            "cmt" to cmt,
            "pre" to kw,
            "str" to str,
            "kw" to kw,
            "bi" to bi,
            "fn" to fn,
            "num" to num,
        )

        return buildAnnotatedString {
            var cursor = 0
            for (m in lexer.pattern.findAll(code)) {
                if (m.range.first > cursor) {
                    pushStyle(plain)
                    append(code.substring(cursor, m.range.first))
                    pop()
                }
                val hit = lexer.groups.firstOrNull { m.groups[it] != null }
                pushStyle(hit?.let { styles.getValue(it) } ?: plain)
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
