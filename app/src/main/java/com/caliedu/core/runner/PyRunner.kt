package com.caliedu.core.runner

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class RunResult(
    val ok: Boolean,
    val output: String,
    val ms: Long,
    val timedOut: Boolean = false,
)

/**
 * 本机 Python 执行器。
 *
 * 用反射调 Chaquopy，而不是直接 import：这样「执行器」有没有被编进 App
 * 都能编译、都能跑，缺了就老实说缺了，而不是整个 App 起不来。
 *
 * 三条已知边界（不能装不知道）：
 * - 只支持 64 位 ABI（arm64-v8a / x86_64）；
 * - 只有纯 Python 与少量带 Android wheel 的库，cv2 / torch 之类没有；
 * - C 扩展里的死循环没法强杀，只能超时后放它继续跑。
 */
object PyRunner {

    @Volatile
    private var ready = false

    @Volatile
    private var failure: String? = null

    private var python: Any? = null

    fun available(context: Context): Boolean = ensure(context) != null

    fun reason(): String = failure ?: "执行器未启用"

    private fun ensure(context: Context): Any? {
        if (ready) return python
        failure?.let { return null }
        return synchronized(this) {
            if (ready) return python
            runCatching {
                val pythonCls = Class.forName("com.chaquo.python.Python")
                val started = pythonCls.getMethod("isStarted").invoke(null) as Boolean
                if (!started) {
                    val platformCls = Class.forName("com.chaquo.python.android.AndroidPlatform")
                    val platform = platformCls.getConstructor(Context::class.java).newInstance(context)
                    // Python.Platform 是嵌套接口，类名是 Python$Platform
                    pythonCls.getMethod("start", Class.forName("com.chaquo.python.Python\$Platform"))
                        .invoke(null, platform)
                }
                val instance = pythonCls.getMethod("getInstance").invoke(null)
                python = instance
                ready = true
                instance
            }.getOrElse { e ->
                failure = when (e) {
                    is ClassNotFoundException -> "这个安装包没有带 Python 引擎"
                    else -> "Python 起不来：${e.message ?: e::class.simpleName}"
                }
                null
            }
        }
    }

    suspend fun run(context: Context, code: String, timeoutMs: Long = 12_000): RunResult =
        withContext(Dispatchers.IO) {
            val py = ensure(context) ?: return@withContext RunResult(false, reason(), 0)
            val started = System.currentTimeMillis()
            val gate = CompletableDeferred<RunResult>()
            Thread {
                val result = runCatching { invoke(py, code) }.getOrElse { e ->
                    RunResult(false, "执行失败：${e.cause?.message ?: e.message ?: e::class.simpleName}", 0)
                }
                if (!gate.isCompleted) gate.complete(result.copy(ms = System.currentTimeMillis() - started))
            }.apply { isDaemon = true }.start()

            withTimeoutOrNull(timeoutMs) { gate.await() } ?: RunResult(
                ok = false,
                output = "超过 ${timeoutMs / 1000} 秒还没结束。可能是死循环——它还在后台跑，本机没法强杀。",
                ms = System.currentTimeMillis() - started,
                timedOut = true,
            )
        }

    private fun invoke(py: Any, code: String): RunResult {
        val pyObjectClass = Class.forName("com.chaquo.python.PyObject")
        val module = py.javaClass.getMethod("getModule", String::class.java).invoke(py, "runner")
        val call = pyObjectClass.getMethod("callAttr", String::class.java, Array<Any>::class.java)
        val returned = call.invoke(module, "run", arrayOf<Any>(code))
        val values = pyObjectClass.getMethod("asList").invoke(returned) as List<*>
        val ok = values.getOrNull(0)?.toString()?.trim() == "True"
        val out = values.getOrNull(1)?.toString().orEmpty()
        return RunResult(ok, out.ifBlank { if (ok) "（没有任何输出）" else "（没有输出，但退出码非零）" }, 0)
    }
}
