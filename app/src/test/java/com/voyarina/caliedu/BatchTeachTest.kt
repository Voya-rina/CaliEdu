package com.voyarina.caliedu

import com.voyarina.caliedu.core.Prompts
import com.voyarina.caliedu.core.Track
import com.voyarina.caliedu.llm.LessonGenerator
import com.voyarina.caliedu.model.LessonPlan
import com.voyarina.caliedu.model.StepBrief
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 接力模式下「一次写完一批」的对齐逻辑。
 *
 * 模型吐回来的 n 经常缺、重、或者从 0 开始，截断也常见。
 * 这里钉住三件事：编号能对上、没编号能按顺序补、截断时保住已经完整的步骤。
 */
class BatchTeachTest {

    private val briefs = listOf(
        StepBrief(1, "第一关", "概念", "说清定义", "choice"),
        StepBrief(2, "第二关", "辨析", "分清边界", "short"),
        StepBrief(3, "第三关", "结论", "收束答案", "choice"),
    )
    private val plan = LessonPlan(title = "测试课", kind = "general", steps = briefs)
    private val generator = LessonGenerator()

    @Test
    fun alignsByStepNumber() {
        val raw = """{"steps":[
            {"n":2,"blocks":[{"depth":"core","md":"第二步"}],"question":{"prompt":"q2","kind":"short"},"hints":["a","b","c"]},
            {"n":1,"blocks":[{"depth":"core","md":"第一步"}],"question":{"prompt":"q1","kind":"choice"},"hints":["a","b","c"]}
        ]}"""
        val map = generator.parseBatch(raw, briefs)
        assertEquals(setOf(1, 2), map.keys)
        assertEquals("第一步", map.getValue(1).blocks.first().md)
        assertEquals("第二步", map.getValue(2).blocks.first().md)
    }

    @Test
    fun missingNumbersFallBackToOrder() {
        val raw = """{"steps":[
            {"blocks":[{"depth":"core","md":"甲"}]},
            {"blocks":[{"depth":"core","md":"乙"}]}
        ]}"""
        val map = generator.parseBatch(raw, briefs)
        assertEquals(setOf(1, 2), map.keys)
        assertEquals("甲", map.getValue(1).blocks.first().md)
        assertEquals("乙", map.getValue(2).blocks.first().md)
    }

    @Test
    fun duplicateNumbersDoNotOverwrite() {
        val raw = """{"steps":[
            {"n":1,"blocks":[{"depth":"core","md":"甲"}]},
            {"n":1,"blocks":[{"depth":"core","md":"乙"}]}
        ]}"""
        val map = generator.parseBatch(raw, briefs)
        assertEquals(setOf(1, 2), map.keys)
        assertEquals("甲", map.getValue(1).blocks.first().md)
        assertEquals("乙", map.getValue(2).blocks.first().md)
    }

    @Test
    fun truncatedBatchKeepsCompleteSteps() {
        val raw = "{\"steps\":[" +
            "{\"n\":1,\"blocks\":[{\"depth\":\"core\",\"md\":\"完整\"}],\"question\":{\"prompt\":\"q\"}}," +
            "{\"n\":2,\"blocks\":[{\"depth\":\"core\",\"md\":\"被截断的"
        val map = generator.parseBatch(raw, briefs)
        assertEquals(setOf(1), map.keys)
        assertEquals("完整", map.getValue(1).blocks.first().md)
    }

    @Test
    fun fencedJsonIsAccepted() {
        val raw = """
            ```json
            {"steps":[{"n":3,"blocks":[{"depth":"core","md":"第三步"}]}]}
            ```
        """.trimIndent()
        val map = generator.parseBatch(raw, briefs)
        assertEquals(setOf(3), map.keys)
    }

    @Test
    fun userPromptMarksOnlyThisBatch() {
        val all = Prompts.teachAllUser(plan, briefs, "")
        assertTrue("整批时应当说每一步都要写", all.contains("每一步都要写"))
        assertTrue(all.contains("共 3 步"))

        val part = Prompts.teachAllUser(plan, briefs.drop(1), "")
        assertTrue("分批时要标出这一批写哪几步", part.contains("← 要写"))
        assertTrue("分批时要提醒别重复上一批", part.contains("不要重复"))
        assertTrue(part.contains("共 2 步"))
    }

    @Test
    fun systemPromptDemandsTheWholeBatch() {
        val sys = Prompts.teachAllSystem(Track.GENERAL)
        assertTrue(sys.contains("\"steps\""))
        assertTrue(sys.contains("不许合并、跳号"))
        assertTrue(sys.contains("不许重复"))
    }
}
