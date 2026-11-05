package com.union.campus.ui.prism

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------- 字号角色（iOS Dynamic Type 档 × Roboto Flex） ----------------

/** 页面大标题 24/30 w600 */
@Composable
fun pageTitleStyle() = MaterialTheme.typography.headlineSmall.copy(
    fontSize = 24.sp, lineHeight = 30.sp, fontWeight = androidx.compose.ui.text.font.FontWeight(600),
)

/** 分组头/卡片头 17 w600 */
@Composable
fun headlineStyle() = MaterialTheme.typography.titleMedium.copy(
    fontSize = 17.sp, lineHeight = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight(600),
)

/** 列表标题 17 w400 */
@Composable
fun bodyStyle() = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 22.sp)

/** 列表副标题/正文 15 */
@Composable
fun subheadlineStyle() = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp)

/** footer 13 */
@Composable
fun footnoteStyle() = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp)

/** 导航/chip 12 w600 */
@Composable
fun caption1Style() = MaterialTheme.typography.labelMedium.copy(
    fontSize = 12.sp, lineHeight = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight(600),
)

/** 仪表盘大数字 40 */
@Composable
fun displayStyle() = MaterialTheme.typography.displayMedium.copy(fontSize = 40.sp, lineHeight = 44.sp)

// ---------------- 卡片 ----------------

/**
 * Prism 卡片：raised surface 填充、连续圆角 16、ink 按压（点击时）、无阴影。
 */
@Composable
fun PrismCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = surfaceColorOf(LocalSurfaceLevel.current.next()),
    corner: Float = PrismCorner.Md,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val shape = remember(corner, density.density) {
        PrismShape(corner * density.density)
    }
    if (onClick != null) {
        Surface(
            modifier = modifier
                .clip(shape)
                .clickable(onClick = onClick),
            color = containerColor,
            shape = shape,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(modifier = modifier, color = containerColor, shape = shape) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/** 卡片头：图标 20 + 标题 headline/onSurfaceVariant·α0.8。 */
@Composable
fun CardHeader(title: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(
                icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            title,
            style = headlineStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

// ---------------- 分组列表（iOS inset grouped） ----------------

/**
 * 分组列表：行包在连续面里（首 top24 / 尾 bottom24 / 中 0），
 * 分隔线 hairline onSurface·α0.14 从标题端缩进 14。
 */
@Composable
fun PrismGroupedList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), content = content)
}

@Composable
fun groupRowShape(index: Int, count: Int): PrismShape {
    val density = LocalDensity.current
    val d = density.density
    return remember(index, count, d) {
        when {
            count <= 1 -> PrismShape(PrismCorner.Xl * d)
            index == 0 -> PrismShape(0f, corners = floatArrayOf(PrismCorner.Xl * d, PrismCorner.Xl * d, 0f, 0f))
            index == count - 1 -> PrismShape(0f, corners = floatArrayOf(0f, 0f, PrismCorner.Xl * d, PrismCorner.Xl * d))
            else -> PrismShape(0f)
        }
    }
}

/** 分组行：高 ≥56、水平 16、标题 17/副标题 15、ink 按压（index/count 决定圆角）。 */
@Composable
fun PrismRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    index: Int = 0,
    count: Int = 1,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(groupRowShape(index, count))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (leading != null) leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = bodyStyle(), color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = subheadlineStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) trailing()
    }
}

/** 分组分隔线（hairline，左缩进 14）。 */
@Composable
fun GroupDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .padding(start = 14.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
    )
}

/** 分组头：headline/onSurfaceVariant，T24 B8 L16 R16。 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = headlineStyle(),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** 分组 footer：footnote，T8。 */
@Composable
fun SectionFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = footnoteStyle(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
    )
}

// ---------------- 信息展示 ----------------

/** 带图标容器的列表行 leading（40dp primaryContainer 圆 + 24dp 图标）。 */
@Composable
fun IconBadge(icon: ImageVector, container: Color = MaterialTheme.colorScheme.primaryContainer) {
    Box(
        modifier = Modifier.size(40.dp).clip(PrismShape(PrismCorner.Md)).background(container),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * 空状态：插图 + 文案 stagger 入场（60ms 步进 + 12dp 上浮）。
 */
@Composable
fun NullStatus(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StaggerItem(shown, 0) {
            androidx.compose.material3.Icon(
                icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(44.dp),
            )
        }
        StaggerItem(shown, 1) {
            Text(title, style = headlineStyle(), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        }
        if (subtitle.isNotBlank()) {
            StaggerItem(shown, 2) {
                Text(
                    subtitle, style = subheadlineStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StaggerItem(shown: Boolean, index: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(220, delayMillis = 60 * index)) +
            slideInVertically(tween(220, delayMillis = 60 * index)) { it / 2 },
    ) { content() }
}

/** 小状态胶囊（MetaChip 风格：raised 底、sm8、caption2）。 */
@Composable
fun MetaChip(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val density = LocalDensity.current
    val shape = remember { PrismShape(PrismCorner.Sm * density.density, 0.4f) }
    Surface(
        modifier = modifier,
        color = color.takeIf { it != Color.Unspecified }
            ?: surfaceColorOf(SurfaceLevel.Inset),
        shape = shape,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}
