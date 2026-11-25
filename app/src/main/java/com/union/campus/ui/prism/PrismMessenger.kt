package com.union.campus.ui.prism

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class PrismMessageTone { Success, Warning, Error, Info }

data class PrismMessage(
    val id: Long = System.nanoTime(),
    val text: String,
    val tone: PrismMessageTone = PrismMessageTone.Info,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

/** 全局轻提示：替代“已取消排队”“解绑完成”这类打断式弹窗。 */
class PrismMessenger {
    internal var current by mutableStateOf<PrismMessage?>(null)
        private set

    fun show(
        text: String,
        tone: PrismMessageTone = PrismMessageTone.Info,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        current = PrismMessage(
            text = text,
            tone = tone,
            actionLabel = actionLabel,
            onAction = onAction,
        )
    }

    fun success(text: String) = show(text, PrismMessageTone.Success)
    fun info(text: String) = show(text, PrismMessageTone.Info)
    fun warning(text: String) = show(text, PrismMessageTone.Warning)
    fun error(text: String) = show(text, PrismMessageTone.Error)

    fun dismiss(id: Long) {
        if (current?.id == id) current = null
    }

    fun dismiss() {
        current = null
    }
}

val LocalPrismMessenger = staticCompositionLocalOf { PrismMessenger() }

@Composable
fun PrismSnackbarHost(
    messenger: PrismMessenger,
    modifier: Modifier = Modifier,
) {
    val message = messenger.current
    val haptics = rememberPrismHaptics()
    val motion = rememberPrismMotion()
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        when (current.tone) {
            PrismMessageTone.Success -> haptics.success()
            PrismMessageTone.Warning -> haptics.warning()
            PrismMessageTone.Error -> haptics.error()
            PrismMessageTone.Info -> Unit
        }
        val baseTimeout = if (current.actionLabel != null) 5000L else 3200L
        val timeout = accessibility?.calculateRecommendedTimeoutMillis(
            baseTimeout, containsIcons = true, containsText = true, containsControls = current.actionLabel != null,
        ) ?: baseTimeout
        delay(timeout)
        messenger.dismiss(current.id)
    }

    val density = LocalDensity.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedContent(
            targetState = message,
            transitionSpec = {
                if (motion.enabled) {
                    (slideInVertically { it } + fadeIn(motion.tween(PrismMotion.Quick))) togetherWith
                        (slideOutVertically { it } + fadeOut(motion.tween(PrismMotion.Fast)))
                } else (EnterTransition.None togetherWith ExitTransition.None).using(null)
            },
            label = "prism-snackbar",
        ) { current ->
            if (current == null) {
                Spacer(Modifier.height(0.dp))
            } else {
                // 官方 M3：所有 snackbar 统一 inverse 配色，tone 由图标 + 触觉 + liveRegion 表达。
                val container = MaterialTheme.colorScheme.inverseSurface
                val content = MaterialTheme.colorScheme.inverseOnSurface
                val actionColor = MaterialTheme.colorScheme.inversePrimary
                val icon = when (current.tone) {
                    PrismMessageTone.Success -> Icons.Rounded.CheckCircle
                    PrismMessageTone.Warning -> Icons.Rounded.WarningAmber
                    PrismMessageTone.Error -> Icons.Rounded.ErrorOutline
                    PrismMessageTone.Info -> Icons.Rounded.Info
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    shape = PrismShape(16f * density.density),
                    color = container,
                    contentColor = content,
                    tonalElevation = 0.dp,
                    shadowElevation = 6.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            text = current.text,
                            style = subheadlineStyle(),
                            modifier = Modifier.weight(1f),
                        )
                        if (current.actionLabel != null) {
                            PrismTextButton(
                                onClick = {
                                    current.onAction?.invoke()
                                    messenger.dismiss(current.id)
                                },
                                modifier = Modifier.defaultMinSize(minHeight = 36.dp),
                            ) {
                                Text(current.actionLabel, color = actionColor)
                            }
                        }
                    }
                }
            }
        }
    }
}
