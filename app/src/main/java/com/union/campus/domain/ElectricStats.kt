package com.union.campus.domain

import java.util.Locale

/** 电费统计的纯计算逻辑：用量、单价与费用估算。 */
object ElectricStats {

    /** 从接口返回的字符串里取正数电价；空、0、非法值都视为未返回。 */
    fun parseUnitPrice(text: String?): Double? =
        text?.trim()?.replace(",", "")?.toDoubleOrNull()?.takeIf { it > 0.0 }

    fun unitPrice(balance: ElectricBalance?): Double? = parseUnitPrice(balance?.price)

    /** 用量字符串转数值，兼容空串和异常值。 */
    fun useValue(text: String?): Double =
        text?.trim()?.replace(",", "")?.toDoubleOrNull() ?: 0.0

    fun totalUse(days: List<ElectricUsageDay>): Double =
        days.sumOf { useValue(it.use) }

    fun dailyAverage(days: List<ElectricUsageDay>): Double =
        if (days.isEmpty()) 0.0 else totalUse(days) / days.size

    /** 度数 × 电价；电价未知时返回 null，避免把缺失数据当成 0 元。 */
    fun cost(kwh: Double, price: Double?): Double? = price?.let { kwh * it }

    fun totalCost(days: List<ElectricUsageDay>, price: Double?): Double? =
        cost(totalUse(days), price)

    fun averageCost(days: List<ElectricUsageDay>, price: Double?): Double? =
        cost(dailyAverage(days), price)

    /** 按日均费用推算 30 天费用。 */
    fun projectedMonthlyCost(days: List<ElectricUsageDay>, price: Double?, monthDays: Int = 30): Double? =
        averageCost(days, price)?.times(monthDays)

    /** 剩余电量按当前单价折算的价值。 */
    fun remainingValue(balance: ElectricBalance?): Double? {
        val kwh = useValue(balance?.kwh)
        return cost(kwh, unitPrice(balance))
    }

    fun formatMoney(value: Double): String = String.format(Locale.CHINA, "%.2f", value)

    fun formatKwh(value: Double): String = String.format(Locale.CHINA, "%.1f", value)
}