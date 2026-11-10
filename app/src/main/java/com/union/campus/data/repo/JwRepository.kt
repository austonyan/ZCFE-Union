package com.union.campus.data.repo

import com.union.campus.core.AppError
import com.union.campus.data.remote.jw.JwClient
import com.union.campus.data.store.CacheTtl
import com.union.campus.data.store.CredentialStore
import com.union.campus.data.store.EntityStore
import com.union.campus.data.store.SettingsStore
import com.union.campus.domain.CourseCell
import com.union.campus.domain.DayCourses
import com.union.campus.domain.EvaluationItem
import com.union.campus.domain.Exam
import com.union.campus.domain.ExamsData
import com.union.campus.domain.FreeClassroom
import com.union.campus.domain.Grade
import com.union.campus.domain.GradesData
import com.union.campus.domain.JwNotice
import com.union.campus.domain.SectionTime
import com.union.campus.domain.Semester
import com.union.campus.domain.SemesterCalc
import com.union.campus.domain.StudentRecord
import com.union.campus.domain.TeachingBuilding
import com.union.campus.domain.TextbookItem
import com.union.campus.domain.TimetableData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 教务仓库：CAS 会话管理 + H5 读接口 + 缓存。
 * 教务在校园网内才可达；本仓库对不可达情形返回明确错误，UI 呈现对应空态。
 */
