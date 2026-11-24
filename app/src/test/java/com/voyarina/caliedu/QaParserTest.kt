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
}
