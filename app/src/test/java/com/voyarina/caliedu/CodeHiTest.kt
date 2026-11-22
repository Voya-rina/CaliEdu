package com.voyarina.caliedu

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.voyarina.caliedu.ui.CodeHi
import com.voyarina.caliedu.ui.UiStyle
import com.voyarina.caliedu.ui.skinOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 代码高亮。
 *
 * 两条底线：字符一个不能丢（丢了代码就编译不过，学生照着抄也抄不对），
 * 认得的语言要真的上色，不认得的宁可不上色。
 */
class CodeHiTest {

    private val skin = skinOf(UiStyle.PRINT, dark = false)

    private fun colorAt(text: AnnotatedString, index: Int): Color? =
        text.spanStyles.lastOrNull { index >= it.start && index < it.end }?.item?.color

    private fun colourOf(code: String, lang: String, needle: String): Color? {
        val out = CodeHi.highlight(code, lang, skin)
        val at = out.text.indexOf(needle)
        assertTrue("原文里没有「$needle」", at >= 0)
        return colorAt(out, at)
    }

    @Test
    fun cKeepsEveryCharacter() {
        val code = """
            #include <stdio.h>

            int main(void) {
                // 求和
                int s = 0;
                for (int i = 1; i <= n; i++) s += i;
                printf("%d\n", s);
                return 0;
            }
        """.trimIndent()
        assertEquals(code, CodeHi.highlight(code, "c", skin).text)
    }

    @Test
    fun cKeywordCommentStringAndNumber() {
        val code = "int x = 1; // 初值\nchar *s = \"hi\";\n"
        assertEquals(skin.codeKw, colourOf(code, "c", "int"))
        assertEquals(skin.codeCmt, colourOf(code, "c", "// 初值"))
        assertEquals(skin.codeStr, colourOf(code, "c", "\"hi\""))
        assertEquals(skin.codeNum, colourOf(code, "c", "1"))
    }

    @Test
    fun preprocessorLineIsOneToken() {
        val code = "#include <stdio.h>\nint main(void) { return 0; }\n"
        assertEquals(skin.codeKw, colourOf(code, "c", "#include"))
    }

    @Test
    fun blockCommentSpansLines() {
        val code = "/* 第一行\n   第二行 */\nint a = 2;\n"
        val out = CodeHi.highlight(code, "c", skin)
        assertEquals(skin.codeCmt, colorAt(out, out.text.indexOf("第一行")))
        assertEquals("跨行注释的后半段也要算注释", skin.codeCmt, colorAt(out, out.text.indexOf("第二行")))
        assertEquals(skin.codeKw, colorAt(out, out.text.indexOf("int")))
    }

    @Test
    fun functionCallIsMarked() {
        val code = "printf(\"%d\", n);\n"
        assertEquals(skin.codeFn, colourOf(code, "c", "printf"))
    }

    @Test
    fun cppAndHeaderTagsUseTheSameGrammar() {
        listOf("cpp", "c++", "cc", "h", "hpp").forEach { tag ->
            assertEquals(tag, skin.codeKw, colourOf("int main() { return 0; }", tag, "int"))
        }
    }

    @Test
    fun pythonStillHighlights() {
        val code = "def add(a, b):\n    return a + b  # 相加\n"
        assertEquals(skin.codeKw, colourOf(code, "python", "def"))
        assertEquals(skin.codeCmt, colourOf(code, "python", "# 相加"))
        assertEquals(code, CodeHi.highlight(code, "py", skin).text)
    }

    @Test
    fun unknownLanguageStaysPlain() {
        val code = "SELECT * FROM t WHERE id = 1;"
        val out = CodeHi.highlight(code, "sql", skin)
        assertEquals(code, out.text)
        assertEquals("整段只有一个样式，等于没高亮", 1, out.spanStyles.size)
        assertEquals(skin.codePlain, out.spanStyles.single().item.color)
    }
}
