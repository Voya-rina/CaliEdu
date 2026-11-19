package com.caliedu

import com.caliedu.data.LlmSettings
import com.caliedu.llm.ChatChannel
import com.caliedu.llm.ChatException
import com.caliedu.llm.LessonGenerator
import com.caliedu.model.LessonPlan
import com.caliedu.model.StepBrief
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 同类题只有一个约定：拿回来的必须是一道题，不能是「好的，这是新题：」这种客套。
 * 模型怎么回没法控制，但回来的东西怎么处理必须钉死。
 */
class VariantTest {

    private class Fake(private val reply: String) : ChatChannel {
        var calls = 0
            private set

        override suspend fun complete(system: String, user: String, json: Boolean, label: String): String {
            calls++
            return reply
        }
    }

    private val plan = LessonPlan(
        title = "求数列通项",
        kind = "math",
        oneLine = "首项 1，后一项是前一项的两倍加一",
        tags = listOf("高等数学", "数列"),
        knowledge = listOf("递推数列"),
        steps = listOf(
            StepBrief(n = 1, title = "摆条件", goal = "写出递推式", judgeKind = "choice"),
            StepBrief(n = 2, title = "凑常数", goal = "找到平移量", judgeKind = "short"),
        ),
    )

    private fun generator(reply: String): Pair<LessonGenerator, Fake> {
        val fake = Fake(reply)
        return LessonGenerator(directFor = { fake }) to fake
    }

    @Test
    fun readsFencedJson() = runBlocking {
        val (gen, fake) = generator(
            "```json\n{\"title\":\"新题\",\"problem\":\"已知 b1=3，b(n+1)=2b(n)-1，求通项公式。\"}\n```"
        )
        assertEquals("已知 b1=3，b(n+1)=2b(n)-1，求通项公式。", gen.variant(LlmSettings(), plan, ""))
        assertEquals(1, fake.calls)
    }

    @Test
    fun keepsMathEscapes() = runBlocking {
        val (gen, _) = generator(
            """{"title":"新题","problem":"已知 ${'$'}a_1=3${'$'}，且 ${'$'}a_{n+1}=2a_n-1${'$'}，求 ${'$'}a_n${'$'}。"}"""
        )
        val text = gen.variant(LlmSettings(), plan, "")
        assertTrue(text.contains("a_{n+1}"))
    }

    @Test
    fun acceptsPlainTextWithoutJson() = runBlocking {
        val (gen, _) = generator("题目：已知 c1=2，c(n+1)=3c(n)+2，求通项公式。")
        assertEquals("已知 c1=2，c(n+1)=3c(n)+2，求通项公式。", gen.variant(LlmSettings(), plan, ""))
    }

    @Test
    fun rejectsSmallTalk() = runBlocking {
        val (gen, _) = generator("好的，我来出一道同类型的新题。")
        try {
            gen.variant(LlmSettings(), plan, "")
            throw AssertionError("客套话不该被当成题目")
        } catch (expected: ChatException) {
            assertTrue(expected.message.orEmpty().contains("没能出一道同类题"))
        }
    }
}
