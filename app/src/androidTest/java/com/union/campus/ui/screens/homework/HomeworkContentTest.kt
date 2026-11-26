package com.union.campus.ui.screens.homework

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.union.campus.domain.XxtHomework
import com.union.campus.domain.XxtHomeworkSnapshot
import com.union.campus.ui.theme.UnionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 作业页就绪态的设备端渲染回归：分组标题、倒计时徽标与筛选芯片都来自真实组合。
 * 用固定快照渲染，不需要账号与网络。
 */
@RunWith(AndroidJUnit4::class)
class HomeworkContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = System.currentTimeMillis()

    private fun hw(key: String, title: String, hours: Double?, done: Boolean = false) = XxtHomework(
        key = key,
        courseId = "1",
        clazzId = "2",
        courseName = "高等数学",
        title = title,
        status = if (done) "已提交" else "未提交",
        url = "",
        done = done,
        deadlineAt = hours?.let { now + (it * TimeUnit.HOURS.toMillis(1)).toLong() } ?: 0L,
    )

    private fun render(snapshot: XxtHomeworkSnapshot, showAll: Boolean = false) {
        compose.setContent {
            UnionTheme(darkTheme = false, dynamicColor = false, presetColor = "teal") {
                HomeworkReadyContent(
                    snapshot = snapshot,
                    showAll = showAll,
                    syncing = false,
                    progress = "",
                    onShowAllChange = {},
                )
            }
        }
    }

    @Test
    fun showsPendingSummaryGroupsAndBadges() {
        render(
            XxtHomeworkSnapshot(
                items = listOf(
                    hw("a", "第一次作业", 5.5),
                    hw("b", "第二次作业", 50.0),
                    hw("c", "已完成作业", 5.5, done = true),
                ),
                syncedAt = now,
                courseCount = 1,
            ),
        )

        compose.onNodeWithText("未交 2 项").assertExists()
        compose.onNodeWithText("第一次作业").assertExists()
        compose.onNodeWithText("24 小时内 · 1").assertExists()
        compose.onNodeWithText("3 天内 · 1").assertExists()
        // 倒计时徽标：5 小时
        compose.onNodeWithText("5h").assertExists()
        // 默认只看未交，已交作业不占位置
        compose.onNodeWithText("已完成作业").assertDoesNotExist()
        capture("xxt-homework-ready.png")
    }

    /** 把当前组合渲染成 PNG，落在应用外部目录，方便 adb pull 回来人工复核。 */
    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
            ?: return
        FileOutputStream(File(dir, name)).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun showAllIncludesSubmittedItems() {
        render(
            XxtHomeworkSnapshot(
                items = listOf(
                    hw("a", "第一次作业", 5.5),
                    hw("c", "已完成作业", 5.5, done = true),
                ),
                syncedAt = now,
                courseCount = 1,
            ),
            showAll = true,
        )

        compose.onNodeWithText("全部 2").assertExists()
        compose.onNodeWithText("已完成作业").assertExists()
        compose.onNodeWithText("已交 · 1").assertExists()
    }

    @Test
    fun unknownDeadlineIsExplicit() {
        render(
            XxtHomeworkSnapshot(
                items = listOf(hw("d", "没有时间的作业", null)),
                syncedAt = now,
                courseCount = 1,
            ),
        )
        compose.onNodeWithText("截止未知 · 1").assertExists()
        compose.onNodeWithText("待公布").assertExists()
    }
}
