package com.caliedu

import com.caliedu.ui.Tex
import com.caliedu.ui.TexNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 公式排版的手感靠肉眼验，但「解析成什么」可以钉死。
 * 这里守的是最常见的那几种式子，以及畸形输入不许把解析器卡死。
 */
class TexParseTest {

    private fun parse(src: String) = Tex.parse(src)

    @Test
    fun fractionSplitsIntoTwoRows() {
        val nodes = parse("\\frac{a}{b}")
        assertEquals(1, nodes.size)
        val frac = nodes[0] as TexNode.Frac
        assertEquals(listOf<TexNode>(TexNode.Run("a")), frac.num)
        assertEquals(listOf<TexNode>(TexNode.Run("b")), frac.den)
    }

    @Test
    fun nestedFraction() {
        val frac = parse("\\frac{\\frac{1}{2}}{3}")[0] as TexNode.Frac
        assertTrue(frac.num[0] is TexNode.Frac)
        assertEquals(listOf<TexNode>(TexNode.Run("3")), frac.den)
    }

    @Test
    fun superscriptAttachesToPreviousAtom() {
        val script = parse("x^{2}")[0] as TexNode.Scripts
        assertEquals(listOf<TexNode>(TexNode.Run("x")), script.base)
        assertEquals(listOf<TexNode>(TexNode.Run("2")), script.sup)
        assertEquals(null, script.sub)
    }

    @Test
    fun subscriptAndSuperscriptTogether() {
        val script = parse("a_{n+1}^{2}")[0] as TexNode.Scripts
        assertEquals(listOf<TexNode>(TexNode.Run("n+1")), script.sub)
        assertEquals(listOf<TexNode>(TexNode.Run("2")), script.sup)
    }

    @Test
    fun sumKeepsItsLimits() {
        val op = parse("\\sum_{i=1}^{n}")[0] as TexNode.BigOp
        assertEquals("Σ", op.sym)
        assertEquals(listOf<TexNode>(TexNode.Run("i=1")), op.sub)
        assertEquals(listOf<TexNode>(TexNode.Run("n")), op.sup)
    }

    @Test
    fun rootTakesAnIndex() {
        val root = parse("\\sqrt[3]{8}")[0] as TexNode.Sqrt
        assertEquals(listOf<TexNode>(TexNode.Run("3")), root.index)
        assertEquals(listOf<TexNode>(TexNode.Run("8")), root.body)
    }

    @Test
    fun symbolsBecomeSingleCharacters() {
        val nodes = parse("a \\le b \\times c")
        val text = nodes.filterIsInstance<TexNode.Run>().joinToString("") { it.text }
        assertEquals("a ≤ b × c", text)
    }

    @Test
    fun uprightTextIsMarked() {
        val run = parse("\\text{当且仅当}")[0] as TexNode.Run
        assertEquals("当且仅当", run.text)
        assertTrue(run.upright)
    }

    @Test
    fun brokenInputDoesNotHang() {
        listOf("\\frac", "{", "x^", "\\", "\\sqrt[", "}{", "a\\\\b", "\\begin{cases} a & b \\end{cases}").forEach { raw ->
            val nodes = parse(raw)
            assertTrue("解析 $raw 时抛了异常", nodes.isNotEmpty() || raw.isEmpty())
        }
    }

    @Test
    fun namesAreNotSwallowed() {
        // \le 是符号，\left( 是括号，别把 left 当成未知命令吐出来
        assertEquals("(", (parse("\\left( x \\right)").first() as TexNode.Run).text)
    }

    /** 认不出的命令必须消失。把 "quad"、"hat" 印在正文里，看上去就是渲染坏了。 */
    @Test
    fun unknownCommandsLeaveNothingBehind() {
        val unknown = parse("\\foo \\bar{x} \\baz")
        val text = unknown.filterIsInstance<TexNode.Run>().joinToString("") { it.text }
        assertFalse("未知命令漏出来了：$text", text.contains("foo"))
        assertFalse("未知命令漏出来了：$text", text.contains("baz"))
    }

    @Test
    fun spacingCommandsBecomeSpace() {
        val nodes = parse("a \\quad b")
        val text = nodes.filterIsInstance<TexNode.Run>().joinToString("") { it.text }
        assertTrue("应为 'a  b'，实际 '$text'", text.contains(" "))
        assertFalse(text.contains("quad"))
    }

    @Test
    fun accentKeepsItsArgument() {
        // 帽子横线画不出来，但被装饰的字母不能跟着丢
        val nodes = parse("\\hat{y}")
        val text = nodes.filterIsInstance<TexNode.Group>().flatMap { it.items }
            .filterIsInstance<TexNode.Run>().joinToString("") { it.text }
        assertEquals("y", text)
    }

    @Test
    fun blackboardBoldMapsToUnicode() {
        val run = parse("\\mathbb{R}")[0] as TexNode.Run
        assertEquals("ℝ", run.text)
    }

    @Test
    fun doubleBackslashBreaksTheLine() {
        val nodes = parse("a \\\\ b")
        assertTrue("应含 Break 节点，实际 $nodes", nodes.any { it is TexNode.Break })
    }
}
