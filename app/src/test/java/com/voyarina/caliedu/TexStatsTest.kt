package com.voyarina.caliedu

import com.voyarina.caliedu.data.LessonRecord
import com.voyarina.caliedu.data.Mistake
import com.voyarina.caliedu.data.Stats
import com.voyarina.caliedu.model.LessonPlan
import com.voyarina.caliedu.ui.TexLite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TexLiteTest {

    @Test
    fun fracBecomesSlash() {
        assertEquals("a/b", TexLite.convert("\\frac{a}{b}"))
    }

    @Test
    fun fracWithoutBraces() {
        assertEquals("1/n", TexLite.convert("\\frac1n"))
    }

    @Test
    fun nestedFrac() {
        val out = TexLite.convert("\\frac{\\frac{a}{b}}{c}")
        assertEquals("a/b/c", out)
    }

    @Test
    fun greekAndOperators() {
        assertEquals("σ × μ ≤ ∞", TexLite.convert("\\sigma \\times \\mu \\leq \\infty"))
    }

    @Test
    fun superscriptWhenMappable() {
        assertEquals("x²", TexLite.convert("x^{2}"))
        assertEquals("R²", TexLite.convert("R^2"))
    }

    @Test
    fun superscriptFallsBackToPlain() {
        // θ 没有上标码位，退回普通写法但命令要翻译成 θ
        assertEquals("xθ", TexLite.convert("x^{\\theta}"))
    }

    @Test
    fun subscriptWorks() {
        assertEquals("ssₜₒₜ", TexLite.convert("ss_{tot}"))
    }

    @Test
    fun unknownCommandIsDropped() {
        // 命令名不该被当正文：\mathbb{R} 只留 R，不留 "mathbbR"
        val out = TexLite.convert("\\mathbb{R}")
        assertFalse(out.contains("\\"))
        assertFalse(out.contains("mathbb"))
        assertTrue(out.contains("R"))
    }

    @Test
    fun accentKeepsTheLetter() {
        // \hat{y} 的帽子画不出来，但 y 必须还在
        assertEquals("y", TexLite.convert("\\hat{y}"))
    }
}

class StatsTest {

    private fun record(
        id: String,
        solved: Int,
        wrongs: Int,
        knowledge: List<String>,
        at: Long = System.currentTimeMillis(),
        finished: Boolean = false,
        mistakes: List<Mistake> = emptyList(),
    ) = LessonRecord(
        id = id,
        title = id,
        plan = LessonPlan(title = id, knowledge = knowledge),
        solved = solved,
        wrongs = wrongs,
        updatedAt = at,
        finished = finished,
        mistakes = mistakes,
    )

    @Test
    fun emptyIsZero() {
        val stats = Stats.of(emptyList())
        assertEquals(0, stats.lessons)
        assertEquals(0f, stats.accuracy)
    }

    @Test
    fun accuracyCountsBothSides() {
        val stats = Stats.of(listOf(record("a", solved = 3, wrongs = 1, knowledge = emptyList())))
        assertEquals(0.75f, stats.accuracy, 0.001f)
    }

    @Test
    fun weakPointsRankedByWrongs() {
        val stats = Stats.of(
            listOf(
                record("a", 2, 4, listOf("归一化")),
                record("b", 1, 2, listOf("归一化", "张量形状")),
            )
        )
        assertEquals("归一化", stats.weak.first().name)
        // 2 次错摊到 2 个知识点各 1 次：归一化 4 + 1 = 5
        assertEquals(5, stats.weak.first().wrongs)
    }

    @Test
    fun streakCountsTodayOnly() {
        val now = System.currentTimeMillis()
        val stats = Stats.of(listOf(record("a", 1, 0, emptyList(), at = now)))
        assertEquals(1, stats.streakDays)
    }

    @Test
    fun finishedCounted() {
        val stats = Stats.of(
            listOf(
                record("a", 3, 0, emptyList(), finished = true),
                record("b", 1, 1, emptyList()),
            )
        )
        assertEquals(1, stats.finished)
        assertEquals(2, stats.lessons)
    }
}
