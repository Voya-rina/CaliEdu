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
}
