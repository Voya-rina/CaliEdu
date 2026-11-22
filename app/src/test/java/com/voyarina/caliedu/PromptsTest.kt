package com.voyarina.caliedu

import com.voyarina.caliedu.core.Prompts
import com.voyarina.caliedu.core.Track
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 提示词的规模约束。
 *
 * 这几条不是文风偏好，是实际踩过的坑：一句「讲一下动态规划」被拆成 12 步，
 * 里面还夹着「明确讲解任务」「汇总输出答案」两个元步骤。模型的依据就是原提示词里
 * 写着「步数不设上下限」，而概念题那一段只有结构、没有上限。
 * 这里把上限钉住，防止以后有人为了「灵活」把这句话又删掉。
 */
class PromptsTest {

    private val general = Prompts.analyzeSystem(Track.GENERAL)

    @Test
    fun conceptQuestionsHaveAHardCap() {
        assertTrue("概念题必须写明步数上限", general.contains("最多 6 步"))
        assertTrue("一句话提问要单独给更小的上限", general.contains("3~4 步"))
        assertFalse("概念题不能再写「不设上下限」", general.contains("不设上下限"))
    }

    @Test
    fun conceptQuestionsForbidMetaSteps() {
        assertTrue(general.contains("元步骤"))
        assertTrue("要点名最常出现的元步骤", general.contains("汇总输出答案"))
        assertTrue("开头不许先讲题目要求", general.contains("不要先讲「我们来看题目在问什么」"))
    }

    @Test
    fun conceptStagesHaveNoReadQuestion() {
        // 「读题」这个 stage 在概念题里总会变成「明确题目要求」的一步。
        assertTrue(general.contains("概念 / 辨析 / 推理 / 结论"))
        assertFalse(general.contains("读题"))
    }

    @Test
    fun mathAndCodeKeepTheirFreedom() {
        // 推导链和代码块的长度由题目决定，不能被概念题的上限连累。
        assertTrue(Prompts.analyzeSystem(Track.MATH).contains("不设上下限"))
        assertTrue(Prompts.analyzeSystem(Track.CODE).contains("不设上下限"))
        assertFalse(Prompts.analyzeSystem(Track.MATH).contains("最多 6 步"))
    }

    @Test
    fun conceptTeachingStartsFromTheAnswer() {
        val teach = Prompts.teachSystem(Track.GENERAL)
        assertTrue("讲义第一句就要给结论，不能先复述这一步在问什么", teach.contains("第一句就把这一步的结论说出来"))
        assertFalse(
            "旧提示词让模型先复述「这一步要回答什么」，那是目录不是讲义",
            teach.contains("先把这一步要回答的问题用一句话说出来"),
        )
    }

    @Test
    fun conceptUserPromptDoesNotInviteACourse() {
        val user = Prompts.analyzeUser("讲一下动态规划", Track.GENERAL)
        assertTrue(user.contains("不要扩成一门课"))
        assertTrue("一句话提问要在用户侧再钉一次规模", user.contains("按 3~4 步处理"))
        assertTrue(user.contains("讲一下动态规划"))
    }

    @Test
    fun markupRuleIsPresentInEveryTeachPrompt() {
        // 截图里那种整段 `$...\frac{..}` 印成正文，源头就是模型开了 $ 不关、用 \n 当换行。
        listOf(Track.MATH, Track.CODE, Track.GENERAL).forEach { track ->
            val sys = Prompts.teachSystem(track)
            assertTrue("$track 缺少行内公式成对的要求", sys.contains("必须在同一行用 \$ 关掉"))
            assertTrue("$track 应禁止字面量 \\n", sys.contains("\\n 这两个字符"))
        }
        assertTrue(Prompts.teachAllSystem(Track.MATH).contains("必须在同一行用 \$ 关掉"))
    }

    @Test
    fun answerShouldStaySmallOrBecomeChoice() {
        val plan = Prompts.analyzeSystem(Track.MATH)
        assertTrue("拆题时就要优先 choice", plan.contains("优先 choice"))
        assertTrue("短答要有长度上限", plan.contains("20 字"))
        val teach = Prompts.teachSystem(Track.MATH)
        assertTrue("长式子一律改 choice", teach.contains("一律改 choice"))
        assertTrue("分批写也要允许改 choice", Prompts.teachAllSystem(Track.MATH).contains("就改成 choice"))
    }
}
