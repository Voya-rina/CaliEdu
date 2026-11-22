package com.voyarina.caliedu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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

    /** 只是把一段内容包起来，本身不画东西。重音、字体、\boxed 这类共用。 */
    data class Group(val items: List<TexNode>) : TexNode

    /** `\\`：换行。由 MathLine 切成多行排，不是空格。 */
    data object Break : TexNode
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

    /** 纯间距命令：画一个空格，不留命令名。 */
    private val spacing = setOf(
        "quad", "qquad", "enspace", "enskip", "thinspace", "medspace", "thickspace",
        "hfill", "hfil", "hss", "space", "negthinspace",
    )

    /** 只影响排版位置、不产出内容。 */
    private val invisible = setOf(
        "displaystyle", "textstyle", "scriptstyle", "scriptscriptstyle",
        "limits", "nolimits", "mathstrut", "strut", "relax", "nonumber", "notag",
        "big", "Big", "bigg", "Bigg", "bigl", "bigr", "Bigl", "Bigr", "middle",
        "allowbreak", "nobreak",
    )

    /** 吃掉一个参数的命令：参数描述的是版式（标签、间距、颜色），不是公式内容。 */
    private val swallowArg = setOf(
        "label", "tag", "hspace", "vspace", "hskip", "vskip", "kern", "mkern",
        "color", "pagecolor", "phantom", "hphantom", "vphantom",
    )

    /** 重音与装饰：手机排版画不准帽子横线，保留被装饰的内容、丢掉装饰。 */
    private val accents = setOf(
        "hat", "widehat", "bar", "overline", "underline", "vec", "overrightarrow",
        "tilde", "widetilde", "dot", "ddot", "acute", "grave", "breve", "check", "mathring",
    )

    /** 字体切换：内容照排，字形不换。 */
    private val typefaces = setOf(
        "mathcal", "mathfrak", "mathscr", "mathit", "mathsf", "mathtt",
        "boldsymbol", "bm", "mathnormal",
    )

    /** 语义包裹：把内容原样排出来就行。 */
    private val passthrough = setOf(
        "boxed", "underbrace", "overbrace", "hbox", "mbox",
        "mathord", "mathbin", "mathrel", "mathop", "mathopen", "mathclose", "mathpunct", "smash",
    )

    /** `\mathbb{R}` 这类黑体字母，单字母能直接换成 Unicode。 */
    private val blackboard = mapOf(
        "R" to "ℝ", "N" to "ℕ", "Z" to "ℤ", "Q" to "ℚ", "C" to "ℂ",
        "P" to "ℙ", "H" to "ℍ", "E" to "𝔼",
    )

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
                    '\\' -> TexNode.Break
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
                    // \left( → "(" ；\left. → 什么都不画；\left\{ → 交给转义分支
                    when {
                        i >= s.length -> TexNode.Run("")
                        s[i] == '.' -> {
                            i++
                            TexNode.Run("")
                        }

                        s[i] == '\\' -> readCommand()
                        else -> TexNode.Run(s[i].toString().also { i++ })
                    }
                }

                name == "begin" || name == "end" -> {
                    rawGroup()
                    TexNode.Run("")
                }

                name == "mathbb" -> {
                    val body = readGroup()
                    val only = (body.singleOrNull() as? TexNode.Run)?.text
                    if (only != null && blackboard.containsKey(only)) {
                        TexNode.Run(blackboard.getValue(only), upright = true)
                    } else {
                        TexNode.Group(body)
                    }
                }

                bigOps.containsKey(name) -> TexNode.BigOp(bigOps.getValue(name), null, null)

                functions.contains(name) -> TexNode.Run(name, upright = true)

                symbols.containsKey(name) -> TexNode.Run(symbols.getValue(name))

                typefaces.contains(name) -> TexNode.Group(readGroup())

                accents.contains(name) -> TexNode.Group(readGroup())

                passthrough.contains(name) -> TexNode.Group(readGroup())

                spacing.contains(name) -> TexNode.Run(if (name == "negthinspace") "" else " ")

                invisible.contains(name) -> TexNode.Run("")

                swallowArg.contains(name) -> {
                    readGroup()
                    TexNode.Run("")
                }

                // 认不出的命令一律丢掉。宁可少画一个记号，也不能把 "quad"、"hat" 这种
                // 命令名当正文印出来——那比留白更像坏了。
                else -> TexNode.Run("")
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
        // `\\` 把一条公式切成多行：cases / 矩阵 / 多步推导都靠它，不能被压成一个空格。
        val rows = remember(nodes) {
            val out = mutableListOf<List<TexNode>>()
            val cur = mutableListOf<TexNode>()
            nodes.forEach { node ->
                if (node is TexNode.Break) {
                    if (cur.isNotEmpty()) {
                        out.add(cur.toList())
                        cur.clear()
                    }
                } else {
                    cur.add(node)
                }
            }
            if (cur.isNotEmpty()) out.add(cur.toList())
            if (out.isEmpty()) out.add(emptyList())
            out
        }
        // 长式子（连等、长分式）在手机上放不下，给它一条横向的出路，别裁掉。
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val viewport = maxWidth
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .widthIn(min = viewport),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (rows.size == 1) {
                    Seq(rows[0], size, color, font)
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy((size.value * 0.2f).dp),
                    ) {
                        rows.forEach { Seq(it, size, color, font) }
                    }
                }
            }
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

            is TexNode.Group -> Seq(node.items, size, color, font)

            // Break 正常会被 MathLine 切走；漏到这里的（比如嵌在上下标里）当空格处理。
            TexNode.Break -> Text(
                text = " ",
                fontFamily = font,
                fontSize = size,
                color = color,
            )

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
