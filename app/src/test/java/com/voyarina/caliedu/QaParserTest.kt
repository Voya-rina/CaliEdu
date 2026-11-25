package com.voyarina.caliedu

import com.voyarina.caliedu.core.QaParser
import com.voyarina.caliedu.ui.MdNode
import com.voyarina.caliedu.ui.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 讲义里的问答识别。
 *
 * 认错一个格式的代价很直接：答案会原样排在问题下面，遮罩和出题都没了。
 */
class QaParserTest {

    @Test
    fun sameParagraphIsSplit() {
        val qa = QaParser.split("Q: 什么是梯度下降？ A: 沿着负梯度方向迭代更新参数。")
        assertEquals("什么是梯度下降？", qa?.question)
        assertEquals("沿着负梯度方向迭代更新参数。", qa?.answer)
    }

    @Test
    fun chineseColonAndBoldAreAccepted() {
        val qa = QaParser.split("**问题：** 过拟合怎么判断  **答案：** 训练集好、验证集差")
        assertEquals("过拟合怎么判断", qa?.question)
        assertEquals("训练集好、验证集差", qa?.answer)
    }

    @Test
    fun questionAndAnswerInSeparateBlocks() {
        val list = QaParser.parse(
            """
            Q: 什么是学习率？

            A: 每次参数更新的步长。

            Q: 太大有什么问题？

            A: 会震荡甚至发散。
            """.trimIndent()
        )
        assertEquals(2, list.size)
        assertEquals("什么是学习率？", list[0].question)
        assertEquals("会震荡甚至发散。", list[1].answer)
    }

    @Test
    fun answerWithoutQuestionIsIgnored() {
        assertNull(QaParser.split("A: 只有答案没有题目"))
        assertEquals(0, QaParser.parse("A: 只有答案没有题目").size)
    }

    @Test
    fun longQuestionIsNotAQuestion() {
        val long = "Q: " + "这个说法其实是在正文里出现的".repeat(20) + " A: 答案"
        assertNull(QaParser.split(long))
    }

    @Test
    fun markdownGetsQuizNodes() {
        val nodes = parseMarkdown(
            """
            这一段是普通正文，不该被动。

            Q: 为什么归一化能加快收敛？
            A: 让各维尺度接近，梯度方向更稳。

            后面还有正文。
            """.trimIndent()
        )
        val quiz = nodes.filterIsInstance<MdNode.Quiz>()
        assertEquals(1, quiz.size)
        assertTrue(quiz[0].question.contains("归一化"))
        assertTrue(quiz[0].answer.contains("梯度方向"))
        // 答案不能留在普通段落里
        val paras = nodes.filterIsInstance<MdNode.Para>().joinToString(" ") { it.text }
        assertFalse(paras.contains("梯度方向更稳"))
    }

    @Test
    fun quizAcrossBlankLineAlsoWorks() {
        val nodes = parseMarkdown("Q: 什么是指针？\n\nA: 存放地址的变量。")
        assertEquals(1, nodes.filterIsInstance<MdNode.Quiz>().size)
    }

    /**
     * 讲义里最常见的那种写法：整段包在引用块里。
     * 认不出来就等于这个功能完全没生效——样例讲义全是这个格式。
     */
    @Test
    fun blockQuoteIsAccepted() {
        val qa = QaParser.split(
            """
            > **Q：为什么作者要用 1 / -1 / 0 来编码性别？**
            >
            > **A：** 三个原因，一个比一个深。
            """.trimIndent()
        )
        assertEquals("为什么作者要用 1 / -1 / 0 来编码性别？", qa?.question)
        assertEquals("三个原因，一个比一个深。", qa?.answer)
    }

    @Test
    fun blockQuoteAnswersKeepNoMarkers() {
        val qa = QaParser.split(
            """
            > **Q：** 归一化为什么要减最小值？
            >
            > **A：** 保证最小值落在 0。
            > 1. 尺度一致
            > 2. 平移对齐
            """.trimIndent()
        )
        assertFalse(qa!!.answer.contains(">"))
        assertTrue(qa.answer.contains("平移对齐"))
    }

