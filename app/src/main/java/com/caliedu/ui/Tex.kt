package com.caliedu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 独立公式的排版。
 *
 * 之前是把 TeX 拍平成一行文本，`\frac{a}{b}` 于是变成 `a/b`——读得出，但不像数学。
 * 这里把式子解析成几个盒子：分式上下叠、根号带横线、Σ 带上下限、上下标真的抬高降低。
 *
 * 只覆盖常见的数学记号，覆盖不到的命令一律退化成纯文本，绝不吐反斜杠。
 */
sealed interface TexNode {
    data class Run(val text: String, val upright: Boolean = false) : TexNode
    data class Frac(val num: List<TexNode>, val den: List<TexNode>) : TexNode
    data class Scripts(val base: List<TexNode>, val sup: List<TexNode>?, val sub: List<TexNode>?) : TexNode
    data class Sqrt(val body: List<TexNode>, val index: List<TexNode>?) : TexNode
    data class BigOp(val sym: String, val sup: List<TexNode>?, val sub: List<TexNode>?) : TexNode
}

object Tex {

    private val symbols = mapOf(
        "times" to "×", "cdot" to "·", "div" to "÷", "pm" to "±", "mp" to "∓",
        "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥", "neq" to "≠", "ne" to "≠",
        "approx" to "≈", "equiv" to "≡", "sim" to "∼", "propto" to "∝",
        "infty" to "∞", "partial" to "∂", "nabla" to "∇", "emptyset" to "∅",
        "in" to "∈", "notin" to "∉", "subset" to "⊂", "subseteq" to "⊆", "supset" to "⊃",
        "cup" to "∪", "cap" to "∩", "forall" to "∀", "exists" to "∃",
        "to" to "→", "rightarrow" to "→", "leftarrow" to "←", "Rightarrow" to "⇒",
        "Leftarrow" to "⇐", "leftrightarrow" to "↔", "iff" to "⇔",
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
        "epsilon" to "ε", "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η",
        "theta" to "θ", "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
        "pi" to "π", "rho" to "ρ", "sigma" to "σ", "tau" to "τ", "phi" to "φ",
        "varphi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ",
        "Sigma" to "Σ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
        "angle" to "∠", "perp" to "⊥", "parallel" to "∥", "degree" to "°",
        "prime" to "′", "ldots" to "…", "cdots" to "⋯", "dots" to "…",
    )

    private val bigOps = mapOf(
        "sum" to "Σ", "prod" to "∏", "int" to "∫", "iint" to "∬", "oint" to "∮",
        "lim" to "lim", "max" to "max", "min" to "min", "sup" to "sup", "inf" to "inf",
    )

    private val functions = setOf("sin", "cos", "tan", "cot", "sec", "csc", "log", "ln", "lg", "exp", "arg", "det", "gcd")

    fun parse(src: String): List<TexNode> = Reader(src).parseSeq(top = true)

    // ---------------------------------------------------------------- 解析

    private class Reader(private val s: String) {
        private var i = 0

        fun parseSeq(top: Boolean): List<TexNode> {
            val out = mutableListOf<TexNode>()
            val run = StringBuilder()
            fun flush() {
                if (run.isNotEmpty()) {
                    out += TexNode.Run(run.toString())
                    run.setLength(0)
                }
            }
            while (i < s.length) {
                val c = s[i]
                when {
                    c == '}' && !top -> break
                    c == '}' -> i++
                    c == '{' -> {
                        flush()
                        i++
                        val inner = parseSeq(false)
                        if (i < s.length && s[i] == '}') i++
                        out += if (inner.size == 1) inner[0] else TexNode.Scripts(inner, null, null)
                    }

                    c == '\\' -> {
                        flush()
                        out += readCommand()
                    }

                    c == '^' || c == '_' -> {
                        // 上下标粘到前一个原子上；没有前一个就当前导符号处理
                        flush()
                        i++ // 先跳过 ^ / _ 本身，readGroup 才会看参数
                        val script = readGroup()
                        val base = if (out.isNotEmpty()) listOf(out.removeAt(out.lastIndex)) else emptyList()
                        out += attach(base, if (c == '^') script else null, if (c == '_') script else null)
                    }

                    c == '&' || c == '~' -> {
                        flush()
                        run.append(' ')
                        i++
                    }

                    c == '\n' -> {
                        flush()
                        if (run.isEmpty()) out += TexNode.Run(" ")
                        i++
                    }

                    else -> {
                        run.append(c)
                        i++
                    }
                }
            }
            flush()
            return out
        }

