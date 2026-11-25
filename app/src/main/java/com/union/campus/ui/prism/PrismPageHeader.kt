package com.union.campus.ui.prism

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow

/**
 * Tab 页统一页头：大标题 + 一行说明，作为首个内容项随页面滚动。
 * 与今天页保持同一套结构，不再使用会收放的折叠顶栏——
 * 顶栏高度不变，滚动时就不会出现标题忽大忽小、底色忽明忽暗的闪烁。
 */
@Composable
fun PrismPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = pageTitleStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // TalkBack 可按标题跳转到各 Tab 页标题。
                modifier = Modifier.semantics { heading() },
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = footnoteStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // 学期名较长时宁可换行，也不要显示成「2026-2027学年第…」。
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}