class JwRepository constructor(
    private val client: JwClient,
    private val store: EntityStore,
    private val credentials: CredentialStore,
    private val settings: SettingsStore,
) {
    private val parse = Parse

    private val _sessionState = MutableStateFlow(SessionState.UNKNOWN)
    val sessionState: StateFlow<SessionState> = _sessionState

    enum class SessionState { UNKNOWN, VALID, EXPIRED, NOT_BOUND }

    private var connected: Boolean = false

    fun isBound(): Boolean = connected && credentials.load().jwStudentNo.isNotBlank()

    fun maskedStudentNo(): String {
        val no = credentials.load().jwStudentNo
        return if (no.length > 4) no.take(4) + "****" else no.ifBlank { "未绑定" }
    }

    /** 原始学号：仅用于绑定电费时带入，不展示。 */
    fun studentNo(): String = credentials.load().jwStudentNo

    fun restoreSession() {
        val cred = credentials.load()
        if (cred.jwCookie.isNotBlank()) {
            client.cookieJar.restore(
                listOf(
                    okhttp3.Cookie.Builder()
                        .name("JSESSIONID")
                        .value(cred.jwCookie)
                        .domain("jw.zzife.edu.cn")
                        .build(),
                ),
            )
            client.studentNo = cred.jwStudentNo
            connected = cred.jwStudentNo.isNotBlank()
            _sessionState.value = if (connected) SessionState.UNKNOWN else SessionState.NOT_BOUND
        } else {
            _sessionState.value = SessionState.NOT_BOUND
        }
    }

    /** 拉取 CAS 图形验证码（失败时由调用方提示）。 */
    suspend fun captchaImage(studentNo: String): ByteArray = client.fetchCaptchaImage(studentNo)

    /** CAS 登录：成功后保存 Cookie + 学号。 */
    suspend fun login(studentNo: String, password: String, captcha: String = "") {
        client.login(studentNo, password, captcha)
        // authserver 与 jw 都可能写 JSESSIONID；持久化教务域上的那个
        val cookie = client.cookieJar.snapshot().firstOrNull {
            it.name == "JSESSIONID" && it.domain.endsWith("jw.zzife.edu.cn")
        } ?: client.cookieJar.snapshot().firstOrNull { it.name == "JSESSIONID" }
        val cred = credentials.load().copy(
            jwStudentNo = studentNo,
            jwCookie = cookie?.value ?: "",
            jwPassword = password,
        )
        credentials.save(cred)
        client.studentNo = studentNo
        connected = true
        _sessionState.value = SessionState.VALID
        // 换账号后不能复用上一个账号的课表/成绩/学籍缓存
        store.removeByPrefix("jw.")
        // 用教务返回的“当前学期”校正本地学期，避免猜成 20261（未来学期）导致课表为空
        runCatching { syncCurrentSemester() }
    }

    suspend fun logout() {
        credentials.save(credentials.load().copy(jwStudentNo = "", jwCookie = "", jwPassword = ""))
        client.cookieJar.clear()
        store.removeByPrefix("jw.")
        settings.setCurrentSemesterDm("")
        connected = false
        _sessionState.value = SessionState.NOT_BOUND
    }

    /** 会话保活/探测：getxnxq_xl。 */
    suspend fun probe(): Boolean = try {
        semesterList(forceRefresh = true)
        true
    } catch (_: AppError) {
        false
    }

    /** 原始 H5 调用（选课/评教等未封装接口使用）。 */
    suspend fun rawCall(action: String, params: Map<String, String> = emptyMap()): kotlinx.serialization.json.JsonElement =
        client.h5Call(action, params)

    // ---------------- 基础字典 ----------------

    suspend fun semesterList(forceRefresh: Boolean = false): List<Semester> {
        val cacheKey = "jw.semesters"
        if (!forceRefresh) {
            val cached = store.read(cacheKey, ListSerializer(Semester.serializer()))
            if (cached != null && fresh(cached.updatedAt, CacheTtl.CALENDAR)) {
                return cached.data
            }
        }
        val element = client.h5Call("getxnxq_xl")
        val semesters = parseSemesters(element)
        store.write(cacheKey, ListSerializer(Semester.serializer()), semesters)
        return semesters
    }

    private fun parseSemesters(element: JsonElement): List<Semester> {
        val arr = parse.array(element) ?: return emptyList()
        return parse.objects(arr).mapNotNull { obj ->
            val dm = parse.str(obj, "dm", "xnxq", "xnxqdm")
            val mc = parse.str(obj, "mc", "xnxqmc", "name").ifBlank {
                if (dm.length >= 5) "${dm.take(4)}-${dm.drop(4).toIntOrNull() ?: 0}学期" else dm
            }
            if (dm.isBlank()) null else Semester(dm, mc, parse.str(obj, "dqxq") == "1")
        }.distinctBy { it.dm }
    }

    /** 当前学期代码：优先本地保存，否则从教务学期列表里取 dqxq=1 的学期。 */
    suspend fun currentSemesterDm(): String {
        val saved = settings.currentSemesterDm.first()
        if (saved.isNotBlank()) return saved
        if (!isBound()) return SemesterCalc.guessSemesterDm()
        return syncCurrentSemester()
    }

    /** 从教务学期列表同步“当前学期”，写入本地设置。 */
    suspend fun syncCurrentSemester(): String {
        val fallback = SemesterCalc.guessSemesterDm()
        val current = try {
            val list = semesterList(forceRefresh = true)
            list.firstOrNull { it.isCurrent }?.dm
                ?: list.firstOrNull { it.dm == fallback }?.dm
                ?: list.firstOrNull()?.dm
                ?: fallback
        } catch (_: AppError) {
            // 网络/会话异常时不要写入猜测值，下次进入再尝试同步
            return fallback
        }
        settings.setCurrentSemesterDm(current)
        return current
    }

    suspend fun sectionTimes(): List<SectionTime> {
        val cacheKey = "jw.sectiontimes"
        val cached = store.read(cacheKey, ListSerializer(SectionTime.serializer()))
        if (cached != null) return cached.data
        val element = try {
            client.h5Call("getZxsj")
        } catch (_: AppError) {
            return defaultSectionTimes()
        }
        val arr = parse.array(element)
        val times = parse.objects(arr).mapIndexedNotNull { i, obj ->
            val start = parse.str(obj, "kssj", "start", "sj1", "d1")
            val end = parse.str(obj, "jssj", "end", "sj2", "d2")
            val no = parse.int(obj, "jc", "jcxx", "no", "id").takeIf { it > 0 } ?: (i + 1)
            if (start.isBlank()) null else SectionTime(no, start, end)
        }
        val result = times.ifEmpty { defaultSectionTimes() }
        store.write(cacheKey, ListSerializer(SectionTime.serializer()), result)
        return result
    }

    private fun defaultSectionTimes(): List<SectionTime> = listOf(
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

    // ---------------- 课表 ----------------

    suspend fun timetable(semesterDm: String, week: Int, forceRefresh: Boolean = false): TimetableData {
        val sem = semesterDm.ifBlank { currentSemesterDm() }
        val explicitWeek = week > 0
        val cacheKey = "jw.timetable.$sem.w$week"
        if (explicitWeek && !forceRefresh) {
            store.read(cacheKey, TimetableData.serializer())?.let { return it.data }
        }
        val params = buildMap {
            put("xnxq", sem)
            if (explicitWeek) put("week", week.toString())
        }
        val element = client.h5Call("mycourseschedule", params)
        val semesterName = runCatching {
            semesterList().firstOrNull { it.dm == sem }?.name
        }.getOrNull().orEmpty()
        val data = parseTimetableData(element, sem, if (explicitWeek) week else 0, semesterName)
        if (data.week > 0) {
            store.write("jw.timetable.$sem.w${data.week}", TimetableData.serializer(), data)
        }
        saveWeekStart(data)
        return data
    }

    /** 用教务返回的“本周周一”反推第一周周一，供本地周次计算使用。 */
    private suspend fun saveWeekStart(data: TimetableData) {
        if (data.weekStart.isBlank() || data.week <= 0) return
        val start = firstWeekStart(data.weekStart, data.week) ?: return
        settings.setFirstWeekStart(start)
    }

    private fun firstWeekStart(weekStart: String, week: Int): Long? {
        val date = runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(weekStart)
        }.getOrNull() ?: return null
        val cal = Calendar.getInstance()
        cal.time = date
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_MONTH, -7 * (week - 1))
        return cal.timeInMillis
    }


    // ---------------- 成绩 ----------------

    suspend fun grades(semesterDm: String, effective: Boolean, forceRefresh: Boolean = false): GradesData {
        val cacheKey = "jw.grades.$semesterDm.${if (effective) 1 else 0}"
        if (!forceRefresh) {
            store.read(cacheKey, GradesData.serializer())?.let { return it.data }
        }
        val element = client.h5Call(
            "MyCourseScore",
            mapOf("xnxq" to semesterDm, "type" to if (effective) "1" else "0"),
        )
        val arr = parse.array(element)
        val list = parse.objects(arr).map { g ->
            Grade(
                courseCode = parse.str(g, "kcdm", "courseCode"),
                courseName = parse.str(g, "kcmc", "courseName").ifBlank { "未知课程" },
                credits = parse.str(g, "xf", "credit", "学分"),
                score = parse.str(g, "kscj", "cj", "score", "zpj"),
                gpa = parse.str(g, "jd", "jidian", "gpa"),
                category = parse.str(g, "kclb", "type", "kcxz"),
            )
        }
        val data = GradesData(semesterDm, effective, list, now())
        store.write(cacheKey, GradesData.serializer(), data)
        return data
    }

    // ---------------- 考试 ----------------

    suspend fun exams(semesterDm: String, forceRefresh: Boolean = false): ExamsData {
        val cacheKey = "jw.exams.$semesterDm"
        if (!forceRefresh) {
            store.read(cacheKey, ExamsData.serializer())?.let { return it.data }
        }
        val element = client.h5Call("getAllExam", mapOf("xnxq" to semesterDm))
        val arr = parse.array(element)
        val list = parse.objects(arr).mapIndexed { i, e ->
            Exam(
                id = parse.str(e, "ksapid", "id").ifBlank { "exam_$i" },
                courseName = parse.str(e, "kcmc", "course").ifBlank { "未知课程" },
                time = parse.str(e, "kssj", "sjsj", "time").ifBlank { "时间待定" },
                location = parse.str(e, "ksdd", "cdmc", "room").ifBlank { "以教务通知为准" },
                seatNo = parse.str(e, "zwh", "seat", "zwh_"),
                note = parse.str(e, "bz", "ksxz", "note"),
            )
        }
        val data = ExamsData(semesterDm, list, now())
        store.write(cacheKey, ExamsData.serializer(), data)
        return data
    }

    // ---------------- 评教 ----------------

    suspend fun pendingEvaluations(): List<EvaluationItem> {
        val element = client.h5Call("getJxpj_wjs")
        val arr = parse.array(element)
        return parse.objects(arr).mapIndexed { i, e ->
            EvaluationItem(
                hideKey = parse.str(e, "hidekey", "id"),
                courseName = parse.str(e, "kcmc", "course").ifBlank { "课程 $i" },
                teacher = parse.str(e, "jsmc", "rkjs", "teacher"),
                round = parse.str(e, "pjlcmc", "pjlcdm", "round"),
                xnxq = parse.str(e, "xnxq", "xnxqdm"),
            )
        }
    }

    suspend fun evaluationQuestions(hideKey: String, xnxq: String): JsonElement =
        client.h5Call(
            "getJxpjnr",
            mapOf("hidekey" to hideKey, "xnxq" to xnxq),
        )

    suspend fun submitEvaluation(payload: Map<String, String>) {
        // 写接口：不自动重试；结果以教务返回为准
        client.h5Call("submitJxpj", payload)
    }

    // ---------------- 教材 ----------------

    suspend fun textbooks(): List<TextbookItem> {
        val element = client.h5Call("getQrxyjc")
        val arr = parse.array(element)
        return parse.objects(arr).mapIndexed { i, t ->
            val confirmed = parse.str(t, "qrzt", "status", "zt").let {
                it.contains("已") || it == "1" || it == "2"
            }
            TextbookItem(
                id = parse.str(t, "id", "jcid").ifBlank { "tb_$i" },
                courseName = parse.str(t, "kcmc", "course").ifBlank { "课程 $i" },
                bookName = parse.str(t, "jcmc", "jcsm", "book").ifBlank { "教材 $i" },
                count = parse.str(t, "sl", "count", "num").ifBlank { "1" },
                price = parse.str(t, "je", "price", "dj"),
                confirmed = confirmed,
            )
        }
    }

    suspend fun confirmTextbook(xnxq: String, jcId: String) {
        client.h5Call("doQrxyjc", mapOf("xnxq" to xnxq, "jc" to jcId))
    }

    // ---------------- 学籍 ----------------

    suspend fun studentRecord(): StudentRecord {
        val cacheKey = "jw.record"
        store.read(cacheKey, StudentRecord.serializer())?.let { return it.data }
        val loginId = credentials.load().jwStudentNo.ifBlank { client.studentNo }
        // 教务要求 loginId；只传 other=1 会返回空 resultSet
        val element = client.h5Call("MyXjda", mapOf("loginId" to loginId))
        val record = parseStudentRecordData(element, loginId)
        store.write(cacheKey, StudentRecord.serializer(), record)
        return record
    }

    // ---------------- 通知 ----------------

    suspend fun notices(): List<JwNotice> {
        val cacheKey = "jw.notices.v2"
        store.read(cacheKey, ListSerializer(JwNotice.serializer()))?.let { return it.data }
        val news = try {
            val element = client.h5Call("edunews")
            parse.objects(parse.array(element)).mapIndexed { i, n ->
                JwNotice(
                    id = "news_$i",
                    title = parse.str(n, "title", "newstitle", "bt").ifBlank { "教务公告 $i" },
                    date = parse.str(n, "time", "createtime", "rq", "fbsj"),
                    source = parse.str(n, "source", "fbr", "dept").ifBlank { "教务科" },
                    kind = "news",
                    body = parse.str(n, "content", "nr", "body"),
                )
            }
        } catch (_: AppError) {
            emptyList()
        }
        val changes = try {
            val element = client.h5Call("MyTkXx")
            parse.objects(parse.array(element)).mapIndexed { i, n ->
                val kc = parse.str(n, "kc", "kcmc")
                val tklx = parse.str(n, "tklx", "lx").ifBlank { "调课" }
                val details = parse.objects(n["tknr"] as? kotlinx.serialization.json.JsonArray)
                    .mapNotNull { d ->
                        val item = parse.str(d, "tkxm", "xm")
                        val from = parse.str(d, "tkq", "q")
                        val to = parse.str(d, "tkh", "h")
                        if (item.isBlank()) null
                        else "$item: $from" + if (to.isNotBlank() && to != from) " → $to" else ""
                    }
                val body = buildString {
                    val campus = parse.str(n, "skbjgc")
                    if (campus.isNotBlank()) appendLine(campus)
                    val bz = parse.str(n, "bz", "tkyy")
                    if (bz.isNotBlank()) appendLine(bz)
                    append(details.joinToString("\n"))
                }.trim()
                JwNotice(
                    id = "change_$i",
                    title = if (kc.isBlank()) "${tklx}通知" else "$kc · $tklx",
                    date = parse.str(n, "tksj", "tkrq", "time", "createtime", "rq"),
                    source = "调课",
                    kind = "change",
                    body = body,
                )
            }
        } catch (_: AppError) {
            emptyList()
        }
        val result = (news + changes).take(20)
        if (result.isNotEmpty()) {
            store.write(cacheKey, ListSerializer(JwNotice.serializer()), result)
        }
        return result
    }

    // ---------------- 空闲教室 ----------------

    suspend fun teachingBuildings(): List<TeachingBuilding> {
        val element = client.h5Call("GetJxlbyXQ", mapOf("flag" to "1"))
        return parseTeachingBuildings(element)
    }

    /**
     * 空闲教室。
     * `jxl` 必须是楼栋代码（教学楼接口返回的 dm，如 11），传楼栋名称会返回空响应。
     * 响应结构：`{"result":{"zdjc":"10","jsxx":[{jsmc,jsrl,jslxmc,sfkx}]}}`。
     */
    suspend fun freeClassrooms(date: String, buildingCode: String): List<FreeClassroom> {
        if (buildingCode.isBlank()) return emptyList()
        val element = try {
            client.h5Call("getJsxx", mapOf("rq" to date, "jxl" to buildingCode))
        } catch (e: AppError.Upstream) {
            // 部分楼栋/日期上游返回空 body，视为“没有空闲教室”而不是协议错误
            if (e.debugDetail?.contains("空响应") == true) return emptyList()
            throw e
        }
        return parseFreeClassrooms(element)
    }

    suspend fun clearModuleCache() {
        store.removeByPrefix("jw.")
    }

    private fun String.maskId(): String = if (length >= 10) take(4) + "**********" + takeLast(2) else "已脱敏"

    private fun fresh(updatedAt: Long, ttl: Long) = System.currentTimeMillis() - updatedAt < ttl

    private fun now() = System.currentTimeMillis()
}

