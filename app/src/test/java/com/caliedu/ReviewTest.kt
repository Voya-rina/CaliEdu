package com.caliedu

import com.caliedu.data.Mistake
import com.caliedu.data.Review
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 复习阶梯：1 / 3 / 7 / 21 天。
 *
 * 最要紧的一条是「订正过也要回来」——之前 isDue 里带着 !fixed，
 * 第一次答对之后这条错题就再也不会出现，阶梯等于白排。
 */
class ReviewTest {

    private val day = 24L * 60 * 60 * 1000
    private val t0 = 1_600_000_000_000L

    private fun mistake() = Mistake(step = 2, title = "换元", prompt = "下一步怎么换", at = t0)

    @Test
    fun ladderIsOneThreeSevenTwentyOne() {
        assertEquals(1, Review.intervalDays(0))
        assertEquals(3, Review.intervalDays(1))
        assertEquals(7, Review.intervalDays(2))
        assertEquals(21, Review.intervalDays(3))
        // 超过档位就停在 21 天，不无限放大
        assertEquals(21, Review.intervalDays(9))
    }

    @Test
    fun freshMistakeFallsDueNextDay() {
        val m = mistake()
        assertFalse(Review.isDue(m, t0 + day - 1))
        assertTrue(Review.isDue(m, t0 + day))
    }

    @Test
    fun correctedMistakeStillComesBack() {
        val fixed = Review.afterCorrect(mistake(), t0)
        assertTrue(fixed.fixed)
        // 第一档推后 3 天：第 2 天不该来打扰
        assertFalse(Review.isDue(fixed, t0 + 2 * day))
        assertTrue(Review.isDue(fixed, t0 + 3 * day))
    }

    @Test
    fun wrongAgainGoesBackToOneDay() {
        val reviewed = Review.afterCorrect(mistake(), t0)
        val again = Review.afterWrong(reviewed, t0 + 10 * day)
        assertFalse(again.fixed)
        assertEquals(t0 + 11 * day, again.dueAt)
        assertTrue(Review.isDue(again, t0 + 11 * day))
    }

    @Test
    fun countOnlyCountsDueOnes() {
        val records = listOf(
            com.caliedu.data.LessonRecord(
                id = "a",
                mistakes = listOf(
                    mistake(),
                    mistake().copy(at = t0 + 5 * day),
                    mistake().copy(at = t0, fixed = true, reviews = 3, dueAt = t0 + 30 * day),
                ),
            ),
        )
        assertEquals(2, Review.dueCount(records, t0 + 6 * day))
    }
}
