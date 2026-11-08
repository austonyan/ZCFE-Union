package com.union.campus

import com.union.campus.data.remote.electric.swaeInnerBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 完美校园网关响应回归测试。
 *
 * 外层是 `{"code_":0,"body":"{\"result\":\"0\",...}"}`，body 为转义后的 JSON 字符串。
 * 旧实现用 `toString().removeSurrounding("\"")`，内层引号仍是 `\"`，导致
 * “园区/楼栋/楼层/房间”全部加载失败并提示“数据源返回异常”。
 */
class ElectricParsingTest {

    @Test
    fun unwrapsEscapedGatewayBody() {
        val outer = Json.parseToJsonElement(
            """{"code_":0,"sign":"X","result_":"true","body":"{\"result\":\"0\",\"ver\":3,\"data\":[{\"id\":101,\"name\":\"航空港区\"}],\"message\":\"操作成功\"}"}""",
        ).jsonObject

        val inner = swaeInnerBody(outer)

        assertEquals("0", (inner["result"] as? JsonPrimitive)?.content)
        assertEquals("101", inner["data"]!!.jsonArray[0].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("航空港区", inner["data"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun unwrapsNestedListBody() {
        val outer = Json.parseToJsonElement(
            """{"code_":0,"body":"{\"result\":\"0\",\"data\":[{\"id\":39,\"name\":\"学生公寓D09\"}]}"}""",
        ).jsonObject

        val list = swaeInnerBody(outer)["data"]!!.jsonArray
        assertEquals("学生公寓D09", list[0].jsonObject["name"]!!.jsonPrimitive.content)
    }
}