/**
 * 学籍档案解析。
 * `MyXjda` 的响应可能是标准 JSON，也可能是单引号对象（调用方已归一化）。
 * 关键字段：yhxh/xm/yx/zy/ssbj/rxnj/zxzt/sfzjh。
 */
internal fun parseStudentRecordData(element: JsonElement, fallbackNo: String): StudentRecord {
    val root = element as? JsonObject
    val first = Parse.array(element)?.firstOrNull() as? JsonObject
    val obj = when {
        root != null && (root.containsKey("xm") || root.containsKey("yhxh") || root.containsKey("xh")) -> root
        first != null -> first
        else -> root ?: JsonObject(emptyMap())
    }
    return StudentRecord(
        name = Parse.str(obj, "xm", "name"),
        studentNo = Parse.str(obj, "yhxh", "xh", "xhdm", "loginid").ifBlank { fallbackNo },
        college = stripSchoolCode(Parse.str(obj, "yx", "yxmc", "college")),
        major = stripSchoolCode(Parse.str(obj, "zy", "lqzy", "zymc", "major")),
        className = Parse.str(obj, "ssbj", "bjmc", "bj", "className"),
        grade = Parse.str(obj, "rxnj", "nj", "grade"),
        status = Parse.str(obj, "zxzt", "xjzt", "status").ifBlank { "在读" },
        idCardMasked = maskId(Parse.str(obj, "sfzjh", "sfzh", "sfz", "idcard")),
        raw = flattenJson(obj),
    )
}

