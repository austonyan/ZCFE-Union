package com.union.campus

import android.view.KeyEvent
import android.os.SystemClock
import android.view.Window
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.union.campus.navigation.Screen
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismDock
import com.union.campus.ui.prism.PrismSnapSheet
import com.union.campus.ui.theme.UnionTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrismModalAccessibilityTest {
    @get:Rule val rule = createComposeRule()

    private fun sendKeyboardKey(code: Int, shift: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (shift) {
            val time = SystemClock.uptimeMillis()
            instrumentation.sendKeySync(KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0, KeyEvent.META_SHIFT_ON))
            instrumentation.sendKeySync(KeyEvent(time, time, KeyEvent.ACTION_UP, code, 0, KeyEvent.META_SHIFT_ON))
        } else instrumentation.sendKeyDownUpSync(code)
        rule.waitForIdle()
    }

    @Test
    fun dock_keyboardFocus_isVisible_andEnterSelectsFocusedDestination() {
        var selected by mutableStateOf<Screen?>(Screen.Today)
        rule.setContent {
            UnionTheme(dynamicColor = false) {
                PrismDock(Screen.Tabs, selected, { selected = it }, Modifier.width(360.dp))
            }
        }
        val tab = rule.onNode(
            hasText("生活") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
        )
        val before = tab.captureToImage().toPixelMap()
        sendKeyboardKey(KeyEvent.KEYCODE_TAB)
        rule.onNode(hasText("今天") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assertIsFocused()
        tab.performSemanticsAction(SemanticsActions.RequestFocus) { it() }.assertIsFocused()
        rule.mainClock.advanceTimeBy(500)
        val after = tab.captureToImage().toPixelMap()
        var changed = 0
        for (y in 0 until before.height) for (x in 0 until before.width) {
            if (before[x, y] != after[x, y]) changed++
        }
        assertTrue("Keyboard focus must visibly change the destination, not only semantics", changed > 100)
        tab.performKeyInput { pressKey(Key.Enter) }.assertIsSelected()
        rule.runOnIdle { assertEquals(Screen.Life, selected) }
    }

    @Test
    fun sheet_hasNativeDialogAndDismissSemantics_andRestoresOpenerFocus() {
        var visible by mutableStateOf(false)
        var dismissals = 0
        rule.setContent {
            UnionTheme(dynamicColor = false) {
                PrismButton(onClick = { visible = true }) {
                    Text("打开筛选")
                }
                PrismSnapSheet(
                    visible = visible,
                    paneTitle = "筛选条件",
                    onDismiss = { dismissals++; visible = false },
                ) { Text("选择条件") }
            }
        }
        sendKeyboardKey(KeyEvent.KEYCODE_TAB)
        rule.onNodeWithText("打开筛选").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        val dialog = SemanticsMatcher.keyIsDefined(SemanticsProperties.IsDialog)
        rule.onNode(dialog).assertExists()
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "筛选条件"))
            .performSemanticsAction(SemanticsActions.Dismiss) { assertTrue(it()) }
        rule.waitUntil(5_000) { rule.onAllNodes(dialog).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("打开筛选").assertIsFocused()
        rule.runOnIdle { assertEquals(1, dismissals) }
        // Verify actual activation after focus returns, not only the stored Compose focus flag.
        sendKeyboardKey(KeyEvent.KEYCODE_ENTER)
        rule.onNode(dialog).assertExists()
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "筛选条件"))
            .performSemanticsAction(SemanticsActions.Dismiss) { assertTrue(it()) }
        rule.waitUntil(5_000) { rule.onAllNodes(dialog).fetchSemanticsNodes().isEmpty() }
        rule.runOnIdle { assertEquals(2, dismissals) }
    }

    @Test
    fun sheet_keyboardActivation_staysInModalWindow_andDoesNotActivateBackground() {
        var visible by mutableStateOf(true)
        var backgroundClicks = 0
        var modalClicks = 0
        rule.setContent {
            UnionTheme(dynamicColor = false) {
                PrismButton(onClick = { backgroundClicks++ }) { Text("背景操作") }
                PrismSnapSheet(visible = visible, onDismiss = { visible = false }) {
                    Column {
                        Text("筛选")
                        PrismButton(onClick = { modalClicks++; visible = false }) { Text("应用筛选") }
                    }
                }
            }
        }
        sendKeyboardKey(KeyEvent.KEYCODE_TAB)
        rule.onNodeWithText("应用筛选").assertIsFocused()
        repeat(4) {
            sendKeyboardKey(KeyEvent.KEYCODE_TAB)
            rule.onNodeWithText("应用筛选").assertIsFocused()
            sendKeyboardKey(KeyEvent.KEYCODE_TAB, shift = true)
            rule.onNodeWithText("应用筛选").assertIsFocused()
        }
        sendKeyboardKey(KeyEvent.KEYCODE_ENTER)
        rule.waitUntil(5_000) { modalClicks == 1 }
        rule.runOnIdle { assertEquals(0, backgroundClicks) }
    }

    @Test
    fun sheet_windowUsesThemeMatchedSystemBars_andOnlyAuthoredAnimationAndScrim() {
        var dark by mutableStateOf(false)
        var modalWindow: Window? = null
        rule.setContent {
            UnionTheme(darkTheme = dark, dynamicColor = false) {
                PrismSnapSheet(visible = true, onDismiss = {}) {
                    val view = LocalView.current
                    SideEffect { modalWindow = (view.parent as? DialogWindowProvider)?.window }
                    Text("窗口验证")
                }
            }
        }
        fun assertWindow(expectedLightIconsBackground: Boolean) {
            rule.runOnIdle {
                val window = requireNotNull(modalWindow)
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                assertEquals(expectedLightIconsBackground, controller.isAppearanceLightStatusBars)
                assertEquals(expectedLightIconsBackground, controller.isAppearanceLightNavigationBars)
                assertEquals(0f, window.attributes.dimAmount, 0.001f)
                assertEquals(0, window.attributes.windowAnimations)
            }
        }
        assertWindow(true)
        rule.runOnIdle { dark = true }
        assertWindow(false)
    }
}
