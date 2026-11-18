package com.caliedu.core

import com.caliedu.model.Question

enum class Verdict { CORRECT, PARTIAL, WRONG }

object Judge {

    fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            val c = if (ch.code in 0xFF01..0xFF5E) (ch.code - 0xFEE0).toChar() else ch
            when {
                c.isLetterOrDigit() -> sb.append(c.lowercaseChar())
                c == '.' || c == '/' -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** 选择题零歧义，本地判就是终判；开放题本地只是初判，判不准时再问模型。 */
    fun isOpenEnded(question: Question): Boolean = question.kind != "choice"

    fun judge(question: Question, input: String): Verdict = when (question.kind) {
        "short" -> judgeShort(question, input)
        "checklist" -> judgeChecklist(question, input)
        else -> judgeChoice(question, input)
    }

    private fun judgeChoice(question: Question, input: String): Verdict {
        if (question.answer.isBlank()) return Verdict.WRONG
        val ok = normalize(input) == normalize(question.answer) ||
            question.choices.any { it.value == input && normalize(it.value) == normalize(question.answer) }
        return if (ok) Verdict.CORRECT else Verdict.WRONG
    }

    private fun judgeShort(question: Question, input: String): Verdict {
        val given = normalize(input)
        if (given.isEmpty()) return Verdict.WRONG
        val accepted = question.expect.split('|', '｜', ';', '；', ',', '，')
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
        if (accepted.isEmpty()) return Verdict.WRONG
        if (accepted.any { it == given }) return Verdict.CORRECT
        val hit = accepted.any { it.length >= 2 && (given.contains(it) || it.contains(given)) }
        return if (hit) Verdict.PARTIAL else Verdict.WRONG
    }

    /** 要点题：按命中比例给分，全中才算过。 */
    private fun judgeChecklist(question: Question, input: String): Verdict {
        val given = normalize(input)
        if (given.isEmpty()) return Verdict.WRONG
        if (question.rubric.isEmpty()) return judgeShort(question, input)
        val hits = checklistHits(question, input)
        val total = question.rubric.size
        return when {
            hits >= total -> Verdict.CORRECT
            hits >= maxOf(1, (total + 1) / 2) -> Verdict.PARTIAL
            else -> Verdict.WRONG
        }
    }

    fun checklistHits(question: Question, input: String): Int {
        val text = loose(input)
        return question.rubric.count { key -> loose(key).let { it.isNotEmpty() && text.contains(it) } }
    }

    /**
     * 要点匹配只丢空白、不丢符号：像 `1 - ss_res/ss_tot` 这种要点，
     * 一旦把 `-`、`/` 也删掉，就退化成几个字母，反而更容易误命中。
     */
    fun loose(raw: String): String = raw.filterNot { it.isWhitespace() }.lowercase()
}
