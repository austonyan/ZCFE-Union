package com.union.campus

import com.union.campus.data.repo.XxtParse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 学习通页面解析回归：fixture 结构与网页端实测一致（服务端渲染 + 宽松正则）。 */
class XxtParsingTest {

    private val coursePage = """
        <div class="course-list">
          <a href="https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=2001&amp;clazzid=3001&amp;cpi=4001">
            <span class="course-name" title="高等数学">高等数学</span>
            <p class="overHidden2">张三</p>
          </a>
          <a href="https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=2002&amp;clazzid=3002&amp;cpi=4002">
            <span class="course-name" title="大学英语">大学英语</span>
            <p class="overHidden2">李四</p>
          </a>
          <a href="https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=2001&amp;clazzid=3001&amp;cpi=4001">
            <span class="course-name" title="高等数学">高等数学</span>
          </a>
        </div>
    """.trimIndent()

    @Test
    fun parsesCoursesAndDropsDuplicates() {
        val courses = XxtParse.parseCourses(coursePage)
        assertEquals(2, courses.size)
        assertEquals("高等数学", courses[0].name)
        assertEquals("张三", courses[0].teacher)
        assertEquals("2001", courses[0].courseId)
        assertEquals("3001", courses[0].clazzId)
        assertEquals("4001", courses[0].cpi)
        assertEquals("大学英语", courses[1].name)
    }

    private val workList = """
        <div class="work-panel"><span>1/2</span></div>
        <ul class="work-list">
          <li class="clearfix" goTask="true" data="https%3A%2F%2Fmooc1.chaoxing.com%2Fwork%2FdoHomeWorkNew%3FworkId%3D9001001">
            <div class="clearfix"><p class="overHidden2">第一次作业</p><p class="status">未提交</p></div>
          </li>
          <li class="clearfix" goTask="true" data="https%3A%2F%2Fmooc1.chaoxing.com%2Fwork%2FdoHomeWorkNew%3FworkId%3D9001002">
            <div class="clearfix"><p class="overHidden2">第二次作业</p><p class="status">已提交</p></div>
          </li>
          <li class="clearfix"><p class="overHidden2">不是作业的行</p></li>
        </ul>
    """.trimIndent()

    @Test
    fun parsesWorkListAndKeepsRawStatus() {
        val page = XxtParse.parseHomeworkList(workList)
        assertEquals(1, page.done)
        assertEquals(2, page.total)
        assertEquals(2, page.items.size)
        assertEquals("第一次作业", page.items[0].title)
        assertEquals("未提交", page.items[0].status)
        assertTrue(page.items[0].url.contains("workId=9001001"))
        assertEquals("已提交", page.items[1].status)
    }

    @Test
    fun parsesHiddenSignatureValues() {
        val html = """
            <input type="hidden" id="enc" value="ENC123" />
            <input type="hidden" value="OPEN456" id="openc" />
            <input type='hidden' id='workEnc' value='WORK789' />
        """.trimIndent()
        assertEquals("ENC123", XxtParse.hiddenValue(html, "enc"))
        assertEquals("OPEN456", XxtParse.hiddenValue(html, "openc"))
        assertEquals("WORK789", XxtParse.hiddenValue(html, "workEnc"))
        assertEquals("", XxtParse.hiddenValue(html, "examEnc"))
    }

    @Test
    fun parsesDetailDeadlineFromAnswerRange() {
        val html = """
            <div class="detail">作业详情
              <h1>第三章习题</h1>
              <p>题量：8</p><p>满分：100</p>
              <p>作答时间：11-20 08:00 至 11-28 23:59</p>
            </div>
        """.trimIndent()
        val detail = XxtParse.parseHomeworkDetail(html)
        assertEquals(8, detail.questionCount)
        assertEquals("100", detail.fullScore)
        assertEquals("11-20 08:00", detail.startRaw)
        assertEquals("11-28 23:59", detail.deadlineRaw)
        assertTrue(detail.title.contains("第三章习题"))
    }

    @Test
    fun parsesDeadlineFallbackWhenNoAnswerRange() {
        val html = "<div>作业详情 <h1>单元测验</h1><p>截止时间：12-05 22:00</p></div>"
        val detail = XxtParse.parseHomeworkDetail(html)
        assertEquals("12-05 22:00", detail.deadlineRaw)
        assertEquals("", detail.startRaw)
    }

    @Test
    fun detectsLoginAndAntispiderPages() {
        assertTrue(XxtParse.looksLikeLogin("https://i.chaoxing.com/base", "<title>用户登录</title>"))
        // 移动端 UA 下 base 会跳到 mlogin，只认 /login 会把过期会话误判成已登录
        assertTrue(
            XxtParse.looksLikeLogin(
                "https://passport2.chaoxing.com/mlogin?fid=12&refer=https%3A%2F%2Fi.chaoxing.com%2Fbase",
                "<title>登录</title>",
            ),
        )
        assertTrue(!XxtParse.looksLikeLogin("https://i.chaoxing.com/base", "<title>个人空间</title>"))
        assertTrue(
            XxtParse.isAntispider(
                "https://mooc1.chaoxing.com/antispiderShowVerify",
                "<p>操作异常，请输入图片中的验证码</p>",
            ),
        )
        // 题干里出现“验证码”不该被判成拦截页
        assertTrue(
            !XxtParse.isAntispider(
                "https://mooc1.chaoxing.com/work/view",
                "<h1>作业</h1>" + "题干".repeat(600) + "请输入验证码",
            ),
        )
    }

    private val noticePayload = """
        {"status":true,"notices":{"lastGetId":"100","lastPage":false,"list":[
          {"id":"n1","title":"关于期末考试安排的通知","content":"<p>请于本周五前确认考场</p>",
           "createrName":"教务处","completeTime":"1795000000000","isread":false},
          {"noticeId":"n2","title":"停课通知","content":"周五下午停课","createrName":"学院",
           "completeTime":"1794000000000","isread":"1"}
        ]}}
    """.trimIndent()

    @Test
    fun parsesNoticeListWithUnreadStateAndCursor() {
        val payload = Json.parseToJsonElement(noticePayload).jsonObject
        val page = XxtParse.parseNotices(payload)
        assertEquals(2, page.items.size)
        assertEquals("100", page.lastId)
        assertEquals(false, page.lastPage)
        val first = page.items[0]
        assertEquals("n1", first.noticeId)
        assertEquals("关于期末考试安排的通知", first.title)
        // 正文里的 HTML 被压成纯文本
        assertEquals("请于本周五前确认考场", first.content)
        assertEquals("教务处", first.sender)
        assertEquals(false, first.read)
        // isread 用字符串 "1" 也表示已读
        assertEquals(true, page.items[1].read)
    }

    @Test
    fun noticeWithoutIdGetsStableFallback() {
        val payload = Json.parseToJsonElement(
            """{"notices":{"list":[{"title":"没有 id 的通知","completeTime":"1794"}]}}""",
        ).jsonObject
        val first = XxtParse.parseNotices(payload).items.single()
        val second = XxtParse.parseNotices(payload).items.single()
        assertEquals(first.noticeId, second.noticeId)
        assertTrue(first.noticeId.startsWith("t"))
    }
}
