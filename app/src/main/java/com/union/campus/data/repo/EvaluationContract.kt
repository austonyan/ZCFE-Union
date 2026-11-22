package com.union.campus.data.repo

import com.union.campus.domain.EvaluationCategory
import com.union.campus.core.AppError
import com.union.campus.domain.EvaluationFormat
import com.union.campus.domain.EvaluationForm
import com.union.campus.domain.EvaluationItem
import com.union.campus.domain.EvaluationOption
import com.union.campus.domain.EvaluationQuestion
import java.util.Locale
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Parses the same grouped `resultSet -> kcSet` shape rendered by the official H5 list. */
internal fun parseEvaluationItems(element: JsonElement): List<EvaluationItem> {
    val root = element as? JsonObject ?: throw AppError.Upstream("评教列表格式错误")
    val rounds = Parse.objects(root["resultSet"] as? JsonArray ?: throw AppError.Upstream("评教列表缺少 resultSet"))
    return buildList {
        rounds.forEachIndexed { roundIndex, round ->
            val courses = round["kcSet"] as? JsonArray
                ?: round["courseSet"] as? JsonArray
                ?: round["courses"] as? JsonArray
            val courseRows = courses?.let(Parse::objects)
                ?: if (Parse.str(round, "kcdm", "courseCode", "kcmc", "courseName").isNotBlank()) listOf(round)
                else emptyList()
            courseRows.forEachIndexed { courseIndex, course ->
                val row = JsonObject(round + course)
                val semester = Parse.str(round, "xnxqmc", "semesterName", "xnxqname")
                val roundName = Parse.str(round, "pjlcmc", "roundName", "round")
                add(
                    EvaluationItem(
                        xnxq = Parse.str(row, "xnxqdm", "xnxq", "xnxqdm"),
                        evaluationRoundCode = Parse.str(row, "pjlcdm", "evaluationRoundCode"),
                        courseCode = Parse.str(row, "kcdm", "courseCode"),
                        teacherCode = Parse.str(row, "jsdm", "teacherCode"),
                        evaluationTypeCode = Parse.str(row, "pjlxdm", "evaluationTypeCode"),
                        evaluationStatusCode = Parse.str(row, "pjztdm", "evaluationStatusCode"),
                        courseStatusCode = Parse.str(row, "zt", "courseStatusCode"),
                        courseName = Parse.str(row, "kcmc", "courseName", "course").ifBlank { "课程信息缺失" },
                        teacher = Parse.str(row, "jsmc", "teacher"),
                        credits = Parse.str(row, "xf", "credits", "credit"),
                        evaluationType = Parse.str(row, "pjlxmc", "evaluationType", "typeName"),
                        semesterName = semester,
                        roundName = roundName,
                        startsAt = Parse.str(round, "kssj", "startsAt", "startTime"),
                        endsAt = Parse.str(round, "jssj", "endsAt", "endTime"),
                        roundIndex = roundIndex,
                        courseIndex = courseIndex,
                        maxCount = Parse.str(row, "maxcount", "maxCount"),
                        canEvaluate = Parse.str(row, "kqxjpj", "canEvaluate"),
                        completedCourseCount = Parse.str(row, "wcxjpj", "completedCourseCount"),
                        rawCourseInfo = primitiveFields(course),
                        rawRoundInfo = primitiveFields(round),
                    ),
                )
            }
        }
    }
}

