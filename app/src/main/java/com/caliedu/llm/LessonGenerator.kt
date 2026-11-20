package com.caliedu.llm

import com.caliedu.core.CodeDigest
import com.caliedu.core.Prompts
import com.caliedu.core.Track
import com.caliedu.core.Subject
import com.caliedu.core.TrackDetector
import com.caliedu.core.Verdict
import com.caliedu.data.ChannelMode
import com.caliedu.data.LlmSettings
import com.caliedu.model.LessonPlan
import com.caliedu.model.Question
import com.caliedu.model.StepBrief
import com.caliedu.model.StepContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
private data class GradeJson(val verdict: String = "", val note: String = "")

@Serializable
private data class VariantJson(val title: String = "", val problem: String = "")

data class Grade(val verdict: Verdict, val note: String)

class LessonGenerator(
    val relay: RelayBroker = RelayBroker(),
    private val directFor: (LlmSettings) -> ChatChannel = { OpenAiChatClient(it) },
) {

    private val relayChannel: ChatChannel by lazy { RelayChannel(relay) }

    fun channelOf(settings: LlmSettings): ChatChannel =
        if (settings.mode == ChannelMode.RELAY) relayChannel else directFor(settings)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    suspend fun analyze(settings: LlmSettings, input: String, forced: Track? = null): LessonPlan {
        // 用户手动选了类型就听他的：本地猜的和模型说的都不算。
        val hint = forced ?: TrackDetector.detect(input)
        val isCode = hint == Track.CODE
        val payload = if (isCode && CodeDigest.shouldDigest(input)) CodeDigest.digest(input) else input
        val raw = channelOf(settings).complete(
            Prompts.analyzeSystem(hint),
            Prompts.analyzeUser(payload, hint),
            json = true,
            label = "拆题",
        )
        var plan = decode(raw, LessonPlan.serializer())
        // 半截 JSON、漏了 steps：补发一次，别让用户自己重来。
        if (!planOk(plan)) {
            val fixed = repair(settings, PLAN_SCHEMA, raw, "拆题 · 补字段")
            plan = decode(fixed, LessonPlan.serializer()) ?: plan
        }
        if (!planOk(plan)) throw ChatException("没能把返回内容读成步骤表。开头是：${raw.take(120)}")
        val safe = plan!!
        // 步数不设限，但仍要挡住模型发疯或漏编号的情况：编号强制连续，超长截断。
        val steps = safe.steps
            .filter { it.title.isNotBlank() }
            .take(MAX_STEPS)
            .mapIndexed { index, step -> step.copy(n = index + 1) }
        val track = forced ?: settle(hint, safe.kind)
        // 分类必须以固定大类开头，模型写花了就地折回来。
        return safe.copy(
            kind = track.key,
            tags = Subject.normalize(safe.tags, track, input),
            steps = steps,
        )
    }

    /**
     * 照原题写一道同类型的新题，只返回题目原文。
     *
     * 拆题、讲义、判分仍然走原来那条流水线——不另起一套 prompt，
     * 也不新增一种「练习课」的数据结构。
     */
    suspend fun variant(settings: LlmSettings, plan: LessonPlan, notes: String): String {
        val raw = channelOf(settings).complete(
            Prompts.variantSystem,
            Prompts.variantUser(plan, notes),
            json = true,
            label = "出一道同类题",
        )
        val unwrap = listOf("题", "题目", "problem")
        var text = decode(raw, VariantJson.serializer(), unwrapKeys = unwrap)
            ?.problem?.trim().orEmpty()
        if (text.length < MIN_PROBLEM) {
            val fixed = repair(settings, VARIANT_SCHEMA, raw, "同类题 · 补字段")
            val again = decode(fixed, VariantJson.serializer(), unwrapKeys = unwrap)
                ?.problem?.trim().orEmpty()
            if (again.length > text.length) text = again
        }
        if (text.length >= MIN_PROBLEM) return text

        // 模型偶尔不按 JSON 来：剥掉围栏整段当题目用，总比把人卡住强。
        val plain = raw
            .replace(Regex("```[a-zA-Z]*"), "")
            .replace("```", "")
            .trim()
            .removePrefix("题目：")
            .removePrefix("新题：")
            .trim()
        if (plain.length >= MIN_PROBLEM && !plain.startsWith("{")) return plain
        throw ChatException("没能出一道同类题。开头是：${raw.take(120)}")
    }

    /**
     * 本地先猜的题型和模型返回的题型对不上时，谁说了算：
     * 代码文件不会被误判成数学题，所以本地认出代码就直接用代码那套；
     * 其余情况宁可偏向更具体的数学，也不要掉进通用模板。
     */
    private fun settle(hint: Track, modelKind: String): Track {
        val fromModel = Track.of(modelKind)
        return when {
            hint == Track.CODE -> Track.CODE
            hint == Track.MATH -> Track.MATH
            fromModel == Track.CODE || fromModel == Track.MATH -> fromModel
            else -> Track.GENERAL
        }
    }

    private fun planOk(plan: LessonPlan?): Boolean =
        plan != null && plan.steps.isNotEmpty() && plan.steps.any { it.title.isNotBlank() }

    private fun contentOk(content: StepContent?): Boolean =
        content != null && content.blocks.any { it.md.isNotBlank() }

    private suspend fun repair(settings: LlmSettings, schema: String, raw: String, label: String): String =
        channelOf(settings).complete(
            Prompts.repairSystem(schema),
            Prompts.repairUser(raw),
            json = true,
            label = label,
        )

    suspend fun teach(
        settings: LlmSettings,
        plan: LessonPlan,
        brief: StepBrief,
        notes: String,
    ): StepContent {
        val raw = channelOf(settings).complete(
            Prompts.teachSystem(Track.of(plan.kind)),
            Prompts.teachUser(plan, brief, notes),
            json = true,
            label = "第 ${brief.n} 步《${brief.title}》",
        )
        var content = decode(raw, StepContent.serializer(), unwrapKeys = listOf("step", "内容", "讲义"))
        if (!contentOk(content)) {
            val fixed = repair(settings, STEP_SCHEMA, raw, "第 ${brief.n} 步 · 补字段")
            content = decode(fixed, StepContent.serializer(), unwrapKeys = listOf("step", "内容", "讲义")) ?: content
        }
        if (!contentOk(content)) {
            throw ChatException("第 ${brief.n} 步的正文没能解析。开头是：${raw.take(120)}")
        }
        return content!!.copy(n = brief.n)
    }

    /**
     * 开放题的兜底批改。返回 null 表示这次不判（没网、解析失败），
     * 调用方应退回本地判定，不能因为批改失败就把学生卡住。
     */
    suspend fun grade(
        settings: LlmSettings,
        brief: StepBrief,
        question: Question,
        input: String,
    ): Grade? {
        if (!settings.canGrade) return null
        val raw = runCatching {
            channelOf(settings).complete(
                Prompts.judgeSystem,
                Prompts.judgeUser(question, brief, input),
                json = true,
                label = "批改第 ${brief.n} 步",
            )
        }.getOrNull() ?: return null
        val parsed = decode(raw, GradeJson.serializer()) ?: return null
        val verdict = when (parsed.verdict.trim().lowercase()) {
            "correct", "对", "正确", "pass" -> Verdict.CORRECT
            "partial", "部分", "部分正确", "half" -> Verdict.PARTIAL
            "wrong", "错", "错误", "fail" -> Verdict.WRONG
            else -> return null
        }
        return Grade(verdict, parsed.note.trim())
    }

    /** 追问：流式返回，逐段回调。 */
    suspend fun ask(
        settings: LlmSettings,
        system: String,
        user: String,
        onDelta: (String) -> Unit,
    ): String = channelOf(settings).stream(system, user, onDelta)

    private fun <T> decode(
        raw: String,
        serializer: kotlinx.serialization.DeserializationStrategy<T>,
        unwrapKeys: List<String> = emptyList(),
    ): T? {
        val text = JsonRepair.extract(raw) ?: return null
        val element = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return null
        val root = normalizeKeys(fixMathEscapes(element)) as? JsonObject ?: return null
        val direct = runCatching { json.decodeFromJsonElement(serializer, root) }.getOrNull()
        if (direct != null && !looksEmpty(root)) return direct
        for (key in unwrapKeys) {
            val inner = root[key] as? JsonObject ?: continue
            runCatching { json.decodeFromJsonElement(serializer, inner) }.getOrNull()?.let { return it }
        }
        return direct
    }

    private fun looksEmpty(root: JsonObject): Boolean =
        root.keys.none { it in setOf("title", "steps", "blocks", "question", "verdict", "problem") }

    private fun fixMathEscapes(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (_, value) -> fixMathEscapes(value) })
        is JsonArray -> JsonArray(element.map { fixMathEscapes(it) })
        is JsonPrimitive -> if (element.isString) JsonPrimitive(MathEscapes.restore(element.content)) else element
        else -> element
    }

    private fun normalizeKeys(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.mapValues { (_, value) -> normalizeKeys(value) }
                .mapKeys { (key, value) -> keyMap[key] ?: key }
        )
        is JsonArray -> JsonArray(element.map { normalizeKeys(it) })
        else -> element
    }

    private companion object {
        /** 不设上下限，但得挡住上万步把内存和钱烧穿。正常题目远到不了这个数。 */
        const val MAX_STEPS = 60

        const val PLAN_SCHEMA =
            """{"title":"","kind":"code|math|general","oneLine":"","tags":[""],"knowledge":[""],"steps":[{"n":1,"title":"","stage":"读题|框架|预备|逐块|排错|整合|自测|建模|定路|推导|检验|收束|概念|推理|结论","goal":"","judgeKind":"choice|short|checklist"}],"needsRun":false}"""

        /** 一道题最短也得有这么多字，否则多半是模型在说「好的」而不是给题。 */
        const val MIN_PROBLEM = 20

        const val VARIANT_SCHEMA = """{"title":"","problem":""}"""

        const val STEP_SCHEMA =
            """{"n":1,"blocks":[{"depth":"core|deep","md":""}],"question":{"prompt":"","kind":"choice|short|checklist","choices":[{"label":"","value":""}],"answer":"","expect":"","rubric":[""]},"hints":["","",""],"onCorrect":"","summary":[""]}"""
    }

    private val keyMap = mapOf(
        "标题" to "title", "名字" to "title", "题名" to "title",
        "题型" to "kind", "类型" to "kind",
        "一句话" to "oneLine", "简介" to "oneLine", "概述" to "oneLine",
        "知识点" to "knowledge", "知识" to "knowledge",
        "分类" to "tags", "分类标签" to "tags", "章节" to "tags", "学科" to "tags",
        "步骤" to "steps", "节点" to "steps", "步骤表" to "steps", "节点计划" to "steps",
        "需运行" to "needsRun", "需要运行" to "needsRun",
        "步号" to "n", "序号" to "n", "编号" to "n",
        "目标" to "goal", "阶段" to "stage",
        "判定方式" to "judgeKind", "判定" to "judgeKind", "判断方式" to "judgeKind",
        "讲义" to "blocks", "内容" to "blocks", "块" to "blocks", "正文块" to "blocks",
        "问题" to "question", "提问" to "question",
        "选项" to "choices", "标签" to "label", "值" to "value",
        "答案" to "answer", "正确答案" to "answer",
        "可接受答案" to "expect", "答案要点" to "rubric", "打分要点" to "rubric",
        "提示" to "hints", "三级提示" to "hints",
        "答对" to "onCorrect", "正确回应" to "onCorrect", "答对回应" to "onCorrect",
        "小结" to "summary", "收束" to "summary", "要点总结" to "summary",
        "深度" to "depth", "正文" to "md", "markdown" to "md", "内容块" to "md",
        "判定结果" to "verdict", "结果" to "verdict", "点评" to "note", "说明" to "note",
    )
}
