package com.union.campus.domain

import kotlinx.serialization.Serializable

// ---------------- 教务 ----------------

@Serializable
data class Semester(val dm: String, val name: String, val isCurrent: Boolean = false) {
    val year: String get() = dm.take(4)
    val term: String get() = dm.drop(4)
    val shortLabel: String
        get() {
            val y = year.toIntOrNull() ?: 0
            return when (term) {
                "0" -> "$year 秋"
                "1" -> "${y + 1} 春"
                else -> "${y + 1} 夏"
            }
        }
}

@Serializable
data class SectionTime(val section: Int, val start: String, val end: String)

@Serializable
data class CourseCell(
    val id: String,
    val name: String,
    val teacher: String,
    val location: String,
    val day: Int, // 1..7
    val sections: String, // jcxx 原文，如 "3-4节"
    val startSection: Int,
    val endSection: Int,
    val weeks: String, // skzs 原文
    val note: String = "",
)

/** 某周某日的课程列表 */
@Serializable
data class DayCourses(val day: Int, val cells: List<CourseCell>)

@Serializable
data class TimetableData(
    val semesterDm: String,
    val week: Int,
    val days: List<DayCourses>,
    val fetchedAt: Long,
    val maxWeek: Int = 25,
    val weekStart: String = "",
    val semesterName: String = "",
)

@Serializable
data class Grade(
    val courseCode: String,
    val courseName: String,
    val credits: String,
    val score: String,
    val gpa: String,
    val category: String, // kclb
    val raw: Map<String, String> = emptyMap(),
)

@Serializable
data class GradesData(
    val semesterDm: String,
    val isEffective: Boolean,
    val grades: List<Grade>,
    val fetchedAt: Long,
)

@Serializable
data class Exam(
    val id: String,
    val courseName: String,
    val time: String, // kssj 原文
    val location: String,
    val seatNo: String,
    val note: String = "",
)

@Serializable
data class ExamsData(val semesterDm: String, val exams: List<Exam>, val fetchedAt: Long)

@Serializable
data class EvaluationItem(
    val xnxq: String,
    val evaluationRoundCode: String,
    val courseCode: String,
    val teacherCode: String,
    val evaluationTypeCode: String,
    val evaluationStatusCode: String,
    val courseStatusCode: String,
    val courseName: String,
    val teacher: String,
    val credits: String,
    val evaluationType: String,
    val semesterName: String,
    val roundName: String,
    val startsAt: String,
    val endsAt: String,
    val roundIndex: Int = 0,
    val courseIndex: Int = 0,
    val maxCount: String = "",
    val canEvaluate: String = "",
    val completedCourseCount: String = "",
    val rawCourseInfo: Map<String, String> = emptyMap(),
    val rawRoundInfo: Map<String, String> = emptyMap(),
) {
    val roundKey: String
        get() = listOf(xnxq, evaluationRoundCode, semesterName, roundName, startsAt, endsAt, roundIndex).joinToString("|")

    val courseKey: String
        get() = listOf(roundKey, courseCode, teacherCode, evaluationTypeCode, evaluationStatusCode, courseStatusCode, courseIndex).joinToString("|")

    val isPending: Boolean
        get() = courseStatusCode == "0"
}

@Serializable
data class EvaluationForm(
    val statusCode: String,
    val formats: List<EvaluationFormat>,
)

@Serializable
data class EvaluationFormat(
    val code: String,
    val title: String,
    val categories: List<EvaluationCategory>,
)

@Serializable
data class EvaluationCategory(
    val code: String,
    val title: String,
    val questions: List<EvaluationQuestion>,
)

@Serializable
data class EvaluationQuestion(
    val key: String,
    val code: String,
    val title: String,
    val typeCode: String,
    val options: List<EvaluationOption> = emptyList(),
    val initialAnswers: List<String> = emptyList(),
    val minimum: String = "",
    val maximum: String = "",
)

@Serializable
data class EvaluationOption(val code: String, val label: String)

@Serializable
data class TextbookItem(
    val id: String,
    val courseName: String,
    val bookName: String,
    val count: String,
    val price: String, // 空串=待结算
    val confirmed: Boolean,
)

@Serializable
data class StudentRecord(
    val name: String,
    val studentNo: String,
    val college: String,
    val major: String,
    val className: String,
    val grade: String,
    val status: String,
    val idCardMasked: String,
    val raw: Map<String, String> = emptyMap(),
)

/** 教学楼列表：dm 是接口需要的楼栋代码，name 用于展示。 */
@Serializable
data class TeachingBuilding(
    val dm: String,
    val name: String,
    val campus: String = "",
)

/**
 * 空闲教室（getJsxx）：freeSections 为各节次是否空闲的标记串（旧缓存兼容）。
 * sections 与 zdjc 对齐：true 空闲 / false 占用 / null 未知。
 */
