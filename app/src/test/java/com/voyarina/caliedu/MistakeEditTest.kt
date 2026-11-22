package com.voyarina.caliedu

import com.voyarina.caliedu.data.LessonRecord
import com.voyarina.caliedu.data.Mistake
import com.voyarina.caliedu.data.withoutMistake
import com.voyarina.caliedu.data.withoutMistakes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 删错题。
 *
 * 错题是嵌在课程记录里的，删一条要能精确命中、又不能把同一步的另一条带走。
 */
class MistakeEditTest {

    private fun mistake(step: Int, at: Long, input: String = "") =
        Mistake(step = step, title = "第 $step 步", input = input, at = at)

    private fun record(vararg rows: Mistake) = LessonRecord(
        id = "r1",
        title = "一课",
        mistakes = rows.toList(),
        solved = 5,
        wrongs = rows.size,
    )

    @Test
    fun removesExactlyTheOneAsked() {
        val keep = mistake(1, 100L)
        val drop = mistake(2, 200L)
        val next = record(keep, drop).withoutMistake(drop)
        assertEquals(listOf(keep), next.mistakes)
    }

    @Test
    fun sameStepButDifferentAttemptIsKept() {
        val first = mistake(2, 200L, "x=1")
        val second = mistake(2, 300L, "x=2")
        val next = record(first, second).withoutMistake(second)
        assertEquals(listOf(first), next.mistakes)
    }

    @Test
    fun fallsBackWhenInstanceIsNotIdentical() {
        // 记录从磁盘重读过一遍后，界面拿到的对象不一定是同一个实例，
        // 但「步号 + 时间 + 自己的答案」能认出来。
        val stored = mistake(3, 400L, "y=7")
        val fromUi = mistake(3, 400L, "y=7").copy(title = "标题被改过")
        val next = record(stored, mistake(4, 500L)).withoutMistake(fromUi)
        assertEquals(1, next.mistakes.size)
        assertEquals(4, next.mistakes.first().step)
    }

    @Test
    fun missingTargetLeavesRecordUntouched() {
        val only = mistake(1, 100L)
        val r = record(only)
        assertSame(r, r.withoutMistake(mistake(9, 900L)))
    }

    @Test
    fun progressSurvivesMistakeRemoval() {
        val next = record(mistake(1, 100L)).withoutMistake(mistake(1, 100L))
        assertEquals(5, next.solved)
        assertEquals("删错题不该动答题数", 1, next.wrongs)
    }

    @Test
    fun clearAllKeepsTheRecord() {
        val r = record(mistake(1, 100L), mistake(2, 200L))
        val next = r.withoutMistakes()
        assertEquals(emptyList<Mistake>(), next.mistakes)
        assertEquals("r1", next.id)
        assertEquals(5, next.solved)
    }

    @Test
    fun clearingAnEmptyBookIsANoOp() {
        val r = record()
        assertSame(r, r.withoutMistakes())
    }
}
