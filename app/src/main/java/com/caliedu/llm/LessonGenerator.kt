package com.caliedu.llm

import com.caliedu.core.CodeDigest
import com.caliedu.core.Judge
import com.caliedu.core.Prompts
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

    suspend fun analyze(settings: LlmSettings, input: String): LessonPlan {
        val isCode = CodeDigest.looksLikeCode(input)
        val payload = if (isCode && CodeDigest.shouldDigest(input)) CodeDigest.digest(input) else input
        val raw = channelOf(settings).complete(
            Prompts.analyzeSystem,
            Prompts.analyzeUser(payload, isCode),
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
        return safe.copy(steps = safe.steps.mapIndexed { index, step ->
            step.copy(n = if (step.n > 0) step.n else index + 1)
        })
    }

    private fun planOk(plan: LessonPlan?): Boolean =
        plan != null && plan.steps.isNotEmpty() && plan.steps.all { it.title.isNotBlank() }

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
            Prompts.teachSystem,
            Prompts.teachUser(plan, brief, notes),
            json = true,
            label = "第 ${brief.n} 步《${brief.title}》讲义",
        )
        var content = decode(raw, StepContent.serializer(), unwrapKeys = listOf("step", "内容", "讲义"))
        if (!contentOk(content)) {
            val fixed = repair(settings, STEP_SCHEMA, raw, "第 ${brief.n} 步 · 补字段")
            content = decode(fixed, StepContent.serializer(), unwrapKeys = listOf("step", "内容", "讲义")) ?: content
        }
        if (!contentOk(content)) {
            throw ChatException("第 ${brief.n} 步的讲义没能解析。开头是：${raw.take(120)}")
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
        root.keys.none { it in setOf("title", "steps", "blocks", "question", "verdict") }

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
        const val PLAN_SCHEMA =
            """{"title":"","kind":"code|math|general","oneLine":"","knowledge":[""],"steps":[{"n":1,"title":"","stage":"读题|框架|预备|逐块|排错|整合|自测","goal":"","judgeKind":"choice|short|checklist"}],"needsRun":false}"""

        const val STEP_SCHEMA =
            """{"n":1,"blocks":[{"depth":"core|deep","md":""}],"question":{"prompt":"","kind":"choice|short|checklist","choices":[{"label":"","value":""}],"answer":"","expect":"","rubric":[""]},"hints":["","",""],"onCorrect":"","summary":[""]}"""
    }

    private val keyMap = mapOf(
        "标题" to "title", "名字" to "title", "题名" to "title",
        "题型" to "kind", "类型" to "kind",
        "一句话" to "oneLine", "简介" to "oneLine", "概述" to "oneLine",
        "知识点" to "knowledge", "知识" to "knowledge",
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
