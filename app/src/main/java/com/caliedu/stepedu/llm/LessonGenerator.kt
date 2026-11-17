package com.caliedu.stepedu.llm

import com.caliedu.stepedu.core.CodeDigest
import com.caliedu.stepedu.core.Prompts
import com.caliedu.stepedu.data.LlmSettings
import com.caliedu.stepedu.model.LessonPlan
import com.caliedu.stepedu.model.StepBrief
import com.caliedu.stepedu.model.StepContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

class LessonGenerator(
    private val channelFor: (LlmSettings) -> ChatChannel = { OpenAiChatClient(it) },
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    suspend fun analyze(settings: LlmSettings, input: String): LessonPlan {
        val isCode = CodeDigest.looksLikeCode(input)
        val payload = if (isCode && CodeDigest.shouldDigest(input)) CodeDigest.digest(input) else input
        val raw = channelFor(settings).complete(
            Prompts.analyzeSystem,
            Prompts.analyzeUser(payload, isCode),
            json = true,
        )
        val plan = decode(raw, LessonPlan.serializer())
            ?: throw ChatException("没能把返回内容读成步骤表。开头是：${raw.take(100)}")
        if (plan.steps.isEmpty()) throw ChatException("模型给出了空步骤，重试一次或换个模型。")
        return plan.copy(steps = plan.steps.mapIndexed { index, step ->
            step.copy(n = if (step.n > 0) step.n else index + 1)
        })
    }

    suspend fun teach(
        settings: LlmSettings,
        plan: LessonPlan,
        brief: StepBrief,
        notes: String,
    ): StepContent {
        val raw = channelFor(settings).complete(
            Prompts.teachSystem,
            Prompts.teachUser(plan, brief, notes),
            json = true,
        )
        val content = decode(raw, StepContent.serializer(), unwrapKeys = listOf("step", "内容", "讲义"))
            ?: throw ChatException("第 ${brief.n} 步的讲义没能解析。开头是：${raw.take(100)}")
        return content.copy(n = brief.n)
    }

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
        root.keys.none { it in setOf("title", "steps", "blocks", "question") }

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
    )
}
