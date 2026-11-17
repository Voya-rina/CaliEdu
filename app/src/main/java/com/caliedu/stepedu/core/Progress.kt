package com.caliedu.stepedu.core

/**
 * 一步之内的推进规则，与 UI 无关，可单测。
 *
 * 答对 → 可进入下一步（先给出正确回应）
 * 答错 → 连错几次就放几级提示；连错三次直接把答案补上，然后允许推进
 */
data class Progress(
    val idx: Int = 0,
    val hintsShown: Int = 0,
    val wrongStreak: Int = 0,
    val solved: Boolean = false,
    val revealed: Boolean = false,
) {
    val canAdvance: Boolean get() = solved || revealed

    fun correct(): Progress = copy(solved = true)

    fun wrong(): Progress {
        val streak = wrongStreak + 1
        return copy(
            wrongStreak = streak,
            hintsShown = maxOf(hintsShown, minOf(3, streak)),
            revealed = streak >= 3,
        )
    }

    fun hint(): Progress = copy(hintsShown = minOf(3, hintsShown + 1))

    fun next(total: Int): Progress =
        if (idx + 1 >= total) copy(idx = total) else Progress(idx = idx + 1)
}
