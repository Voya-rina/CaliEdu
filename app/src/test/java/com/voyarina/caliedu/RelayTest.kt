package com.voyarina.caliedu

import com.voyarina.caliedu.data.ChannelMode
import com.voyarina.caliedu.data.LlmSettings
import com.voyarina.caliedu.llm.LessonGenerator
import com.voyarina.caliedu.llm.RelayBroker
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 接力通道的核心契约：生成链路在等用户，用户粘回 JSON 后链路照常往下走。
 * 这条路径不碰网络，所以能在纯 JVM 上测。
 */
class RelayTest {

    private val relaySettings = LlmSettings(mode = ChannelMode.RELAY, apiKey = "")

    @Test
    fun analyzeSuspendsUntilPaste() = runTest {
        val broker = RelayBroker()
        val generator = LessonGenerator(relay = broker)

        val job = async { generator.analyze(relaySettings, "写一个冒泡排序") }

        val pending = broker.pending.first { it != null }!!
        assertTrue(pending.packet.contains("能独立判对错的关口"))
        assertEquals("拆题", pending.label)

        broker.submit(
            """{"title":"冒泡排序","kind":"code","oneLine":"把数组排好",
               "steps":[{"n":1,"title":"看框架","stage":"框架","goal":"知道两层循环","judgeKind":"choice"}]}"""
        )

        val plan = job.await()
        assertEquals("冒泡排序", plan.title)
        assertEquals(1, plan.steps.size)
        assertNull(broker.pending.value)
    }

    @Test
    fun pasteWithFenceStillParses() = runTest {
        val broker = RelayBroker()
        val generator = LessonGenerator(relay = broker)

        val job = async { generator.analyze(relaySettings, "题目") }
        broker.pending.first { it != null }
        broker.submit(
            """
            好的，下面是结果：
            ```json
            {"title":"带围栏","steps":[{"n":1,"title":"第一步","goal":"目标"}]}
            ```
            希望有帮助。
            """.trimIndent()
        )

        assertEquals("带围栏", job.await().title)
    }

    @Test
    fun cancelReleasesTheGate() = runTest {
        val broker = RelayBroker()
        val generator = LessonGenerator(relay = broker)
        val job = async { runCatching { generator.analyze(relaySettings, "题目") } }
        broker.pending.first { it != null }
        broker.cancel()
        job.await()
        assertNull(broker.pending.value)
    }

    @Test
    fun relayModeDoesNotNeedApiKey() {
        assertTrue(relaySettings.canTalk)
        // 但接力模式下不该自动批改：每判一题都要人工转一趟
        assertTrue(!relaySettings.canGrade)
    }
}
