package com.caliedu.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 记录存储：整表读写一个 JSON 文件。
 *
 * 为什么不用 Room：这里的数据形状是「一课一份 JSON」，天然就是文档而不是关系表，
 * 而且结构还在改。用 Room 反而要写迁移，还要多一个 KSP 插件。
 * 记录数控制在 [MAX] 条，文件只有几十 KB。
 */
class LessonStore(context: Context) {

    private val file = File(context.filesDir, "lessons.json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val mutex = Mutex()

    /** 读文件时发现损坏、把原文件改名备份后的路径。读一次就清掉。 */
    @Volatile
    var quarantined: String? = null
        private set

    /** 上一次写盘失败的原因；写成功时清空。 */
    @Volatile
    var lastWriteError: String? = null
        private set

    /** 取走「坏文件已备份」这件事，交给界面提示一次。 */
    fun consumeQuarantine(): String? {
        val value = quarantined
        quarantined = null
        return value
    }

    suspend fun all(): List<LessonRecord> = withContext(Dispatchers.IO) {
        mutex.withLock { readLocked() }.sortedByDescending { it.updatedAt }
    }

    suspend fun find(id: String): LessonRecord? = withContext(Dispatchers.IO) {
        mutex.withLock { readLocked().firstOrNull { it.id == id } }
    }

    /** 返回因为超出 [MAX] 被挤掉的条数，调用方据此提醒用户。 */
    suspend fun save(record: LessonRecord): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            val list = readLocked().toMutableList()
            val index = list.indexOfFirst { it.id == record.id }
            if (index >= 0) list[index] = record else list += record
            val sorted = list.sortedByDescending { it.updatedAt }
            val trimmed = sorted.take(MAX)
            writeLocked(trimmed)
            sorted.size - trimmed.size
        }
    }

    suspend fun delete(id: String): Unit = withContext(Dispatchers.IO) {
        mutex.withLock { writeLocked(readLocked().filterNot { it.id == id }) }
    }

    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock { writeLocked(emptyList()) }
    }

    /**
     * 读全表。
     *
     * 反序列化失败时**绝不能**返回空表：`save()` 是「读全表 → 改一条 → 写全表」，
     * 空表会被原样写回去，用户的历史就永久没了。坏文件先改名留证，再当空表处理。
     */
    private fun readLocked(): List<LessonRecord> {
        if (!file.exists()) return emptyList()
        val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrElse { return emptyList() }
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer, raw)
        }.getOrElse {
            val backup = File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}")
            runCatching { file.renameTo(backup) }
            quarantined = backup.name
            emptyList()
        }
    }

    /** 先写临时文件再改名，避免中途被杀留下半个文件。 */
    private fun writeLocked(list: List<LessonRecord>) {
        lastWriteError = null
        runCatching {
            val text = json.encodeToString(ListSerializer, list)
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(text, Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(text, Charsets.UTF_8)
                tmp.delete()
            }
        }.onFailure { e ->
            lastWriteError = e.message ?: e::class.simpleName ?: "写入失败"
        }
    }

    companion object {
        const val MAX = 60
        private val ListSerializer = kotlinx.serialization.builtins.ListSerializer(LessonRecord.serializer())
    }
}
