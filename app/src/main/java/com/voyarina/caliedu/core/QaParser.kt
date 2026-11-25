package com.voyarina.caliedu.core

/**
 * 讲义里的问答对。
 *
 * 教材、笔记、面试题集里大量「Q: … A: …」的写法，本来就是问题 + 答案，
 * 没必要再让模型重写一遍。识别出来之后有两条用处：
 * 阅读时把答案遮住、点开对照；出题时只让模型补干扰项。
 */
data class Qa(val question: String, val answer: String)

object QaParser {

    /** 行首的引用 / 列表符号。答案里跟着一串 `>` 时也要归一，否则点开遮罩会露出一列尖括号。 */
    private val leadMark = Regex("""^\s*(?:(?:>\s*)+|[-*+]\s+|\d+[.)]\s+)""")

    /**
     * 问题前缀。
     *
     * 讲义里的写法比想象中杂：`**Q：**`、`**Q**：`、`【问】`、`Ｑ：`、`- 问1.` 都出现过。
     * 关键是允许标签本身被加粗/括号包住，也允许整段有多组问答；
     * 只用单一形态去认，结果就是整段问答原样躺在正文里，遮罩和出题全失效。
     */
    private val qPrefix = Regex(
        """^\s*(?:[-*+]\s+|\d+[.)]\s+)*(?:【|\[)?\s*(?:\*\*|__)?\s*(?:question|q|问题|问)\s*\d*\s*(?:\*\*|__)?\s*[:：.、)）】\]]\s*(?:\*\*|__)?\s*""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )

    private val aPrefix = Regex(
        """(?:^|\s)(?:[-*+]\s+|\d+[.)]\s+)*(?:【|\[)?\s*(?:\*\*|__)?\s*(?:answer|a|答案|答)\s*\d*\s*(?:\*\*|__)?\s*[:：.、)）】\]]\s*(?:\*\*|__)?\s*""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )

    /** 太长的「问题」多半是正文里恰好出现了「问：」，不是真的问答。 */
    private const val MAX_QUESTION = 220

    private const val MAX_ANSWER = 4000

    /** 只折全角字母：问号和句号是正文的一部分，折了会改变用户看到的原文。 */
    internal fun normalize(text: String): String =
        text.replace("\r\n", "\n").lines().joinToString("\n") { fold(stripLead(it)) }

    private fun stripLead(line: String): String {
        var out = line
        while (true) {
            val m = leadMark.find(out) ?: return out
            if (m.range.first != 0) return out
            out = out.substring(m.range.last + 1)
        }
    }

    private fun fold(line: String): String = buildString(line.length) {
        line.forEach { ch ->
            val folded = when (ch) {
                in '\uFF21'..'\uFF3A' -> ('A'.code + (ch.code - 0xFF21)).toChar()
                in '\uFF41'..'\uFF5A' -> ('a'.code + (ch.code - 0xFF41)).toChar()
                else -> ch
            }
            append(folded)
        }
    }

    /** 一段里的一组问答；同一段有多组时取第一组。 */
    fun split(raw: String): Qa? = splitAll(raw).firstOrNull()

    /** 一段里的全部问答，按出现顺序。 */
    fun splitAll(raw: String): List<Qa> {
        val text = normalize(raw)
        val questions = qPrefix.findAll(text).toList()
        if (questions.isEmpty()) return emptyList()
        val answers = aPrefix.findAll(text).toList()
        val out = mutableListOf<Qa>()
        var aAt = 0
        questions.forEachIndexed { index, q ->
            // 答案必须在这个问题之后；同一段里上一组的答案不能算到这一组。
            while (aAt < answers.size && answers[aAt].range.first < q.range.last) aAt++
            val a = answers.getOrNull(aAt) ?: return@forEachIndexed
            val question = clean(text.substring(q.range.last + 1, a.range.first))
            val end = questions.getOrNull(index + 1)?.range?.first ?: text.length
            val answer = text.substring(a.range.last + 1, end).trim()
            build(question, answer)?.let { out += it }
            aAt++
        }
        return out
    }

    /** 整段只有问题（答案在下一段）。 */
    fun questionOf(raw: String): String? {
        val text = normalize(raw)
        val q = qPrefix.find(text) ?: return null
        val rest = text.substring(q.range.last + 1)
        // 带 A 的交给 split，这里只管纯问题
        if (aPrefix.containsMatchIn(rest)) return null
        return clean(rest).takeIf { it.isNotBlank() }
    }

    /** 整段只有答案。 */
    fun answerOf(raw: String): String? {
        val text = normalize(raw)
        val a = aPrefix.find(text) ?: return null
        return text.substring(a.range.last + 1).trim().takeIf { it.isNotBlank() }
    }

    /** 整篇扫描一遍，给「出题」用。 */
    fun parse(raw: String): List<Qa> {
        val out = mutableListOf<Qa>()
        val blocks = raw.replace("\r\n", "\n")
            .split(Regex("""\n\s*\n"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        var i = 0
        while (i < blocks.size) {
            val block = blocks[i]
            val same = splitAll(block)
            if (same.isNotEmpty()) {
                out += same
                i++
                continue
            }
            val q = questionOf(block)
            if (q != null && i + 1 < blocks.size) {
                val a = answerOf(blocks[i + 1])
                if (a != null) {
                    build(q, a)?.let { out += it }
                    i += 2
                    continue
                }
            }
            i++
        }
        return out
    }

    private fun build(question: String, answer: String): Qa? {
        if (question.isBlank() || answer.isBlank()) return null
        if (question.length > MAX_QUESTION) return null
        if (answer.length > MAX_ANSWER) return null
        return Qa(question, answer)
    }

    /** 去掉残留的强调符号和首尾空白。问号是问题的一部分，不能去掉。 */
    private fun clean(raw: String): String =
        raw.trim().trim('*', '_', ' ', '\u3000').trim()
}