/** 去掉教务字段里的 [10402] 前缀。 */
private fun stripSchoolCode(raw: String): String = raw.replace(Regex("^\\[[^]]*]\\s*"), "")

private fun maskId(raw: String): String = when {
    raw.isBlank() -> "已脱敏"
    raw.length >= 10 -> raw.take(4) + "**********" + raw.takeLast(2)
    else -> raw
}

private fun flattenJson(obj: JsonObject): Map<String, String> {
    val out = mutableMapOf<String, String>()
    obj.forEach { (k, v) ->
        when (v) {
            is JsonPrimitive -> out[k] = v.content
            is JsonObject -> v.forEach { (k2, v2) ->
                (v2 as? JsonPrimitive)?.let { out["$k.$k2"] = it.content }
            }
            else -> Unit
        }
    }
    return out
}

/**
 * 课表解析。
 * 未指定 week 时教务返回当前周（`zc`）与该周周一（`qssj`），
 * 用它校正“第几周”，避免本地按 9 月 1 日估算导致周次错误。
 */
internal fun parseTimetableData(
    element: JsonElement,
    semesterDm: String,
    requestedWeek: Int,
    semesterName: String = "",
): TimetableData {
    val obj = element as? JsonObject
        ?: return TimetableData(
            semesterDm = semesterDm,
            week = requestedWeek.coerceAtLeast(1),
            days = emptyList(),
            fetchedAt = System.currentTimeMillis(),
            semesterName = semesterName,
        )
    var idCounter = 0
    val days = (1..7).map { day ->
        val arr = obj["week$day"] as? kotlinx.serialization.json.JsonArray
        val cells = Parse.objects(arr).map { c ->
            val name = Parse.str(c, "kcmc", "kcmc__mc", "course")
            val jcxx = Parse.str(c, "jcxx", "jcdm", "jc")
            val (start, end) = parseSections(jcxx)
            idCounter++
            CourseCell(
                id = "d${day}_$idCounter",
                name = name.ifBlank { "未命名课程" },
                teacher = Parse.str(c, "rkjs", "jsmc", "teacher").ifBlank { "未安排" },
                location = Parse.str(c, "skdd", "cdmc", "room").ifBlank { "未安排" },
                day = day,
                sections = jcxx.ifBlank { "未安排" },
                startSection = start,
                endSection = end,
                weeks = Parse.str(c, "skzs", "zcd", "weeks"),
                note = Parse.str(c, "bz", "note"),
            )
        }.sortedBy { it.startSection }
        DayCourses(day, cells)
    }
    val serverWeek = Parse.int(obj, "zc").takeIf { it > 0 } ?: requestedWeek.coerceAtLeast(1)
    val maxWeek = Parse.int(obj, "maxzc").takeIf { it > 0 } ?: 25
    return TimetableData(
        semesterDm = semesterDm,
        week = serverWeek,
        days = days,
        fetchedAt = System.currentTimeMillis(),
        maxWeek = maxWeek,
        weekStart = Parse.str(obj, "qssj"),
        semesterName = semesterName,
    )
}

