package com.union.campus.data.repo

import com.union.campus.domain.XxtCourse

/**
 * 学习通页面的纯解析函数（不联网、不依赖 Android），单元测试可直接覆盖。
 * 页面是服务端渲染，统一用宽松正则抽取；页面小改版时尽量不整段崩掉。
 */
object XxtParse {

    /** 作业列表里的一条，url 来自 <li> 的 data 属性。 */
    data class WorkItem(val index: Int, val title: String, val status: String, val url: String)

    /** 作业列表页：进度 (已完成, 总数) 与条目。 */
    data class WorkPage(val done: Int?, val total: Int?, val items: List<WorkItem>)

    /** 作业详情页字段；取不到的留空。 */
    data class WorkDetail(
        val title: String = "",
        val questionCount: Int = 0,
        val fullScore: String = "",
        val startRaw: String = "",
        val deadlineRaw: String = "",
        val score: String = "",
    )

    private val ENTITIES = mapOf(
        "&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">",
        "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'",
    )

    fun unescapeHtml(text: String): String {
        var out = text
        ENTITIES.forEach { (k, v) -> out = out.replace(k, v) }
        out = Regex("&#(\\d+);").replace(out) { m ->
            m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
        }
        return out
    }

    /** 去标签 + 反转义 + 压缩空白（先反转义，避免 &nbsp; 变成 \u00a0 混在文本里）。 */
    fun stripTags(html: String): String {
        val noScript = Regex("<script[\\s\\S]*?</script>|<style[\\s\\S]*?</style>")
            .replace(html, " ")
        val noTags = Regex("<[^>]+>").replace(noScript, " ")
        return unescapeHtml(noTags).split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** 取 <input type="hidden" id="X" value="Y"> 的 value，兼容属性顺序与单引号。 */
    fun hiddenValue(html: String, id: String): String {
        if (html.isBlank()) return ""
        val patterns = listOf(
            "id=\"${Regex.escape(id)}\"[^>]*?value=\"([^\"]*)\"",
            "value=\"([^\"]*)\"[^>]*?id=\"${Regex.escape(id)}\"",
            "id='${Regex.escape(id)}'[^>]*?value='([^']*)'",
        )
        for (pattern in patterns) {
            Regex(pattern).find(html)?.let { return it.groupValues[1] }
        }
        return ""
    }

    /** 块内 class 含指定词的第一个标签的纯文本。 */
    private fun firstTagText(block: String, classWord: String): String {
        val m = Regex("<[^>]*class=\"[^\"]*\\b${Regex.escape(classWord)}\\b[^\"]*\"[^>]*>([\\s\\S]*?)<")
            .find(block)
            ?: Regex("<[^>]*class=\"[^\"]*\\b${Regex.escape(classWord)}\\b[^\"]*\"[^>]*>([^<]*)")
                .find(block)
        return m?.let { stripTags(it.groupValues[1]) } ?: ""
    }

    /** 课程列表页：stucoursemiddle 链接给 id，标题与教师紧跟其后。 */
    fun parseCourses(html: String): List<XxtCourse> {
        val out = mutableListOf<XxtCourse>()
        val seen = mutableSetOf<String>()
        val link = Regex("stucoursemiddle\\?courseid=(\\d+)&(?:amp;)?clazzid=(\\d+)&(?:amp;)?cpi=(\\d+)")
        for (m in link.findAll(html)) {
            val (courseId, clazzId, cpi) = m.destructured
            if (!seen.add("$courseId:$clazzId:$cpi")) continue
            val seg = html.substring(m.range.last + 1, minOf(html.length, m.range.last + 1 + 2000))
            val name = Regex("course-name[^>]*title=\"([^\"]+)\"").find(seg)?.groupValues?.get(1)
                ?: Regex("title=\"([^\"]+)\"[^>]*class=\"[^\"]*course-name").find(seg)?.groupValues?.get(1)
            var teacher = ""
            Regex("<p class=\"[^\"]*overHidden2[^\"]*\"[^>]*>([^<]{1,20})</p>").findAll(seg).forEach { pm ->
                val text = pm.groupValues[1].trim()
                if (teacher.isEmpty() && text.isNotEmpty() && text != name) teacher = text
            }
            out += XxtCourse(
                courseId = courseId,
                clazzId = clazzId,
                cpi = cpi,
                name = name?.let { unescapeHtml(it) } ?: "未命名课程",
                teacher = unescapeHtml(teacher),
            )
        }
        return out
    }

    /** 作业列表页："已完成 3/8" 的进度 + 每条 goTask 条目。 */
    fun parseHomeworkList(html: String): WorkPage {
        val progress = listOf(
            Regex("<span>(\\d+)\\s*/\\s*(\\d+)</span>"),
            Regex("已完成[^0-9]{0,12}(\\d+)\\s*/\\s*(\\d+)"),
        ).firstNotNullOfOrNull { pattern ->
            pattern.find(html)?.let { it.groupValues[1].toIntOrNull() to it.groupValues[2].toIntOrNull() }
        }

        val items = mutableListOf<WorkItem>()
        for (li in Regex("<li\\b([^>]*)>([\\s\\S]*?)</li>").findAll(html)) {
            val attrs = li.groupValues[1]
            val block = li.groupValues[2]
            if (!attrs.contains("goTask") || !attrs.contains("data=")) continue
            val url = Regex("data=\"([^\"]+)\"").find(attrs)?.groupValues?.get(1) ?: continue
            var title = firstTagText(block, "overHidden2")
            val status = firstTagText(block, "status")
            if (title.isBlank()) title = stripTags(block).take(60)
            items += WorkItem(
                index = items.size + 1,
                title = title.ifBlank { "未命名作业" },
                status = status,
                url = unescapeHtml(url).let { java.net.URLDecoder.decode(it, "UTF-8") },
            )
        }
        return WorkPage(progress?.first, progress?.second, items)
    }

    /** 作业详情页：题量 / 满分 / 作答起止时间 / 得分 / 标题。 */
    fun parseHomeworkDetail(html: String): WorkDetail {
        val text = stripTags(html)
        fun group(pattern: String, index: Int = 1): String =
            Regex(pattern).find(text)?.groupValues?.get(index) ?: ""

        var title = ""
        val countAt = Regex("题量[:：]").find(text)
        if (countAt != null) {
            val head = text.substring(0, countAt.range.first)
            val detailAt = head.lastIndexOf("作业详情")
            if (detailAt >= 0) title = head.substring(detailAt + "作业详情".length).trim(' ', '：', ':', '·', '-')
        }

        val range = Regex(
            "作答时间[:：]\\s*(\\d{1,2}-\\d{1,2}\\s+\\d{1,2}:\\d{2})\\s*至\\s*(\\d{1,2}-\\d{1,2}\\s+\\d{1,2}:\\d{2})",
        ).find(text)
        val startRaw = range?.groupValues?.get(1) ?: ""
        var deadlineRaw = range?.groupValues?.get(2) ?: ""
        if (deadlineRaw.isEmpty()) {
            deadlineRaw = group("(?:截止时间|结束时间)[:：]\\s*(\\d{1,2}-\\d{1,2}\\s+\\d{1,2}:\\d{2})")
        }

        return WorkDetail(
            title = title,
            questionCount = group("题量[:：]\\s*(\\d+)").toIntOrNull() ?: 0,
            fullScore = group("满分[:：]\\s*(\\d+(?:\\.\\d+)?)"),
            startRaw = startRaw,
            deadlineRaw = deadlineRaw,
            score = group("(?:得分|总评|成绩|批阅)[:：]?\\s*(\\d+(?:\\.\\d+)?)\\s*分"),
        )
    }

    /**
     * 登录页判断：桌面端跳 /login，移动端 UA 会跳 /mlogin，两者都算失效。
     * 只匹配 "/login" 会把 mlogin 误判成已登录，进而把空数据当成"没有作业"。
     */
    fun looksLikeLogin(url: String, body: String): Boolean =
        url.contains("passport2.chaoxing.com") ||
            body.take(4000).contains("用户登录") ||
            body.take(2000).contains("id=\"loginBtn\"")

    /** 反爬拦截页：只扫正文开头，作业题干里的"验证码"不算。 */
    fun isAntispider(url: String, body: String): Boolean =
        url.contains("antispiderShowVerify") ||
            body.take(800).contains("操作异常，请输入图片中的验证码")
}
