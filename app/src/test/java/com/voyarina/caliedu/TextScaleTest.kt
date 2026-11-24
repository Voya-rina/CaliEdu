package com.voyarina.caliedu

import com.voyarina.caliedu.data.TextScale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 字号刻度。
 *
 * 这里最容易出错的不是范围，是「一共几档」：`(1.3 - 0.9) / 0.05` 在浮点里
 * 是 7.999…，按它算步数会少一档，滑块就从 105% 直接跳到 111%，110% 永远停不上。
 */
class TextScaleTest {

    @Test
    fun nineStopsFrom90To130() {
        // steps 是两端之间的点数：9 档 - 2 端 = 7
        assertEquals(7, TextScale.STEPS)
        val stops = (0..TextScale.STEPS + 1).map { i ->
            TextScale.MIN + i * (TextScale.MAX - TextScale.MIN) / (TextScale.STEPS + 1)
        }
        assertEquals(
            listOf(90, 95, 100, 105, 110, 115, 120, 125, 130),
            stops.map { TextScale.percent(it) },
        )
    }

    @Test
    fun snapLandsOnFivePercent() {
        assertEquals(110, TextScale.percent(TextScale.snap(1.1f)))
        assertEquals(105, TextScale.percent(TextScale.snap(1.04f)))
        // 106 离 105 更近，108 离 110 更近
        assertEquals(105, TextScale.percent(TextScale.snap(1.06f)))
        assertEquals(110, TextScale.percent(TextScale.snap(1.08f)))
        assertEquals(90, TextScale.percent(TextScale.snap(0.5f)))
        assertEquals(130, TextScale.percent(TextScale.snap(2f)))
    }

    @Test
    fun oldScaleAboveRangeIsPulledDown() {
        // 上一版上限是 140%，存下来的 1.4 不能原样用
        assertEquals(130, TextScale.percent(TextScale.snap(1.4f)))
    }

    @Test
    fun labelIsPercent() {
        assertEquals("110%", TextScale.label(1.1f))
    }
}
