package com.voyarina.caliedu.llm

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 手动接力的中转站。
 *
 * 生成链路完全不知道自己在跟谁说话：[RelayChannel] 只是把请求挂起，
 * 等用户把模型输出粘回来再放行。所以拆题、写讲义、批改三处代码一个字都不用改。
 */
class RelayBroker {

    data class Pending(
        val id: Long = 0L,
        val label: String = "",
        val system: String = "",
        val user: String = "",
        val json: Boolean = true,
    ) {
        /** 贴给外部模型时用的整段文本。 */
        val packet: String get() = buildString {
            append(system.trim())
            if (system.isNotBlank() && user.isNotBlank()) append("\n\n---\n\n")
            append(user.trim())
        }
    }

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    private var gate: CompletableDeferred<String>? = null
    private var seq = 0L

    suspend fun await(label: String, system: String, user: String, json: Boolean): String {
        val deferred = CompletableDeferred<String>()
        gate = deferred
        _pending.value = Pending(++seq, label, system, user, json)
        try {
            return deferred.await()
        } finally {
            if (gate === deferred) {
                gate = null
                _pending.value = null
            }
        }
    }

    fun submit(text: String): Boolean {
        val g = gate ?: return false
        if (g.isCompleted) return false
        g.complete(text)
        return true
    }

    fun cancel() {
        gate?.cancel()
        gate = null
        _pending.value = null
    }
}

class RelayChannel(private val broker: RelayBroker) : ChatChannel {
    override suspend fun complete(system: String, user: String, json: Boolean, label: String): String =
        broker.await(label = label, system = system, user = user, json = json)
}
