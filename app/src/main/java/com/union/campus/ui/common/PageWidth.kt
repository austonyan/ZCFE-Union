package com.union.campus.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 页面正文的最大宽度；M3 建议大屏正文控制在 840dp 以内，避免超长行宽。 */
val PageMaxWidth: Dp = 840.dp

/**
 * 限制页面内容的最大宽度并水平居中。
 *
 * 调用方直接套在页面的根容器（LazyColumn / verticalScroll 的 Column 等）上即可：
 * 外层仍占满父容器的可用宽度，只有向下传递的内容约束被收窄到 [max] 并居中。
 * 因此不依赖可滚动容器的测量特性，全屏背景、手势热区也不会跟着缩小。
 */
fun Modifier.pageWidth(max: Dp = PageMaxWidth): Modifier =
    this
        .fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = max)