package com.union.campus

import com.union.campus.data.remote.jw.normalizeLooseJson
import com.union.campus.domain.Semester
import com.union.campus.domain.SemesterCalc
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 教务响应兼容性回归。
 *
 * `MyXjda` 实际返回 JavaScript 对象字面量（单引号 + dminfo 未加引号），
 * 直接 json.parseToJsonElement 会报 “Unexpected JSON token at offset 1”。
 */
class JwLooseJsonTest {

    @Test
    fun parsesSingleQuotedStudentRecord() {
        val raw = "{'resultSet':[{'yhxh':'202400000001','xm':'张三','xz':'4',dminfo:{'yxbdm':'02','zydm':'022300'},'ssbj':'24软工本1班'}]}"

        val obj = Json.parseToJsonElement(normalizeLooseJson(raw)).jsonObject
        val first = obj["resultSet"]!!.jsonArray[0].jsonObject
        assertEquals("202400000001", first["yhxh"]!!.jsonPrimitive.content)
        assertEquals("张三", first["xm"]!!.jsonPrimitive.content)
        assertEquals("02", first["dminfo"]!!.jsonObject["yxbdm"]!!.jsonPrimitive.content)
        assertEquals("24软工本1班", first["ssbj"]!!.jsonPrimitive.content)
    }

    @Test
    fun normalizesEmptyResultSet() {
        val obj = Json.parseToJsonElement(normalizeLooseJson("{'resultSet':[ ]}")).jsonObject
        assertTrue(obj["resultSet"]!!.jsonArray.isEmpty())
    }

    @Test
    fun standardJsonIsUnchanged() {
        val raw = "{\"xscj\":[{\"kcmc\":\"软件工程\"}]}"
        assertEquals(raw, normalizeLooseJson(raw))
    }

    @Test
    fun fallSemesterUsesYearPlusZero() {
        assertEquals("20260", SemesterCalc.guessSemesterDm(date(2026, Calendar.NOVEMBER, 8)))
        assertEquals("2026 秋", Semester("20260", "2026-2027学年第一学期").shortLabel)
    }

    @Test
    fun springSemesterUsesPreviousYearPlusOne() {
        assertEquals("20261", SemesterCalc.guessSemesterDm(date(2027, Calendar.MARCH, 1)))
        assertEquals("2027 春", Semester("20261", "2026-2027学年第二学期").shortLabel)
    }

    private fun date(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(year, month, day, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}