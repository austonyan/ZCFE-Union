package com.union.campus.data.repo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 上游字段名不稳定的宽松解析工具。 */
object Parse {

    /** 依次尝试多个候选键，取第一个非空字符串。 */
    fun str(obj: JsonObject, vararg keys: String): String {
        for (k in keys) {
            val v = obj[k]
            val s = (v as? JsonPrimitive)?.content ?: v?.toString()?.removeSurrounding("\"")
            if (!s.isNullOrBlank() && s != "null") return s
        }
        return ""
    }

    fun int(obj: JsonObject, vararg keys: String): Int =
        str(obj, *keys).toIntOrNull() ?: 0

    fun double(obj: JsonObject, vararg keys: String): Double =
        str(obj, *keys).toDoubleOrNull() ?: 0.0

    /** 递归找第一个数组（不同接口包裹层级不一）。 */
    fun firstArray(element: JsonElement?): JsonArray? = when (element) {
        is JsonArray -> element
        is JsonObject -> element.values.firstNotNullOfOrNull { firstArray(it) }
        else -> null
    }

    /** 直接取某键的数组，若无则递归找。 */
    fun array(obj: JsonElement, vararg keys: String): JsonArray? {
        val json = obj as? JsonObject ?: return firstArray(obj)
        for (k in keys) {
            val arr = json[k] as? JsonArray
            if (arr != null) return arr
        }
        return firstArray(json)
    }

    fun arrayElementAt(arr: JsonArray, i: Int): JsonObject =
        (arr[i] as? JsonObject) ?: JsonObject(emptyMap())

    /** 数组元素转 JsonObject（跳过原始值）。 */
    fun objects(arr: JsonArray?): List<JsonObject> =
        arr?.mapNotNull { it as? JsonObject } ?: emptyList()
}
