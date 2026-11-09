package com.union.campus

import com.union.campus.domain.BuildingGroup
import com.union.campus.domain.CourseCell
import com.union.campus.domain.MorningSlot
import com.union.campus.domain.TimetableSchedule
import com.union.campus.domain.TimetableScheduleConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 课表节次时间：固定节次、两套时间槽、楼号识别与午休/晚休区间。 */
class TimetableScheduleTest {

    private val defaultConfig = TimetableScheduleConfig(
        j1ToJ6Slot = MorningSlot.Late,
        fallbackGroup = BuildingGroup.J1ToJ6,
    )

    private fun cell(location: String, start: Int = 3, end: Int = 4) = CourseCell(
        id = "test",
        name = "测试课",
        teacher = "老师",
        location = location,
        day = 1,
        sections = "$start-${end}节",
        startSection = start,
        endSection = end,
        weeks = "1-16",
    )

    @Test
    fun hasExactlyTenSections() {
        val times = TimetableSchedule.sectionTimes(MorningSlot.Early)
        assertEquals(TimetableSchedule.MAX_SECTION, times.size)
        assertEquals((1..10).toList(), times.map { it.section })
    }

    @Test
    fun fixedSectionsMatchSchoolTimetable() {
        val times = TimetableSchedule.sectionTimes(MorningSlot.Early).associateBy { it.section }
        assertEquals("08:20" to "09:05", times.getValue(1).start to times.getValue(1).end)
        assertEquals("09:15" to "10:00", times.getValue(2).start to times.getValue(2).end)
        assertEquals("14:30" to "15:15", times.getValue(5).start to times.getValue(5).end)
        assertEquals("17:25" to "18:10", times.getValue(8).start to times.getValue(8).end)
        assertEquals("18:30" to "19:15", times.getValue(9).start to times.getValue(9).end)
        assertEquals("19:25" to "20:10", times.getValue(10).start to times.getValue(10).end)
    }

    @Test
    fun twoSlotsDifferOnlyInThirdAndFourthSections() {
        val early = TimetableSchedule.sectionTimes(MorningSlot.Early).associateBy { it.section }
        val late = TimetableSchedule.sectionTimes(MorningSlot.Late).associateBy { it.section }

        assertEquals("10:20" to "11:05", early.getValue(3).start to early.getValue(3).end)
        assertEquals("11:15" to "12:00", early.getValue(4).start to early.getValue(4).end)
        assertEquals("10:40" to "11:25", late.getValue(3).start to late.getValue(3).end)
        assertEquals("11:35" to "12:20", late.getValue(4).start to late.getValue(4).end)

        (1..10).filter { it != 3 && it != 4 }.forEach { section ->
            assertEquals(early.getValue(section), late.getValue(section))
        }
    }

    @Test
    fun buildingCodesMapToTheRightGroup() {
        assertEquals(BuildingGroup.J1ToJ6, TimetableSchedule.buildingGroupOf("J1-101"))
        assertEquals(BuildingGroup.J1ToJ6, TimetableSchedule.buildingGroupOf("知真楼(J6)[J6-201]"))
        assertEquals(BuildingGroup.S1ToS6AndJ7, TimetableSchedule.buildingGroupOf("S2-301"))
        assertEquals(BuildingGroup.S1ToS6AndJ7, TimetableSchedule.buildingGroupOf("J7-101"))
        assertNull(TimetableSchedule.buildingGroupOf("3号楼204"))
    }

    @Test
    fun courseLocationSelectsItsOwnSchedule() {
        val j = TimetableSchedule.courseSchedule(cell("J3-101"), defaultConfig)
        val s = TimetableSchedule.courseSchedule(cell("S2-301"), defaultConfig)
        val j7 = TimetableSchedule.courseSchedule(cell("J7-101"), defaultConfig)

        assertEquals(BuildingGroup.J1ToJ6, j?.group)
        assertEquals(MorningSlot.Late, j?.slot)
        assertEquals("10:40", j?.start)
        assertEquals("12:20", j?.end)
        assertTrue(j?.detectedFromLocation == true)

        assertEquals(BuildingGroup.S1ToS6AndJ7, s?.group)
        assertEquals(MorningSlot.Early, s?.slot)
        assertEquals("10:20", s?.start)
        assertEquals("12:00", s?.end)
        assertTrue(s?.detectedFromLocation == true)

        assertEquals(BuildingGroup.S1ToS6AndJ7, j7?.group)
        assertEquals(MorningSlot.Early, j7?.slot)
    }

    @Test
    fun swappedSemesterFollowsTheStoredMapping() {
        val swapped = TimetableScheduleConfig(
            j1ToJ6Slot = MorningSlot.Early,
            fallbackGroup = BuildingGroup.S1ToS6AndJ7,
        )
        val j = TimetableSchedule.courseSchedule(cell("J1-101"), swapped)
        val s = TimetableSchedule.courseSchedule(cell("S1-101"), swapped)
        assertEquals(MorningSlot.Early, j?.slot)
        assertEquals("10:20", j?.start)
        assertEquals(MorningSlot.Late, s?.slot)
        assertEquals("10:40", s?.start)
    }

    @Test
    fun unknownLocationUsesFallbackGroup() {
        val info = TimetableSchedule.courseSchedule(cell("3号楼204"), defaultConfig)
        assertEquals(BuildingGroup.J1ToJ6, info?.group)
        assertEquals(MorningSlot.Late, info?.slot)
        assertFalse(info?.detectedFromLocation == true)
    }

    @Test
    fun lunchAndDinnerRangesFollowSectionTimes() {
        assertEquals("12:00" to "14:30", TimetableSchedule.lunchRange(MorningSlot.Early))
        assertEquals("12:20" to "14:30", TimetableSchedule.lunchRange(MorningSlot.Late))
        assertEquals("18:10" to "18:30", TimetableSchedule.dinnerRange())
    }

    @Test
    fun scheduleConfigFromStored() {
        val thisSemester = TimetableScheduleConfig.fromStored(jLate = true, fallbackValue = 1)
        assertEquals(BuildingGroup.J1ToJ6, thisSemester.fallbackGroup)
        assertEquals(MorningSlot.Late, thisSemester.slotFor(BuildingGroup.J1ToJ6))
        assertEquals(MorningSlot.Early, thisSemester.slotFor(BuildingGroup.S1ToS6AndJ7))

        val nextSemester = TimetableScheduleConfig.fromStored(jLate = false, fallbackValue = 2)
        assertEquals(BuildingGroup.S1ToS6AndJ7, nextSemester.fallbackGroup)
        assertEquals(MorningSlot.Early, nextSemester.slotFor(BuildingGroup.J1ToJ6))
        assertEquals(MorningSlot.Late, nextSemester.slotFor(BuildingGroup.S1ToS6AndJ7))
    }
}