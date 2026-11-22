package com.voyarina.caliedu.sample

import com.voyarina.caliedu.model.LessonPlan
import com.voyarina.caliedu.model.StepContent

/** 内置示例的样子：一份大纲 + 已经写好的正文。让「示例」按钮能按题型挑一份。 */
interface Sample {
    val plan: LessonPlan
    val contents: Map<Int, StepContent>
}
