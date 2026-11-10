package com.union.campus

import com.union.campus.data.repo.formatShowerEpoch
import com.union.campus.data.repo.parseShowerOrdersIfLast
import com.union.campus.data.repo.parseShowerOrdersPage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 消费明细：分页、ifLast、start_time=0 兜底与东八区格式化。 */
class ShowerOrderParseTest {

    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun parsesConsumptionListAndKeepsEveryItem() {
        val page = parseShowerOrdersPage(
            obj(
                """
                {
                  "consumptionList": [
                    {"order_sn": "a", "device_name": "01", "total_used_time": "1439", "cash_amount": "1.20", "start_time": "1794229500"},
                    {"order_sn": "b", "device_name": "02", "total_used_time": "60", "cash_amount": "0.50", "start_time": "1792683000"}
                  ],
                  "ifLast": 0,
                  "totalAmount": "1.70"
                }
                """.trimIndent(),
            ),
        )
        assertEquals(2, page.items.size)
        assertEquals("a", page.items[0].orderSn)
        assertEquals("2026-11-09 21:05", page.items[0].time)
        assertEquals("23分59秒", page.items[0].durationText)
        assertFalse(page.isLast!!)
    }

    @Test
    fun fallsBackToCreateTimeWhenStartTimeIsZero() {
        val page = parseShowerOrdersPage(
            obj(
                """
                {
                  "consumptionList": [
                    {"order_sn": "c", "start_time": 0, "create_time": "1794229500"}
                  ],
                  "ifLast": 1
                }
                """.trimIndent(),
            ),
        )
        assertEquals("2026-11-09 21:05", page.items.single().time)
        assertTrue(page.isLast!!)
    }

    @Test
    fun epochIsFormattedInChinaStandardTime() {
        assertEquals("2026-10-22 23:30", formatShowerEpoch("1792683000"))
        assertEquals("2026-10-22 23:30", formatShowerEpoch("1792683000000"))
        assertEquals("", formatShowerEpoch("0"))
    }

    @Test
    fun recognizesIfLastVariantsAndMissingValue() {
        assertEquals(false, parseShowerOrdersIfLast(buildJsonObject { put("ifLast", 0) }))
        assertEquals(true, parseShowerOrdersIfLast(buildJsonObject { put("ifLast", "1") }))
        assertEquals(true, parseShowerOrdersIfLast(buildJsonObject { put("ifLast", true) }))
        assertEquals(false, parseShowerOrdersIfLast(buildJsonObject { put("if_last", false) }))
        assertNull(parseShowerOrdersIfLast(buildJsonObject { }))
        assertNull(parseShowerOrdersIfLast(null))
    }
}