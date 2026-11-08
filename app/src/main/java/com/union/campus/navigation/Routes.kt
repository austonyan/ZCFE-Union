package com.union.campus.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

/** 34 个屏幕的完整路由表。 */
enum class Screen(val route: String, val title: String, val tabIcon: ImageVector? = null) {
    // 绑定流程
    Welcome("welcome", "欢迎"),
    LoginAcademic("login-academic", "登录教务"),
    LoginShower("login-shower", "登录洗浴"),
    BindElectric("bind-electric", "绑定电费"),
    BindElectricAdd("bind-electric-add", "添加电费房间"),
    BindDone("bind-done", "绑定完成"),

    // 学习
    Today("today", "今天", Icons.Rounded.Home),
    Timetable("timetable", "课表", Icons.AutoMirrored.Rounded.MenuBook),
    CourseDetail("course-detail", "课程详情"),
    Grades("grades", "成绩"),
    GradeDetail("grade-detail", "成绩详情"),
    Exams("exams", "考试"),
    ExamDetail("exam-detail", "考试详情"),
    CourseSelect("course-select", "选课"),
    ConfirmSelection("confirm-selection", "确认选课"),
    Evaluation("evaluation", "教学评价"),
    Textbooks("textbooks", "教材"),
    AcademicAffairs("academic-affairs", "教务事务"),
    StudentRecord("student-record", "学籍档案"),
    Notices("notices", "教务通知"),
    NoticeDetail("notice-detail", "通知详情"),

    // 生活
    Life("life", "生活", Icons.Rounded.WaterDrop),
    Shower("shower", "洗浴"),
    ShowerSession("shower-session", "洗浴中"),
    DeviceDetail("device-detail", "设备详情"),
    Wallet("wallet", "钱包与账单"),
    Electric("electric", "电费"),
    UsageDetail("usage-detail", "用量明细"),
    ElectricTopUp("electric-top-up", "电费充值"),
    FreeClassrooms("free-classrooms", "空闲教室"),
    ClassroomDetail("classroom-detail", "教室详情"),

    // 我的
    Profile("profile", "我的", Icons.Rounded.Person),
    Credentials("credentials", "凭证中心"),
    Reminders("reminders", "提醒中心"),
    Privacy("privacy", "隐私与安全"),
    ;

    companion object {
        val Tabs = listOf(Today, Timetable, Life, Profile)

        fun fromRoute(route: String?): Screen? = entries.firstOrNull { it.route == route }
    }
}
