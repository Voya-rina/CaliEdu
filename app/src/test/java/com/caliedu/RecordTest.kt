package com.caliedu

import com.caliedu.data.LessonRecord
import com.caliedu.model.LessonPlan
import com.caliedu.model.StepBrief
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 进度条不再等同于「光标停在第几步」。
 *
 * 翻回第 3 步看一眼，光标会退回去；如果还按光标算，整条进度条会跟着倒退。
 * 现在按「过了几步」算，旧记录没有 passed 字段时退回 cursor。
 */
class RecordTest {

    private fun record(cursor: Int, passed: List<Int>) = LessonRecord(
        plan = LessonPlan(steps = List(10) { StepBrief(n = it + 1, title = "第${it + 1}步") }),
        cursor = cursor,
        passed = passed,
    )

    @Test
    fun ratioCountsPassedSteps() {
        assertEquals(0.4f, record(cursor = 1, passed = listOf(0, 1, 2, 3)).ratio, 0.001f)
    }

    @Test
    fun ratioIgnoresCursorWhenPassedExists() {
        // 翻回第 2 步看，光标倒退，但进度条不该倒退
        assertEquals(0.5f, record(cursor = 1, passed = (0..4).toList()).ratio, 0.001f)
    }

    @Test
    fun oldRecordFallsBackToCursor() {
        assertEquals(0.3f, record(cursor = 3, passed = emptyList()).ratio, 0.001f)
    }

    @Test
    fun ratioStaysInRange() {
        assertEquals(1f, record(cursor = 99, passed = emptyList()).ratio, 0.001f)
        assertEquals(0f, LessonRecord().ratio, 0.001f)
    }
}
