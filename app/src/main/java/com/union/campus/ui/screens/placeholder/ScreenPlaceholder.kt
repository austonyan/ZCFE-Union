package com.union.campus.ui.screens.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.union.campus.navigation.Screen
import com.union.campus.ui.common.UnionScreenScaffold
import com.union.campus.ui.prism.PrismCard
import com.union.campus.ui.prism.bodyStyle
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.pageTitleStyle

/** 壳阶段的占位屏幕：统一的顶部应用栏 + 居中提示，后续阶段逐屏替换为完整实现。 */
@Composable
fun ScreenPlaceholder(
    screen: Screen,
    showBack: Boolean,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    UnionScreenScaffold(
        title = screen.title,
        onBack = if (showBack) onBack else null,
        modifier = modifier,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = screen.title,
                    style = pageTitleStyle(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = screen.route,
                    style = footnoteStyle(),
                    color = MaterialTheme.colorScheme.primary,
                )
                PrismCard {
                    Text(
                        text = "功能开发中。",
                        style = bodyStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}