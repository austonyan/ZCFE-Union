package com.union.campus

import com.union.campus.data.repo.chooseBuildingAreas
import com.union.campus.data.repo.isShowerQueueTurn
import com.union.campus.data.repo.parseMyQueue
import com.union.campus.data.repo.parseQueueCounts
import com.union.campus.domain.ShowerArea
import com.union.campus.domain.ShowerQueue
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 洗浴：区域队列与我的排队响应解析。 */
class ShowerQueueTest {

    @Test
    fun parsesQueueCountArray() {
        val raw = """[{"device_area_id":"875","queue_num":"3"},{"device_area_id":"876","queue_num":"0"}]"""
        val counts = parseQueueCounts(Json.parseToJsonElement(raw))
        assertEquals(3, counts["875"])
        assertEquals(0, counts["876"])
    }

    @Test
    fun parsesQueueCountMap() {
        val raw = """{"875":"2","876":"1"}"""
        val counts = parseQueueCounts(Json.parseToJsonElement(raw))
        assertEquals(2, counts["875"])
        assertEquals(1, counts["876"])
    }

    @Test
    fun emptyQueueInfoMeansNotQueued() {
        assertNull(parseMyQueue(Json.parseToJsonElement("\"\"")))
        assertNull(parseMyQueue(null))
    }

    @Test
    fun parsesMyQueueObject() {
        val raw = """{"device_area_id":"875","device_area_name":"3层男浴室","queue_position":"4","queue_num":"5","wait_time":"8"}"""
        val queue = parseMyQueue(Json.parseToJsonElement(raw))
        assertEquals("875", queue?.areaId)
        assertEquals("3层男浴室", queue?.areaName)
        assertEquals(4, queue?.position)
        assertEquals(5, queue?.queueNum)
        assertEquals(8, queue?.waitMinutes)
    }

    @Test
    fun parsesMyQueueJsonString() {
        val raw = Json.parseToJsonElement("\"{\\\"device_area_id\\\":\\\"875\\\",\\\"queue_position\\\":\\\"2\\\"}\"")
        val queue = parseMyQueue(raw)
        assertEquals("875", queue?.areaId)
        assertEquals(2, queue?.position)
    }

    @Test
    fun areaSelectionFallsBackWhenDefinitionsDiffer() {
        val states = listOf(
            ShowerArea("875", "3层男浴室", 2, 5, 0, buildingId = "1"),
            ShowerArea("876", "4层男浴室", 1, 5, 0, buildingId = "1"),
            ShowerArea("900", "1层女浴室", 0, 5, 0, buildingId = "2"),
        )
        // getDeviceArea 返回的 ID 和实时区域完全对不上时，不能返回空
        val defs = listOf(ShowerArea("x", "未知", 0, 0, 0, buildingId = "1"))
        val result = chooseBuildingAreas(states, defs, "1")
        assertEquals(listOf("875", "876"), result.map { it.id })
    }

    @Test
    fun areaSelectionDoesNotDropAreasWithoutBuildingId() {
        val states = listOf(
            ShowerArea("875", "3层男浴室", 2, 5, 0),
            ShowerArea("876", "4层男浴室", 1, 5, 0),
        )
        val result = chooseBuildingAreas(states, emptyList(), "1")
        assertEquals(2, result.size)
    }

    @Test
    fun queueTurnRequiresIdleDeviceAndFrontOfLine() {
        val front = ShowerQueue("875", "3层男浴室", position = 1, queueNum = 2, waitMinutes = 4)
        val waiting = ShowerQueue("875", "3层男浴室", position = 3, queueNum = 4, waitMinutes = 8)
        assertTrue(isShowerQueueTurn(front, idleCount = 1))
        assertFalse("没有空闲设备时不能预约", isShowerQueueTurn(front, idleCount = 0))
        assertFalse("未排到队首时不能越过前面的人", isShowerQueueTurn(waiting, idleCount = 2))
        assertFalse(isShowerQueueTurn(null, idleCount = 3))
    }

    @Test
    fun zeroWaitMeansQueueTurnEvenWithoutPosition() {
        val called = ShowerQueue("875", "3层男浴室", position = 0, queueNum = 1, waitMinutes = 0)
        assertTrue(isShowerQueueTurn(called, idleCount = 1))
    }

    @Test
    fun zeroWaitDoesNotOverrideAnExplicitBackPosition() {
        val third = ShowerQueue("875", "3层男浴室", position = 3, queueNum = 4, waitMinutes = 0)
        assertFalse(isShowerQueueTurn(third, idleCount = 2))
    }
}