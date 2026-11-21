package com.caliedu

import com.caliedu.ui.UiStyle
import com.caliedu.ui.dropDuplicateTitle
import com.caliedu.ui.inlineMarkdown
import com.caliedu.ui.repairNewlines
import com.caliedu.ui.skinOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 正文里的公式兜底。
 *
 * 真实踩到的样子：模型写了 `$f(a)=...\frac{1}{2}f''(\xi)` 却不关 `$`，
 * 还用字面量 `\n` 当换行，整段公式被当正文印出来，满屏反斜杠。
 */
class MathTextRepairTest {

    private val skin = skinOf(UiStyle.PRINT)

    @Test
    fun literalBackslashNBecomesRealNewline() {
        assertEquals("第一行\n第二行", repairNewlines("第一行\\n第二行"))
        assertEquals("a\nb\nc", repairNewlines("a\\nb\\nc"))
        // 后面跟 f 也要换行：截图里就是 `$f(a)=0\nf'(x)>0`
        assertEquals("\$f(a)=0\nf'(x)>0", repairNewlines("\$f(a)=0\\nf'(x)>0"))
    }

    @Test
    fun latexCommandsAreNotSplit() {
        // \nabla / \neq / \nu 都以 \n 开头，不能被当成换行拆开
        assertEquals("\\nabla f", repairNewlines("\\nabla f"))
        assertEquals("\\neq", repairNewlines("\\neq"))
        assertEquals("\\nu", repairNewlines("\\nu"))
    }

    @Test
    fun unclosedDollarRendersAsMath() {
        val text = inlineMarkdown("\$f(a)=f(b)+\\frac{1}{2}f''(\\xi)", skin).text
        assertFalse("不该漏出美元号：$text", text.contains('$'))
        assertFalse("不该漏出反斜杠：$text", text.contains('\\'))
        assertTrue(text.contains("f(a)=f(b)"))
        assertTrue("分式要变成 1/2：$text", text.contains("1/2"))
    }

    @Test
    fun pairedDollarStillWorks() {
        val text = inlineMarkdown("由 \$x^{2}+1\$ 可得", skin).text
        assertFalse(text.contains('$'))
        assertTrue("上标要变成 Unicode：$text", text.contains("x²+1"))
    }

    @Test
    fun currencyIsNotSwallowed() {
        assertEquals("价格 \$5，另一件 \$8", inlineMarkdown("价格 \$5，另一件 \$8", skin).text)
    }

    @Test
    fun unclosedDollarWithChineseStaysText() {
        val text = inlineMarkdown("\$f(a)=0 是右端点锚点", skin).text
        assertTrue("公式后面跟中文时不能把中文吞进 mathStyle", text.contains("是右端点锚点"))
        assertTrue(text.contains('$'))
    }

    @Test
    fun screenshotParagraphNoLongerLeaksBackslashes() {
        // 截图里的原文：$ 不关 + 字面量 \n + 后面直接接中文
        val src = "\$f(a)=f(b)+f'(b)(a-b)+\\frac{1}{2}f''(\\xi)\\n(a-b)^2\\n得到带拉格朗日余项的一阶泰勒公式"
        val text = inlineMarkdown(src, skin).text
        assertFalse("不该再漏出反斜杠：$text", text.contains('\\'))
        assertFalse("不该再漏出美元号：$text", text.contains('$'))
        assertTrue(text.contains("得到带拉格朗日余项"))
        assertTrue("第一行公式要渲染出来：$text", text.contains("f(a)=f(b)"))
    }

    @Test
    fun screenshotConditionsLineStaysReadable() {
        val src = "\$f(a)=0\\nf'(x)>0\\nf''(x)>0\\nb>a\\n得到分工："
        val text = inlineMarkdown(src, skin).text
        assertFalse(text.contains('\\'))
        assertTrue(text.contains("得到分工"))
        assertTrue(text.contains("b>a"))
    }

    @Test
    fun duplicateStepTitleIsDropped() {
        assertEquals("正文第一段", dropDuplicateTitle("### 写出泰勒展开\n\n正文第一段", "写出泰勒展开"))
        // 动作名和步骤名不一样时要留着
        assertEquals("### 两边同加一\n正文", dropDuplicateTitle("### 两边同加一\n正文", "配方"))
        assertEquals("没有标题的正文", dropDuplicateTitle("没有标题的正文", "配方"))
    }
}
