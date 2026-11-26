package com.union.campus

import com.union.campus.data.remote.xxt.XxtClient
import com.union.campus.data.remote.xxt.XxtCookieJar
import com.union.campus.data.remote.xxt.XxtEndpoints
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * 端到端链路回归：本机起一个极简 HTTP 服务，按学习通真实响应形状回包，
 * 让真实 OkHttp 客户端走完「登录 → 会话 → 课程 → 作业列表 → 详情 → 通知」。
 * 不依赖账号，也不替代对真实服务器的登录验证（见 docs/verification-v0.2.52.md）。
 */
class XxtFlowTest {

    private lateinit var server: MiniServer

    private val courseHtml = """
        <div class="course">
          <a href="https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=2001&amp;clazzid=3001&amp;cpi=4001">
            <span class="course-name" title="高等数学">高等数学</span>
            <p class="overHidden2">张三</p>
          </a>
        </div>
    """.trimIndent()

    /** 列表条目的 data 属性就是详情直链，必须带上 mock 服务的端口。 */
    private fun workHtml(port: Int) = """
        <div><span>1/2</span></div>
        <ul>
          <li class="clearfix" goTask="true" data="http://127.0.0.1:$port/work/detail?workId=999">
            <p class="overHidden2">第三章作业</p><p class="status">未提交</p>
          </li>
          <li class="clearfix" goTask="true" data="http://127.0.0.1:$port/work/detail?workId=1000">
            <p class="overHidden2">第二章作业</p><p class="status">已提交</p>
          </li>
        </ul>
    """.trimIndent()

    private val detailHtml = """
        <div>作业详情<h1>第三章作业</h1>
        <p>题量：5</p><p>满分：100</p>
        <p>作答时间：11-20 08:00 至 11-28 23:59</p></div>
    """.trimIndent()

    private val noticeJson = """
        {"status":true,"notices":{"lastGetId":"9","lastPage":true,"list":[
          {"id":"n1","title":"期末考试安排","content":"<p>本周五确认考场</p>",
           "createrName":"教务处","completeTime":"1795000000000","isread":false}
        ]}}
    """.trimIndent()

    @Before
    fun setUp() {
        server = MiniServer { method, path ->
            when {
                method == "POST" && path == "/fanyalogin" -> 200 to """{"status":true,"msg2":"登录成功"}"""
                path == "/base" -> 200 to "<html><title>个人空间</title><script>var fid = '427';</script></html>"
                path.startsWith("/courselistdata") -> 200 to courseHtml
                path.startsWith("/stucoursemiddle") -> 200 to
                    """<input type="hidden" id="enc" value="E1"><input type="hidden" id="openc" value="O1">""" +
                        """<input type="hidden" id="workEnc" value="W1">"""
                path.startsWith("/mooc2/work/list") -> 200 to workHtml(server.port)
                path.startsWith("/work/detail") -> 200 to detailHtml
                method == "POST" && path == "/notice" -> 200 to noticeJson
                else -> 404 to "not found"
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun endpoints() = XxtEndpoints(
        login = "http://127.0.0.1:${server.port}/fanyalogin",
        base = "http://127.0.0.1:${server.port}/base",
        courseList = "http://127.0.0.1:${server.port}/courselistdata",
        courseMiddle = "http://127.0.0.1:${server.port}/stucoursemiddle",
        workList = "http://127.0.0.1:${server.port}/mooc2/work/list",
        notices = "http://127.0.0.1:${server.port}/notice",
        noticeReferer = "http://127.0.0.1:${server.port}/notice",
    )

    @Test
    fun walksLoginCoursesHomeworkDetailAndNotices() = runBlocking {
        val jar = XxtCookieJar()
        val client = XxtClient(jar, endpoints())

        client.login("202400000001", "not-a-real-password")
        assertTrue("登录后应有会话 cookie", jar.export().contains("JSESSIONID"))
        assertTrue("会话探测应判定为已登录", client.checkLogin())

        val courses = client.fetchCourses()
        assertEquals(1, courses.size)
        assertEquals("高等数学", courses[0].name)
        assertEquals("张三", courses[0].teacher)

        val page = client.fetchHomeworkList(courses[0])
        assertEquals(2, page.items.size)
        assertEquals("第三章作业", page.items[0].title)
        assertEquals("未提交", page.items[0].status)
        assertTrue(page.items[0].url.contains("workId=999"))

        val detail = client.fetchHomeworkDetail(page.items[0].url)
        assertEquals(5, detail.questionCount)
        assertEquals("11-20 08:00", detail.startRaw)
        assertEquals("11-28 23:59", detail.deadlineRaw)

        val notices = client.fetchNotices()
        assertEquals(1, notices.size)
        assertEquals("期末考试安排", notices[0].title)
        assertEquals("本周五确认考场", notices[0].content)
        assertEquals(false, notices[0].read)
    }

    /** 极简 HTTP/1.1 服务：顺序处理、每请求一连接、固定 Set-Cookie。 */
    private class MiniServer(private val routes: (String, String) -> Pair<Int, String>) : AutoCloseable {
        private val socket = ServerSocket(0)
        val port: Int get() = socket.localPort
        private var worker: Thread? = null

        fun start() {
            worker = thread(isDaemon = true, name = "mini-http") { loop() }
        }

        private fun loop() {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { conn ->
                        val reader = conn.getInputStream().bufferedReader()
                        val requestLine = reader.readLine() ?: return@use
                        val parts = requestLine.split(' ')
                        val method = parts.getOrElse(0) { "" }
                        val rawPath = parts.getOrElse(1) { "/" }
                        val path = rawPath.substringBefore('?')
                        var length = 0
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                                length = line.substringAfter(':').trim().toIntOrNull() ?: 0
                            }
                        }
                        if (length > 0) {
                            val buffer = CharArray(length)
                            var read = 0
                            while (read < length) {
                                val n = reader.read(buffer, read, length - read)
                                if (n < 0) break
                                read += n
                            }
                        }
                        val (code, body) = routes(method, path)
                        val bytes = body.toByteArray(Charsets.UTF_8)
                        val head = "HTTP/1.1 $code OK\r\n" +
                            "Content-Type: text/html; charset=utf-8\r\n" +
                            "Set-Cookie: JSESSIONID=test-session; Path=/\r\n" +
                            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                        conn.getOutputStream().apply {
                            write(head.toByteArray(Charsets.UTF_8))
                            write(bytes)
                            flush()
                        }
                    }
                } catch (_: Exception) {
                    if (socket.isClosed) return
                }
            }
        }

        override fun close() {
            socket.close()
            worker?.join(500)
        }
    }
}