/** Parses the official `resultSet -> yjzbSet -> ejzbSet -> xxSet` questionnaire tree. */
internal fun parseEvaluationForm(element: JsonElement): EvaluationForm {
    val root = element as? JsonObject ?: throw AppError.Upstream("问卷格式错误")
    val formats = Parse.objects(root["resultSet"] as? JsonArray ?: throw AppError.Upstream("问卷缺少 resultSet")).mapIndexed { formatIndex, format ->
        val formatCode = Parse.str(format, "pjfsdm", "formatCode")
        val categories = Parse.objects(format["yjzbSet"] as? JsonArray).mapIndexed { categoryIndex, category ->
            val categoryCode = Parse.str(category, "yjzbdm", "categoryCode")
            val questions = Parse.objects(category["ejzbSet"] as? JsonArray).mapIndexed { questionIndex, question ->
                val code = Parse.str(question, "ejzbdm", "questionCode")
                val type = Parse.str(question, "ejzbtx", "type")
                val optionRows = Parse.objects(Parse.array(question, "xxSet", "options"))
                val options = optionRows.map { option ->
                    EvaluationOption(
                        code = Parse.str(option, "xuanxdm", "code", "dm", "id"),
                        label = Parse.str(option, "xuanxmc", "mc", "label", "name"),
                    )
                }
                val selected = optionRows.indices.filter { index ->
                    Parse.str(optionRows[index], "isxz", "selected") == "1"
                }.map { options[it].code }
                val initialAnswers = when (type) {
                    "1" -> selected.take(1)
                    "2" -> listOf(Parse.str(question, "tkjg", "answer")).filter(String::isNotBlank)
                    "3" -> selected
                    "4" -> listOf(Parse.str(question, "wdjg", "answer")).filter(String::isNotBlank)
                    else -> emptyList()
                }
                EvaluationQuestion(
                    key = "$formatIndex|$categoryIndex|$questionIndex|$formatCode|$categoryCode|$code",
                    code = code,
                    title = Parse.str(question, "ejzbmc", "ejzbsm", "title", "label").ifBlank { "评价问题 ${questionIndex + 1}" },
                    typeCode = type,
                    options = options,
                    initialAnswers = initialAnswers,
                    minimum = Parse.str(question, "tkxx", "minimum", "min"),
                    maximum = Parse.str(question, "tksx", "maximum", "max"),
                )
            }
            EvaluationCategory(
                code = categoryCode,
                title = Parse.str(category, "yjzbmc", "categoryName", "title"),
                questions = questions,
            )
        }
        EvaluationFormat(
            code = formatCode,
            title = Parse.str(format, "pjfsmc", "formatName", "title"),
            categories = categories,
        )
    }
    return EvaluationForm(statusCode = Parse.str(root, "zt", "status"), formats = formats)
}

internal fun encodeEvaluationMultiAnswer(values: List<String>): String =
    Json.encodeToString(ListSerializer(String.serializer()), values)

internal fun decodeEvaluationMultiAnswer(value: String): List<String> = runCatching {
    Json.decodeFromString(ListSerializer(String.serializer()), value)
}.getOrDefault(emptyList())

/** Mirrors the official H5's validation: each question must have an answer before submit. */
internal fun validateEvaluationAnswers(form: EvaluationForm, answers: Map<String, String>): String? {
    if (form.formats.isEmpty() || form.formats.flatMap { it.categories }.flatMap { it.questions }.isEmpty()) {
        return "教务没有返回评价题目"
    }
    if (form.statusCode != "0") return "该问卷当前不可提交"
    if (form.formats.any { it.code.isBlank() || it.categories.isEmpty() || it.categories.any { c ->
            c.code.isBlank() || c.questions.isEmpty() || c.questions.any { it.code.isBlank() }
        } }) return "教务返回的问卷信息不完整"
    for (question in form.formats.flatMap { it.categories }.flatMap { it.questions }) {
        val answer = answers[question.key].orEmpty()
        when (question.typeCode) {
            "1" -> if (answer.isBlank() || question.options.none { it.code == answer }) return "请完成所有单选题"
            "2" -> {
                val value = answer.toBigDecimalOrNull() ?: return "请填写有效分值"
                val minimum = question.minimum.toBigDecimalOrNull()
                val maximum = question.maximum.toBigDecimalOrNull()
                if ((minimum != null && value < minimum) || (maximum != null && value > maximum)) return "分值超出题目范围"
            }
            "3" -> {
                val selected = decodeEvaluationMultiAnswer(answer)
                if (selected.isEmpty() || selected.any { it !in question.options.map(EvaluationOption::code) }) {
                    return "请完成所有多选题"
                }
            }
            "4" -> if (answer.isBlank()) return "请完成所有问答题"
            else -> return "问卷包含暂不支持的题型，已阻止提交"
        }
    }
    return null
}

