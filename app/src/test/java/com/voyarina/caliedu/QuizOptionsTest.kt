package com.voyarina.caliedu

import com.voyarina.caliedu.llm.LessonGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 干扰项解析：编号错位是最常见的一种，错一位就会把别的题的选项安到这题上。 */
class QuizOptionsTest {

    private val generator = LessonGenerator()

    @Test
    fun matchesByNumber() {
        val raw = """{"items":[{"n":2,"options":["甲","乙","丙"]},{"n":1,"options":["A","B","C"]}]}"""
        val out = generator.parseOptions(raw, listOf(1, 2))
        assertEquals(listOf("A", "B", "C"), out[1])
        assertEquals(listOf("甲", "乙", "丙"), out[2])
    }

    @Test
    fun localNumberingFallsBackToOrder() {
        // 分批之后模型从 1 重新编号，而这一批实际是 7、8。
        val raw = """{"items":[{"n":1,"options":["甲","乙","丙"]},{"n":2,"options":["A","B","C"]}]}"""
        val out = generator.parseOptions(raw, listOf(7, 8))
        assertEquals(3, out[7]?.size)
        assertEquals(3, out[8]?.size)
        assertTrue(out[7]!!.contains("甲"))
        assertTrue(out[8]!!.contains("A"))
    }

    @Test
    fun fencedJsonIsAccepted() {
        val raw = "```json\n{\"items\":[{\"n\":1,\"options\":[\"甲\",\"乙\",\"丙\"]}]}\n```"
        assertEquals(3, generator.parseOptions(raw, listOf(1))[1]?.size)
    }

    @Test
    fun emptyOptionsAreDropped() {
        val raw = """{"items":[{"n":1,"options":["甲","","丙"]}]}"""
        assertEquals(listOf("甲", "丙"), generator.parseOptions(raw, listOf(1))[1])
    }

    @Test
    fun garbageReturnsNothing() {
        assertTrue(generator.parseOptions("模型今天不想干活", listOf(1, 2)).isEmpty())
    }
}
