package com.union.campus.ui.prism

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.TextButton
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
    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        delay(if (current.actionLabel != null) 5000L else 3200L)
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
                (slideInVertically { it } + fadeIn(PrismMotion.tween(PrismMotion.Quick))) togetherWith
                    (slideOutVertically { it } + fadeOut(PrismMotion.tween(PrismMotion.Fast)))
            },
            label = "prism-snackbar",
        ) { current ->
            if (current == null) {
                Spacer(Modifier.height(0.dp))
            } else {
                val (container, content) = when (current.tone) {
                    PrismMessageTone.Success -> PrismStatus.successContainer() to PrismStatus.onSuccessContainer()
                    PrismMessageTone.Warning -> PrismStatus.warningContainer() to PrismStatus.onWarningContainer()
                    PrismMessageTone.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                    PrismMessageTone.Info -> MaterialTheme.colorScheme.inverseSurface to MaterialTheme.colorScheme.inverseOnSurface
                }
                val icon = when (current.tone) {
                    PrismMessageTone.Success -> Icons.Rounded.CheckCircle
                    PrismMessageTone.Warning -> Icons.Rounded.WarningAmber
                    PrismMessageTone.Error -> Icons.Rounded.ErrorOutline
                    PrismMessageTone.Info -> Icons.Rounded.Info
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp),
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
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (current.actionLabel != null) {
                            TextButton(
                                onClick = {
                                    current.onAction?.invoke()
                                    messenger.dismiss(current.id)
                                },
                            ) {
                                Text(current.actionLabel, color = content)
                            }
                        }
                    }
                }
            }
        }
    }
}