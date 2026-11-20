package com.caliedu

import com.caliedu.core.Judge
import com.caliedu.core.MdLesson
import com.caliedu.core.Subject
import com.caliedu.core.Track
import com.caliedu.core.TrackDetector
import com.caliedu.core.Verdict
import com.caliedu.model.Question
import com.caliedu.sample.MathSample
import com.caliedu.ui.MdNode
import com.caliedu.ui.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 分流错了，后面整套 prompt 都会用错，所以这里守得紧一点。 */
class TrackTest {

    @Test
    fun mathFromWordsAlone() {
        assertEquals(Track.MATH, TrackDetector.detect("已知函数 f(x)=x³-3x，求它在区间 [-2,2] 上的最大值和最小值。"))
        assertEquals(Track.MATH, TrackDetector.detect("证明：任意三个连续整数之积能被 6 整除。"))
    }

    @Test
    fun codeFileWins() {
        val code = """
            import numpy as np
            def load(path):
                return np.loadtxt(path)
        """.trimIndent()
        assertEquals(Track.CODE, TrackDetector.detect(code))
    }

    @Test
    fun codeProblemStatementWithoutCode() {
        assertEquals(Track.CODE, TrackDetector.detect("读入一个正整数 n，输出 n 以内所有素数。要求时间复杂度不超过 O(n log log n)。"))
    }

    @Test
    fun proseFallsBackToGeneral() {
        assertEquals(Track.GENERAL, TrackDetector.detect("什么是梯度下降？它为什么能在深度网络里工作？"))
    }

    @Test
    fun shortAnswerFoldsMathNotation() {
        val q = Question(prompt = "a(n) = ?", kind = "short", answer = "2^n-1", expect = "2^n-1|2n-1")
        assertEquals(Verdict.CORRECT, Judge.judge(q, "2ⁿ−1"))
        assertEquals(Verdict.CORRECT, Judge.judge(q, "2^n − 1"))
    }

    @Test
    fun signIsNotGluedTogether() {
        val q = Question(prompt = "x = ?", kind = "short", answer = "x-1", expect = "x-1")
        assertEquals(Verdict.WRONG, Judge.judge(q, "x+1"))
    }
}

/** 数学示例必须真的长成"短推导"，而不是把编程示例那套搬过来。 */
class MathSampleTest {

    private val blocks = MathSample.contents.values.flatMap { content -> content.blocks.map { it.md } }

    @Test
    fun everyStepParses() {
        assertEquals(MathSample.plan.steps.size, MathSample.contents.size)
        MathSample.contents.forEach { (n, content) ->
            val nodes = parseMarkdown(content.blocks.joinToString("\n\n") { it.md })
            assertTrue("第 $n 步没有正文", nodes.isNotEmpty())
            assertTrue("第 $n 步没有推导式子", nodes.any { it is MdNode.Math })
            assertEquals("第 $n 步的编号对不上", n, content.n)
        }
    }

    @Test
    fun noCodeBlocksInMath() {
        assertTrue(blocks.none { it.contains("```") })
    }

    @Test
    fun proseStaysShort() {
        // 去掉公式行和小标题之后，每一步剩下的说明文字不该超过 400 字。
        MathSample.contents.forEach { (n, content) ->
            val body = content.blocks.joinToString("\n") { it.md }
                .lines()
                .filterNot { it.trim().startsWith("$$") || it.trim().startsWith("#") }
                .joinToString("")
                .filterNot { it.isWhitespace() }
            assertTrue("第 $n 步的说明有 ${body.length} 字，太长了", body.length <= 400)
        }
    }

    @Test
    fun answersAreReachable() {
        MathSample.contents.forEach { (n, content) ->
            val q = content.question
            assertTrue("第 $n 步没有提问", q.prompt.isNotBlank())
            when (q.kind) {
                "choice" -> assertTrue("第 $n 步的正确答案不在选项里", q.choices.any { it.value == q.answer })
                "short" -> assertTrue("第 $n 步没有可接受答案", q.expect.isNotBlank())
            }
            assertEquals("第 $n 步的提示不是三条", 3, content.hints.size)
        }
    }
}

/** 把一份写好的 md 讲义切成课：第一个标题是书名，之后每个一/二级标题一节。 */
class MdLessonTest {

    private val doc = """
        # 闯关示例

        开场白。

        ## 1.1 先看数据

        数据有 4177 行，九列，没有表头。第一列是性别，后面八列是测量值。
        这一段够长了，应该被保留成一节。

        ## 1.2 再归一化

        把每一列压到 0 到 1 之间，避免大量纲特征主导梯度。
        这一段也够长，同样应该留下来。

        ## 空

        短。
    """.trimIndent()

