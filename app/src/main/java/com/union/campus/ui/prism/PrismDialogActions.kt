package com.union.campus.ui.prism

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Equal-width actions horizontally when they fit, full-width actions vertically otherwise. */
@Composable
internal fun PrismDialogActions(
    confirmText: String,
    dismissText: String?,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = buttonLabelStyle()
    val padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val labelWidth = with(density) { ((maxWidth - 8.dp) / 2 - 24.dp).toPx() }
        val stacked = dismissText != null && listOf(confirmText, dismissText).any {
            measurer.measure(it, style = style, maxLines = 1, softWrap = false).size.width > labelWidth
        }
        val dismiss: @Composable (Modifier) -> Unit = { buttonModifier ->
            if (dismissText != null) {
                PrismButton(onClick = onDismiss, modifier = buttonModifier, filled = false, contentPadding = padding) {
                    Text(dismissText, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val confirm: @Composable (Modifier) -> Unit = { buttonModifier ->
            PrismButton(
                onClick = onConfirm,
                modifier = buttonModifier,
                contentPadding = padding,
                containerColor = if (destructive) MaterialTheme.colorScheme.errorContainer else null,
                contentColor = if (destructive) MaterialTheme.colorScheme.onErrorContainer else null,
            ) {
                Text(confirmText, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dismiss(Modifier.fillMaxWidth())
                confirm(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (dismissText != null) dismiss(Modifier.weight(1f))
                confirm(Modifier.weight(1f))
            }
        }
    }
}
