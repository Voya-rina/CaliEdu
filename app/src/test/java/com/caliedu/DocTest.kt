package com.caliedu

import com.caliedu.core.Judge
import com.caliedu.core.LessonDoc
import com.caliedu.core.Md2Html
import com.caliedu.core.Verdict
import com.caliedu.model.Block
import com.caliedu.model.Choice
import com.caliedu.model.LessonPlan
import com.caliedu.model.Question
import com.caliedu.model.StepBrief
import com.caliedu.model.StepContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistJudgeTest {

    private val q = Question(
        prompt = "为什么这里的 accuracy 其实是 R²？",
        kind = "checklist",
        rubric = listOf("MSE", "方差", "1 -"),
        answer = "回归任务不能用分类准确率",
    )

    @Test
    fun allPointsHit() {
        assertEquals(Verdict.CORRECT, Judge.judge(q, "它是 1 - MSE/方差，所以是 R²"))
    }

    @Test
    fun halfPointsIsPartial() {
        assertEquals(Verdict.PARTIAL, Judge.judge(q, "跟 MSE 和方差都有关"))
    }

    @Test
    fun emptyIsWrong() {
        assertEquals(Verdict.WRONG, Judge.judge(q, "  "))
    }

    @Test
    fun choiceIsNotOpenEnded() {
        assertFalse(Judge.isOpenEnded(Question(kind = "choice")))
        assertTrue(Judge.isOpenEnded(q))
    }
}

class LessonDocTest {

    private val plan = LessonPlan(
        title = "鲍鱼年龄预测",
        oneLine = "用 8 个测量值回归年龄",
        knowledge = listOf("loadtxt", "归一化"),
        steps = listOf(
            StepBrief(1, "读文件", "读题", "知道矩阵形状", "choice"),
            StepBrief(2, "切分", "框架", "知道 0.8 的边界", "short"),
        ),
    )

    private val contents = mapOf(
        1 to StepContent(
            n = 1,
            blocks = listOf(
                Block("core", "用 `np.loadtxt` 读进来。\n\n```python\nimport numpy as np\nX = np.loadtxt('a.data', delimiter=',')\nprint(X.shape)\n```"),
                Block("core", "```output:inferred\n(4177, 9)\n```"),
            ),
            question = Question(
                prompt = "第一列是什么类型？",
                kind = "choice",
                choices = listOf(Choice("字符串", "字符串"), Choice("浮点", "浮点")),
                answer = "字符串",
            ),
            summary = listOf("形状是 (行, 列)"),
        ),
    )

    @Test
    fun markdownKeepsOrderAndLabels() {
        val md = LessonDoc.markdown(plan, contents)
        assertTrue(md.startsWith("# 鲍鱼年龄预测"))
        assertTrue(md.contains("## 1. 读文件"))
        assertTrue(md.contains("## 2. 切分"))
        assertTrue(md.contains("还没有生成"))
        assertTrue(md.contains("形状是 (行, 列)"))
    }

    @Test
    fun htmlEscapesAndHighlights() {
        val html = LessonDoc.html(plan, contents)
        assertTrue(html.contains("<pre><code class=\"lang-python\">"))
        assertTrue(html.contains("class=\"kw\""))
        assertTrue(html.contains("class=\"tag inferred\""))
        assertTrue(html.contains("推断输出"))
        assertFalse(html.contains("<script>"))
    }

    @Test
    fun markdownSpecialCharsEscaped() {
        val html = Md2Html.convert("a < b && c > d")
        assertTrue(html.contains("&lt;"))
        assertTrue(html.contains("&amp;"))
    }

    @Test
    fun mathBecomesStructuredHtml() {
        val html = Md2Html.convert("损失是 \$\\frac{1}{n}\\sum (y-\\hat{y})^2\$")
        assertTrue(html.contains("class=\"frac\""))
        assertTrue(html.contains("Σ"))
        assertFalse(html.contains("\\frac"))
    }

    @Test
    fun firstCodeExtractsPython() {
        val md = "前言\n\n```python\nprint(1)\n```\n\n后面"
        assertEquals("print(1)", LessonDoc.firstCode(md))
    }
}
