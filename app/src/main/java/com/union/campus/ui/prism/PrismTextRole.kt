package com.union.campus.ui.prism

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 文字层级：颜色 role 与 alpha **只用一层**。
 *
 * 之前的写法是 onSurfaceVariant（本身已是弱化色）再乘 0.72，合成后 13sp 正文只有
 * 约 4.2:1，全屏发灰、层级也拉不开。这里把"多弱"收成枚举，页面只选角色。
 */
enum class PrismTextRole {
    /** 主文、卡片标题、列表标题。 */
    Primary,

    /** 副标题、说明文字。 */
    Secondary,

    /** 仅 ≤12sp 的注记（footer 之外的极弱信息）。 */
    Tertiary,

    Disabled,

    Accent,

    Danger,
}

@Composable
fun PrismTextRole.color(): Color = when (this) {
    PrismTextRole.Primary -> MaterialTheme.colorScheme.onSurface
    PrismTextRole.Secondary -> MaterialTheme.colorScheme.onSurfaceVariant
    PrismTextRole.Tertiary -> MaterialTheme.colorScheme.onSurfaceVariant.withAlpha(PrismAlpha.Secondary)
    PrismTextRole.Disabled -> MaterialTheme.colorScheme.onSurface.withAlpha(PrismAlpha.Disabled)
    PrismTextRole.Accent -> MaterialTheme.colorScheme.primary
    PrismTextRole.Danger -> MaterialTheme.colorScheme.error
}