    @Test
    fun wholeFileWithQuotesIsParsed() {
        val raw = """
            # 讲义

            > **Q：** 什么是学习率？

            > **A：** 每次参数更新的步长。

            > **Q：** 太大有什么问题？

            > **A：** 会震荡甚至发散。
        """.trimIndent()
        val list = QaParser.parse(raw)
        assertEquals(2, list.size)
        assertEquals("什么是学习率？", list[0].question)
        assertEquals("会震荡甚至发散。", list[1].answer)
    }

    @Test
    fun numberedPrefixesAreAccepted() {
        val qa = QaParser.split("Q1：什么是梯度？ A1：函数增长最快的方向。")
        assertEquals("什么是梯度？", qa?.question)
        assertEquals("函数增长最快的方向。", qa?.answer)
    }

    @Test
    fun listPrefixIsStripped() {
        val qa = QaParser.split("- **问：** 为什么要分训练集？\n- **答：** 用来判断过拟合。")
        assertEquals("为什么要分训练集？", qa?.question)
        assertEquals("用来判断过拟合。", qa?.answer)
    }

    @Test
    fun boldLabelOutsideColonIsAccepted() {
        val qa = QaParser.split("**Q**：为什么用交叉验证？ **A**：避免单次划分碰运气。")
        assertEquals("为什么用交叉验证？", qa?.question)
        assertEquals("避免单次划分碰运气。", qa?.answer)
    }

    @Test
    fun bracketAndFullWidthLabelsAreAccepted() {
        val qa = QaParser.split("【问】什么是过拟合？\n【答】记住了训练样本的细节。")
        assertEquals("什么是过拟合？", qa?.question)
        assertEquals("记住了训练样本的细节。", qa?.answer)

        val full = QaParser.split("Ｑ：学习率是什么？ Ａ：参数更新的步长。")
        assertEquals("学习率是什么？", full?.question)
        assertEquals("参数更新的步长。", full?.answer)
    }

    @Test
    fun severalPairsInOneQuoteAreAllFound() {
        val pairs = QaParser.splitAll(
            """
            > **Q：** 第一问？
            > **A：** 第一答。
            >
            > **Q：** 第二问？
            > **A：** 第二答。
            """.trimIndent()
        )
        assertEquals(2, pairs.size)
        assertEquals("第一答。", pairs[0].answer)
        assertEquals("第二问？", pairs[1].question)
    }

    @Test
    fun listQuizBecomesMaskedCard() {
        val nodes = parseMarkdown("- **Q：** 什么是梯度？\n- **A：** 函数增长最快的方向。")
        val quiz = nodes.filterIsInstance<MdNode.Quiz>()
        assertEquals(1, quiz.size)
        assertEquals("函数增长最快的方向。", quiz[0].answer)
    }

    @Test
    fun quoteBlockInsideMarkdownBecomesQuiz() {
        val nodes = parseMarkdown(
            """
            > **Q：为什么加了 copy() 就没问题？**
            >
            > **A：** 因为 copy() 申请了新内存。
            """.trimIndent()
        )
        val quiz = nodes.filterIsInstance<MdNode.Quiz>()
        assertEquals(1, quiz.size)
        assertTrue(quiz[0].question.contains("copy()"))
        assertTrue(quiz[0].answer.contains("新内存"))
    }

    /**
     * 拿真讲义跑一遍：这份样例通篇都是 `> **Q：** … / > **A：** …`。
     * 识别率掉下来的时候，这里是唯一能立刻发现的地方。
     */
    @Test
    fun sampleLectureFindsEveryPair() {
        val file = java.io.File("src/main/assets/samples/abalone.md")
        org.junit.Assume.assumeTrue(file.exists())
        val raw = file.readText()

        val pairs = QaParser.parse(raw)
        assertEquals(45, pairs.size)
        assertTrue(pairs.none { it.answer.contains("**Q") })
        assertTrue(pairs.none { it.question.startsWith(">") })

        val quiz = parseMarkdown(raw).filterIsInstance<MdNode.Quiz>()
        assertEquals(pairs.size, quiz.size)
    }
}
