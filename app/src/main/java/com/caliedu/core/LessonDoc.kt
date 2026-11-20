package com.caliedu.core

import com.caliedu.model.Block
import com.caliedu.model.LessonPlan
import com.caliedu.model.StepContent

/**
 * 把一课导出成两种格式：
 * - Markdown：直接喂给你桌面上那套 #md2pdf，版式不用重造。
 * - HTML：App 内走 WebView 打印成 PDF。
 */
object LessonDoc {

    private val codeFence = Regex("```(?:python|py)?\\s*\\n([\\s\\S]*?)```")

    /** 取讲义里第一段 Python，用来给「试跑」预填。 */
    fun firstCode(md: String): String = codeFence.find(md)?.groupValues?.getOrNull(1)?.trim().orEmpty()

    fun markdown(plan: LessonPlan, contents: Map<Int, StepContent>): String = buildString {
        append("# ").append(plan.title.ifBlank { "未命名" }).append("\n\n")
        if (plan.oneLine.isNotBlank()) append("> ").append(plan.oneLine).append("\n\n")
        val tags = Category.tagsOf(plan)
        append("`").append(tags.joinToString("` `")).append("`\n\n")
        if (plan.knowledge.isNotEmpty()) {
            append("**知识点**：").append(plan.knowledge.joinToString("、")).append("\n\n")
        }
        append("---\n\n")
        plan.steps.forEachIndexed { index, brief ->
            val content = contents[brief.n]
            append("## ").append(brief.n).append(". ").append(brief.title)
            if (brief.stage.isNotBlank()) append("（").append(brief.stage).append("）")
            append("\n\n")
            if (content == null) {
                append("_这一步的讲义还没有生成。_\n\n")
                return@forEachIndexed
            }
            content.blocks.forEach { block ->
                append(block.md.trim()).append("\n\n")
            }
            if (content.question.prompt.isNotBlank()) {
                append("### 想一想\n\n").append(content.question.prompt).append("\n\n")
                if (content.question.choices.isNotEmpty()) {
                    content.question.choices.forEach { append("- ").append(it.label).append('\n') }
                    append('\n')
                }
                if (content.question.answer.isNotBlank()) append("**答案**：").append(content.question.answer).append("\n\n")
            }
            if (content.summary.isNotEmpty()) {
                append("### 小结\n\n")
                content.summary.forEach { append("- ").append(it).append('\n') }
                append('\n')
            }
            append("---\n\n")
        }
    }

    fun html(plan: LessonPlan, contents: Map<Int, StepContent>, mobile: Boolean = false): String {
        val body = StringBuilder()
        body.append("<h1>").append(esc(plan.title.ifBlank { "未命名" })).append("</h1>")
        if (plan.oneLine.isNotBlank()) body.append("<blockquote><p>").append(esc(plan.oneLine)).append("</p></blockquote>")
        body.append("<p>").append(esc(Category.tagsOf(plan).joinToString(" / "))).append("</p>")
        if (plan.knowledge.isNotEmpty()) {
            body.append("<p><strong>知识点</strong>：").append(esc(plan.knowledge.joinToString("、"))).append("</p>")
        }
        plan.steps.forEach { brief ->
            val content = contents[brief.n]
            body.append("<h2>").append(brief.n).append(". ").append(esc(brief.title)).append("</h2>")
            if (content == null) {
                body.append("<p><em>这一步的讲义还没有生成。</em></p>")
                return@forEach
            }
            content.blocks.forEach { body.append(Md2Html.convert(it.md)) }
            if (content.question.prompt.isNotBlank()) {
                body.append("<h3>想一想</h3>").append(Md2Html.convert(content.question.prompt))
                if (content.question.choices.isNotEmpty()) {
                    body.append("<ul>")
                    content.question.choices.forEach { body.append("<li>").append(Md2Html.inline(it.label)).append("</li>") }
                    body.append("</ul>")
                }
            }
            if (content.summary.isNotEmpty()) {
                body.append("<h3>小结</h3><ul>")
                content.summary.forEach { body.append("<li>").append(Md2Html.inline(it)).append("</li>") }
                body.append("</ul>")
            }
        }
        return wrap(body.toString(), mobile)
    }

