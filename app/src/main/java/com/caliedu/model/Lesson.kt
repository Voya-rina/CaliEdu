package com.caliedu.model

import kotlinx.serialization.Serializable

@Serializable
data class LessonPlan(
    val title: String = "",
    val kind: String = "general",
    val oneLine: String = "",
    /** 分类标签：学科 / 章节级，两三个，用来在记录页筛选。knowledge 是具体知识点。 */
    val tags: List<String> = emptyList(),
    val knowledge: List<String> = emptyList(),
    val steps: List<StepBrief> = emptyList(),
    val needsRun: Boolean = false,
)

@Serializable
data class StepBrief(
    val n: Int = 0,
    val title: String = "",
    val stage: String = "",
    val goal: String = "",
    val judgeKind: String = "choice",
)

@Serializable
data class StepContent(
    val n: Int = 0,
    val blocks: List<Block> = emptyList(),
    val question: Question = Question(),
    val hints: List<String> = emptyList(),
    val onCorrect: String = "",
    val summary: List<String> = emptyList(),
)

@Serializable
data class Block(
    val depth: String = "core",
    val md: String = "",
)

@Serializable
data class Question(
    val prompt: String = "",
    val kind: String = "choice",
    val choices: List<Choice> = emptyList(),
    val answer: String = "",
    val expect: String = "",
    val rubric: List<String> = emptyList(),
)

@Serializable
data class Choice(
    val label: String = "",
    val value: String = "",
)
