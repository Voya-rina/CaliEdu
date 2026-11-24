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

    /**
     * 行首的引用 / 列表符号。
     *
     * 讲义几乎不会把 `Q：` 顶格写——常见的是 `> **Q：** …`（引用块，用来做视觉分隔）
     * 和 `- **Q：** …`（列表）。原来只在「行首」找 Q，于是这些写法一条都认不出来。
     */
    private val leadMark = Regex("""^\s*(?:(?:>\s*)+|[-*+]\s+|\d+[.)]\s+)""")

    /** 问题前缀。中英文冒号、句点、顿号、右括号都见过；有的笔记会包一层 `**`。 */
    private val qPrefix = Regex(
        """^\s*(?:\*\*|__)?\s*(?:question|q|问题|问)\s*\d*\s*[:：.、)）]\s*(?:\*\*|__)?\s*""",
        RegexOption.IGNORE_CASE,
    )

    private val aPrefix = Regex(
        """(?:^|\s)(?:\*\*|__)?\s*(?:answer|a|答案|答)\s*\d*\s*[:：.、)）]\s*(?:\*\*|__)?\s*""",
        RegexOption.IGNORE_CASE,
    )

    /** 太长的「问题」多半是正文里恰好出现了「问：」，不是真的问答。 */
    private const val MAX_QUESTION = 220

    private const val MAX_ANSWER = 4000

    /**
     * 去掉每行行首的引用/列表记号，保留行结构。
     *
     * 匹配和取值都在归一化之后的文本上做：答案里跟着一串 `>` 的话，
     * 遮罩点开之后会出来一列尖括号，比不识别还难看。
     */
    internal fun normalize(text: String): String =
        text.replace("\r\n", "\n").lines().joinToString("\n") { stripLead(it) }

    private fun stripLead(line: String): String {
        var out = line
        while (true) {
            val m = leadMark.find(out) ?: return out
            if (m.range.first != 0) return out
            out = out.substring(m.range.last + 1)
        }
    }

    /** 一段里同时有 Q 和 A。 */
    fun split(raw: String): Qa? {
        val text = normalize(raw)
        val q = qPrefix.find(text) ?: return null
        val rest = text.substring(q.range.last + 1)
        val a = aPrefix.find(rest) ?: return null
        val question = clean(rest.substring(0, a.range.first))
        val answer = rest.substring(a.range.last + 1).trim()
        return build(question, answer)
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
            .map { normalize(it).trim() }
            .filter { it.isNotEmpty() }
        var i = 0
        while (i < blocks.size) {
            val block = blocks[i]
            val same = split(block)
            if (same != null) {
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
