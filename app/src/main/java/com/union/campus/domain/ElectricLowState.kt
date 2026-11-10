package com.union.campus.domain

/**
 * 低余额判定：生活页、今天页、电费页与提醒 Worker 共用同一规则。
 * 阈值来自 SettingsStore.lowElectricThreshold（默认 20 度）。
 */
object ElectricLowState {
    const val DefaultThreshold = 20

    fun isLow(balance: ElectricBalance?, threshold: Int): Boolean {
        val kwh = balance?.kwh?.toDoubleOrNull() ?: return false
        return kwh < threshold
    }

    fun thresholdText(threshold: Int): String = "低于 $threshold 度时每天 20:00 提醒"
}