    @Test
    fun splitsOnHeadings() {
        val parsed = MdLesson.parse(doc, "兜底")
        assertEquals("闯关示例", parsed.plan.title)
        // 第三段太短，被当成封面丢掉
        assertEquals(2, parsed.plan.steps.size)
        assertEquals("1.1 先看数据", parsed.plan.steps[0].title)
        assertEquals("none", parsed.plan.steps[0].judgeKind)
    }

    @Test
    fun bodySurvivesVerbatim() {
        val parsed = MdLesson.parse(doc, "兜底")
        val body = parsed.contents[1]?.blocks?.first()?.md.orEmpty()
        assertTrue(body.contains("4177 行"))
        assertTrue(parsed.contents[1]?.question?.prompt.isNullOrBlank())
    }

    @Test
    fun majorTagAlwaysComesFirst() {
        val parsed = MdLesson.parse(doc, "兜底")
        assertTrue(parsed.plan.tags.first() in Subject.ALL)
    }

    @Test
    fun noHeadingStillProducesOneSection() {
        val parsed = MdLesson.parse("只有一段正文，没有标题，但也应该能读。", "兜底")
        assertEquals(1, parsed.plan.steps.size)
        assertEquals("兜底", parsed.plan.title)
    }

    /** 代码块里以 `#` 开头的是注释。当成标题会把一节正文从中间劈开。 */
    @Test
    fun codeCommentsAreNotHeadings() {
        val text = """
            # 讲义

            ## 1.1 读数据

            正文一段。

            ```python
            # ====================
            # 模块 ①：load_data
            # ====================
            def load():
                pass
            ```

            正文另一段，足够长，不该被当成封面丢掉。

            ## 1.2 归一化

            归一化把每一列压到 0 到 1，避免大量纲特征主导梯度。
        """.trimIndent()

        val parsed = MdLesson.parse(text, "兜底")
        assertEquals(listOf("1.1 读数据", "1.2 归一化"), parsed.plan.steps.map { it.title })
        // 代码块应当完整留在一节里
        assertTrue(parsed.contents[1]?.blocks?.first()?.md.orEmpty().contains("# 模块 ①"))
    }

    /** `# 第一部分` 不是一节，它是下面几节的分组头，要出现在被它覆盖的第一节开头。 */
    @Test
    fun partHeadingBelongsToTheSectionItIntroduces() {
        val text = """
            # 书名

            ## 第一节

            第一节的正文写在这里，长度足够，不会被当成封面丢掉，应该保留下来。

            # 第一部分

            这一部分讲两件事。

            ## 第二节

            第二节的正文也写在这里，同样足够长，应该单独成为一节内容。

            ## 第三节

            第三节的正文继续写在这里，长度也够，应该完整地保留下来。
        """.trimIndent()

        val parsed = MdLesson.parse(text, "兜底")
        assertEquals(3, parsed.plan.steps.size)
        val second = parsed.contents[2]?.blocks?.first()?.md.orEmpty()
        assertTrue("「第一部分」应在第二节开头，实际：$second", second.startsWith("# 第一部分"))
        assertFalse("第一节的正文串到第二节去了", second.contains("第一节的正文"))
    }

    /** 通篇只用一级标题时，别死守二级。 */
    @Test
    fun singleLevelOutlineStillSplits() {
        val text = """
            # 书名

            # 第一章

            第一章的正文写在这里，长度足够长，应该单独成一节。

            # 第二章

            第二章的正文也写在这里，同样足够长，应该单独成一节。
        """.trimIndent()

        val parsed = MdLesson.parse(text, "兜底")
        assertEquals(2, parsed.plan.steps.size)
        assertEquals("第一章", parsed.plan.steps[0].title)
    }

    /** 分类看内容，不看代码特征：讲回归的讲义不该被归到 Python。 */
    @Test
    fun categoryFollowsContentNotSyntax() {
        val text = """
            # 用 numpy 预测鲍鱼年龄

            先读入数据集，按 8:2 划出训练集和测试集，再做归一化。
            模型是一个小神经网络，用均方误差当损失函数，梯度下降更新权重。
        """.trimIndent()

        assertEquals("机器学习", Subject.guess(text))
        assertEquals("机器学习", MdLesson.parse(text, "兜底").plan.tags.first())
    }
}
