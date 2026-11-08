package com.union.campus.data.repo

import com.union.campus.core.AppError
import com.union.campus.data.remote.jw.JwClient
import com.union.campus.data.store.CacheTtl
import com.union.campus.data.store.CredentialStore
import com.union.campus.data.store.EntityStore
import com.union.campus.domain.CourseCell
import com.union.campus.domain.DayCourses
import com.union.campus.domain.EvaluationItem
import com.union.campus.domain.Exam
import com.union.campus.domain.ExamsData
import com.union.campus.domain.Grade
import com.union.campus.domain.GradesData
import com.union.campus.domain.JwNotice
import com.union.campus.domain.SectionTime
import com.union.campus.domain.Semester
import com.union.campus.domain.StudentRecord
import com.union.campus.domain.TextbookItem
import com.union.campus.domain.TimetableData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * 教务仓库：CAS 会话管理 + H5 读接口 + 缓存。
 * 教务在校园网内才可达；本仓库对不可达情形返回明确错误，UI 呈现对应空态。
 */
class JwRepository constructor(
    private val client: JwClient,
    private val store: EntityStore,
    private val credentials: CredentialStore,
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
    }

    suspend fun logout() {
        credentials.save(credentials.load().copy(jwStudentNo = "", jwCookie = "", jwPassword = ""))
        client.cookieJar.clear()
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
            if (dm.isBlank()) null else Semester(dm, mc)
        }.distinctBy { it.dm }
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
        SectionTime(1, "08:00", "08:45"),
        SectionTime(2, "08:50", "09:35"),
        SectionTime(3, "10:10", "10:55"),
        SectionTime(4, "11:00", "11:45"),
        SectionTime(5, "14:00", "14:45"),
        SectionTime(6, "14:50", "15:35"),
        SectionTime(7, "16:00", "16:45"),
        SectionTime(8, "16:50", "17:35"),
        SectionTime(9, "19:00", "19:45"),
        SectionTime(10, "19:50", "20:35"),
        SectionTime(11, "20:40", "21:25"),
        SectionTime(12, "21:30", "22:15"),
    )

    // ---------------- 课表 ----------------

    suspend fun timetable(semesterDm: String, week: Int, forceRefresh: Boolean = false): TimetableData {
        val cacheKey = "jw.timetable.$semesterDm.w$week"
        if (!forceRefresh) {
            store.read(cacheKey, TimetableData.serializer())?.let { return it.data }
        }
        val element = client.h5Call(
            "mycourseschedule",
            mapOf("xnxq" to semesterDm, "week" to week.toString()),
        )
        val data = parseTimetable(element, semesterDm, week)
        store.write(cacheKey, TimetableData.serializer(), data)
        return data
    }

    private fun parseTimetable(element: JsonElement, semesterDm: String, week: Int): TimetableData {
        val obj = element as? JsonObject ?: return TimetableData(semesterDm, week, emptyList(), now())
        var idCounter = 0
        val days = (1..7).map { day ->
            val arr = obj["week$day"] as? kotlinx.serialization.json.JsonArray
            val cells = parse.objects(arr).map { c ->
                val name = parse.str(c, "kcmc", "kcmc__mc", "course")
                val jcxx = parse.str(c, "jcxx", "jcdm", "jc")
                val (start, end) = parseSections(jcxx)
                idCounter++
                CourseCell(
                    id = "d${day}_$idCounter",
                    name = name.ifBlank { "未命名课程" },
                    teacher = parse.str(c, "rkjs", "jsmc", "teacher").ifBlank { "未安排" },
                    location = parse.str(c, "skdd", "jsmc", "room").ifBlank { "未安排" },
                    day = day,
                    sections = jcxx.ifBlank { "未安排" },
                    startSection = start,
                    endSection = end,
                    weeks = parse.str(c, "skzs", "zcd", "weeks"),
                    note = parse.str(c, "bz", "note"),
                )
            }.sortedBy { it.startSection }
            DayCourses(day, cells)
        }
        return TimetableData(semesterDm, week, days, now())
    }

    private fun parseSections(jcxx: String): Pair<Int, Int> {
        val nums = Regex("\\d+").findAll(jcxx).toList()
        val start = nums.firstOrNull()?.value?.toIntOrNull() ?: 1
        val end = nums.getOrNull(1)?.value?.toIntOrNull() ?: start
        return start to end
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
        val element = client.h5Call("MyXjda", mapOf("other" to "1"))
        val obj = (element as? JsonObject)
            ?: (parse.array(element)?.firstOrNull() as? JsonObject)
            ?: JsonObject(emptyMap())
        val record = StudentRecord(
            name = parse.str(obj, "xm", "name"),
            studentNo = parse.str(obj, "xh", "xhdm", "loginid").ifBlank { credentials.load().jwStudentNo },
            college = parse.str(obj, "yxmc", "yx", "college"),
            major = parse.str(obj, "zymc", "zy", "major"),
            className = parse.str(obj, "bjmc", "bj", "className"),
            grade = parse.str(obj, "nj", "grade"),
            status = parse.str(obj, "xjzt", "status").ifBlank { "在读" },
            idCardMasked = parse.str(obj, "sfzh", "sfz", "idcard").maskId(),
        )
        store.write(cacheKey, StudentRecord.serializer(), record)
        return record
    }

    // ---------------- 通知 ----------------

    suspend fun notices(): List<JwNotice> {
        val cacheKey = "jw.notices"
        store.read(cacheKey, ListSerializer(JwNotice.serializer()))?.let { return it.data }
        val news = try {
            val element = client.h5Call("edunews")
            parse.objects(parse.array(element)).mapIndexed { i, n ->
                JwNotice(
                    id = "news_$i",
                    title = parse.str(n, "title", "newstitle", "bt").ifBlank { "教务公告 $i" },
                    date = parse.str(n, "time", "createtime", "rq"),
                    source = parse.str(n, "source", "fbr", "dept").ifBlank { "教务科" },
                    kind = "news",
                )
            }
        } catch (_: AppError) {
            emptyList()
        }
        val changes = try {
            val element = client.h5Call("MyTkXx")
            parse.objects(parse.array(element)).mapIndexed { i, n ->
                JwNotice(
                    id = "change_$i",
                    title = parse.str(n, "title", "bt", "xxnr").ifBlank { "调课通知 $i" },
                    date = parse.str(n, "time", "createtime", "rq"),
                    source = "调课",
                    kind = "change",
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

    suspend fun teachingBuildings(): List<String> {
        val element = client.h5Call("GetJxlbyXQ")
        val arr = parse.array(element)
        return parse.objects(arr).mapNotNull { b ->
            parse.str(b, "jxlmc", "mc", "name").takeIf { it.isNotBlank() }
        }
    }

    suspend fun freeClassrooms(date: String, building: String): List<String> {
        val element = client.h5Call("getJsxx", mapOf("rq" to date, "jxl" to building))
        val arr = parse.array(element)
        return parse.objects(arr).mapNotNull { r ->
            val room = parse.str(r, "jsdm", "jsmc", "room", "name")
            room.takeIf { it.isNotBlank() }
        }
    }

    suspend fun clearModuleCache() {
        store.removeByPrefix("jw.")
    }

    private fun String.maskId(): String = if (length >= 10) take(4) + "**********" + takeLast(2) else "已脱敏"

    private fun fresh(updatedAt: Long, ttl: Long) = System.currentTimeMillis() - updatedAt < ttl

    private fun now() = System.currentTimeMillis()
}