/** Builds the nested `param=escape(JSON.stringify(e))` payload used by the official H5. */
internal fun buildEvaluationSubmissionJson(
    item: EvaluationItem,
    form: EvaluationForm,
    answers: Map<String, String>,
    studentNo: String,
): String {
    if (!item.isPending || studentNo.isBlank() || !hasCoreEvaluationIds(item)) {
        throw AppError.Validation("课程评教信息不完整或该课程已提交")
    }
    validateEvaluationAnswers(form, answers)?.let { throw AppError.Validation(it) }
    val pjnrSet = buildJsonArray {
        form.formats.forEach { format ->
            add(buildJsonObject {
                put("pjfsdm", format.code)
                put("yjzbSet", buildJsonArray {
                    format.categories.forEach { category ->
                        add(buildJsonObject {
                            put("yjzbdm", category.code)
                            put("ejzbSet", buildJsonArray {
                                category.questions.forEach { question ->
                                    val answer = answers.getValue(question.key)
                                    val result = if (question.typeCode == "3") {
                                        decodeEvaluationMultiAnswer(answer).joinToString("|")
                                    } else answer
                                    add(buildJsonObject {
                                        put("ejzbdm", question.code)
                                        put("ejzbtx", question.typeCode)
                                        put("ejzbpjjg", result)
                                    })
                                }
                            })
                        })
                    }
                })
            })
        }
    }
    return buildJsonObject {
        put("userid", studentNo)
        put("usertype", "STU")
        put("xnxq", item.xnxq)
        put("pjlcdm", item.evaluationRoundCode)
        put("kcdm", item.courseCode)
        put("jsdm", item.teacherCode)
        put("pjlxdm", item.evaluationTypeCode)
        put("pjztdm", item.evaluationStatusCode)
        put("pjnrSet", pjnrSet)
    }.toString()
}

private fun primitiveFields(obj: JsonObject): Map<String, String> = obj.mapNotNull { (key, value) ->
    val text = (value as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@mapNotNull null
    key to text
}.toMap()

internal data class EvaluationSubmissionReply(val flag: String, val message: String)

internal fun parseEvaluationSubmissionReply(element: JsonElement): EvaluationSubmissionReply? {
    val result = ((element as? JsonObject)?.get("resultSet") as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
    val flag = Parse.str(result, "flag")
    if (flag.isBlank()) return null
    return EvaluationSubmissionReply(flag, Parse.str(result, "bz", "msg", "message"))
}

internal fun buildEvaluationRequestParams(payload: String): Map<String, String> = mapOf(
    "param" to escapeForLegacyH5(payload),
)

internal fun evaluationCourseParams(item: EvaluationItem) = mapOf(
    "xnxq" to item.xnxq, "pjlcdm" to item.evaluationRoundCode, "kcdm" to item.courseCode,
    "jsdm" to item.teacherCode, "pjlxdm" to item.evaluationTypeCode, "pjztdm" to item.evaluationStatusCode,
)

/** 只有这四项能唯一定位到课程与轮次；pjlxdm/pjztdm 允许为空，与 H5 原样传空值保持一致。 */
internal fun hasCoreEvaluationIds(item: EvaluationItem): Boolean =
    listOf(item.xnxq, item.evaluationRoundCode, item.courseCode, item.teacherCode).all(String::isNotBlank)

class EvaluationSubmissionRejected(message: String) : Exception(message)

/** JavaScript global escape(), including its legacy %uXXXX handling for Chinese text. */
internal fun escapeForLegacyH5(value: String): String = buildString(value.length * 2) {
    value.forEach { char ->
        val code = char.code
        val safe = code in 'a'.code..'z'.code || code in 'A'.code..'Z'.code || code in '0'.code..'9'.code ||
            char in "@*_+-./"
        when {
            safe -> append(char)
            code < 256 -> append("%${code.toString(16).uppercase(Locale.ROOT).padStart(2, '0')}")
            else -> append("%u${code.toString(16).uppercase(Locale.ROOT).padStart(4, '0')}")
        }
    }
}
