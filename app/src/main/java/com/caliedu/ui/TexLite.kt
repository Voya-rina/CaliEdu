package com.caliedu.ui

/**
 * 极简 TeX → 可读文本。
 *
 * 不追求排版精度：手机上真正要看懂的是 `\frac{ss_res}{ss_tot}` 这种式子，
 * 把它变成 ss_res/ss_tot 就够了。上下标尽量用 Unicode，
 * 映射不到就退回 `^2` 的写法，绝不吐出一串反斜杠。
 */
object TexLite {

    private val SYMBOLS = listOf(
        "\\times" to "×", "\\cdot" to "·", "\\div" to "÷", "\\pm" to "±",
        "\\leq" to "≤", "\\le" to "≤", "\\geq" to "≥", "\\ge" to "≥",
        "\\neq" to "≠", "\\ne" to "≠", "\\approx" to "≈", "\\equiv" to "≡",
        "\\infty" to "∞", "\\sum" to "Σ", "\\prod" to "Π", "\\int" to "∫",
        "\\partial" to "∂", "\\nabla" to "∇", "\\in" to "∈", "\\subset" to "⊂",
        "\\cup" to "∪", "\\cap" to "∩", "\\to" to "→", "\\rightarrow" to "→",
        "\\Rightarrow" to "⇒", "\\leftrightarrow" to "↔",
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
        "\\epsilon" to "ε", "\\varepsilon" to "ε", "\\zeta" to "ζ", "\\eta" to "η",
        "\\theta" to "θ", "\\lambda" to "λ", "\\mu" to "μ", "\\nu" to "ν",
        "\\xi" to "ξ", "\\pi" to "π", "\\rho" to "ρ", "\\sigma" to "σ",
        "\\tau" to "τ", "\\phi" to "φ", "\\varphi" to "φ", "\\chi" to "χ",
        "\\psi" to "ψ", "\\omega" to "ω",
        "\\Delta" to "Δ", "\\Sigma" to "Σ", "\\Omega" to "Ω", "\\Lambda" to "Λ",
        "\\min" to "min", "\\max" to "max", "\\log" to "log", "\\ln" to "ln",
        "\\exp" to "exp", "\\sin" to "sin", "\\cos" to "cos", "\\tan" to "tan",
        "\\lim" to "lim", "\\arg" to "arg", "\\det" to "det",
        "\\left" to "", "\\right" to "", "\\quad" to "  ", "\\qquad" to "   ",
        "\\," to "", "\\;" to "", "\\!" to "", "\\ " to " ",
        "\\sqrt" to "√", "\\cdots" to "⋯", "\\ldots" to "…", "\\dots" to "…",
        "\\forall" to "∀", "\\exists" to "∃",
        "\\hat" to "", "\\bar" to "", "\\vec" to "", "\\overline" to "",
    )

    private val SUP = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ', 'T' to 'ᵀ',
    )

    private val SUB = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'i' to 'ᵢ', 'j' to 'ⱼ', 'k' to 'ₖ',
        'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ', 'p' to 'ₚ',
        'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ', 'v' to 'ᵥ', 'x' to 'ₓ',
    )

    fun convert(src: String): String {
        var s = src.trim()
        // 只有 \frac 需要递归，先把它处理掉，剩下的扫一遍就够。
        s = replaceFrac(s)
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            when {
                s[i] == '\\' -> {
                    val symbol = SYMBOLS.firstOrNull { s.startsWith(it.first, i) }
                    if (symbol != null) {
                        out.append(symbol.second)
                        i += symbol.first.length
                    } else {
                        val word = Regex("""\\[a-zA-Z]+""").find(s, i)
                        if (word != null && word.range.first == i) {
                            // 认不出的命令直接丢掉。留着命令名会把 \hat{y} 念成 "haty"，
                            // 比少一个符号更糟——学生看到的是乱码，不是公式。
                            i = word.range.last + 1
                        } else {
                            i++
                        }
                    }
                }

                s[i] == '^' || s[i] == '_' -> {
                    val sup = s[i] == '^'
                    val (body, next) = group(s, i + 1)
                    val map = if (sup) SUP else SUB
                    val mapped = body.map { map[it] }
                    if (body.isNotEmpty() && mapped.all { it != null }) {
                        out.append(mapped.joinToString("") { it.toString() })
                    } else if (body.isNotEmpty()) {
                        // 映射不到就退回普通写法，但里面的命令要先翻译掉
                        out.append(convert(body))
                    }
                    i = next
                }

                s[i] == '{' || s[i] == '}' -> i++
                s[i] == '~' -> {
                    out.append(' ')
                    i++
                }

                else -> {
                    out.append(s[i])
                    i++
                }
            }
        }
        return out.toString().replace("  ", " ").trim()
    }

    /** \frac{a}{b} → a/b，分子分母带空格时加括号。 */
    private fun replaceFrac(src: String): String {
        var out = src
        var guard = 0
        while (guard++ < 20) {
            val start = out.indexOf("\\frac")
            if (start < 0) return out
            val a = group(out, start + 5)
            val b = group(out, a.second)
            val num = unwrap(a.first)
            val den = unwrap(b.first)
            val text = if (den.contains(' ')) "($num)/($den)" else "$num/$den"
            out = out.substring(0, start) + text + out.substring(b.second)
        }
        return out
    }

    private fun unwrap(raw: String): String = convert(raw)

    private fun group(s: String, from: Int): Pair<String, Int> {
        var i = from
        while (i < s.length && s[i] == ' ') i++
        if (i >= s.length) return "" to i
        if (s[i] == '{') {
            var depth = 0
            val start = i
            while (i < s.length) {
                when (s[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return s.substring(start + 1, i) to i + 1
                    }
                }
                i++
            }
            return s.substring(start + 1) to s.length
        }
        return s[i].toString() to i + 1
    }
}
