package com.caliedu

import com.caliedu.llm.Sse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SSE 解析：真机上只能"看着像在打字"，这层行为得在这儿钉死。 */
class StreamTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun readsContentDelta() {
        val line = """data: {"choices":[{"delta":{"content":"先"}}]}"""
        assertEquals("先", Sse.delta(line, json))
    }

    @Test
    fun ignoresRoleOnlyChunk() {
        val line = """data: {"choices":[{"delta":{"role":"assistant"}}]}"""
        assertNull(Sse.delta(line, json))
    }

    @Test
    fun ignoresNonDataLines() {
        assertNull(Sse.delta(": keep-alive", json))
        assertNull(Sse.delta("", json))
        assertNull(Sse.delta("event: message", json))
    }

    @Test
    fun detectsDone() {
        assertTrue(Sse.isDone("data: [DONE]"))
        assertTrue(Sse.isDone("data:[DONE]"))
        assertFalse(Sse.isDone("""data: {"choices":[]}"""))
    }

    @Test
    fun brokenJsonDoesNotCrash() {
        assertNull(Sse.delta("data: {not json", json))
    }

    @Test
    fun multiLineJsonInOneEventStillWorks() {
        // 有些服务端一个事件里塞多行 data，这里取到第一行就够——正文都在同一行里
        val line = "data: " + """{"choices":[{"delta":{"content":"多行"}}]}""" + " "
        assertEquals("多行", Sse.delta(line, json))
    }
}
