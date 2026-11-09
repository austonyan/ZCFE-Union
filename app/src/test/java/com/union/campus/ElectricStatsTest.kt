package com.union.campus

import com.union.campus.domain.ElectricBalance
import com.union.campus.domain.ElectricStats
import com.union.campus.domain.ElectricUsageDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 电费统计：电价解析、用量汇总与费用估算。 */
class ElectricStatsTest {

    private fun day(date: String, use: String) = ElectricUsageDay(date, use)

    private fun balance(kwh: String, price: String) = ElectricBalance(
        roomVerify = "room",
        fullName = "11#宿舍 101",
        kwh = kwh,
        price = price,
        todayUse = "2.5",
        fetchedAt = 0L,
    )

    @Test
    fun unitPriceParsesOnlyPositiveValues() {
        assertEquals(0.56, ElectricStats.parseUnitPrice("0.56")!!, 0.0001)
        assertEquals(0.56, ElectricStats.parseUnitPrice(" 0.56 ")!!, 0.0001)
        assertNull(ElectricStats.parseUnitPrice(""))
        assertNull(ElectricStats.parseUnitPrice(null))
        assertNull(ElectricStats.parseUnitPrice("0"))
        assertNull(ElectricStats.parseUnitPrice("abc"))
    }

    @Test
    fun totalAndAverageUseIgnoreInvalidEntries() {
        val days = listOf(day("2026-11-01", "2"), day("2026-11-02", "4"), day("2026-11-03", "6"))
        assertEquals(12.0, ElectricStats.totalUse(days), 0.0001)
        assertEquals(4.0, ElectricStats.dailyAverage(days), 0.0001)
        assertEquals(0.0, ElectricStats.totalUse(emptyList()), 0.0001)
    }

    @Test
    fun costIsNullWhenPriceIsMissing() {
        val days = listOf(day("2026-11-01", "4"))
        assertEquals(2.24, ElectricStats.totalCost(days, 0.56)!!, 0.0001)
        assertNull(ElectricStats.totalCost(days, null))
        assertEquals(1.4, ElectricStats.cost(2.5, 0.56)!!, 0.0001)
    }

    @Test
    fun monthlyProjectionUsesDailyAverage() {
        val days = listOf(day("2026-11-01", "2"), day("2026-11-02", "4"), day("2026-11-03", "6"))
        // 日均 4 度 × 0.56 元 = 2.24 元/天，30 天 = 67.2 元
        assertEquals(67.2, ElectricStats.projectedMonthlyCost(days, 0.56)!!, 0.0001)
        assertNull(ElectricStats.projectedMonthlyCost(days, null))
    }

    @Test
    fun remainingValueUsesFetchedPrice() {
        assertEquals(56.0, ElectricStats.remainingValue(balance("100", "0.56"))!!, 0.0001)
        assertNull(ElectricStats.remainingValue(balance("100", "")))
    }

    @Test
    fun formattingUsesTwoDecimalsForMoney() {
        assertEquals("5.60", ElectricStats.formatMoney(5.6))
        assertEquals("5.6", ElectricStats.formatKwh(5.6))
    }
}