package com.union.campus.data.store

import android.content.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 实体缓存：JSON 文件落盘（filesDir/cache_data/），带 updatedAt 元数据。
 * 课表/成绩/考试/通知/洗浴区域/电费余额等读缓存统一走这里。
 */
class EntityStore(private val context: Context) {

    private val dir: File by lazy {
        File(context.filesDir, "cache_data").apply { mkdirs() }
    }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    /** 缓存条目：payload + 更新时间。 */
    data class Entry<T>(val data: T, val updatedAt: Long)

    fun <T> read(key: String, serializer: KSerializer<T>): Entry<T>? = try {
        val file = fileFor(key)
        if (!file.exists()) return null
        val text = file.readText()
        val obj = json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject ?: return null
        val updatedAt = obj["updatedAt"]?.toString()?.removeSurrounding("\"")?.toLongOrNull() ?: 0L
        val payload = obj["payload"]?.toString()
            ?: return null
        Entry(json.decodeFromString(serializer, payload), updatedAt)
    } catch (_: Exception) {
        null
    }

    fun <T> write(key: String, serializer: KSerializer<T>, data: T) {
        try {
            val payload = json.encodeToString(serializer, data)
            // 手动拼包装避免类型擦除问题
            val wrapped = """{"updatedAt":"${System.currentTimeMillis()}","payload":$payload}"""
            fileFor(key).writeText(wrapped)
        } catch (_: Exception) {
            // 缓存失败不影响主流程
        }
    }

    fun remove(key: String) {
        fileFor(key).delete()
    }

    fun removeAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** 按模块前缀清理：key 形如 "jw.timetable.202620261"。 */
    fun removeByPrefix(prefix: String) {
        dir.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach { it.delete() }
    }

    fun totalSizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun fileFor(key: String) = File(dir, key.replace('/', '_').replace(':', '_') + ".json")
}

/** 缓存 TTL（毫秒），按 power.md §4.5.5。 */
object CacheTtl {
    const val TIMETABLE = 24L * 3600_000
    const val GRADES = 7L * 24 * 3600_000
    const val EXAMS = 24L * 3600_000
    const val CALENDAR = 30L * 24 * 3600_000
    const val ELECTRIC_BALANCE = 10L * 60_000
    const val ELECTRIC_USAGE = 3600_000L
    const val SHOWER_STATE = 30_000L
    const val NOTICES = 3600_000L
}
