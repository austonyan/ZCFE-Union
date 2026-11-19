package com.union.campus

import com.union.campus.data.remote.jw.normalizeLooseJson
import com.union.campus.data.repo.parseFreeClassrooms
import com.union.campus.data.repo.parseStudentRecordData
import com.union.campus.data.repo.parseTeachingBuildings
import com.union.campus.data.repo.parseTimetableData
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 教务真实响应结构解析回归（20260 学期实测字段）。 */
class JwParsingTest {

    @Test
    fun parsesTimetableWithServerWeek() {
        val raw = """
            {"jcflag":"0","xn":"2026","xq":"0","maxzc":"16","zc":"10","qssj":"2026-11-02","jssj":"2026-11-08",
             "week1":[
               {"kcdm":"XGB11207","kcmc":"软件工程","rkjs":"贾蒙蒙","skdd":"知会楼(S1)[S1-301服务器管理实训室]","jcxx":"5-6","skzs":"1-5,7-16周"},
               {"kcdm":"XGB12012","kcmc":"Linux系统及应用","rkjs":"贺澜璞","skdd":"知会楼(S1)[S1-203云计算实训室]","jcxx":"1-2","skzs":"1-16周"}
             ],
             "week2":[],"week3":[],"week4":[],"week5":[],"week6":[],"week7":[]}
        """.trimIndent()

        val data = parseTimetableData(Json.parseToJsonElement(raw), "20260", 0, "2026-2027学年第一学期")

        assertEquals(10, data.week)
        assertEquals(16, data.maxWeek)
        assertEquals("2026-11-02", data.weekStart)
        val monday = data.days.first { it.day == 1 }.cells
        assertEquals(2, monday.size)
        assertEquals("Linux系统及应用", monday[0].name)
        assertEquals("贺澜璞", monday[0].teacher)
        assertEquals(1, monday[0].startSection)
        assertEquals(2, monday[0].endSection)
        assertEquals("软件工程", monday[1].name)
        assertEquals(5, monday[1].startSection)
        assertEquals("知会楼(S1)[S1-301服务器管理实训室]", monday[1].location)
    }

    @Test
    fun parsesStudentRecordWithLoginIdShape() {
        val raw = "{'resultSet':[{'yhxh':'202400000001','xm':'张三','yx':'[10402]信息工程学院','zy':'[022300]软件工程','ssbj':'24软工本1班','rxnj':'2024','zxzt':'在校','sfzjh':'****','xz':'4',dminfo:{'yxbdm':'02'}}]}"

        val record = parseStudentRecordData(Json.parseToJsonElement(normalizeLooseJson(raw)), "fallback")

        assertEquals("张三", record.name)
        assertEquals("202400000001", record.studentNo)
        assertEquals("信息工程学院", record.college)
        assertEquals("软件工程", record.major)
        assertEquals("24软工本1班", record.className)
        assertEquals("2024", record.grade)
        assertEquals("在校", record.status)
        assertEquals("02", record.raw["dminfo.yxbdm"])
    }

    @Test
    fun parsesFreeClassroomsNestedResult() {
        val raw = """{"result":{"zdjc":"10","jsxx":[{"jsmc":"S2-101丝网印刷陶艺实训室","jsrl":"100","jslxmc":"丝网印刷实训室","sfkx":"1,1,0,1"}]}}"""

        val rooms = parseFreeClassrooms(Json.parseToJsonElement(raw))

        assertEquals(1, rooms.size)
        assertEquals("S2-101丝网印刷陶艺实训室", rooms[0].name)
        assertEquals("丝网印刷实训室", rooms[0].type)
        assertEquals("100", rooms[0].capacity)
        assertEquals(3, rooms[0].freeCount)
        assertEquals(4, rooms[0].sectionCount)
        // sfkx 定长到 zdjc：1,1,0,1 后面补 6 个未知
        assertEquals(10, rooms[0].sections.size)
        assertEquals(listOf(true, true, false, true), rooms[0].sections.take(4))
        assertTrue(rooms[0].sections.drop(4).all { it == null })
        assertEquals(listOf(1, 2, 4), rooms[0].freeSectionNumbers)
    }

    @Test
    fun parsesFreeClassroomSectionsWithZdjcAlignment() {
        val raw = """{"result":{"zdjc":"6","jsxx":[{"jsmc":"A-101","jsrl":"40","jslxmc":"普通教室","sfkx":"1,0,,1,0,1,1"}]}}"""

        val room = parseFreeClassrooms(Json.parseToJsonElement(raw)).single()

        assertEquals(6, room.maxSection)
        // 长了截断：第 7 个标记不进入结果
        assertEquals(listOf(true, false, null, true, false, true), room.sections)
        assertEquals(listOf(1, 4, 6), room.freeSectionNumbers)
    }

    @Test
    fun padsShortFreeClassroomSectionsWithUnknown() {
        val raw = """{"result":{"zdjc":"10","jsxx":[{"jsmc":"A-101","sfkx":"1"}]}}"""

        val room = parseFreeClassrooms(Json.parseToJsonElement(raw)).single()

        assertEquals(10, room.sections.size)
        assertEquals(true, room.sections.first())
        assertTrue(room.sections.drop(1).all { it == null })
    }

    @Test
    fun parsesTeachingBuildingsWithCode() {
        val raw = """{"resultSet":[{"dm":"11","mc":"躬行楼(S2)","xqdm":"2","xqmc":"惠济校区"}]}"""

        val list = parseTeachingBuildings(Json.parseToJsonElement(raw))

        assertEquals(1, list.size)
        assertEquals("11", list[0].dm)
        assertEquals("躬行楼(S2)", list[0].name)
        assertEquals("惠济校区", list[0].campus)
    }
}