        private fun attach(base: List<TexNode>, sup: List<TexNode>?, sub: List<TexNode>?): TexNode {
            val only = base.singleOrNull()
            if (only is TexNode.BigOp) {
                return only.copy(
                    sup = mergeExp(only.sup, sup),
                    sub = mergeExp(only.sub, sub),
                )
            }
            if (only is TexNode.Scripts) {
                return only.copy(
                    sup = if (sup != null) sup else only.sup,
                    sub = if (sub != null) sub else only.sub,
                )
            }
            return TexNode.Scripts(base, sup, sub)
        }

        /** `\sum_{i=1}^{n}` 解析时先遇到 `_`，再遇到 `^`，要把两边接上。 */
        private fun mergeExp(old: List<TexNode>?, add: List<TexNode>?): List<TexNode>? = add ?: old

        private fun readCommand(): TexNode {
            i++ // 跳过反斜杠
            val name = buildString {
                while (i < s.length && s[i].isLetter()) {
                    append(s[i]); i++
                }
            }
            if (name.isEmpty()) {
                // \{ \} \, \; \! \\ 这类单符号命令
                val ch = if (i < s.length) s[i].also { i++ } else ' '
                return when (ch) {
                    ',' , ';', ':', '!', ' ' -> TexNode.Run(" ")
                    '\\' -> TexNode.Run(" ")
                    '{' -> TexNode.Run("{")
                    '}' -> TexNode.Run("}")
                    else -> TexNode.Run(ch.toString())
                }
            }
            return when {
                name == "frac" || name == "dfrac" || name == "tfrac" ->
                    TexNode.Frac(readGroup(), readGroup())

                name == "sqrt" -> {
                    val index = if (i < s.length && s[i] == '[') readBracket() else null
                    TexNode.Sqrt(readGroup(), index)
                }

                name == "text" || name == "mathrm" || name == "operatorname" || name == "mathbf" ->
                    TexNode.Run(rawGroup(), upright = true)

                name == "left" || name == "right" -> {
                    // \left( → "(" ；\left. → 什么都不画
                    if (i < s.length && s[i] != '.') TexNode.Run(s[i].toString().also { i++ })
                    else {
                        if (i < s.length) i++
                        TexNode.Run("")
                    }
                }

                name == "begin" || name == "end" -> {
                    rawGroup()
                    TexNode.Run("")
                }

                bigOps.containsKey(name) -> TexNode.BigOp(bigOps.getValue(name), null, null)

                functions.contains(name) -> TexNode.Run(name, upright = true)

                symbols.containsKey(name) -> TexNode.Run(symbols.getValue(name))

                else -> TexNode.Run(name)
            }
        }

        /** 读一个参数：`{...}` 取内部，否则取一个字符。 */
        private fun readGroup(): List<TexNode> {
            while (i < s.length && s[i] == ' ') i++
            if (i >= s.length) return emptyList()
            if (s[i] == '{') {
                i++
                val inner = parseSeq(false)
                if (i < s.length && s[i] == '}') i++
                return inner
            }
            val node = when {
                s[i] == '\\' -> readCommand()
                else -> TexNode.Run(s[i].toString().also { i++ })
            }
            return listOf(node)
        }

        private fun rawGroup(): String {
            while (i < s.length && s[i] == ' ') i++
            if (i >= s.length) return ""
            if (s[i] != '{') return s[i].toString().also { i++ }
            i++
            val start = i
            var depth = 1
            while (i < s.length && depth > 0) {
                when (s[i]) {
                    '{' -> depth++
                    '}' -> depth--
                }
                if (depth == 0) break
                i++
            }
            val body = s.substring(start, i)
            if (i < s.length) i++
            return body
        }

        private fun readBracket(): List<TexNode> {
            i++ // [
            val start = i
            while (i < s.length && s[i] != ']') i++
            val body = s.substring(start, i)
            if (i < s.length) i++
            return Tex.parse(body)
        }
    }

