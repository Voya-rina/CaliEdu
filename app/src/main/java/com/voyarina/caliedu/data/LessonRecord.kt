package com.voyarina.caliedu.data

import com.voyarina.caliedu.model.LessonPlan
import com.voyarina.caliedu.model.StepContent
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
    /**
     * 已经过关的步号（从 0 起）。
     *
     * 没有它就只能靠 cursor 表示进度，于是「翻回第 3 步看一眼」之后再往前走，
     * 第 4~10 步全都要重答一遍。旧记录这个字段是空的，打开时按 cursor 补一次。
     */
    val passed: List<Int> = emptyList(),
    /** 上次退出时是不是停在通读。下次打开接上，不用每次重新切。 */
    val reading: Boolean = false,
    /** 通读读到第几节（从 0 起）；-1 表示没通读过。和做题游标分开，互不覆盖。 */
    val readingAt: Int = -1,
    /**
     * 这份讲义按什么标记分节（`auto` / `1`~`6` / `>`）。
     *
     * 存在记录上而不是全局设置里：同一批讲义的分节习惯往往不一样，
     * 全局选一个只会让另一批全错。
     */
    val mark: String = "auto",
    /** 从哪个文件读进来的。资料页靠它把「文件」和「讲义」合成一条。 */
    val uri: String = "",
) {
    val total: Int get() = plan.steps.size

    /**
     * 讲义型：没有提问，正文来自 md 原文。
     *
     * 这类记录归「资料」页，不进「记录」——后者是做题的地方，
     * 混着几十节只读的讲义，筛选和统计都会失真。
     */
    val isDoc: Boolean
        get() = source.isNotBlank() && plan.steps.isNotEmpty() &&
            plan.steps.all { it.judgeKind == "none" }

    /**
     * 读到哪了。
     *
     * 只看游标，不看过了几关：过了 20 步但最后停在第 8 步，进度条应该停在第 8 步，
     * 那才是「你在哪」。「已过几步」是成绩，不是位置。
     */
    val ratio: Float get() = when {
        total <= 0 -> 0f
        else -> ((cursor + 1).toFloat() / total).coerceIn(0f, 1f)
    }
    val headline: String get() = title.ifBlank { plan.title.ifBlank { "未命名" } }
}

/**
 * 删掉一条错题。
 *
 * 先按整条相等找：界面拿到的就是这一条，正常情况下一次命中。
 * 老记录里 `at` 可能是 0、`input` 可能为空，退一步按「步号 + 时间 + 自己的答案」找，
 * 免得把同一步里另一条错题误删。
 */
fun LessonRecord.withoutMistake(target: Mistake): LessonRecord {
    val exact = mistakes.indexOf(target)
    val index = if (exact >= 0) exact else mistakes.indexOfFirst {
        it.step == target.step && it.at == target.at && it.input == target.input
    }
    if (index < 0) return this
    return copy(mistakes = mistakes.toMutableList().also { it.removeAt(index) })
}

fun LessonRecord.withoutMistakes(): LessonRecord =
    if (mistakes.isEmpty()) this else copy(mistakes = emptyList())

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
    /** 事后在同一步答对过一次，就算订正。旧记录反序列化时默认 false。 */
    val fixed: Boolean = false,
    /** 已经复习过几轮，决定下次隔几天再来。 */
    val reviews: Int = 0,
    /** 下次该重做的时间戳；0 表示按 at + 间隔现算。 */
    val dueAt: Long = 0L,
)

/** 记录页顶部的汇总。全部由本地记录算出来，不联网。 */
data class StudyStats(
    val lessons: Int = 0,
    val finished: Int = 0,
    val solved: Int = 0,
    val wrongs: Int = 0,
    val streakDays: Int = 0,
    val weak: List<WeakPoint> = emptyList(),
    val mastery: List<Mastery> = emptyList(),
    val due: Int = 0,
) {
    val accuracy: Float get() = if (solved + wrongs == 0) 0f else solved.toFloat() / (solved + wrongs)
}

data class WeakPoint(val name: String, val wrongs: Int)

/** 一个知识点的掌握度。分母是这一课的总答题数，按知识点平摊。 */
data class Mastery(val name: String, val solved: Int, val wrongs: Int) {
    val total: Int get() = solved + wrongs
    val ratio: Float get() = if (total == 0) 0f else solved.toFloat() / total
}

object Stats {

    fun of(records: List<LessonRecord>): StudyStats {
        if (records.isEmpty()) return StudyStats()
        val solved = records.sumOf { it.solved }
        val wrongs = records.sumOf { it.wrongs }

        // 每个知识点的错题数：把一课的错按它的知识点平摊，够用且不会夸大。
        val weak = mutableMapOf<String, Int>()
        val rightByTag = mutableMapOf<String, Int>()
        records.forEach { record ->
            val tags = record.plan.knowledge.filter { it.isNotBlank() }
            if (tags.isEmpty()) return@forEach
            val share = 1f / tags.size
            tags.forEach { tag ->
                rightByTag[tag] = (rightByTag[tag] ?: 0) + Math.round(record.solved * share)
                if (record.wrongs > 0) {
                    weak[tag] = (weak[tag] ?: 0) + Math.round(record.wrongs * share)
                }
            }
        }

        val mastery = rightByTag.keys.map { tag ->
            Mastery(tag, rightByTag[tag] ?: 0, weak[tag] ?: 0)
        }.filter { it.total >= 2 }
            .sortedBy { it.ratio }
            .take(5)

        return StudyStats(
            lessons = records.size,
            finished = records.count { it.finished },
            solved = solved,
            wrongs = wrongs,
            streakDays = streak(records.map { it.updatedAt }),
            due = Review.dueCount(records),
            weak = weak.entries
                .filter { it.value > 0 }
                .sortedByDescending { it.value }
                .take(3)
                .map { WeakPoint(it.key, it.value) },
            mastery = mastery,
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
