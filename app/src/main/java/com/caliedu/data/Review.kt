package com.caliedu.data

/**
 * 错题的复习节奏。
 *
 * 借的是 DeepTutor / 间隔重复那一套：答错不是记一笔就完了，
 * 隔一天、三天、七天、二十一天各回来一次；每次答对，下一次就推得更远。
 * 答错则打回一天后，重新排队。
 */
object Review {

    private const val DAY = 24L * 60 * 60 * 1000

    private val ladder = intArrayOf(1, 3, 7, 21)

    fun intervalDays(reviews: Int): Int = ladder[reviews.coerceIn(0, ladder.lastIndex)]

    /** 到期时间。老记录没有这个字段时，按答错时间 + 当前档位现算。 */
    fun dueAt(mistake: Mistake): Long =
        if (mistake.dueAt > 0) mistake.dueAt else mistake.at + intervalDays(mistake.reviews) * DAY

    /**
     * 到没到期只看时间，不看有没有订正过。
     *
     * 订正只代表「当时答对了」，不代表「一周后还记得」——间隔重复的意义
     * 就在于让订正过的题隔几天再回来一次。原来这里带着 `!fixed`，
     * 结果第一次答对之后这条题就再也不出现，后面的 3/7/21 天全白排了。
     */
    fun isDue(mistake: Mistake, now: Long = System.currentTimeMillis()): Boolean =
        now >= dueAt(mistake)

    /** 答对之后：往后推一档。 */
    fun afterCorrect(mistake: Mistake, now: Long = System.currentTimeMillis()): Mistake {
        val next = mistake.reviews + 1
        return mistake.copy(
            fixed = true,
            reviews = next,
            dueAt = now + intervalDays(next) * DAY,
        )
    }

    /** 答错之后：回到一天后，重新来。 */
    fun afterWrong(mistake: Mistake, now: Long = System.currentTimeMillis()): Mistake =
        mistake.copy(fixed = false, dueAt = now + DAY)

    fun dueCount(records: List<LessonRecord>, now: Long = System.currentTimeMillis()): Int =
        records.sumOf { record -> record.mistakes.count { isDue(it, now) } }
}
