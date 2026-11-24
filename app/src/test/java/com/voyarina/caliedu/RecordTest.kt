package com.voyarina.caliedu

import com.voyarina.caliedu.data.LessonRecord
import com.voyarina.caliedu.model.LessonPlan
import com.voyarina.caliedu.model.StepBrief
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun readingStateSurvivesJson() {
        val saved = record(cursor = 3).copy(reading = true, readingAt = 7)
        val text = Json.encodeToString(LessonRecord.serializer(), saved)
        val back = Json.decodeFromString(LessonRecord.serializer(), text)
        assertTrue("上次停在通读，退出重进要还在通读", back.reading)
        assertEquals("读到第 8 节（从 0 起是 7）", 7, back.readingAt)
    }

    @Test
    fun oldRecordsOpenInStepMode() {
        // 加字段之前存下的记录：没有 reading / readingAt，不能被当成「读到第 1 节」。
        val back = Json.decodeFromString(LessonRecord.serializer(), """{"id":"x","cursor":2}""")
        assertFalse(back.reading)
        assertEquals(-1, back.readingAt)
    }

    @Test
    fun readingPositionDoesNotMoveTheBar() {
        // 通读翻到第 10 节，但做题游标还在第 4 步：记录卡的条仍指第 4 步。
        assertEquals(0.4f, record(cursor = 3).copy(reading = true, readingAt = 9).ratio, 0.001f)
    }

    @Test
    fun docNeedsEveryStepReadOnly() {
        val doc = LessonRecord(
            source = "# 讲义",
            plan = LessonPlan(steps = List(3) { StepBrief(n = it + 1, judgeKind = "none") }),
        )
        assertTrue("整门课都没有提问，才是讲义", doc.isDoc)

        val mixed = doc.copy(
            plan = doc.plan.copy(
                steps = doc.plan.steps.mapIndexed { index, brief ->
                    if (index == 0) brief.copy(judgeKind = "choice") else brief
                }
            )
        )
        assertFalse("问答课哪怕有长答案节，也不算讲义", mixed.isDoc)
        assertFalse("没有原文就不是讲义", doc.copy(source = "").isDoc)
    }
}
