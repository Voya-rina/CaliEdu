package com.caliedu.stepedu.core

import com.caliedu.stepedu.model.Question

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

    fun judge(question: Question, input: String): Verdict = when (question.kind) {
        "short" -> judgeShort(question, input)
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
        val accepted = question.expect.split('|', '｜', ';', '；')
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
        if (accepted.isEmpty()) return Verdict.WRONG
        if (accepted.any { it == given }) return Verdict.CORRECT
        val hit = accepted.any { it.length >= 2 && (given.contains(it) || it.contains(given)) }
        return if (hit) Verdict.PARTIAL else Verdict.WRONG
    }

    fun checklistHits(question: Question, input: String): Int {
        val text = normalize(input)
        return question.rubric.count { key -> normalize(key).let { it.isNotEmpty() && text.contains(it) } }
    }
}
