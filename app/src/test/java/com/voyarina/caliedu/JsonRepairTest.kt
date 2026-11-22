package com.voyarina.caliedu

import com.voyarina.caliedu.llm.JsonRepair
import com.voyarina.caliedu.llm.MathEscapes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonRepairTest {

    private val json = Json { isLenient = true }

    @Test
    fun stripsFenceAndSmallTalk() {
        val raw = """
            好的，下面是结果：

            ```json
            {"title":"鲍鱼","steps":[]}
            ```
            希望对你有帮助。
        """.trimIndent()
        val out = JsonRepair.extract(raw)
        assertNotNull(out)
        assertEquals("鲍鱼", json.parseToJsonElement(out!!).jsonObject["title"]?.jsonPrimitive?.content)
    }

    @Test
    fun repairsIllegalEscape() {
        val raw = """{"md":"正则 \lim 不是合法转义"}"""
        val out = JsonRepair.extract(raw)!!
        val md = json.parseToJsonElement(out).jsonObject["md"]!!.jsonPrimitive.content
        assertTrue(md.contains("\\lim"))
    }

    @Test
    fun keepsRealNewlineEscape() {
        val raw = """{"md":"第一行\n第二行"}"""
        val out = JsonRepair.extract(raw)!!
        val md = json.parseToJsonElement(out).jsonObject["md"]!!.jsonPrimitive.content
        assertEquals("第一行\n第二行", md)
    }

    @Test
    fun balancesBracesInsideStrings() {
        val raw = """前缀 {"md":"含 } 花括号的字符串","n":2} 后缀"""
        val out = JsonRepair.extract(raw)!!
        assertEquals(2, json.parseToJsonElement(out).jsonObject["n"]?.jsonPrimitive?.content?.toInt())
    }
}

class MathEscapesTest {

    private val d = "$"

    @Test
    fun restoresFormfeedInsideMath() {
        val broken = "求 ${d}x'=\\frac{a}{b}${d} 的值".replace("\\f", "\u000C")
        assertTrue(MathEscapes.restore(broken).contains("\\frac"))
    }

    @Test
    fun restoresNewlineInsideMath() {
        val broken = "符号 ${d}\\nu${d} 表示自由度".replace("\\n", "\n")
        assertTrue(MathEscapes.restore(broken).contains("\\nu"))
    }

    @Test
    fun leavesTextOutsideMathAlone() {
        val text = "第一行\n第二行，cost\\text 不在公式里"
        assertEquals(text, MathEscapes.restore(text))
    }
}
