package com.caliedu.core

import com.caliedu.model.LessonPlan

/**
 * 一条记录属于哪个一级分类。
 *
 * 旧记录是在分类功能之前建的，tags 是空的；不能因为这样就让它们在筛选里消失。
 * 所以这里兜一层：tags 里认得出大类就用，认不出就按题型和题面猜一个。
 * 猜出来的结果只在界面上用，不写回记录——除非用户自己改。
 */
object Category {

    fun majorOf(plan: LessonPlan, hint: String = ""): String {
        plan.tags.forEach { tag -> Subject.match(tag)?.let { return it } }
        val track = Track.of(plan.kind)
        return Subject.fallback(track, hint.ifBlank { plan.title + plan.oneLine })
    }

    /** 显示用：大类在前，细分标签在后，去重，最多三个。 */
    fun tagsOf(plan: LessonPlan, hint: String = ""): List<String> {
        val major = majorOf(plan, hint)
        val minor = plan.tags
            .asSequence()
            .map { it.trim() }
            .filter { it.length in 2..8 && it != major && Subject.match(it) == null }
            .distinct()
            .take(2)
            .toList()
        return listOf(major) + minor
    }
}
