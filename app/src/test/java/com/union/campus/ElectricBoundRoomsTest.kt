package com.union.campus

import com.union.campus.data.repo.parseBoundRooms
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 完美校园已绑定房间解析回归测试。
 *
 * 单房间时网关返回 existflag=1，房间字段在顶层；旧实现只认 existflag=2 的 roomlist，
 * 导致“已经绑定房间的账号”在绑定页看不到房间，重复绑定同一房间还会报错。
 */
class ElectricBoundRoomsTest {

    @Test
    fun parsesSingleBoundRoom() {
        val body = Json.parseToJsonElement(
            """{"result":"0","account_num":"1022211211330","ver":2,"roomfullname":"惠济校区_11#宿舍_3层_11330房间","isshowsubsidy":"0","existflag":"1","detaillist":[{"use":"891.1","odd":"243.51","businesstype":"0","status":"1"}],"message":"操作成功","roomverify":"102-22--112-11330"}""",
        ).jsonObject

        val rooms = parseBoundRooms(body)

        assertEquals(1, rooms.size)
        assertEquals("102-22--112-11330", rooms[0].roomVerify)
        assertEquals("惠济校区_11#宿舍_3层_11330房间", rooms[0].fullName)
    }

    @Test
    fun parsesMultipleBoundRooms() {
        val body = Json.parseToJsonElement(
            """{"result":"0","existflag":"2","roomlist":[{"roomverify":"102-22--112-11330","roomfullname":"惠济校区_11#宿舍_3层_11330房间"},{"roomverify":"102-22--112-11331","roomfullname":"惠济校区_11#宿舍_3层_11331房间"}]}""",
        ).jsonObject

        val rooms = parseBoundRooms(body)

        assertEquals(2, rooms.size)
        assertTrue(rooms.any { it.roomVerify.endsWith("11331") })
    }

    @Test
    fun noBoundRoom() {
        val body = Json.parseToJsonElement(
            """{"result":"0","ver":2,"isshowsubsidy":"0","existflag":"0","message":"操作成功"}""",
        ).jsonObject

        assertTrue(parseBoundRooms(body).isEmpty())
    }
}