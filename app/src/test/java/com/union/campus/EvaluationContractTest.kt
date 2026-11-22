package com.union.campus

import com.union.campus.data.repo.buildEvaluationSubmissionJson
import com.union.campus.data.repo.buildEvaluationRequestParams
import com.union.campus.data.repo.decodeEvaluationMultiAnswer
import com.union.campus.data.repo.encodeEvaluationMultiAnswer
import com.union.campus.data.repo.escapeForLegacyH5
import com.union.campus.data.repo.parseEvaluationSubmissionReply
import com.union.campus.data.repo.parseEvaluationForm
import com.union.campus.data.repo.parseEvaluationItems
import com.union.campus.data.repo.validateEvaluationAnswers
import com.union.campus.domain.EvaluationItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationContractTest {
    @Test
    fun parsesOfficialGroupedList_withSemesterDatesCoursesAndIdentifiers() {
        val items = parseEvaluationItems(Json.parseToJsonElement("""
            {
              "resultSet": [{
                "xnxqdm":"20260", "xnxqmc":"2025-2026学年第一学期",
                "pjlcdm":"R2026", "pjlcmc":"学生评教",
                "kssj":"2026-11-02 00:00", "jssj":"2026-11-27 23:59",
                "kcSet":[
                  {"kcdm":"C1","kcmc":"课程甲","jsdm":"T1","jsmc":"教师甲","xf":"3","pjlxdm":"L1","pjlxmc":"讲解","zt":"0"},
                  {"kcdm":"C2","kcmc":"课程乙","jsdm":"T2","jsmc":"教师乙","xf":"2","pjlxdm":"L2","pjlxmc":"实验","zt":"1"}
                ]
              }]
            }
        """.trimIndent()))

        assertEquals(2, items.size)
        assertEquals("20260", items.first().xnxq)
        assertEquals("2025-2026学年第一学期", items.first().semesterName)
        assertEquals("学生评教", items.first().roundName)
        assertEquals("2026-11-02 00:00", items.first().startsAt)
        assertEquals("2026-11-27 23:59", items.first().endsAt)
        assertEquals("C1", items.first().courseCode)
        assertEquals("T1", items.first().teacherCode)
        assertEquals("3", items.first().credits)
        assertTrue(items.first().isPending)
        assertFalse(items.last().isPending)
        assertEquals("C2", items.last().rawCourseInfo["kcdm"])
    }

    @Test
    fun parsesQuestionnaireHierarchy_allOfficialTypesAndExistingAnswers() {
        val form = parseEvaluationForm(Json.parseToJsonElement("""
            {
              "zt":"0",
              "resultSet":[{"pjfsdm":"F1","pjfsmc":"总体评价","yjzbSet":[{
                "yjzbdm":"Y1","yjzbmc":"教师授课情况","ejzbSet":[
                  {"ejzbdm":"Q1","ejzbtx":"1","ejzbmc":"单选","xxSet":[{"xuanxdm":"A","xuanxmc":"选项A","isxz":"1"},{"xuanxdm":"B","xuanxmc":"选项B","isxz":"0"}]},
                  {"ejzbdm":"Q2","ejzbtx":"2","ejzbmc":"分值","tkxx":"60","tksx":"100","tkjg":"90"},
                  {"ejzbdm":"Q3","ejzbtx":"3","ejzbmc":"多选","xxSet":[{"xuanxdm":"X","xuanxmc":"选项X","isxz":"1"},{"xuanxdm":"Y","xuanxmc":"选项Y","isxz":"0"}]},
                  {"ejzbdm":"Q4","ejzbtx":"4","ejzbmc":"问答","wdjg":"课程建议"}
                ]
              }]}]
            }
        """.trimIndent()))

        val questions = form.formats.single().categories.single().questions
        assertEquals("0", form.statusCode)
        assertEquals(listOf("1", "2", "3", "4"), questions.map { it.typeCode })
        assertEquals(listOf("A"), questions[0].initialAnswers)
        assertEquals("60", questions[1].minimum)
        assertEquals("100", questions[1].maximum)
        assertEquals(listOf("X"), questions[2].initialAnswers)
        assertEquals(listOf("课程建议"), questions[3].initialAnswers)
    }

    @Test
    fun validatesRequiredAnswersAndRefusesUnknownQuestionTypes() {
        val form = parseEvaluationForm(Json.parseToJsonElement("""
            {"zt":"0","resultSet":[{"pjfsdm":"F","yjzbSet":[{"yjzbdm":"Y","ejzbSet":[
              {"ejzbdm":"Q1","ejzbtx":"1","xxSet":[{"xuanxdm":"A","xuanxmc":"是"}]},
              {"ejzbdm":"Q2","ejzbtx":"3","xxSet":[{"xuanxdm":"X","xuanxmc":"选项"}]}
            ]}]}]}
        """.trimIndent()))
        val questions = form.formats.single().categories.single().questions
        assertEquals("请完成所有多选题", validateEvaluationAnswers(form, mapOf(questions[0].key to "A")))
        assertNull(validateEvaluationAnswers(form, mapOf(questions[0].key to "A", questions[1].key to "[\"X\"]")))

        val unknown = parseEvaluationForm(Json.parseToJsonElement("""
            {"zt":"0","resultSet":[{"pjfsdm":"F","yjzbSet":[{"yjzbdm":"Y","ejzbSet":[{"ejzbdm":"Q","ejzbtx":"9"}]}]}]}
        """.trimIndent()))
        assertTrue(validateEvaluationAnswers(unknown, emptyMap())?.contains("暂不支持") == true)
    }

    @Test
    fun buildsExactNestedSubmission_forCourseAndEachQuestionType() {
        val course = sampleCourse()
        val form = parseEvaluationForm(Json.parseToJsonElement("""
            {"zt":"0","resultSet":[{"pjfsdm":"F1","yjzbSet":[{"yjzbdm":"Y1","ejzbSet":[
              {"ejzbdm":"Q1","ejzbtx":"1","xxSet":[{"xuanxdm":"A","xuanxmc":"甲"}]},
              {"ejzbdm":"Q2","ejzbtx":"2","tkxx":"1","tksx":"100"},
              {"ejzbdm":"Q3","ejzbtx":"3","xxSet":[{"xuanxdm":"X","xuanxmc":"甲"},{"xuanxdm":"Y","xuanxmc":"乙"}]},
              {"ejzbdm":"Q4","ejzbtx":"4"}
            ]}]}]}
        """.trimIndent()))
        val questions = form.formats.single().categories.single().questions
        val answers = mapOf(
            questions[0].key to "A",
            questions[1].key to "88",
            questions[2].key to encodeEvaluationMultiAnswer(listOf("X", "Y")),
            questions[3].key to "真实意见",
        )

        val payload = Json.parseToJsonElement(buildEvaluationSubmissionJson(course, form, answers, "202400000001")).jsonObject
        assertEquals("202400000001", payload.getValue("userid").jsonPrimitive.content)
        assertEquals("20260", payload.getValue("xnxq").jsonPrimitive.content)
        assertEquals("R1", payload.getValue("pjlcdm").jsonPrimitive.content)
        assertEquals("C1", payload.getValue("kcdm").jsonPrimitive.content)
        assertEquals("T1", payload.getValue("jsdm").jsonPrimitive.content)
        val formats = payload.getValue("pjnrSet").jsonArray
        assertEquals("F1", formats.single().jsonObject.getValue("pjfsdm").jsonPrimitive.content)
        val categories = formats.single().jsonObject.getValue("yjzbSet").jsonArray
        assertEquals("Y1", categories.single().jsonObject.getValue("yjzbdm").jsonPrimitive.content)
        val answersOut = categories.single().jsonObject.getValue("ejzbSet").jsonArray
        assertEquals(listOf("A", "88", "X|Y", "真实意见"), answersOut.map {
            it.jsonObject.getValue("ejzbpjjg").jsonPrimitive.content
        })
    }

    @Test
    fun escapesPayloadLikeJavaScriptAndAcceptsOnlyExplicitWriteSuccess() {
        assertEquals("abc@*_+-./%20%u8BC4%u4EF7", escapeForLegacyH5("abc@*_+-./ 评价"))
        val request = buildEvaluationRequestParams("{\"param\":\"评价\"}")
        assertEquals("%7B%22param%22%3A%22%u8BC4%u4EF7%22%7D", request["param"])
        assertEquals(setOf("param"), request.keys)
        assertEquals("1", parseEvaluationSubmissionReply(Json.parseToJsonElement("""{"resultSet":[{"flag":"1","bz":"提交成功"}]}"""))?.flag)
        assertEquals("已提交过", parseEvaluationSubmissionReply(Json.parseToJsonElement("""{"resultSet":[{"flag":"0","bz":"已提交过"}]}"""))?.message)
        assertNull(parseEvaluationSubmissionReply(Json.parseToJsonElement("""{"resultSet":[]}""")))
    }

    @Test
    fun multiAnswerDraftsRoundTripWithoutCommaCorruption() {
        val choices = listOf("含,逗号", "含|竖线", "选项C")
        assertEquals(choices, decodeEvaluationMultiAnswer(encodeEvaluationMultiAnswer(choices)))
        assertEquals(emptyList<String>(), decodeEvaluationMultiAnswer("not-json"))
    }

    private fun sampleCourse() = EvaluationItem(
        xnxq = "20260",
        evaluationRoundCode = "R1",
        courseCode = "C1",
        teacherCode = "T1",
        evaluationTypeCode = "L1",
        evaluationStatusCode = "P1",
        courseStatusCode = "0",
        courseName = "课程甲",
        teacher = "教师甲",
        credits = "3",
        evaluationType = "讲解",
        semesterName = "2025-2026学年第一学期",
        roundName = "学生评教",
        startsAt = "2026-11-02 00:00",
        endsAt = "2026-11-27 23:59",
    )
}
