package com.union.campus.domain

/**
 * 上午作息分组：学校按班级把上午作息分为两组，两组仅第 3、4 节时间不同。
 * 分组时间每学期可能调整，因此这里只提供两套预设，具体由用户在设置中选择。
 */
enum class MorningGroup(
    val storedValue: Int,
    val label: String,
    val classCodes: String,
) {
    GroupOne(1, "一组", "J1–J6"),
    GroupTwo(2, "二组", "S1–S6 · J7");

    companion object {
        fun fromStored(value: Int): MorningGroup =
            entries.firstOrNull { it.storedValue == value } ?: GroupOne
    }
}

/**
 * 课表节次时间（与教务作息对齐）。
 *
 * 第 1、2 节与第 5–10 节固定；第 3、4 节按 [MorningGroup] 取不同时间。
 * 课表最多展示到第 10 节，第 9、10 节无课时不显示。
 */
object TimetableSchedule {

    const val MAX_SECTION = 10

    /** 第 4 节之后是午休。 */
    const val LUNCH_AFTER_SECTION = 4

    /** 第 8 节之后是晚休。 */
    const val DINNER_AFTER_SECTION = 8

    /** 第 9、10 节所在区间的起始节次。 */
    const val EVENING_START_SECTION = 9

    private val fixedTimes = listOf(
        SectionTime(1, "08:20", "09:05"),
        SectionTime(2, "09:15", "10:00"),
        SectionTime(3, "10:20", "11:05"),
        SectionTime(4, "11:15", "12:00"),
        SectionTime(5, "14:30", "15:15"),
        SectionTime(6, "15:25", "16:10"),
        SectionTime(7, "16:30", "17:15"),
        SectionTime(8, "17:25", "18:10"),
        SectionTime(9, "18:30", "19:15"),
        SectionTime(10, "19:25", "20:10"),
    )

    /** 按上午分组返回完整的 1–10 节时间。 */
    fun sectionTimes(group: MorningGroup): List<SectionTime> = fixedTimes.map { time ->
        when {
            group == MorningGroup.GroupTwo && time.section == 3 -> time.copy(start = "10:40", end = "11:25")
            group == MorningGroup.GroupTwo && time.section == 4 -> time.copy(start = "11:35", end = "12:20")
            else -> time
        }
    }

    /** 第三节与第四节的时间（设置页/引导页展示用）。 */
    fun morningTimes(group: MorningGroup): Pair<SectionTime, SectionTime> {
        val times = sectionTimes(group)
        return times.first { it.section == 3 } to times.first { it.section == 4 }
    }

    /** 午休区间：第 4 节结束 → 第 5 节开始。 */
    fun lunchRange(group: MorningGroup): Pair<String, String> {
        val times = sectionTimes(group)
        return times.first { it.section == LUNCH_AFTER_SECTION }.end to
            times.first { it.section == LUNCH_AFTER_SECTION + 1 }.start
    }

    /** 晚休区间：第 8 节结束 → 第 9 节开始。 */
    fun dinnerRange(): Pair<String, String> {
        val times = sectionTimes(MorningGroup.GroupOne)
        return times.first { it.section == DINNER_AFTER_SECTION }.end to
            times.first { it.section == DINNER_AFTER_SECTION + 1 }.start
    }
}