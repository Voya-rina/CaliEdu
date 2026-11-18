package com.caliedu.data

import com.caliedu.model.LessonPlan
import com.caliedu.model.StepContent
import kotlinx.serialization.Serializable

/**
 * 一课的全部可持久化状态。
 *
 * `contents` 一起存下来：既让「继续」不必重新花钱生成，
 * 也让断网时还能把已经生成的步骤读完。
 */
@Serializable
data class LessonRecord(
    val id: String = "",
    val title: String = "",
    val oneLine: String = "",
    val source: String = "",
    val offline: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val plan: LessonPlan = LessonPlan(),
    val contents: Map<Int, StepContent> = emptyMap(),
    val cursor: Int = 0,
    val solved: Int = 0,
    val wrongs: Int = 0,
    val finished: Boolean = false,
    val mistakes: List<Mistake> = emptyList(),
) {
    val total: Int get() = plan.steps.size
    val ratio: Float get() = if (total <= 0) 0f else (cursor.toFloat() / total).coerceIn(0f, 1f)
    val headline: String get() = title.ifBlank { plan.title.ifBlank { "未命名" } }
}

/**
 * 一条错题。存题干和自己的答案，才能在没有原课的情况下复盘；
 * 只存"错了几次"的数字是没法复习的。
 */
@Serializable
data class Mistake(
    val step: Int = 0,
    val title: String = "",
    val prompt: String = "",
    val input: String = "",
    val answer: String = "",
    val at: Long = 0L,
)

/** 记录页顶部的汇总。全部由本地记录算出来，不联网。 */
data class StudyStats(
    val lessons: Int = 0,
    val finished: Int = 0,
    val solved: Int = 0,
    val wrongs: Int = 0,
    val streakDays: Int = 0,
    val weak: List<WeakPoint> = emptyList(),
) {
    val accuracy: Float get() = if (solved + wrongs == 0) 0f else solved.toFloat() / (solved + wrongs)
}

data class WeakPoint(val name: String, val wrongs: Int)

object Stats {

    fun of(records: List<LessonRecord>): StudyStats {
        if (records.isEmpty()) return StudyStats()
        val solved = records.sumOf { it.solved }
        val wrongs = records.sumOf { it.wrongs }

        // 每个知识点的错题数：把一课的错按它的知识点平摊，够用且不会夸大。
        val weak = mutableMapOf<String, Int>()
        records.forEach { record ->
            val tags = record.plan.knowledge.filter { it.isNotBlank() }
            if (tags.isEmpty() || record.wrongs == 0) return@forEach
            val each = record.wrongs.toFloat() / tags.size
            tags.forEach { tag -> weak[tag] = (weak[tag] ?: 0) + Math.round(each) }
        }

        return StudyStats(
            lessons = records.size,
            finished = records.count { it.finished },
            solved = solved,
            wrongs = wrongs,
            streakDays = streak(records.map { it.updatedAt }),
            weak = weak.entries
                .filter { it.value > 0 }
                .sortedByDescending { it.value }
                .take(3)
                .map { WeakPoint(it.key, it.value) },
        )
    }

    /** 连续天数：从今天往前数，哪天没有任何记录就断。 */
    private fun streak(times: List<Long>): Int {
        if (times.isEmpty()) return 0
        val days = times.map { dayKey(it) }.toSortedSet(reverseOrder())
        val today = dayKey(System.currentTimeMillis())
        var cursor = today
        var count = 0
        for (day in days) {
            if (day > cursor) continue
            if (day == cursor) {
                count++
                cursor--
            } else {
                break
            }
        }
        return count
    }

    private fun dayKey(at: Long): Int {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = at }
        return cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
    }
}