    // ---------------------------------------------------------------- 排版

    /** 独立公式：一行里可以放分式、根号、大算符，纵向居中。 */
    @Composable
    fun MathLine(
        tex: String,
        size: TextUnit,
        color: Color,
        modifier: Modifier = Modifier,
        font: FontFamily = FontFamily.Serif,
    ) {
        val nodes = remember(tex) { runCatching { parse(tex) }.getOrElse { listOf(TexNode.Run(tex)) } }
        Row(
            modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Seq(nodes, size, color, font)
        }
    }

    @Composable
    private fun Seq(nodes: List<TexNode>, size: TextUnit, color: Color, font: FontFamily) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            nodes.forEach { node -> Node(node, size, color, font) }
        }
    }

    @Composable
    private fun Node(node: TexNode, size: TextUnit, color: Color, font: FontFamily) {
        when (node) {
            is TexNode.Run -> if (node.text.isNotEmpty()) {
                Text(
                    text = node.text,
                    fontFamily = font,
                    fontSize = size,
                    lineHeight = size * 1.25f,
                    fontStyle = if (node.upright) FontStyle.Normal else FontStyle.Italic,
                    color = color,
                )
            }

            is TexNode.Frac -> FracBox(node, size, color, font)

            is TexNode.Sqrt -> SqrtBox(node, size, color, font)

            is TexNode.BigOp -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                node.sup?.let { Seq(it, size * 0.62f, color, font) }
                Text(
                    node.sym,
                    fontFamily = font,
                    fontSize = if (node.sym.length > 1) size * 0.95f else size * 1.45f,
                    fontWeight = if (node.sym.length > 1) FontWeight.Normal else FontWeight.Light,
                    fontStyle = FontStyle.Normal,
                    color = color,
                )
                node.sub?.let { Seq(it, size * 0.62f, color, font) }
            }

            is TexNode.Scripts -> ScriptBox(node, size, color, font)
        }
    }

    @Composable
    private fun FracBox(node: TexNode.Frac, size: TextUnit, color: Color, font: FontFamily) {
        Column(
            Modifier.width(IntrinsicSize.Max),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.padding(horizontal = 3.dp)) { Seq(node.num, size * 0.92f, color, font) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .height(1.dp)
                    .background(color.copy(alpha = 0.6f))
            )
            Box(Modifier.padding(horizontal = 3.dp)) { Seq(node.den, size * 0.92f, color, font) }
        }
    }

    @Composable
    private fun SqrtBox(node: TexNode.Sqrt, size: TextUnit, color: Color, font: FontFamily) {
        Row(verticalAlignment = Alignment.Top) {
            if (node.index != null) {
                Box(Modifier.offset(y = (-size.value * 0.35f).dp)) {
                    Seq(node.index, size * 0.55f, color, font)
                }
            }
            Text(
                "√",
                fontFamily = font,
                fontSize = size * 1.1f,
                color = color,
                modifier = Modifier.offset(y = (-size.value * 0.02f).dp),
            )
            Column(Modifier.width(IntrinsicSize.Max)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(color.copy(alpha = 0.6f))
                )
                Box(Modifier.padding(horizontal = 2.dp)) { Seq(node.body, size, color, font) }
            }
        }
    }

    @Composable
    private fun ScriptBox(node: TexNode.Scripts, size: TextUnit, color: Color, font: FontFamily) {
        val small = size * 0.66f
        when {
            node.sup != null && node.sub != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Seq(node.base, size, color, font)
                Column {
                    Seq(node.sup, small, color, font)
                    Seq(node.sub, small, color, font)
                }
            }

            node.sup != null -> Row(verticalAlignment = Alignment.Top) {
                Seq(node.base, size, color, font)
                Box(Modifier.offset(y = (-size.value * 0.44f).dp)) {
                    Seq(node.sup, small, color, font)
                }
            }

            node.sub != null -> Row(verticalAlignment = Alignment.Top) {
                Seq(node.base, size, color, font)
                Box(Modifier.offset(y = (size.value * 0.34f).dp)) {
                    Seq(node.sub, small, color, font)
                }
            }

            else -> Seq(node.base, size, color, font)
        }
    }
}
