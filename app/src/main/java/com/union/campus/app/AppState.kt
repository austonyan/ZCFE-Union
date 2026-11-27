package com.union.campus.app

import com.union.campus.domain.CourseCell
import com.union.campus.domain.Exam
import com.union.campus.domain.Grade
import com.union.campus.domain.JwNotice
import com.union.campus.domain.ShowerArea
import com.union.campus.domain.ShowerDevice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.union.campus.ui.screens.study.SelectableCourse

/** 屏幕间对象传递（单 Activity 内存态，不入路由参数）。 */
object AppState {
    var selectedCourse by androidx.compose.runtime.mutableStateOf<CourseCell?>(null)
    var selectedGrade by androidx.compose.runtime.mutableStateOf<Grade?>(null)
    var selectedExam by androidx.compose.runtime.mutableStateOf<Exam?>(null)
    var selectedNotice by androidx.compose.runtime.mutableStateOf<JwNotice?>(null)
    var selectedDevice by androidx.compose.runtime.mutableStateOf<ShowerDevice?>(null)
    var selectedAreaName by androidx.compose.runtime.mutableStateOf("")
    var confirmCourse by androidx.compose.runtime.mutableStateOf<SelectableCourse?>(null)

    /** 系统通知点击带进来的深链；UnionApp 消费后清空。 */
    var deepLink by androidx.compose.runtime.mutableStateOf<String?>(null)

    fun clear() {
        selectedCourse = null
        selectedGrade = null
        selectedExam = null
        selectedNotice = null
        selectedDevice = null
        selectedAreaName = ""
        confirmCourse = null
        deepLink = null
    }
}
