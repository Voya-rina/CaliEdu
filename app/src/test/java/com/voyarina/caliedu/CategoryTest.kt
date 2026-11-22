package com.voyarina.caliedu

import com.voyarina.caliedu.core.Category
import com.voyarina.caliedu.core.Subject
import com.voyarina.caliedu.model.LessonPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分类是记录页筛选的唯一依据。旧记录没有 tags，也不能在筛选里消失——
 * 这一组测试守的就是「任何一条记录都算得出一个合法大类」。
 */
class CategoryTest {

    private fun plan(tags: List<String> = emptyList(), kind: String = "math", title: String = "求通项") =
        LessonPlan(title = title, kind = kind, tags = tags)

    @Test
    fun keepsModelMajor() {
        val p = plan(tags = listOf("高等数学", "数列"))
        assertEquals("高等数学", Category.majorOf(p))
        assertEquals(listOf("高等数学", "数列"), Category.tagsOf(p))
    }

    @Test
    fun foldsAliasIntoMajor() {
        val p = plan(tags = listOf("线代", "矩阵"))
        assertEquals("线性代数", Category.majorOf(p))
        // 「矩阵」也是线性代数的别名，不该再作为细分标签露出来
        assertEquals(listOf("线性代数"), Category.tagsOf(p))
    }

    @Test
    fun oldRecordFallsBackByTrack() {
        assertEquals("高等数学", Category.majorOf(plan()))
        assertEquals("其他", Category.majorOf(plan(kind = "general", title = "什么是熵")))
    }

    @Test
    fun oldCodeRecordFallsBackBySource() {
        val code = plan(kind = "code", title = "main")
        assertEquals("Python", Category.majorOf(code, "import numpy as np"))
        assertEquals("C语言", Category.majorOf(code, "#include <stdio.h>"))
        assertEquals("Java", Category.majorOf(code, "public static void main"))
    }

    @Test
    fun anythingGetsExactlyOneMajor() {
        listOf(plan(), plan(kind = "code"), plan(kind = "general"), LessonPlan()).forEach { p ->
            val major = Category.majorOf(p)
            assertTrue("大类必须来自固定列表：$major", major in Subject.ALL)
            assertEquals(major, Category.tagsOf(p).first())
            assertTrue(Category.tagsOf(p).size in 1..3)
        }
    }

    @Test
    fun minorsAreCappedAndDeduped() {
        val p = plan(tags = listOf("Python", "列表", "字典", "循环", "递归"))
        val tags = Category.tagsOf(p)
        assertEquals("Python", tags.first())
        assertEquals(3, tags.size)
        assertEquals(tags.distinct(), tags)
    }
}
