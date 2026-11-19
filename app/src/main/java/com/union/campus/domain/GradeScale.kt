package com.union.campus.domain

import java.util.Locale

/** 成绩等级：列表色标、分布条与绩点折算共用同一套分档。 */
enum class GradeBand(val label: String) {
    Excellent("优秀"),
    Good("良好"),
    Medium("中等"),
    Pass("及格"),
    Fail("不及格"),
    Unknown("未知"),
}

/** 4.0 制 GPA 汇总（只统计能折算绩点的课程）。 */
data class GpaSummary(
    val gpa: String,
    val weightedAverage: String,
    val credits: Double,
    val courseCount: Int,
    val excellentCount: Int,
    val excludedCount: Int,
)

/** 不计入 GPA、且不能按等级折算的成绩。 */
private val AbsentTokens = listOf("缺考", "缓考")

/**
 * 分数/等级 → 五档。
 *
 * 数字分按 90/80/70/60 切档；等级文字识别优秀/良好/中等/及格/不及格；
 * 缺考、缓考和无法识别且无绩点的成绩记为 [GradeBand.Unknown]。
 */
fun gradeBandOf(score: String, gpa: String): GradeBand {
    val text = score.trim()
    if (AbsentTokens.any { text.contains(it) }) return GradeBand.Unknown
    text.toDoubleOrNull()?.let { return bandOfScore(it) }
    textBandOf(text)?.let { return it }
    // 只有绩点没有分数时，按等级折算的同一套阈值回退。
    return gpa.trim().toDoubleOrNull()?.let { bandOfPoints(it) } ?: GradeBand.Unknown
}

/**
 * 单门课绩点：优先取教务返回的绩点，其次数字分按学校换算表折算，
 * 再其次按等级制（优 4.0 / 良 3.0 / 中 2.0 / 及格 1.0 / 不及格 0.0）。
 * 缺考、缓考与无法折算的成绩返回 null（不计入 GPA）。
 */
fun pointsOf(score: String, gpa: String): Double? {
    val text = score.trim()
    if (AbsentTokens.any { text.contains(it) }) return null
    gpa.trim().toDoubleOrNull()?.let { return it }
    text.toDoubleOrNull()?.let { return scoreToPoints(it) }
    return when (textBandOf(text)) {
        GradeBand.Excellent -> 4.0
        GradeBand.Good -> 3.0
        GradeBand.Medium -> 2.0
        GradeBand.Pass -> 1.0
        GradeBand.Fail -> 0.0
        else -> null
    }
}

/**
 * 4.0 制 GPA 汇总。
 *
 * 与旧实现的差异：等级制课程不再被整门跳过；只有绩点无法折算（缺考/缓考/
 * 无法识别的等级）才计入 [GpaSummary.excludedCount]。学分缺失的课程仍计入
 * [GpaSummary.courseCount]，但不参与学分与加权计算。
 */
fun computeGpa(grades: List<Grade>): GpaSummary {
    var totalCredits = 0.0
    var weightedPoints = 0.0
    var weightedScore = 0.0
    var scoreCredits = 0.0
    var courseCount = 0
    var excellentCount = 0
    var excludedCount = 0

    grades.forEach { grade ->
        val points = pointsOf(grade.score, grade.gpa)
        if (points == null) {
            excludedCount++
            return@forEach
        }
        courseCount++
        if (gradeBandOf(grade.score, grade.gpa) == GradeBand.Excellent) {
            excellentCount++
        }
        val credit = grade.credits.trim().toDoubleOrNull() ?: return@forEach
        totalCredits += credit
        weightedPoints += credit * points
        grade.score.trim().toDoubleOrNull()?.let { score ->
            weightedScore += credit * score
            scoreCredits += credit
        }
    }

    return GpaSummary(
        gpa = if (totalCredits > 0) format(weightedPoints / totalCredits, 2) else "—",
        weightedAverage = if (scoreCredits > 0) format(weightedScore / scoreCredits, 1) else "—",
        credits = totalCredits,
        courseCount = courseCount,
        excellentCount = excellentCount,
        excludedCount = excludedCount,
    )
}

private fun bandOfScore(score: Double): GradeBand = when {
    score >= 90 -> GradeBand.Excellent
    score >= 80 -> GradeBand.Good
    score >= 70 -> GradeBand.Medium
    score >= 60 -> GradeBand.Pass
    else -> GradeBand.Fail
}

private fun bandOfPoints(points: Double): GradeBand = when {
    points >= 4.0 -> GradeBand.Excellent
    points >= 3.0 -> GradeBand.Good
    points >= 2.0 -> GradeBand.Medium
    points >= 1.0 -> GradeBand.Pass
    else -> GradeBand.Fail
}

/** 等级文字识别；"不及格/不合格/未通过"必须先于"及格/合格/通过"判断（子串重叠）。 */
private fun textBandOf(text: String): GradeBand? = when {
    text.isEmpty() -> null
    text.contains("优秀") || text == "优" -> GradeBand.Excellent
    text.contains("良好") || text == "良" -> GradeBand.Good
    text.contains("中等") || text == "中" -> GradeBand.Medium
    text.contains("不及格") || text.contains("不合格") || text.contains("未通过") -> GradeBand.Fail
    text.contains("及格") || text.contains("合格") || text.contains("通过") -> GradeBand.Pass
    else -> null
}

/**
 * 数字分 → 4.0 制绩点（学校换算表）：
 * 90+→4.0, 85+→3.7, 82+→3.3, 78+→3.0, 75+→2.7, 72+→2.3,
 * 68+→2.0, 64+→1.5, 60+→1.0, 其余 0。
 */
private fun scoreToPoints(score: Double): Double = when {
    score >= 90 -> 4.0
    score >= 85 -> 3.7
    score >= 82 -> 3.3
    score >= 78 -> 3.0
    score >= 75 -> 2.7
    score >= 72 -> 2.3
    score >= 68 -> 2.0
    score >= 64 -> 1.5
    score >= 60 -> 1.0
    else -> 0.0
}

private fun format(value: Double, digits: Int): String =
    String.format(Locale.ROOT, "%.${digits}f", value)
/**
 * 考核方式（考试 / 考查）。
 *
 * 不同学期的教务接口字段名不一致，先按常见键名取，取不到就在原始字段里
 * 找"考核"相关的键或直接等于考试/考查的值。
 */
fun Grade.assessmentText(): String {
    val preferred = listOf("khfs", "ksfs", "khlx", "考核方式", "考核形式", "khxs")
    preferred.firstNotNullOfOrNull { key -> raw[key]?.trim()?.takeIf { it.isNotBlank() } }
        ?.let { return it }
    raw.entries.firstOrNull { (key, value) ->
        key.contains("考核") || value.trim() in setOf("考试", "考查", "考察", "开卷", "闭卷")
    }?.let { return it.value.trim() }
    return ""
}
