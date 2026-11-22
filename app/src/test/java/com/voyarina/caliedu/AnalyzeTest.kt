package com.voyarina.caliedu

import com.voyarina.caliedu.core.Track
import com.voyarina.caliedu.data.LlmSettings
import com.voyarina.caliedu.llm.ChatChannel
import com.voyarina.caliedu.llm.LessonGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 拆题用哪套 prompt，取决于题型。
 *
 * 用户手动选了类型就听用户的：模型返回的 kind 只在自己没选时才有发言权。
 * 这条规则一旦破了，数学题会被拆成代码结构，整套提示词都白花。
 */
class AnalyzeTest {

    private class Fake(private val reply: String) : ChatChannel {
        override suspend fun complete(system: String, user: String, json: Boolean, label: String): String = reply
    }

    private val planJson = """
        {"title":"练习","kind":"math","oneLine":"一句话","tags":["高等数学"],"knowledge":["数列"],
         "steps":[{"n":1,"title":"摆条件","stage":"读题","goal":"写出条件","judgeKind":"choice"},
                  {"n":2,"title":"变形","stage":"推导","goal":"做一次变形","judgeKind":"short"}],
         "needsRun":false}
    """.trimIndent()

    private fun generator() = LessonGenerator(directFor = { Fake(planJson) })

    @Test
    fun forcedTrackBeatsModelKind() = runBlocking {
        // 输入是代码，模型却说是数学——用户选了编程，就按编程走
        val plan = generator().analyze(LlmSettings(), "for i in range(3): print(i)", Track.CODE)
        assertEquals("code", plan.kind)
        assertEquals(2, plan.steps.size)
        // 步号会被强制连续
        assertEquals(listOf(1, 2), plan.steps.map { it.n })
    }

    @Test
    fun modelKindWinsWhenNotForced() = runBlocking {
        // 题面没有明显特征（本地判为 general），模型说是数学，就按数学走
        val plan = generator().analyze(LlmSettings(), "谈谈熵")
        assertEquals("math", plan.kind)
    }

    @Test
    fun forcedGeneralBeatsModelMath() = runBlocking {
        val plan = generator().analyze(LlmSettings(), "什么是熵", Track.GENERAL)
        assertEquals("general", plan.kind)
    }
}
