package com.caliedu.stepedu

import com.caliedu.stepedu.core.Judge
import com.caliedu.stepedu.core.Progress
import com.caliedu.stepedu.core.Verdict
import com.caliedu.stepedu.model.Choice
import com.caliedu.stepedu.model.Question
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JudgeTest {

    private val choiceQuestion = Question(
        prompt = "哪 8 列是特征？",
        kind = "choice",
        choices = listOf(
            Choice("1~7 列", "1~7 列"),
            Choice("0~7 列", "0~7 列"),
        ),
        answer = "0~7 列",
    )

    private val shortQuestion = Question(
        prompt = "I 代表什么？",
        kind = "short",
        expect = "幼体|infant",
    )

    @Test
    fun choiceCorrect() {
        assertEquals(Verdict.CORRECT, Judge.judge(choiceQuestion, "0~7 列"))
    }

    @Test
    fun choiceWrong() {
        assertEquals(Verdict.WRONG, Judge.judge(choiceQuestion, "1~7 列"))
    }

    @Test
    fun shortAcceptsAlias() {
        assertEquals(Verdict.CORRECT, Judge.judge(shortQuestion, "Infant"))
        assertEquals(Verdict.CORRECT, Judge.judge(shortQuestion, "幼体"))
    }

    @Test
    fun shortIgnoresPunctuationAndCase() {
        assertEquals(Verdict.CORRECT, Judge.judge(shortQuestion, " 幼 体。"))
    }

    @Test
    fun shortPartialWhenOnlyPartMatches() {
        assertEquals(Verdict.PARTIAL, Judge.judge(shortQuestion, "这是幼体阶段的小鲍鱼"))
    }

    @Test
    fun shortWrongWhenUnrelated() {
        assertEquals(Verdict.WRONG, Judge.judge(shortQuestion, "成年雄性"))
    }
}

class ProgressTest {

    @Test
    fun wrongEscalatesHints() {
        var p = Progress()
        p = p.wrong()
        assertEquals(1, p.hintsShown)
        p = p.wrong()
        assertEquals(2, p.hintsShown)
        p = p.wrong()
        assertTrue(p.revealed)
        assertTrue(p.canAdvance)
    }

    @Test
    fun hintStopsAtThree() {
        var p = Progress()
        repeat(5) { p = p.hint() }
        assertEquals(3, p.hintsShown)
    }

    @Test
    fun correctAllowsAdvance() {
        val p = Progress().correct()
        assertTrue(p.canAdvance)
        assertEquals(1, p.next(3).idx)
        assertFalse(Progress().wrong().canAdvance)
    }
}
