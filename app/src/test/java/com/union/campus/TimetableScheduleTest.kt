package com.union.campus

import com.union.campus.domain.MorningGroup
import com.union.campus.domain.TimetableSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** 课表节次时间：固定节次、上午分组差异与午休/晚休区间。 */
class TimetableScheduleTest {

    @Test
    fun hasExactlyTenSections() {
        val times = TimetableSchedule.sectionTimes(MorningGroup.GroupOne)
        assertEquals(TimetableSchedule.MAX_SECTION, times.size)
        assertEquals((1..10).toList(), times.map { it.section })
    }

    @Test
    fun fixedSectionsMatchSchoolTimetable() {
        val times = TimetableSchedule.sectionTimes(MorningGroup.GroupOne).associateBy { it.section }
        assertEquals("08:20" to "09:05", times.getValue(1).start to times.getValue(1).end)
        assertEquals("09:15" to "10:00", times.getValue(2).start to times.getValue(2).end)
        assertEquals("14:30" to "15:15", times.getValue(5).start to times.getValue(5).end)
        assertEquals("17:25" to "18:10", times.getValue(8).start to times.getValue(8).end)
        assertEquals("18:30" to "19:15", times.getValue(9).start to times.getValue(9).end)
        assertEquals("19:25" to "20:10", times.getValue(10).start to times.getValue(10).end)
    }

    @Test
    fun morningGroupsDifferOnlyInThirdAndFourthSections() {
        val one = TimetableSchedule.sectionTimes(MorningGroup.GroupOne).associateBy { it.section }
        val two = TimetableSchedule.sectionTimes(MorningGroup.GroupTwo).associateBy { it.section }

        assertEquals("10:20" to "11:05", one.getValue(3).start to one.getValue(3).end)
        assertEquals("11:15" to "12:00", one.getValue(4).start to one.getValue(4).end)
        assertEquals("10:40" to "11:25", two.getValue(3).start to two.getValue(3).end)
        assertEquals("11:35" to "12:20", two.getValue(4).start to two.getValue(4).end)

        (1..10).filter { it != 3 && it != 4 }.forEach { section ->
            assertEquals(one.getValue(section), two.getValue(section))
        }
    }

    @Test
    fun lunchAndDinnerRangesFollowSectionTimes() {
        assertEquals("12:00" to "14:30", TimetableSchedule.lunchRange(MorningGroup.GroupOne))
        assertEquals("12:20" to "14:30", TimetableSchedule.lunchRange(MorningGroup.GroupTwo))
        assertEquals("18:10" to "18:30", TimetableSchedule.dinnerRange())
    }

    @Test
    fun unknownStoredGroupFallsBackToGroupOne() {
        assertEquals(MorningGroup.GroupOne, MorningGroup.fromStored(0))
        assertEquals(MorningGroup.GroupOne, MorningGroup.fromStored(99))
        assertEquals(MorningGroup.GroupTwo, MorningGroup.fromStored(2))
        assertNotEquals(MorningGroup.GroupOne, MorningGroup.fromStored(2))
    }
}