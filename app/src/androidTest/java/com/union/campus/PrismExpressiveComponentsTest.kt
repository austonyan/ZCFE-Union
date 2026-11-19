package com.union.campus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismChip
import com.union.campus.ui.prism.PrismGroupedList
import com.union.campus.ui.prism.PrismRow
import com.union.campus.ui.prism.PrismSegmented
import com.union.campus.ui.theme.UnionTheme
import com.union.campus.ui.theme.PresetPalettes
import com.union.campus.ui.theme.LightColors
import com.union.campus.ui.theme.DarkColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Contract fixtures for the public components; no page or navigation dependency. */
@RunWith(AndroidJUnit4::class)
class PrismExpressiveComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tonalSelectionColors_retainTextContrastInAllEighteenThemes() {
        val presets = listOf("blue", "purple", "pink", "red", "orange", "yellow", "green", "gray")
        val schemes = listOf("teal-light" to LightColors, "teal-dark" to DarkColors) +
            presets.flatMap { id ->
                listOf(false, true).map { dark -> "$id-$dark" to requireNotNull(PresetPalettes.scheme(id, dark)) }
            }
        assertEquals(18, schemes.size)
        schemes.forEach { (name, scheme) ->
            val foreground = scheme.onSecondaryContainer.luminance()
            val background = scheme.secondaryContainer.luminance()
            val contrast = (maxOf(foreground, background) + 0.05f) / (minOf(foreground, background) + 0.05f)
            org.junit.Assert.assertTrue("$name tonal text contrast: $contrast", contrast >= 4.5f)
        }
    }

    @Test
    fun connectedSegments_keepSingleSelectionAndTouchTargets_atLargeFontInDarkTheme() {
        var selected by mutableStateOf(0)
        var changes = 0
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.4f)) {
                UnionTheme(darkTheme = true, dynamicColor = false) {
                    PrismSegmented(
                        options = listOf("本周", "下周", "全部"),
                        selectedIndex = selected,
                        onSelect = { selected = it; changes++ },
                        modifier = Modifier.width(280.dp),
                    )
                }
            }
        }
        composeRule.onNodeWithText("本周").assertIsSelected().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("下周")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .performClick()
            .assertIsSelected()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.runOnIdle { assertEquals(1, changes) }
    }

    @Test
    fun loadingButton_preservesWidthAndLabel_butDisablesAction() {
        var loading by mutableStateOf(false)
        var clicks = 0
        composeRule.setContent {
            UnionTheme(dynamicColor = false) {
                PrismButton(
                    onClick = { clicks++ },
                    loading = loading,
                    modifier = Modifier.testTag("submit"),
                ) { Text("同步课程") }
            }
        }
        val width = composeRule.onNodeWithTag("submit").fetchSemanticsNode().boundsInRoot.width
        composeRule.runOnIdle { loading = true }
        composeRule.onNodeWithTag("submit")
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate,
            ))
        composeRule.onNodeWithText("同步课程").assertExists()
        assertEquals(width, composeRule.onNodeWithTag("submit").fetchSemanticsNode().boundsInRoot.width, 0.5f)
        composeRule.runOnIdle {
            assertEquals(0, clicks)
        }
    }

    @Test
    fun selectedChip_doesNotChangeWidth_whenCheckAppears() {
        var selected by mutableStateOf(false)
        composeRule.setContent {
            UnionTheme(dynamicColor = false) {
                PrismChip("已选课程", selected, { selected = true }, Modifier.testTag("chip"))
            }
        }
        val width = composeRule.onNodeWithTag("chip").fetchSemanticsNode().boundsInRoot.width
        composeRule.onNodeWithTag("chip").performClick().assertIsSelected()
        assertEquals(width, composeRule.onNodeWithTag("chip").fetchSemanticsNode().boundsInRoot.width, 0.5f)
    }

    @Test
    fun groupedList_remeasuresAfterConditionalRowRemoval() {
        var showSecond by mutableStateOf(true)
        composeRule.setContent {
            UnionTheme(dynamicColor = false) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrismGroupedList(Modifier.testTag("group")) {
                        PrismRow("首行")
                        if (showSecond) PrismRow("尾行")
                    }
                    PrismChip("禁用筛选", selected = false, onClick = {}, enabled = false)
                }
            }
        }
        val oldHeight = composeRule.onNodeWithTag("group").fetchSemanticsNode().boundsInRoot.height
        composeRule.runOnIdle { showSecond = false }
        composeRule.onNodeWithText("首行").assertIsDisplayed()
        composeRule.onNodeWithText("尾行").assertDoesNotExist()
        composeRule.onNodeWithText("禁用筛选").assertIsNotEnabled()
        val newHeight = composeRule.onNodeWithTag("group").fetchSemanticsNode().boundsInRoot.height
        org.junit.Assert.assertTrue(newHeight < oldHeight)
    }
}
