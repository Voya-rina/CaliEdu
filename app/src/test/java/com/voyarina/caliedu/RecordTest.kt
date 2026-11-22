package com.voyarina.caliedu

import com.voyarina.caliedu.data.LessonRecord
import com.voyarina.caliedu.model.LessonPlan
import com.voyarina.caliedu.model.StepBrief
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 记录卡上的进度条表示「上次停在哪」，不是「过了几关」。
 *
 * 过了 20 步、退出时却在第 8 步，条就该停在第 8 步 —— 那才是你打开它时的位置。
 * 数字和卡片上「第 8 / 25 步」那句保持一致，所以游标要 +1 再除。
 */
class RecordTest {

    private fun record(cursor: Int, passed: List<Int> = emptyList()) = LessonRecord(
        plan = LessonPlan(steps = List(10) { StepBrief(n = it + 1, title = "第${it + 1}步") }),
        cursor = cursor,
        passed = passed,
    )

    @Test
    fun barFollowsTheCursor() {
        assertEquals(0.4f, record(cursor = 3).ratio, 0.001f)
    }

    @Test
    fun passedStepsDoNotMoveTheBar() {
        assertEquals(0.1f, record(cursor = 0, passed = (0..9).toList()).ratio, 0.001f)
    }

    @Test
    fun jumpingBackShowsWhereYouLeft() {
        // 过了一多半，但退出时翻回了第 2 步：条跟着退回去，和「第 2 / 10 步」一致。
        assertEquals(0.2f, record(cursor = 1, passed = (0..7).toList()).ratio, 0.001f)
    }

    @Test
    fun lastStepIsFull() {
        assertEquals(1f, record(cursor = 9).ratio, 0.001f)
    }

    @Test
    fun ratioStaysInRange() {
        assertEquals(1f, record(cursor = 99).ratio, 0.001f)
        assertEquals(0f, LessonRecord().ratio, 0.001f)
    }
}
