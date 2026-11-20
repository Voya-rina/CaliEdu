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
 * 正文原样保留，每节不带提问（question 留空），学习页会退化成纯阅读。
 *
 * 分节这件事比看上去麻烦，两个坑都踩过：
 * 1. 代码块里以 `#` 开头的是 Python 注释，不是标题。不认围栏的话，
 *    一份讲义会被 `# ======` 这种注释切出几十个只有两行的碎片。
 * 2. 讲义常常有多个一级标题（书名、章、部分）。只按「# 和 ## 都算一节」切，
 *    章标题会变成正文只有两句话的孤儿节，而它真正的作用是给下面几节分组。
 */
object MdLesson {

    data class Doc(val plan: LessonPlan, val contents: Map<Int, StepContent>)

    /** 太短的小节多半是章节封面或者分隔线，收进来只是给自己添堵。 */
    private const val MIN_BODY = 24

    private const val MAX_TITLE = 40

    private data class Head(val line: Int, val level: Int, val text: String)

    fun parse(raw: String, fallbackTitle: String = "未命名讲义"): Doc {
        val lines = raw.replace("\r\n", "\n").split("\n")
        val heads = headings(lines)

        if (heads.isEmpty()) {
            // 没有任何标题：整篇当一节
            val body = raw.trim()
            val single = listOf("正文" to body)
            return Doc(planOf(fallbackTitle, raw, single), contentsOf(single))
        }

        val titleHead = heads.first()
        val title = titleHead.text.ifBlank { fallbackTitle }
        val level = sectionLevel(heads.drop(1))

        val sections = if (level <= 0) {
            // 通篇只有一个标题：标题之后的所有内容当一节
            val body = lines.drop(titleHead.line + 1).joinToString("\n").trim()
            listOf("正文" to body)
        } else {
            slice(lines, heads, level, titleHead.line + 1)
        }
        val kept = sections.filter { it.second.length >= MIN_BODY }.ifEmpty { sections }
        return Doc(planOf(title, raw, kept), contentsOf(kept))
    }

    /**
     * 找出真正的标题行：跳过围栏代码块，只认 `#` 后面跟空格或行尾的那些。
     */
    private fun headings(lines: List<String>): List<Head> {
        val out = mutableListOf<Head>()
        var fence: String? = null

        lines.forEachIndexed { index, line ->
            val trimmed = line.trimStart()
            if (fence == null) {
                if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                    fence = trimmed.take(3)
                    return@forEachIndexed
                }
                if (trimmed.isEmpty() || trimmed[0] != '#') return@forEachIndexed
                val level = trimmed.takeWhile { it == '#' }.length
                if (level > 6) return@forEachIndexed
                val rest = trimmed.drop(level)
                // `#标题` 和 `#hashtag` 不是标题
                if (rest.isNotEmpty() && !rest.startsWith(" ")) return@forEachIndexed
                out += Head(index, level, rest.trim())
            } else if (trimmed.startsWith(fence)) {
                fence = null
            }
        }
        return out
    }

    /**
     * 这一份讲义按几级标题分节。
     *
     * 讲义几乎都用二级标题分节；只有二级标题不到两个时才往深处退一步，
     * 再不行就退到一级。按标题数量投票会让「两个部分标题 + 25 个小节」
     * 被判成两节，所以这里是有序的偏好，不是取最大值。
     */
    private fun sectionLevel(rest: List<Head>): Int {
        if (rest.isEmpty()) return 0
        val counts = rest.groupingBy { it.level }.eachCount()
        return listOf(2, 3, 1, 4, 5, 6).firstOrNull { (counts[it] ?: 0) >= 2 }
            ?: rest.minOf { it.level }
    }

    /**
     * 按 [level] 切段。
     *
     * - 同级标题开新节；
     * - 更浅的标题（`# 第一部分`）不开节，它连同后面的引言挂到**下一节**的开头，
     *   这样「第一部分」正好出现在它覆盖的那几节之前，而不是上一节的尾巴上；
     * - 更深的标题留在正文里，照常当小标题渲染。
     */
    private fun slice(
        lines: List<String>,
        heads: List<Head>,
        level: Int,
        from: Int,
    ): List<Pair<String, String>> {
        val byLine = heads.associateBy { it.line }
        val out = mutableListOf<Pair<String, String>>()
        var title: String? = null
        var body = mutableListOf<String>()
        // 还没找到归属的「部分 / 章」标题与它们的引言
        val pending = mutableListOf<String>()

        fun flush() {
            title?.let { out += it to body.joinToString("\n").trim() }
            title = null
            body = mutableListOf()
        }

        for (i in from until lines.size) {
            val head = byLine[i]
            if (head == null) {
                if (title != null) body += lines[i] else pending += lines[i]
                continue
            }
            when {
                head.level == level -> {
                    // 只有全文开篇（还没切出任何一节）的引言才单独成为「前言」。
                    // 中途的引言是「部分 / 章」标题的开场，属于下一节，不能单拎出来。
                    if (title == null && out.isEmpty() &&
                        pending.joinToString("\n").trim().length >= MIN_BODY
                    ) {
                        out += "前言" to pending.joinToString("\n").trim()
                        pending.clear()
                    }
                    flush()
                    title = head.text.ifBlank { "第 ${out.size + 1} 节" }
                    body = pending.toMutableList()
                    pending.clear()
                }

                head.level < level -> {
                    flush()
                    pending += lines[i]
                }

                else -> if (title != null) body += lines[i] else pending += lines[i]
            }
        }
        flush()

        // 文末还挂着「部分」标题却没有下一节，并回最后一节，别丢
        if (pending.isNotEmpty() && out.isNotEmpty()) {
            val last = out.removeAt(out.lastIndex)
            out += last.first to (last.second + "\n" + pending.joinToString("\n")).trim()
        }
        return out
    }

    private fun planOf(title: String, raw: String, sections: List<Pair<String, String>>): LessonPlan {
        val steps = sections.mapIndexed { index, (name, body) ->
            StepBrief(
                n = index + 1,
                title = name.take(28),
                stage = "讲义",
                goal = lead(body),
                judgeKind = "none",
            )
        }
        val track = TrackDetector.detect(raw)
        // 分类必须看内容，不能看 Track：一份用 numpy 讲回归的讲义满篇都是代码特征，
        // 但它的学科是机器学习。Track 决定怎么教，分类决定它属于哪一门。
        val guessed = Subject.guess(raw, title)
        return LessonPlan(
            title = title.take(MAX_TITLE),
            kind = track.key,
            oneLine = "${sections.size} 节",
            tags = if (guessed != null) listOf(guessed) else Subject.normalize(emptyList(), track, title),
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
            .firstOrNull {
                it.isNotEmpty() && !it.startsWith("```") && !it.startsWith("#") &&
                    !it.startsWith("|") && !it.startsWith(">") && !it.startsWith("---")
            }
            ?: return ""
        return first.replace(Regex("[*`_]"), "").take(38)
    }
}