    private fun wrap(body: String, mobile: Boolean): String {
        val page = if (mobile) "@page { size: 100mm 178mm; margin: 8mm 8mm 9mm; }"
        else "@page { size: A4; margin: 15mm 13mm 17mm 13mm; }"
        val base = if (mobile) "body { font-size: 10.2pt; line-height: 1.72; }"
        else "body { font-size: 11.5pt; line-height: 1.78; }"
        return """<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>CaliEdu</title>
<style>
$page
* { box-sizing: border-box; }
body { font-family: "PingFang SC","Noto Sans CJK SC","Microsoft YaHei",sans-serif;
       color:#1f2937; margin:0; overflow-wrap:anywhere; }
h1 { font-size: 19pt; color:#1e3a8a; border-bottom:3px solid #2563eb; padding-bottom:8px; margin:.2em 0 .8em; }
h2 { font-size: 14.5pt; color:#1e40af; border-left:5px solid #2563eb; padding-left:10px;
     margin:1.6em 0 .5em; page-break-after:avoid; }
h3 { font-size: 12.5pt; color:#312e81; margin:1.3em 0 .4em; page-break-after:avoid; }
p { text-align: justify; margin: 9px 0; }
pre { background:#f6f8fb; border:1px solid #dbe3ee; border-radius:8px; padding:11px 14px;
      white-space:pre-wrap; overflow-wrap:anywhere; font-size:9.6pt; line-height:1.6;
      page-break-inside:avoid; margin:11px 0; }
pre code { font-family: Consolas,"Cascadia Mono","Courier New",monospace; background:none; padding:0; }
code { font-family: Consolas,"Cascadia Mono",monospace; background:#eef2f8; border-radius:4px;
       color:#0f172a; padding:1px 5px; font-size:.87em; }
.kw{color:#7c3aed;font-weight:bold}.cmt{color:#64748b;font-style:italic}.str{color:#047857}
.num{color:#b45309}.fn{color:#0369a1;font-weight:bold}
table { border-collapse:collapse; width:100%; font-size:10pt; margin:12px 0; page-break-inside:avoid; }
th,td { border:1px solid #cbd5e1; text-align:left; vertical-align:top; padding:6px 9px; }
th { background:#eff6ff; color:#1e3a8a; }
tr:nth-child(even) td { background:#f8fafc; }
blockquote { border-left:5px solid #f59e0b; background:#fffbeb; color:#57534e;
             border-radius:0 8px 8px 0; margin:12px 0; padding:9px 14px; }
blockquote p { margin:4px 0; }
hr { border:none; border-top:1.5px dashed #cbd5e1; margin:20px 0; }
strong { color:#0f172a; }
ul,ol { margin:9px 0; padding-left:22px; }
li { margin:4px 0; }
.tag { display:inline-block; font-size:8.4pt; letter-spacing:.4px; padding:1px 7px;
       border-radius:9px; margin:10px 0 -4px; font-family:monospace; }
.tag.measured { background:#ecfdf5; color:#047857; border:1px solid #a7f3d0; }
.tag.inferred { background:#fffbeb; color:#b45309; border:1px solid #fde68a; }
.tag.unmeasured { background:#f8fafc; color:#64748b; border:1px solid #e2e8f0; }
.tex { font-family:"Latin Modern Math","STIX Two Math","Cambria Math",serif; }
.tex i { font-style:italic; }
.frac { display:inline-flex; flex-direction:column; align-items:center; vertical-align:middle;
        margin:0 .14em; font-size:.9em; line-height:1.2; }
.frac .num { border-bottom:1px solid currentColor; padding:0 .3em .06em; }
.frac .den { padding:.06em .3em 0; }
$base
</style></head><body>
$body
</body></html>"""
    }

    private fun esc(s: String): String = Md2Html.escape(s)
}

/** 极小的 Markdown → HTML，够覆盖讲义里实际出现的写法，不引第三方库。 */
object Md2Html {

    private fun esc(s: String): String = escape(s)

    fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    fun convert(md: String): String {
        val out = StringBuilder()
        val lines = md.replace("\r\n", "\n").lines()
        var i = 0
        var inList = false
        var inQuote = false

        fun closeList() {
            if (inList) {
                out.append("</ul>")
                inList = false
            }
        }

        fun closeQuote() {
            if (inQuote) {
                out.append("</blockquote>")
                inQuote = false
            }
        }

        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trimEnd()
            val t = line.trim()

            if (t.startsWith("```")) {
                closeList(); closeQuote()
                val info = t.removePrefix("```").trim()
                val lang = info.substringBefore(':').ifBlank { "" }
                val tag = info.substringAfter(':', "")
                val body = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    body.append(esc(lines[i])).append('\n')
                    i++
                }
                i++
                if (tag.isNotBlank()) {
                    val label = when (tag) {
                        "measured" -> "实测输出"
                        "inferred" -> "推断输出"
                        else -> "未实测"
                    }
                    out.append("<div class=\"tag ").append(tag).append("\">").append(label).append("</div>")
                }
                out.append("<pre><code class=\"lang-").append(esc(lang)).append("\">")
                    .append(highlight(body.toString(), lang)).append("</code></pre>")
                continue
            }

