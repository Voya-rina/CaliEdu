package com.caliedu.llm

import com.caliedu.data.LlmSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ChatException(message: String) : IOException(message)

/** SSE 的一行 → 增量。抽成纯函数，好在单测里钉住行为。 */
internal object Sse {

    fun delta(line: String, json: Json): String? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("data:")) return null
        val data = trimmed.removePrefix("data:").trim()
        if (data.isEmpty() || data == "[DONE]") return null
        return runCatching {
            json.parseToJsonElement(data).jsonObject["choices"]
                ?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("delta")?.jsonObject
                ?.get("content")?.jsonPrimitive?.content
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun isDone(line: String): Boolean = line.trim() == "data: [DONE]" || line.trim() == "data:[DONE]"
}

interface ChatChannel {
    /** [label] 只给人看：直连时用作进度文案，接力时告诉用户这一包是干什么的。 */
    suspend fun complete(system: String, user: String, json: Boolean, label: String = ""): String

    /**
     * 流式版本。[onDelta] 会在每个增量到达时被调用，返回值是全文。
     * 默认退化成一次性返回——不是所有通道都值得实现流式。
     */
    suspend fun stream(system: String, user: String, onDelta: (String) -> Unit): String {
        val text = complete(system, user, json = false)
        onDelta(text)
        return text
    }
}

/**
 * 只实现一种协议：OpenAI 兼容的 /chat/completions。
 * DeepSeek、Kimi、智谱、LM Studio、llama-server、Ollama 都吃这一套。
 */
class OpenAiChatClient(private val settings: LlmSettings) : ChatChannel {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun endpoint(): String {
        val base = settings.baseUrl.trim().trimEnd('/')
        return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
    }

    override suspend fun complete(system: String, user: String, json: Boolean, label: String): String {
        return try {
            send(system, user, jsonMode = json)
        } catch (e: ChatException) {
            if (json && e.message?.contains("response_format") == true) send(system, user, jsonMode = false) else throw e
        }
    }

    private fun payload(system: String, user: String, jsonMode: Boolean, streamMode: Boolean = false): String =
        buildJsonObject {
            put("model", settings.model)
            put("temperature", 0.3)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", system)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", user)
                }
            }
            if (jsonMode) putJsonObject("response_format") { put("type", "json_object") }
            if (streamMode) put("stream", true)
        }.toString()

    /**
     * SSE：每行 `data: {...}`，取 `choices[0].delta.content`。
     * 用同步 execute 而不是 enqueue，因为要边读边吐；协程取消时关掉连接即可。
     */
    override suspend fun stream(system: String, user: String, onDelta: (String) -> Unit): String {
        val request = Request.Builder()
            .url(endpoint())
            .addHeader("Authorization", "Bearer ${settings.apiKey}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(payload(system, user, jsonMode = false, streamMode = true).toRequestBody("application/json".toMediaType()))
            .build()
        return withContext(Dispatchers.IO) {
            val call = client.newCall(request)
            val response = try {
                call.execute()
            } catch (e: IOException) {
                throw translate(e)
            }
            try {
                if (!response.isSuccessful) {
                    throw ChatException(httpMessage(response.code, runCatching { response.body?.string() }.getOrNull().orEmpty()))
                }
                val source = response.body?.source() ?: throw ChatException("服务器没有返回内容。")
                val out = StringBuilder()
                while (currentCoroutineContext().isActive) {
                    val line = source.readUtf8Line() ?: break
                    if (Sse.isDone(line)) break
                    val delta = Sse.delta(line, json) ?: continue
                    out.append(delta)
                    onDelta(delta)
                }
                if (out.isEmpty()) throw ChatException("模型没有吐出任何内容，换个模型试试。")
                out.toString()
            } finally {
                runCatching { response.close() }
            }
        }
    }

    private suspend fun send(system: String, user: String, jsonMode: Boolean): String {
        val request = Request.Builder()
            .url(endpoint())
            .addHeader("Authorization", "Bearer ${settings.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(payload(system, user, jsonMode).toRequestBody("application/json".toMediaType()))
            .build()
        val body = call(request)
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw ChatException("接口返回的不是 JSON：${body.take(120)}")
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        val content = choice?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
        val reasoning = choice?.get("message")?.jsonObject?.get("reasoning_content")?.jsonPrimitive?.content
        val text = content?.takeIf { it.isNotBlank() } ?: reasoning
        return text?.takeIf { it.isNotBlank() }
            ?: throw ChatException("模型返回了空内容。思考型模型有时把结果放在 reasoning_content，可换个非思考模型再试。")
    }

    private suspend fun call(request: Request): String = suspendCancellableCoroutine { cont ->
        val call = client.newCall(request)
        cont.invokeOnCancellation { runCatching { call.cancel() } }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isActive) return
                cont.resumeWithException(translate(e))
            }

            override fun onResponse(call: Call, response: Response) {
                val text = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                if (!cont.isActive) return
                if (!response.isSuccessful) {
                    cont.resumeWithException(ChatException(httpMessage(response.code, text)))
                    return
                }
                cont.resume(text)
            }
        })
    }

    private fun translate(e: IOException): ChatException = when (e) {
        is UnknownHostException -> ChatException("连不上服务器，检查网络或地址：${settings.baseUrl}")
        is SocketTimeoutException -> ChatException("等太久没回应。本地模型读完整个提示词可能要几分钟，也可以先取消。")
        else -> ChatException("请求失败：${e.message ?: e::class.simpleName}")
    }

    private fun httpMessage(code: Int, body: String): String {
        val detail = body.take(200)
        return when (code) {
            401, 403 -> "Key 被拒绝（$code）。检查 Key 是否填错或已过期。$detail"
            404 -> "地址或模型名不对（404）。当前地址 ${endpoint()}，模型 ${settings.model}。$detail"
            429 -> "请求太频繁或额度用完（429）。$detail"
            in 500..599 -> "服务端出错（$code），稍后再试。$detail"
            else -> "HTTP $code：$detail"
        }
    }
}
