package com.voyarina.caliedu

import com.voyarina.caliedu.core.MdLesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 讲义分节：自动判断 + 用户指定标记。
 *
 * 认错的代价是整份讲义白读——要么只有一节，要么被切成几十个两行的碎片。
 * 认错了总还能在阅读器里改，所以这里主要验「改得动」和「改错不崩」。
 */
class MdSectionTest {

    private val twoLevel = """
        # 鲍鱼年龄预测

        ## 读文件

        用 loadtxt 把整张表读进来，注意第一列是字符串，不能直接转 float 数组。

        ## 切分

        拿八成交训练、两成验证，切之前一定要先打乱，否则同一批样本连着进模型。

        ### 细节

        这里的小标题只是正文里的分点，不该单独成节。
    """.trimIndent()

    private val quoted = """
        # 讲义

        开头这段话属于导语，没有引用块，按理要挂到第一节。

        > 第一节
        第一节的正文，写够长度才好判断它是不是真的被切出来了。

        > 第二节
        第二节的正文，同样写够长度，避免被过滤掉。

        > 第三节
        第三节的正文，也写够长度，三节都能出现。
    """.trimIndent()

    private val mixed = """
        # 讲义

        ## 第一章

        章的导语，写够长度写够长度写够长度写够长度。

        ### 甲

        甲的正文，写够长度写够长度写够长度。

        ### 乙

        乙的正文，写够长度写够长度写够长度。

        ## 第二章

        章的导语，写够长度写够长度写够长度写够长度。
    """.trimIndent()

    @Test
    fun autoPicksSecondLevel() {
        val doc = MdLesson.parse(twoLevel, "讲义")
        assertEquals(2, doc.plan.steps.size)
        assertEquals("读文件", doc.plan.steps[0].title)
        assertEquals("切分", doc.plan.steps[1].title)
        assertEquals("auto", doc.mark)
    }

    @Test
    fun forcingLevelChangesSplit() {
        assertEquals("第一章", MdLesson.parse(mixed, "讲义").plan.steps[0].title)
        // 同一份讲义改成按 ### 切：第一章的导语落到「前言」，后面才是两个小节
        val doc = MdLesson.parse(mixed, "讲义", "3")
        assertEquals(listOf("前言", "甲", "乙"), doc.plan.steps.map { it.title })
        assertEquals("3", doc.mark)
    }

    @Test
    fun impossibleMarkFallsBackToAuto() {
        // 这份讲义只有 ##，指定 #### 切不出东西，不能给一个空列表
        val doc = MdLesson.parse(twoLevel, "讲义", "4")
        assertEquals(2, doc.plan.steps.size)
        assertEquals("auto", doc.mark)
    }

    @Test
    fun quoteMarkSplitsByBlockQuote() {
        val doc = MdLesson.parse(quoted, "讲义", ">")
        assertEquals(3, doc.plan.steps.size)
        assertEquals("第一节", doc.plan.steps[0].title)
        assertEquals(">", doc.mark)
        // 第一个引用块之前的导语不能丢
        assertTrue(doc.contents[1]?.blocks?.first()?.md.orEmpty().contains("导语"))
    }

    @Test
    fun everyStepGetsContent() {
        val doc = MdLesson.parse(quoted, "讲义", ">")
        doc.plan.steps.forEach { brief ->
            val body = doc.contents[brief.n]?.blocks?.first()?.md.orEmpty()
            assertTrue("第 ${brief.n} 节没正文", body.isNotBlank())
        }
    }

    @Test
    fun markLabelsAreReadable() {
        assertEquals("自动", MdLesson.labelOf(MdLesson.MARK_AUTO))
        assertEquals("##", MdLesson.labelOf("2"))
        assertEquals("引用块 >", MdLesson.labelOf(">"))
    }

    /** 真讲义跑一遍：节数和标题都得像人写的，不能被切成一堆两行的碎片。 */
    @Test
    fun sampleLectureSectionsAreSane() {
        val file = java.io.File("src/main/assets/samples/abalone.md")
        org.junit.Assume.assumeTrue(file.exists())
        val doc = MdLesson.parse(file.readText(), "鲍鱼年龄预测")
        println("sections=${doc.plan.steps.size} titles=${doc.plan.steps.map { it.title }}")
        assertTrue(doc.plan.steps.size in 10..60)
        assertTrue(doc.plan.steps.all { it.title.isNotBlank() })
        assertTrue(doc.plan.steps.all { doc.contents[it.n] != null })
        // 按引用块切是另一套结果，但同样不能崩
        val quoted = MdLesson.parse(file.readText(), "鲍鱼年龄预测", ">")
        assertTrue(quoted.plan.steps.size > doc.plan.steps.size / 2)
    }
}
