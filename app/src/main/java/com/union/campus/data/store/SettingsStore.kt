package com.union.campus.data.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "union_settings")

/**
 * 全局设置（DataStore）。所有开关/阈值/引导状态都持久化，重启保留。
 */
class SettingsStore(private val context: Context) {

    private object K {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val boundDone = booleanPreferencesKey("bind_done")
        val remindEnabled = booleanPreferencesKey("remind_enabled")
        val remindClass = booleanPreferencesKey("remind_class")
        val remindClassLead = intPreferencesKey("remind_class_lead")
        val remindExam = booleanPreferencesKey("remind_exam")
        val remindLowElectric = booleanPreferencesKey("remind_low_electric")
        val lowElectricThreshold = intPreferencesKey("low_electric_threshold")
        val remindEvaluation = booleanPreferencesKey("remind_evaluation")
        val remindShowerQueue = booleanPreferencesKey("remind_shower_queue")
        val remindGradeUpdate = booleanPreferencesKey("remind_grade_update")
        val quietEnabled = booleanPreferencesKey("quiet_enabled")
        val quietStart = intPreferencesKey("quiet_start")
        val quietEnd = intPreferencesKey("quiet_end")
        val biometricUnlock = booleanPreferencesKey("biometric_unlock")
        val autoLock = booleanPreferencesKey("auto_lock")
        val autoLockMinutes = intPreferencesKey("auto_lock_minutes")
        val screenshotBlock = booleanPreferencesKey("screenshot_block")
        val logMask = booleanPreferencesKey("log_mask")
        val currentTab = stringPreferencesKey("current_tab")
        val gpaRule = stringPreferencesKey("gpa_rule")
        val firstWeekStart = longPreferencesKey("first_week_start")
        val showQuickEntries = booleanPreferencesKey("show_quick_entries")
        val showNoticesCard = booleanPreferencesKey("show_notices_card")
        val currentSemesterDm = stringPreferencesKey("current_semester_dm")
        val gradeEffectiveMode = booleanPreferencesKey("grade_effective_mode")
        val timetableWeekMode = stringPreferencesKey("timetable_week_mode") // week/odd/all
        val timetableJLate = booleanPreferencesKey("timetable_j_late") // true=J1–J6 10:40；false=10:20
        val timetableFallbackGroup = intPreferencesKey("timetable_fallback_group") // 1=J1–J6 2=S1–S6、J7
        val lastShowerArea = stringPreferencesKey("last_shower_area")
        val lastShowerBuilding = stringPreferencesKey("last_shower_building")
        val lastShowerOrgArea = stringPreferencesKey("last_shower_org_area")
        val lastShowerOrgId = stringPreferencesKey("last_shower_org_id")
        val lastShowerBuildingId = stringPreferencesKey("last_shower_building_id")
        val lastShowerAreaId = stringPreferencesKey("last_shower_area_id")
        val lastElectricRoom = stringPreferencesKey("last_electric_room")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val themeMode = stringPreferencesKey("theme_mode") // system/light/dark
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val themeColorPreset = stringPreferencesKey("theme_color_preset") // teal/blue/...
        val timetableSemesterDm = stringPreferencesKey("timetable_semester_dm")
    }

    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[K.onboardingDone] ?: false }
    val boundDone: Flow<Boolean> = context.dataStore.data.map { it[K.boundDone] ?: false }
    val remindEnabled: Flow<Boolean> = context.dataStore.data.map { it[K.remindEnabled] ?: true }
    val remindClass: Flow<Boolean> = context.dataStore.data.map { it[K.remindClass] ?: true }
    val remindClassLead: Flow<Int> = context.dataStore.data.map { it[K.remindClassLead] ?: 10 }
    val remindExam: Flow<Boolean> = context.dataStore.data.map { it[K.remindExam] ?: true }
    val remindLowElectric: Flow<Boolean> = context.dataStore.data.map { it[K.remindLowElectric] ?: true }
    val lowElectricThreshold: Flow<Int> = context.dataStore.data.map { it[K.lowElectricThreshold] ?: 20 }
    val remindEvaluation: Flow<Boolean> = context.dataStore.data.map { it[K.remindEvaluation] ?: true }
    val remindShowerQueue: Flow<Boolean> = context.dataStore.data.map { it[K.remindShowerQueue] ?: true }
    val remindGradeUpdate: Flow<Boolean> = context.dataStore.data.map { it[K.remindGradeUpdate] ?: true }
    val quietEnabled: Flow<Boolean> = context.dataStore.data.map { it[K.quietEnabled] ?: true }
    val quietStart: Flow<Int> = context.dataStore.data.map { it[K.quietStart] ?: 23 }
    val quietEnd: Flow<Int> = context.dataStore.data.map { it[K.quietEnd] ?: 7 }
    val biometricUnlock: Flow<Boolean> = context.dataStore.data.map { it[K.biometricUnlock] ?: false }
    val autoLock: Flow<Boolean> = context.dataStore.data.map { it[K.autoLock] ?: true }
    val autoLockMinutes: Flow<Int> = context.dataStore.data.map { it[K.autoLockMinutes] ?: 5 }
    val screenshotBlock: Flow<Boolean> = context.dataStore.data.map { it[K.screenshotBlock] ?: false }
    val logMask: Flow<Boolean> = context.dataStore.data.map { it[K.logMask] ?: true }
    val currentTab: Flow<String> = context.dataStore.data.map { it[K.currentTab] ?: "" }
    val gpaRule: Flow<String> = context.dataStore.data.map { it[K.gpaRule] ?: "4.0" }
    val firstWeekStart: Flow<Long> = context.dataStore.data.map { it[K.firstWeekStart] ?: 0L }
    val showQuickEntries: Flow<Boolean> = context.dataStore.data.map { it[K.showQuickEntries] ?: true }
    val showNoticesCard: Flow<Boolean> = context.dataStore.data.map { it[K.showNoticesCard] ?: true }
    val currentSemesterDm: Flow<String> = context.dataStore.data.map { it[K.currentSemesterDm] ?: "" }
    val gradeEffectiveMode: Flow<Boolean> = context.dataStore.data.map { it[K.gradeEffectiveMode] ?: true }
    val timetableWeekMode: Flow<String> = context.dataStore.data.map { it[K.timetableWeekMode] ?: "week" }
    val timetableJLate: Flow<Boolean> = context.dataStore.data.map { it[K.timetableJLate] ?: true }
    val timetableFallbackGroup: Flow<Int> = context.dataStore.data.map { it[K.timetableFallbackGroup] ?: 1 }
    val lastShowerArea: Flow<String> = context.dataStore.data.map { it[K.lastShowerArea] ?: "" }
    val lastShowerBuilding: Flow<String> = context.dataStore.data.map { it[K.lastShowerBuilding] ?: "" }
    val lastShowerOrgArea: Flow<String> = context.dataStore.data.map { it[K.lastShowerOrgArea] ?: "" }
    val lastShowerOrgId: Flow<String> = context.dataStore.data.map { it[K.lastShowerOrgId] ?: "" }
    val lastShowerBuildingId: Flow<String> = context.dataStore.data.map { it[K.lastShowerBuildingId] ?: "" }
    val lastShowerAreaId: Flow<String> = context.dataStore.data.map { it[K.lastShowerAreaId] ?: "" }
    val lastElectricRoom: Flow<String> = context.dataStore.data.map { it[K.lastElectricRoom] ?: "" }
    val keepScreenOn: Flow<Boolean> = context.dataStore.data.map { it[K.keepScreenOn] ?: true }
    val themeMode: Flow<String> = context.dataStore.data.map { it[K.themeMode] ?: "system" }
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[K.dynamicColor] ?: false }
    val themeColorPreset: Flow<String> = context.dataStore.data.map { it[K.themeColorPreset] ?: "teal" }
    val timetableSemesterDm: Flow<String> = context.dataStore.data.map { it[K.timetableSemesterDm] ?: "" }

    suspend fun setOnboardingDone() = context.dataStore.edit { it[K.onboardingDone] = true }
    suspend fun setBoundDone(done: Boolean) = context.dataStore.edit { it[K.boundDone] = done }
    suspend fun setRemindEnabled(v: Boolean) = context.dataStore.edit { it[K.remindEnabled] = v }
    suspend fun setRemindClass(v: Boolean) = context.dataStore.edit { it[K.remindClass] = v }
    suspend fun setRemindClassLead(v: Int) = context.dataStore.edit { it[K.remindClassLead] = v }
    suspend fun setRemindExam(v: Boolean) = context.dataStore.edit { it[K.remindExam] = v }
    suspend fun setRemindLowElectric(v: Boolean) = context.dataStore.edit { it[K.remindLowElectric] = v }
    suspend fun setLowElectricThreshold(v: Int) = context.dataStore.edit { it[K.lowElectricThreshold] = v }
    suspend fun setRemindEvaluation(v: Boolean) = context.dataStore.edit { it[K.remindEvaluation] = v }
    suspend fun setRemindShowerQueue(v: Boolean) = context.dataStore.edit { it[K.remindShowerQueue] = v }
    suspend fun setRemindGradeUpdate(v: Boolean) = context.dataStore.edit { it[K.remindGradeUpdate] = v }
    suspend fun setQuietEnabled(v: Boolean) = context.dataStore.edit { it[K.quietEnabled] = v }
    suspend fun setQuietRange(start: Int, end: Int) = context.dataStore.edit {
        it[K.quietStart] = start; it[K.quietEnd] = end
    }
    suspend fun setBiometricUnlock(v: Boolean) = context.dataStore.edit { it[K.biometricUnlock] = v }
    suspend fun setAutoLock(v: Boolean) = context.dataStore.edit { it[K.autoLock] = v }
    suspend fun setAutoLockMinutes(v: Int) = context.dataStore.edit { it[K.autoLockMinutes] = v }
    suspend fun setScreenshotBlock(v: Boolean) = context.dataStore.edit { it[K.screenshotBlock] = v }
    suspend fun setLogMask(v: Boolean) = context.dataStore.edit { it[K.logMask] = v }
    suspend fun setCurrentTab(v: String) = context.dataStore.edit { it[K.currentTab] = v }
    suspend fun setGpaRule(v: String) = context.dataStore.edit { it[K.gpaRule] = v }
    suspend fun setFirstWeekStart(ms: Long) = context.dataStore.edit { it[K.firstWeekStart] = ms }
    suspend fun setShowQuickEntries(v: Boolean) = context.dataStore.edit { it[K.showQuickEntries] = v }
    suspend fun setShowNoticesCard(v: Boolean) = context.dataStore.edit { it[K.showNoticesCard] = v }
    suspend fun setCurrentSemesterDm(v: String) = context.dataStore.edit { it[K.currentSemesterDm] = v }
    suspend fun setGradeEffectiveMode(v: Boolean) = context.dataStore.edit { it[K.gradeEffectiveMode] = v }
    suspend fun setTimetableWeekMode(v: String) = context.dataStore.edit { it[K.timetableWeekMode] = v }
    suspend fun setTimetableJLate(v: Boolean) = context.dataStore.edit { it[K.timetableJLate] = v }
    suspend fun setTimetableFallbackGroup(v: Int) = context.dataStore.edit { it[K.timetableFallbackGroup] = v }
    suspend fun setLastShower(area: String, building: String, orgArea: String) = context.dataStore.edit {
        it[K.lastShowerArea] = area; it[K.lastShowerBuilding] = building; it[K.lastShowerOrgArea] = orgArea
    }

    /** 保存完整的洗浴选择：学校 / 校区 / 楼栋 / 区域。 */
    suspend fun setShowerSelection(
        orgId: String,
        orgAreaId: String,
        buildingId: String,
        buildingName: String,
        areaId: String,
        areaName: String,
    ) = context.dataStore.edit {
        it[K.lastShowerOrgId] = orgId
        it[K.lastShowerOrgArea] = orgAreaId
        it[K.lastShowerBuildingId] = buildingId
        it[K.lastShowerBuilding] = buildingName
        it[K.lastShowerAreaId] = areaId
        it[K.lastShowerArea] = areaName
    }
    suspend fun setLastElectricRoom(v: String) = context.dataStore.edit { it[K.lastElectricRoom] = v }
    suspend fun setKeepScreenOn(v: Boolean) = context.dataStore.edit { it[K.keepScreenOn] = v }
    suspend fun setThemeMode(v: String) = context.dataStore.edit {
        it[K.themeMode] = if (v in setOf("system", "light", "dark")) v else "system"
    }
    suspend fun setDynamicColor(v: Boolean) = context.dataStore.edit { it[K.dynamicColor] = v }
    suspend fun setThemeColorPreset(v: String) = context.dataStore.edit {
        it[K.themeColorPreset] = v.ifBlank { "teal" }
    }
    suspend fun setTimetableSemesterDm(v: String) = context.dataStore.edit { it[K.timetableSemesterDm] = v }

    /** 清除全部设置（含引导与绑定标记），用于“清除全部数据”。 */
    suspend fun clearAll() = context.dataStore.edit { it.clear() }

    /** 一次性读取快照（引导流程用）。 */
    suspend fun snapshot(): Snapshot = Snapshot(
        onboardingDone = onboardingDone.first(),
        boundDone = boundDone.first(),
        biometricUnlock = biometricUnlock.first(),
        remindEnabled = remindEnabled.first(),
    )

    data class Snapshot(
        val onboardingDone: Boolean,
        val boundDone: Boolean,
        val biometricUnlock: Boolean,
        val remindEnabled: Boolean,
    )
}
