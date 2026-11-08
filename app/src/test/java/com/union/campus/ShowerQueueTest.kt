package com.union.campus

import com.union.campus.data.repo.parseMyQueue
import com.union.campus.data.repo.parseQueueCounts
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}