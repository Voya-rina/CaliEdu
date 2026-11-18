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

    suspend fun all(): List<LessonRecord> = withContext(Dispatchers.IO) {
        mutex.withLock { readLocked() }.sortedByDescending { it.updatedAt }
    }

    suspend fun find(id: String): LessonRecord? = withContext(Dispatchers.IO) {
        mutex.withLock { readLocked().firstOrNull { it.id == id } }
    }

    suspend fun save(record: LessonRecord): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val list = readLocked().toMutableList()
            val index = list.indexOfFirst { it.id == record.id }
            if (index >= 0) list[index] = record else list += record
            val trimmed = list.sortedByDescending { it.updatedAt }.take(MAX)
            writeLocked(trimmed)
        }
    }

    suspend fun delete(id: String): Unit = withContext(Dispatchers.IO) {
        mutex.withLock { writeLocked(readLocked().filterNot { it.id == id }) }
    }

    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock { writeLocked(emptyList()) }
    }

    private fun readLocked(): List<LessonRecord> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer, file.readText(Charsets.UTF_8))
        }.getOrDefault(emptyList())
    }

    /** 先写临时文件再改名，避免中途被杀留下半个文件。 */
    private fun writeLocked(list: List<LessonRecord>) {
        runCatching {
            val text = json.encodeToString(ListSerializer, list)
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(text, Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(text, Charsets.UTF_8)
                tmp.delete()
            }
        }
    }

    private companion object {
        const val MAX = 60
        val ListSerializer = kotlinx.serialization.builtins.ListSerializer(LessonRecord.serializer())
    }
}