private fun parseSections(jcxx: String): Pair<Int, Int> {
    val nums = Regex("\\d+").findAll(jcxx).toList()
    val start = nums.firstOrNull()?.value?.toIntOrNull() ?: 1
    val end = nums.getOrNull(1)?.value?.toIntOrNull() ?: start
    return start to end
}

/** 教学楼列表：`{"resultSet":[{dm,mc,xqmc}]}`。 */
internal fun parseTeachingBuildings(element: JsonElement): List<TeachingBuilding> =
    Parse.objects(Parse.array(element)).mapNotNull { b ->
        val dm = Parse.str(b, "dm", "id")
        val name = Parse.str(b, "mc", "jxlmc", "name")
        if (dm.isBlank() || name.isBlank()) null
        else TeachingBuilding(dm, name, Parse.str(b, "xqmc", "campus"))
    }.distinctBy { it.dm }

/** 空闲教室：`{"result":{"jsxx":[{jsmc,jsrl,jslxmc,sfkx}]}}`。 */
internal fun parseFreeClassrooms(element: JsonElement): List<FreeClassroom> =
    Parse.objects(Parse.array(element)).mapNotNull { r ->
        val name = Parse.str(r, "jsmc", "jsdm", "room", "name")
        if (name.isBlank()) null
        else FreeClassroom(
            name = name,
            type = Parse.str(r, "jslxmc", "jslxdm"),
            capacity = Parse.str(r, "jsrl", "rl"),
            freeSections = Parse.str(r, "sfkx"),
        )
    }
