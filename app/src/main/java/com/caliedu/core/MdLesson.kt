package com.caliedu.core

import com.caliedu.model.Block
import com.caliedu.model.LessonPlan
import com.caliedu.model.Question
import com.caliedu.model.StepBrief
import com.caliedu.model.StepContent

/**
 * 把一份写好的 Markdown 讲义直接变成一门课。
 *
 * 不是每道题都值得现生成一遍——教材、笔记、别人整理好的题解，本来就已经是分好节的。
 * 这里按「第一个标题当书名，之后每个一级/二级标题当一节」切开，正文原样保留，
 * 每节不带提问（question 留空），学习页会退化成纯阅读。
 */
object MdLesson {

    data class Doc(val plan: LessonPlan, val contents: Map<Int, StepContent>)

    /** 太短的小节多半是章节封面或者分隔线，收进来只是给自己添堵。 */
    private const val MIN_BODY = 24

    fun parse(raw: String, fallbackTitle: String = "未命名讲义"): Doc {
        val lines = raw.replace("\r\n", "\n").split("\n")
        val heads = mutableListOf<Int>()      // 每个标题所在行
        val headText = mutableListOf<String>()
        val levels = mutableListOf<Int>()

        lines.forEachIndexed { index, line ->
            val level = when {
                line.startsWith("# ") -> 1
                line.startsWith("## ") -> 2
                else -> 0
            }
            if (level > 0) {
                heads += index
                levels += level
                headText += line.drop(level + 1).trim()
            }
        }
        if (heads.isEmpty()) {
            // 没有任何标题：整篇当一节
            val body = raw.trim()
            return Doc(
                plan = planOf(fallbackTitle, TrackDetector.detect(raw), listOf("正文" to body)),
                contents = contentsOf(listOf("正文" to body)),
            )
        }

        // 第一个标题当书名，其余每节一段
        val title = headText.first().ifBlank { fallbackTitle }
        val sections = mutableListOf<Pair<String, String>>()
        for (k in 1 until heads.size) {
            val start = heads[k]
            val end = heads.getOrElse(k + 1) { lines.size }
            val body = lines.subList(start + 1, end).joinToString("\n").trim()
            val name = headText[k].ifBlank { "第 $k 节" }
            sections += name to body
        }
        val kept = sections.filter { it.second.length >= MIN_BODY }
            .ifEmpty { sections }

        val track = TrackDetector.detect(raw)
        return Doc(
            plan = planOf(title, track, kept),
            contents = contentsOf(kept),
        )
    }

    private fun planOf(title: String, track: Track, sections: List<Pair<String, String>>): LessonPlan {
        val steps = sections.mapIndexed { index, (name, body) ->
            StepBrief(
                n = index + 1,
                title = name.take(28),
                stage = "讲义",
                goal = lead(body),
                judgeKind = "none",
            )
        }
        return LessonPlan(
            title = title.take(40),
            kind = track.key,
            oneLine = "${sections.size} 节",
            tags = Subject.normalize(emptyList(), track, title),
            steps = steps,
            needsRun = false,
        )
    }

    private fun contentsOf(sections: List<Pair<String, String>>): Map<Int, StepContent> =
        sections.mapIndexed { index, (_, body) ->
            StepContent(
                n = index + 1,
                blocks = listOf(Block("core", body)),
                // 讲义类没有提问：question 留空，学习页会自动隐藏答题卡。
                question = Question(),
                hints = emptyList(),
                onCorrect = "",
                summary = emptyList(),
            )
        }.associateBy { it.n }

    /** 取正文第一句当这节的摘要，目录里能看到「这节讲什么」。 */
    private fun lead(body: String): String {
        val first = body.lines()
            .asSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("```") && !it.startsWith("#") && !it.startsWith("|") }
            ?: return ""
        return first.replace(Regex("[*`_]"), "").take(38)
    }
}
