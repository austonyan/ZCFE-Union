package com.union.campus

import com.union.campus.data.remote.electric.swaeInnerBody
import com.union.campus.data.repo.selectElectricMod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun selectsElectricModuleDespiteMisspelledType() {
        // 实测 h5_getstuindexpage 中字段是 bussnesstype（少一个 i），
        // 旧代码只认 businesstype，导致 modlist 被过滤空、整页显示“暂无电量数据”。
        val body = Json.parseToJsonElement(
            """{"result":"0","modlist":[{"bussnesstype":0,"odd":239.51,"price":"0.56","devicename":"DDH1351多用户电表","weekuselist":[{"date":"11-07","use":4}],"monthuselist":[{"yearmonth":"2026.11","monthuse":49.6}]}]}""",
        ).jsonObject

        val mod = selectElectricMod(body)

        assertNotNull(mod)
        assertEquals("239.51", (mod!!["odd"] as? JsonPrimitive)?.content)
        assertEquals("0.56", (mod["price"] as? JsonPrimitive)?.content)
        assertEquals("49.6", (mod["monthuselist"]!!.jsonArray[0].jsonObject["monthuse"] as? JsonPrimitive)?.content)
    }
}