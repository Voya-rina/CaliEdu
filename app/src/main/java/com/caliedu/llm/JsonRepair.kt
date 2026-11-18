package com.caliedu.llm

/**
 * 模型返回的 JSON 常有三类瑕疵：包了 markdown 围栏、前后有客套话、公式里的
 * 反斜杠被当成 JSON 转义吃掉（`\frac` 的 `\f` 是合法换页符，`\nu` 的 `\n` 是换行）。
 *
 * 这里只做结构修复：剥围栏、取完整的对象、补掉非法转义。
 * 被合法转义吃掉的反斜杠不在这里猜，交给 [LessonGenerator] 在公式区间内还原。
 */
object JsonRepair {

    fun extract(raw: String): String? {
        val text = raw.trim().removePrefix("\uFEFF")
        val body = extractFence(text) ?: text
        val candidate = balancedObject(body) ?: return null
        return repairIllegalEscapes(candidate)
    }

    private fun extractFence(text: String): String? {
        val start = text.indexOf("```")
        if (start < 0) return null
        val lineEnd = text.indexOf('\n', start)
        if (lineEnd < 0) return null
        val end = text.indexOf("```", lineEnd)
        return if (end < 0) text.substring(lineEnd + 1) else text.substring(lineEnd + 1, end)
    }

    private fun balancedObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return text.substring(start, i + 1)
                    }
                }
            }
        }
        return null
    }

    private const val LEGAL_ESCAPES = "\"\\/bfnrtu"

    private fun repairIllegalEscapes(json: String): String {
        val sb = StringBuilder(json.length + 32)
        var inString = false
        var i = 0
        while (i < json.length) {
            val c = json[i]
            if (!inString) {
                sb.append(c)
                if (c == '"') inString = true
                i++
                continue
            }
            if (c == '"') {
                sb.append(c)
                inString = false
                i++
                continue
            }
            if (c != '\\' || i + 1 >= json.length) {
                sb.append(c)
                i++
                continue
            }
            val next = json[i + 1]
            if (next in LEGAL_ESCAPES) {
                sb.append(c).append(next)
            } else {
                sb.append("\\\\").append(next)
            }
            i += 2
        }
        return sb.toString()
    }
}