@Serializable
data class FreeClassroom(
    val name: String,
    val type: String = "",
    val capacity: String = "",
    val freeSections: String = "",
    val sections: List<Boolean?> = emptyList(),
    val maxSection: Int = 10,
) {
    val freeCount: Int get() = freeSections.split(',').count { it.trim() == "1" }
    val sectionCount: Int get() = freeSections.split(',').count { it.isNotBlank() }

    /** 由 sections 计算的可空闲节次（1 基）；旧缓存没有 sections 时回落到 freeSections。 */
    val freeSectionNumbers: List<Int>
        get() = if (sections.isNotEmpty()) {
            sections.mapIndexedNotNull { index, free -> (index + 1).takeIf { free == true } }
        } else {
            freeSections.split(',').mapIndexedNotNull { index, flag ->
                (index + 1).takeIf { flag.trim() == "1" }
            }
        }
}

@Serializable
data class JwNotice(
    val id: String,
    val title: String,
    val date: String,
    val source: String,
    val kind: String, // news / change(调课) / general
    val body: String = "",
)

// ---------------- 洗浴 ----------------

@Serializable
data class ShowerOrg(val id: String, val name: String)

@Serializable
data class ShowerBuilding(
    val id: String,
    val name: String,
    val orgAreaId: String = "",
)

@Serializable
data class ShowerArea(
    val id: String,
    val name: String,
    val idleNum: Int,
    val totalNum: Int,
    val queueNum: Int,
    val serviceTime: String = "",
    val maintenance: String = "",
    val buildingId: String = "",
    val floor: String = "",
    val genderLimit: String = "",
)

/** 我的排队信息；未排队时为空。 */
@Serializable
data class ShowerQueue(
    val areaId: String,
    val areaName: String,
    val position: Int,
    val queueNum: Int,
    val waitMinutes: Int,
)

@Serializable
data class ShowerDevice(
    val uuid: String,
    val name: String, // 设备号
    val state: Int, // 0 空闲 / 1 使用中 / 2 已预约 / 3 故障
    val remainingText: String = "", // 使用中剩余/预约时间
    // 上游 need_reserve=1：先 DeviceApi/reserve 预约，再 DeviceApi/turnOn，否则返回 code 130
    val needReserve: Boolean = false,
)

@Serializable
data class ShowerWallet(
    val balance: String,
    val availableBalance: String,
    val deposit: String,
    val giftBalance: String = "",
)

@Serializable
data class ShowerOrder(
    val orderSn: String,
    val deviceName: String,
    val durationText: String,
    val amount: String,
    val time: String,
)

@Serializable
data class ShowerSessionState(
    val deviceUuid: String,
    val deviceName: String,
    val areaName: String,
    val buildingName: String,
    val startedAt: Long,
    val orderSn: String = "",
)

/**
 * 当前预约（不落盘，服务端状态为准）。
 * `expiresAt` 来自 `getStateByDeviceType` 的 `remainTime`（实时剩余秒数），
 * 或 `deviceInfo.reserve_time + reserve_expires`，用于本地每秒递减显示。
 */
data class ShowerReservation(
    val deviceUuid: String,
    val deviceName: String,
    val areaName: String,
    val maxWaitSeconds: Int,
    val expiresAt: Long,
)

// ---------------- 电费 ----------------

@Serializable
data class ElectricRoom(
    val roomVerify: String,
    val fullName: String,
    val zoneId: String = "",
    val buildingId: String = "",
    val floorId: String = "",
    val roomNum: String = "",
)

@Serializable
data class ElectricBalance(
    val roomVerify: String,
    val fullName: String,
    val kwh: String, // odd 余额（度）
    val price: String, // 元/度
    val todayUse: String,
    val weekUseList: List<ElectricDayUse> = emptyList(),
    val monthUseList: List<ElectricMonthUse> = emptyList(),
    val lineStatus: String = "",
    val deviceName: String = "",
    val fetchedAt: Long,
)

@Serializable
data class ElectricDayUse(val date: String, val use: String, val weekday: Int = 0)

@Serializable
data class ElectricMonthUse(val yearMonth: String, val use: String)

@Serializable
data class ElectricUsageDay(val date: String, val use: String, val balance: String = "")

@Serializable
data class ElectricUsage(
    val roomVerify: String,
    val total: String,
    val days: List<ElectricUsageDay>,
    val kind: String, // range / month
)

// ---------------- 我的 / 通用 ----------------

@Serializable
data class UserProfile(
    val name: String,
    val studentNo: String,
    val college: String,
    val major: String,
    val className: String,
    val grade: String,
    val showerMobileMasked: String = "",
    val avatarSeed: String = "",
)

@Serializable
data class CredentialStatus(
    val module: String, // jw / shower / electric
    val connected: Boolean,
    val detail: String, // 学号 2023**** / 手机 138****6621 / 已绑定房间
    val lastCheckedAt: Long,
    val lastError: String = "",
)

@Serializable
data class TodoItem(
    val id: String,
    val source: String, // jw / electric / shower / local
    val type: String, // evaluation / textbook / exam / lowElectric / examCountdown
    val title: String,
    val subtitle: String,
    val dueText: String = "",
    val deepLink: String,
)

@Serializable
data class BindWizardNode(
    val id: String,
    val name: String,
)