            when {
                t.isEmpty() -> {
                    closeList(); closeQuote()
                }

                t.startsWith("#") -> {
                    closeList(); closeQuote()
                    val level = t.takeWhile { it == '#' }.length.coerceIn(1, 6)
                    out.append("<h").append(level).append('>')
                        .append(inline(t.drop(level).trim())).append("</h").append(level).append('>')
                }

                t.startsWith(">") -> {
                    closeList()
                    if (!inQuote) {
                        out.append("<blockquote>")
                        inQuote = true
                    }
                    out.append("<p>").append(inline(t.removePrefix(">").trim())).append("</p>")
                }

                t == "---" || t == "***" -> {
                    closeList(); closeQuote()
                    out.append("<hr>")
                }

                t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ") -> {
                    closeQuote()
                    if (!inList) {
                        out.append("<ul>")
                        inList = true
                    }
                    out.append("<li>").append(inline(t.drop(2).trim())).append("</li>")
                }

                Regex("""^\d+[.)]\s""").containsMatchIn(t) -> {
                    closeQuote()
                    closeList()
                    out.append("<p class=\"li\">").append(inline(t)).append("</p>")
                }

                t.startsWith("|") && t.endsWith("|") -> {
                    closeList(); closeQuote()
                    val rows = mutableListOf<List<String>>()
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        val cells = lines[i].trim().trim('|').split('|').map { it.trim() }
                        if (!cells.all { it.matches(Regex(""":?-{2,}:?""")) }) rows += cells
                        i++
                    }
                    if (rows.isNotEmpty()) {
                        out.append("<table>")
                        rows.forEachIndexed { r, cells ->
                            out.append("<tr>")
                            cells.forEach { c ->
                                out.append(if (r == 0) "<th>" else "<td>").append(inline(c))
                                    .append(if (r == 0) "</th>" else "</td>")
                            }
                            out.append("</tr>")
                        }
                        out.append("</table>")
                    }
                    continue
                }

                else -> {
                    closeList(); closeQuote()
                    out.append("<p>").append(inline(t)).append("</p>")
                }
            }
            i++
        }
        closeList(); closeQuote()
        return out.toString()
    }

    fun inline(text: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        sb.append("<code>").append(esc(text.substring(i + 1, end))).append("</code>")
                        i = end + 1
                    } else {
                        sb.append("`"); i++
                    }
                }

                c == '$' -> {
                    val end = text.indexOf('$', i + 1)
                    if (end > i + 1) {
                        sb.append("<span class=\"tex\">").append(tex(text.substring(i + 1, end))).append("</span>")
                        i = end + 1
                    } else {
                        sb.append("$"); i++
                    }
                }

                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        sb.append("<strong>").append(inline(text.substring(i + 2, end))).append("</strong>")
                        i = end + 2
                    } else {
                        sb.append("**"); i += 2
                    }
                }

                c == '[' -> {
                    val close = text.indexOf("](", i)
                    val end = if (close > 0) text.indexOf(')', close) else -1
                    if (close > 0 && end > close) {
                        val label = text.substring(i + 1, close)
                        val href = text.substring(close + 2, end)
                        sb.append("<a href=\"").append(esc(href)).append("\">").append(inline(label)).append("</a>")
                        i = end + 1
                    } else {
                        sb.append('['); i++
                    }
                }

                else -> {
                    sb.append(esc(c.toString())); i++
                }
            }
        }
        return sb.toString()
    }

    /** 极简 TeX：分数、上下标、常见符号。目标是不让 \frac 原样露出来。 */
    fun tex(src: String): String {
        var s = src
        for ((from, to) in SYMBOLS) s = s.replace(from, to)
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            when {
                s.startsWith("\\frac", i) -> {
                    val a = group(s, i + 5)
                    val b = if (a != null) group(s, a.second) else null
                    if (a != null && b != null) {
                        out.append("<span class=\"frac\"><span class=\"num\">").append(tex(a.first))
                            .append("</span><span class=\"den\">").append(tex(b.first)).append("</span></span>")
                        i = b.second
                    } else {
                        out.append("frac")
                        i += 5
                    }
                }

                s.startsWith("\\sqrt", i) -> {
                    val a = group(s, i + 5)
                    if (a != null) {
                        out.append("√(").append(tex(a.first)).append(")")
                        i = a.second
                    } else {
                        out.append("√"); i += 5
                    }
                }

                s[i] == '^' || s[i] == '_' -> {
                    val tag = if (s[i] == '^') "sup" else "sub"
                    val a = group(s, i + 1)
                    if (a != null) {
                        out.append('<').append(tag).append('>').append(tex(a.first))
                            .append("</").append(tag).append('>')
                        i = a.second
                    } else {
                        i++
                    }
                }

                s[i] == '\\' -> {
                    val m = Regex("""\\([a-zA-Z]+)""").find(s, i)
                    if (m != null && m.range.first == i) {
                        out.append("<i>").append(m.groupValues[1]).append("</i>")
                        i = m.range.last + 1
                    } else {
                        i++
                    }
                }

                else -> {
                    out.append(esc(s[i].toString())); i++
                }
            }
        }
        return out.toString()
    }

    private fun group(s: String, from: Int): Pair<String, Int>? {
        var i = from
        while (i < s.length && s[i] == ' ') i++
        if (i >= s.length) return null
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
            return null
        }
        // 单字符参数：x^2 的 2
        return s[i].toString() to i + 1
    }

    private val SYMBOLS = listOf(
        "\\times" to " × ", "\\cdot" to " · ", "\\div" to " ÷ ",
        "\\le" to " ≤ ", "\\leq" to " ≤ ", "\\ge" to " ≥ ", "\\geq" to " ≥ ",
        "\\ne" to " ≠ ", "\\neq" to " ≠ ", "\\approx" to " ≈ ",
        "\\pm" to " ± ", "\\infty" to " ∞ ", "\\sum" to " Σ", "\\prod" to " Π",
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
        "\\lambda" to "λ", "\\mu" to "μ", "\\sigma" to "σ", "\\theta" to "θ",
        "\\pi" to "π", "\\eta" to "η", "\\epsilon" to "ε", "\\Delta" to "Δ",
        "\\min" to "min", "\\max" to "max", "\\log" to "log", "\\ln" to "ln",
        "\\sin" to "sin", "\\cos" to "cos", "\\hat" to "", "\\bar" to "",
        "\\left" to "", "\\right" to "", "\\," to " ", "\\;" to " ", "\\ " to " ",
        // 版式命令：留下内容，丢掉命令本身。否则导出的 PDF 里会出现 *quad*、*mathbb*。
        "\\quad" to "  ", "\\qquad" to "    ", "\\text" to "", "\\mathrm" to "",
        "\\mathbb" to "", "\\mathcal" to "", "\\vec" to "", "\\overline" to "",
        "\\cdots" to " ⋯ ", "\\ldots" to " … ", "\\to" to " → ", "\\Rightarrow" to " ⇒ ",
    )

    private val KEYWORDS = setOf(
        "def", "class", "return", "if", "elif", "else", "for", "while", "in", "not",
        "and", "or", "import", "from", "as", "with", "try", "except", "finally",
        "raise", "pass", "break", "continue", "lambda", "yield", "global", "None",
        "True", "False", "is", "assert", "del", "async", "await", "self",
    )

    /** 和 App 内 CodeHi 同色系，够用即可。 */
    fun highlight(code: String, lang: String): String {
        if (lang.isNotEmpty() && lang != "python" && lang != "py") return code
        val sb = StringBuilder()
        code.lines().forEachIndexed { index, row ->
            if (index > 0) sb.append('\n')
            sb.append(highlightLine(row))
        }
        return sb.toString()
    }

    private fun highlightLine(src: String): String {
        val hash = src.indexOf('#')
        val code = if (hash >= 0) src.substring(0, hash) else src
        val comment = if (hash >= 0) src.substring(hash) else ""
        val sb = StringBuilder()
        var i = 0
        while (i < code.length) {
            val c = code[i]
            when {
                c == '"' || c == '\'' -> {
                    var j = i + 1
                    while (j < code.length) {
                        if (code[j] == '\\') j += 2 else if (code[j] == c) { j++; break } else j++
                    }
                    sb.append("<span class=\"str\">").append(esc(code.substring(i, j.coerceAtMost(code.length))))
                        .append("</span>")
                    i = j
                }

                c.isDigit() && (i == 0 || !code[i - 1].isLetterOrDigit()) -> {
                    var j = i
                    while (j < code.length && (code[j].isDigit() || code[j] == '.')) j++
                    sb.append("<span class=\"num\">").append(code.substring(i, j)).append("</span>")
                    i = j
                }

                c.isLetter() || c == '_' -> {
                    var j = i
                    while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                    val word = code.substring(i, j)
                    if (word in KEYWORDS) {
                        sb.append("<span class=\"kw\">").append(word).append("</span>")
                    } else if (j < code.length && code[j] == '(') {
                        sb.append("<span class=\"fn\">").append(word).append("</span>")
                    } else {
                        sb.append(esc(word))
                    }
                    i = j
                }

                else -> {
                    sb.append(esc(c.toString())); i++
                }
            }
        }
        if (comment.isNotEmpty()) sb.append("<span class=\"cmt\">").append(esc(comment)).append("</span>")
        return sb.toString()
    }
}
