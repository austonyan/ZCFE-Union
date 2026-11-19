package com.union.campus.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 成绩分档与 GPA 汇总口径：数字分、等级制、缺考、学分缺失。 */
class GradeScaleTest {

    private fun grade(
        score: String,
        gpa: String = "",
        credits: String = "3.0",
        category: String = "必修",
    ) = Grade(
        courseCode = "",
        courseName = "测试课程",
        credits = credits,
        score = score,
        gpa = gpa,
        category = category,
    )

    @Test
    fun numericScoreBands() {
        assertEquals(GradeBand.Excellent, gradeBandOf("90", ""))
        assertEquals(GradeBand.Excellent, gradeBandOf("100", ""))
        assertEquals(GradeBand.Good, gradeBandOf("89.5", ""))
        assertEquals(GradeBand.Good, gradeBandOf("80", ""))
        assertEquals(GradeBand.Medium, gradeBandOf("79", ""))
        assertEquals(GradeBand.Medium, gradeBandOf("70", ""))
        assertEquals(GradeBand.Pass, gradeBandOf("69", ""))
        assertEquals(GradeBand.Pass, gradeBandOf("60", ""))
        assertEquals(GradeBand.Fail, gradeBandOf("59.5", ""))
    }

    @Test
    fun textScoreBands() {
        assertEquals(GradeBand.Excellent, gradeBandOf("优秀", ""))
        assertEquals(GradeBand.Excellent, gradeBandOf("优", ""))
        assertEquals(GradeBand.Good, gradeBandOf("良好", ""))
        assertEquals(GradeBand.Good, gradeBandOf("良", ""))
        assertEquals(GradeBand.Medium, gradeBandOf("中等", ""))
        assertEquals(GradeBand.Medium, gradeBandOf("中", ""))
        assertEquals(GradeBand.Pass, gradeBandOf("及格", ""))
        assertEquals(GradeBand.Pass, gradeBandOf("合格", ""))
        assertEquals(GradeBand.Pass, gradeBandOf("通过", ""))
        assertEquals(GradeBand.Fail, gradeBandOf("不及格", ""))
        assertEquals(GradeBand.Fail, gradeBandOf("不合格", ""))
        assertEquals(GradeBand.Fail, gradeBandOf("未通过", ""))
    }

    @Test
    fun absentScoresAreUnknown() {
        assertEquals(GradeBand.Unknown, gradeBandOf("缺考", ""))
        assertEquals(GradeBand.Unknown, gradeBandOf("缓考", ""))
        // 缺考/缓考即使带了绩点也不计入。
        assertEquals(GradeBand.Unknown, gradeBandOf("缺考", "0"))
        assertNull(pointsOf("缺考", "0"))
        assertNull(pointsOf("缓考", "2.0"))
    }

    @Test
    fun pointsPreferGpaField() {
        assertEquals(3.5, pointsOf("50", "3.5")!!, 0.0001)
        assertEquals(4.0, pointsOf("90", "4.0")!!, 0.0001)
        // 没有绩点时按数字分换算。
        assertEquals(4.0, pointsOf("92", "")!!, 0.0001)
        assertEquals(3.0, pointsOf("80", "")!!, 0.0001)
        assertEquals(1.0, pointsOf("60", "")!!, 0.0001)
        assertEquals(0.0, pointsOf("59", "")!!, 0.0001)
    }

    @Test
    fun pointsForTextGrades() {
        assertEquals(4.0, pointsOf("优秀", "")!!, 0.0001)
        assertEquals(3.0, pointsOf("良好", "")!!, 0.0001)
        assertEquals(2.0, pointsOf("中等", "")!!, 0.0001)
        assertEquals(1.0, pointsOf("及格", "")!!, 0.0001)
        assertEquals(0.0, pointsOf("不及格", "")!!, 0.0001)
        assertNull(pointsOf("", ""))
    }

