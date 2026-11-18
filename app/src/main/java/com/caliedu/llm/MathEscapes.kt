package com.caliedu.llm

/**
 * 公式里的反斜杠会被 JSON 的合法转义吃掉：
 * `$\frac{a}{b}$` 里的 `\f` 变成换页符，`$\nu$` 里的 `\n` 变成换行，`$\times$` 里的 `\t` 变成制表符。
 * 这些都发生在 `$...$` 之内，而公式里不可能真的有换页符，所以就地还原是安全的；
 * 公式之外一个字都不动。
 */
object MathEscapes {

    fun restore(source: String): String {
        if (source.indexOf('$') < 0) return source
        val out = StringBuilder(source.length + 16)
        var inMath = false
        for (c in source) {
            when {
                c == '$' -> {
                    inMath = !inMath
                    out.append(c)
                }

                inMath && c.code < 0x20 -> {
                    val letter = when (c) {
                        '\u000C' -> 'f'
                        '\u0008' -> 'b'
                        '\n' -> 'n'
                        '\r' -> 'r'
                        '\t' -> 't'
                        else -> null
                    }
                    if (letter != null) out.append('\\').append(letter) else out.append(c)
                }

                else -> out.append(c)
            }
        }
        return out.toString()
    }
}
