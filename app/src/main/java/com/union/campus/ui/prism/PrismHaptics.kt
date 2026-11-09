package com.union.campus.ui.prism

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * 轻量触感封装：
 * - 点击/选中：KEYBOARD_TAP / CONFIRM
 * - 成功：CONFIRM；警告/失败：REJECT
 * 不接管滚动，不在列表滚动时触发。
 */
class PrismHaptics(private val view: View) {

    fun tap() = perform(HapticFeedbackConstants.KEYBOARD_TAP)

    fun select() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        },
    )

    fun toggle() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        },
    )

    fun success() = select()

    fun warning() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        },
    )

    fun error() = warning()

    private fun perform(constant: Int) {
        if (!view.isHapticFeedbackEnabled) return
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberPrismHaptics(): PrismHaptics {
    val view = LocalView.current
    return remember(view) { PrismHaptics(view) }
}