    @Test
    fun gpaFallbackWhenScoreTextMissing() {
        assertEquals(GradeBand.Excellent, gradeBandOf("", "4.0"))
        assertEquals(GradeBand.Good, gradeBandOf("", "3.0"))
        assertEquals(GradeBand.Medium, gradeBandOf("", "2.0"))
        assertEquals(GradeBand.Pass, gradeBandOf("", "1.0"))
        assertEquals(GradeBand.Unknown, gradeBandOf("", ""))
    }

    @Test
    fun pureNumericSummary() {
        val summary = computeGpa(
            listOf(
                grade(score = "90", credits = "4.0"),
                grade(score = "80", credits = "2.0"),
            ),
        )
        assertEquals("3.67", summary.gpa)
        assertEquals("86.7", summary.weightedAverage)
        assertEquals(6.0, summary.credits, 0.0001)
        assertEquals(2, summary.courseCount)
        assertEquals(1, summary.excellentCount)
        assertEquals(0, summary.excludedCount)
    }

    @Test
    fun levelSummaryNowCountsCreditsAndPoints() {
        val summary = computeGpa(
            listOf(
                grade(score = "优秀", credits = "3.0"),
                grade(score = "良好", credits = "2.0"),
            ),
        )
        // 旧实现会整门跳过等级制课程；新口径必须计入 (3×4.0 + 2×3.0) / 5 = 3.60。
        assertEquals("3.60", summary.gpa)
        assertEquals("—", summary.weightedAverage)
        assertEquals(5.0, summary.credits, 0.0001)
        assertEquals(2, summary.courseCount)
        assertEquals(1, summary.excellentCount)
        assertEquals(0, summary.excludedCount)
    }

    @Test
    fun mixedNumericAndLevelSummary() {
        val summary = computeGpa(
            listOf(
                grade(score = "85", credits = "4.0"),
                grade(score = "优秀", credits = "2.0"),
            ),
        )
        assertEquals("3.80", summary.gpa)
        assertEquals("85.0", summary.weightedAverage)
        assertEquals(6.0, summary.credits, 0.0001)
        assertEquals(2, summary.courseCount)
        assertEquals(1, summary.excellentCount)
    }

    @Test
    fun absentCoursesAreExcludedButKeptOutOfCounts() {
        val summary = computeGpa(
            listOf(
                grade(score = "92", credits = "4.0"),
                grade(score = "缺考", credits = "3.0"),
                grade(score = "缓考", credits = "2.0"),
            ),
        )
        assertEquals("4.00", summary.gpa)
        assertEquals(4.0, summary.credits, 0.0001)
        assertEquals(1, summary.courseCount)
        assertEquals(1, summary.excellentCount)
        assertEquals(2, summary.excludedCount)
    }

    @Test
    fun missingCreditsStillCountCourse() {
        val summary = computeGpa(listOf(grade(score = "90", credits = "")))
        assertEquals("—", summary.gpa)
        assertEquals("—", summary.weightedAverage)
        assertEquals(0.0, summary.credits, 0.0001)
        assertEquals(1, summary.courseCount)
        assertEquals(1, summary.excellentCount)
        assertEquals(0, summary.excludedCount)
    }

    @Test
    fun gpaFieldWinsOverScoreInSummary() {
        val summary = computeGpa(listOf(grade(score = "50", gpa = "3.5", credits = "2.0")))
        assertEquals("3.50", summary.gpa)
        assertEquals("50.0", summary.weightedAverage)
        assertEquals(0, summary.excellentCount)
    }

    @Test
    fun emptyListSummary() {
        val summary = computeGpa(emptyList())
        assertEquals("—", summary.gpa)
        assertEquals("—", summary.weightedAverage)
        assertEquals(0.0, summary.credits, 0.0001)
        assertEquals(0, summary.courseCount)
        assertEquals(0, summary.excellentCount)
        assertEquals(0, summary.excludedCount)
    }
}