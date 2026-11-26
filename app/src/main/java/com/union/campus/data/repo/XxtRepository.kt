package com.union.campus.data.repo

import com.union.campus.core.AppError
import com.union.campus.data.remote.xxt.XxtClient
import com.union.campus.data.store.CacheTtl
import com.union.campus.data.store.CredentialStore
import com.union.campus.data.store.EntityStore
import com.union.campus.data.store.SettingsStore
import com.union.campus.domain.XxtHomework
import com.union.campus.domain.XxtHomeworkRules
import com.union.campus.domain.XxtHomeworkSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.util.concurrent.TimeUnit

/**
 * 学习通仓库：会话恢复、课程与作业同步、缓存、提醒去重日志。
 *
 * 同步策略与 xxt-assistant 一致：课程列表与作业列表每轮都拉（列表很轻），
 * 详情页只为“未完成且没读到截止时间”的作业补，每次最多 8 条、失败不中断整轮。
 */
class XxtRepository constructor(
    private val client: XxtClient,
    private val store: EntityStore,
    private val credentials: CredentialStore,
    private val settings: SettingsStore,
) {

    private val _sessionState = MutableStateFlow(SessionState.UNKNOWN)
    val sessionState: StateFlow<SessionState> = _sessionState

    enum class SessionState { UNKNOWN, VALID, EXPIRED, NOT_BOUND }

    private val jsonMap = MapSerializer(String.serializer(), Long.serializer())

    companion object {
        private const val KEY_SNAPSHOT = "xxt.homework"
        private const val KEY_JOURNAL = "xxt.reminder.journal"
        private const val MAX_DETAIL_PER_SYNC = 8
        private val DETAIL_REFRESH_MS = TimeUnit.HOURS.toMillis(6)
        private val JOURNAL_KEEP_MS = TimeUnit.DAYS.toMillis(14)
    }

    // ---------------------------------------------------------------- 账号与会话

    fun isBound(): Boolean = credentials.load().xxtAccount.isNotBlank()

    fun maskedAccount(): String {
        val account = credentials.load().xxtAccount
        return when {
            account.isBlank() -> "未绑定"
            account.length >= 7 -> account.take(3) + "****" + account.takeLast(4)
            else -> account
        }
    }

    /** 冷启动恢复 cookie；cookie 失效时不主动登录，等用户操作或后台同步再处理。 */
    fun restoreSession() {
        val cred = credentials.load()
        if (cred.xxtAccount.isBlank()) {
            _sessionState.value = SessionState.NOT_BOUND
            return
        }
        if (cred.xxtCookies.isNotBlank()) client.restoreCookies(cred.xxtCookies)
        _sessionState.value = SessionState.UNKNOWN
    }

    /**
     * 登录并保存会话。remember=true 时保存密码（Keystore 加密），否则只留账号。
     */
    suspend fun login(account: String, password: String, remember: Boolean) {
        val name = account.trim()
        if (name.isBlank() || password.isBlank()) {
            throw AppError.Validation("请填写学习通账号与密码")
        }
        client.login(name, password)
        client.checkLogin()
        val cred = credentials.load().copy(
            xxtAccount = name,
            xxtPassword = if (remember) password else "",
            xxtCookies = client.exportCookies(),
        )
        credentials.save(cred)
        store.remove(KEY_SNAPSHOT)
        _sessionState.value = SessionState.VALID
    }

    fun logout() {
        client.clearSession()
        val cred = credentials.load().copy(xxtAccount = "", xxtPassword = "", xxtCookies = "")
        credentials.save(cred)
        store.remove(KEY_SNAPSHOT)
        store.remove(KEY_JOURNAL)
        _sessionState.value = SessionState.NOT_BOUND
    }

    private fun persistSession() {
        runCatching {
            val cred = credentials.load()
            credentials.save(cred.copy(xxtCookies = client.exportCookies()))
        }
    }

    private suspend fun ensureLogin() {
        val cred = credentials.load()
        if (cred.xxtAccount.isBlank()) {
            _sessionState.value = SessionState.NOT_BOUND
            throw AppError.AuthExpired(AppError.Module.XXT, "还没有绑定学习通账号")
        }
        if (cred.xxtCookies.isNotBlank() && client.cookieJar.isEmpty()) {
            client.restoreCookies(cred.xxtCookies)
        }
        if (client.checkLogin()) {
            _sessionState.value = SessionState.VALID
            return
        }
        if (cred.xxtPassword.isBlank()) {
            _sessionState.value = SessionState.EXPIRED
            throw AppError.AuthExpired(AppError.Module.XXT, "会话已失效，请重新登录学习通")
        }
        client.login(cred.xxtAccount, cred.xxtPassword)
        persistSession()
        _sessionState.value = SessionState.VALID
    }

    // ---------------------------------------------------------------- 缓存与同步

    fun cached(): XxtHomeworkSnapshot? = store.read(KEY_SNAPSHOT, XxtHomeworkSnapshot.serializer())?.data

    /** 有缓存先用缓存（页面首次进入不空窗）；refresh=true 强制拉取。 */
    suspend fun snapshot(refresh: Boolean = false): XxtHomeworkSnapshot {
        val entry = store.read(KEY_SNAPSHOT, XxtHomeworkSnapshot.serializer())
        if (!refresh && entry != null &&
            System.currentTimeMillis() - entry.updatedAt < CacheTtl.XXT_HOMEWORK
        ) {
            return entry.data
        }
        return sync()
    }

    suspend fun sync(onProgress: (String) -> Unit = {}): XxtHomeworkSnapshot {
        ensureLogin()
        val now = System.currentTimeMillis()
        val previous = cached()
        val prevItems = previous?.items.orEmpty().associateBy { it.key }
        val errors = mutableListOf<String>()

        onProgress("正在获取课程列表…")
        val courses = client.fetchCourses()
        if (courses.isEmpty()) {
            errors += "没有拉取到课程，请确认账号是否已加入课程"
        }

        val items = mutableListOf<XxtHomework>()
        courses.forEachIndexed { index, course ->
            onProgress("正在同步 ${index + 1}/${courses.size} · ${course.name}")
            val page = try {
                client.fetchHomeworkList(course)
            } catch (e: AppError) {
                errors += "${course.name}：${e.userMessage}"
                return@forEachIndexed
            } catch (e: Exception) {
                errors += "${course.name}：同步失败"
                return@forEachIndexed
            }
            page.items.forEach { work ->
                val key = XxtHomeworkRules.assignmentKey(
                    course.courseId, course.clazzId, work.url, work.title,
                )
                val prev = prevItems[key]
                items += XxtHomework(
                    key = key,
                    courseId = course.courseId,
                    clazzId = course.clazzId,
                    courseName = course.name,
                    title = work.title,
                    status = work.status,
                    url = work.url,
                    done = !XxtHomeworkRules.isPending(work.status),
                    deadlineRaw = prev?.deadlineRaw.orEmpty(),
                    deadlineAt = prev?.deadlineAt ?: 0L,
                    startRaw = prev?.startRaw.orEmpty(),
                    questionCount = prev?.questionCount ?: 0,
                    fullScore = prev?.fullScore.orEmpty(),
                    detailCheckedAt = prev?.detailCheckedAt ?: 0L,
                    firstSeenAt = prev?.firstSeenAt ?: now,
                )
            }
        }

        fillDetails(items, now, onProgress, errors)

        val snapshot = XxtHomeworkSnapshot(
            items = items.sortedWith(
                compareBy({ it.done }, { if (it.deadlineAt == 0L) Long.MAX_VALUE else it.deadlineAt }),
            ),
            syncedAt = System.currentTimeMillis(),
            courseCount = courses.size,
            errors = errors,
        )
        store.write(KEY_SNAPSHOT, XxtHomeworkSnapshot.serializer(), snapshot)
        persistSession()
        seedJournalIfFirstRun(previous, items, now)
        _sessionState.value = SessionState.VALID
        return snapshot
    }

    /** 只补“未完成且没读到时间”的详情，每次最多 8 条；失败不打断整轮。 */
    private suspend fun fillDetails(
        items: MutableList<XxtHomework>,
        now: Long,
        onProgress: (String) -> Unit,
        errors: MutableList<String>,
    ) {
        val targets = items.filter {
            !it.done && (it.deadlineAt == 0L || now - it.detailCheckedAt > DETAIL_REFRESH_MS)
        }.take(MAX_DETAIL_PER_SYNC)
        targets.forEach { item ->
            onProgress("正在补全截止时间 · ${item.title}")
            val detail = try {
                client.fetchHomeworkDetail(item.url)
            } catch (e: AppError) {
                if (e is AppError.Upstream && e.userMessage.contains("验证码")) {
                    errors += e.userMessage
                    return@forEach
                }
                return@forEach
            } catch (e: Exception) {
                return@forEach
            }
            val deadlineAt = XxtHomeworkRules.nearestDeadline(
                detail.deadlineRaw, java.time.LocalDate.now(),
            )
            val updated = item.copy(
                deadlineRaw = detail.deadlineRaw,
                deadlineAt = deadlineAt,
                startRaw = detail.startRaw,
                questionCount = detail.questionCount,
                fullScore = detail.fullScore,
                detailCheckedAt = System.currentTimeMillis(),
            )
            val idx = items.indexOfFirst { it.key == item.key }
            if (idx >= 0) items[idx] = updated
        }
    }

    /** 首次同步不补发“新作业”通知，只把当前条目记进日志。 */
    private suspend fun seedJournalIfFirstRun(
        previous: XxtHomeworkSnapshot?,
        items: List<XxtHomework>,
        now: Long,
    ) {
        if (previous != null) return
        if (store.read(KEY_JOURNAL, jsonMap) != null) return
        val journal = items.associate { "${it.key}|new" to now }
        store.write(KEY_JOURNAL, jsonMap, journal)
    }

    // ---------------------------------------------------------------- 提醒日志

    /** "key|tag" → 发送时间，用于同一档只提醒一次。 */
    fun journal(): Map<String, Long> = store.read(KEY_JOURNAL, jsonMap)?.data.orEmpty()

    fun markReminded(entries: List<Pair<String, String>>) {
        if (entries.isEmpty()) return
        val now = System.currentTimeMillis()
        val journal = journal()
            .filterValues { now - it < JOURNAL_KEEP_MS }
            .toMutableMap()
        entries.forEach { (key, tag) -> journal["$key|$tag"] = now }
        store.write(KEY_JOURNAL, jsonMap, journal)
    }

    suspend fun remindersEnabled(): Boolean = settings.remindXxtHomework.first()

    /** 待办聚合用的最小信息：未完成数量 + 最近一条截止。 */
    fun pendingHomework(): List<XxtHomework> = cached()?.pending.orEmpty()

    /** 未来 48 小时内要交的数量（今天页用）。 */
    fun dueSoonCount(now: Long = System.currentTimeMillis()): Int {
        val limit = now + TimeUnit.HOURS.toMillis(48)
        return pendingHomework().count {
            it.deadlineAt in 1..limit
        }
    }
}
