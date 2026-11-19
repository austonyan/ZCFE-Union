package com.union.campus

import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.union.campus.domain.ShowerReservation
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.PrismDialogActions
import com.union.campus.ui.prism.PrismSegmented
import com.union.campus.ui.prism.PrismMetricCard
import com.union.campus.ui.screens.life.ReservationCard
import com.union.campus.ui.screens.study.WeekSwitcher
import com.union.campus.ui.theme.UnionTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** 1200px portrait width: 480/540/600dpi equivalents, normal and largest supported text. */
@RunWith(Parameterized::class)
class TargetResolutionLayoutTest(private val widthDp: Int, private val fontScale: Float) {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun portraitWindow() {
        rule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        rule.waitUntil(5_000) {
            rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        }
    }

    private fun show(content: @Composable () -> Unit) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1200f / widthDp, fontScale)) {
                UnionTheme(dynamicColor = false) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                            content()
                        }
                    }
                }
            }
        }
    }

    private fun assertSingleLine(label: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(label, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("$label must expose its rendered text layout", layouts.isEmpty())
        layouts.forEach {
            assertEquals("$label wraps at ${widthDp}dp / ${fontScale}x", 1, it.lineCount)
            assertFalse("$label clips vertically at ${widthDp}dp / ${fontScale}x", it.didOverflowHeight)
            assertFalse("$label is ellipsized at ${widthDp}dp / ${fontScale}x", it.isLineEllipsized(0))
            // Text intrinsic widths may be fractional while Layout sizes are integer pixels.
            assertTrue("$label clips horizontally at ${widthDp}dp / ${fontScale}x",
                it.getLineRight(0) - it.getLineLeft(0) <= it.size.width + 1f)
            assertEquals(label.length, it.getLineEnd(0, visibleEnd = true))
        }
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = instrumentation.targetContext.getExternalFilesDir("layout-regression")!!
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("No screenshot")
        File(directory, "$name-$widthDp-$fontScale.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test
    fun lifeCards_matchHeightWithDifferentContentLengths() {
        show {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PrismMetricCard(
                    label = "电费", value = "123.45 度", subtitle = "约 30 天 · 今日 2.50 度",
                    compact = true,
                    modifier = Modifier.weight(1f).fillMaxHeight().testTag("electric"),
                )
                PrismMetricCard(
                    label = "洗浴", value = "8 / 20", subtitle = "空闲",
                    compact = true,
                    modifier = Modifier.weight(1f).fillMaxHeight().testTag("shower"),
                )
            }
        }
        screenshot("life-cards")
        val electric = rule.onNodeWithTag("electric").fetchSemanticsNode().boundsInRoot
        val shower = rule.onNodeWithTag("shower").fetchSemanticsNode().boundsInRoot
        assertEquals(electric.top, shower.top, 1f)
        assertEquals(electric.bottom, shower.bottom, 1f)
    }

    @Test
    fun reservationActions_keepFullLabelsOnOneLine() {
        show {
            ReservationCard(
                reservation = ShowerReservation("test-only", "一号设备", "测试区域", 300, System.currentTimeMillis() + 300_000),
                busy = false,
                onStart = {}, onCancel = {}, onExpired = {},
            )
        }
        screenshot("reservation")
        assertSingleLine("启动设备")
        assertSingleLine("取消预约")
    }

    @Test
    fun dialogActions_keepFullConfirmationLabels() {
        show {
            PrismCard {
                PrismDialogActions(
                    confirmText = "确认取消当前设备预约", dismissText = "保留当前预约",
                    destructive = false, onConfirm = {}, onDismiss = {},
                )
            }
        }
        screenshot("dialog")
        assertSingleLine("确认取消当前设备预约")
        assertSingleLine("保留当前预约")
    }

    @Test
    fun longSegments_reflowInsteadOfTruncating() {
        show {
            PrismCard {
                PrismSegmented(listOf("按 J1–J6", "按 S1–S6、J7"), 0, {})
            }
        }
        screenshot("segments")
        assertSingleLine("按 J1–J6")
        assertSingleLine("按 S1–S6、J7")
    }

    @Test
    fun weekSwitcher_preservesLabelsOutsideCurrentWeek() {
        show { WeekSwitcher(25, 30, 12, {}, {}, {}) }
        screenshot("week")
        assertSingleLine("返回本周")
        assertSingleLine("第 25 周")
        assertSingleLine("共 30 周")
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}dp-{1}x")
        fun sizes(): List<Array<Any>> = listOf(400, 356, 320).flatMap { width ->
            listOf(1f, 1.4f).map { scale -> arrayOf<Any>(width, scale) }
        }
    }
}
