package com.union.campus.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.union.campus.ui.prism.NullStatus
import com.union.campus.ui.prism.PrismButton
import com.union.campus.ui.prism.PrismMotion
import com.union.campus.ui.prism.PrismSkeletonList
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.subheadlineStyle
import com.union.campus.ui.prism.surfaceColorOf
import com.union.campus.ui.prism.SurfaceLevel

/** 统一页面框架：透明顶栏 + Page 底色；tab 页可开启大标题折叠。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnionScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    largeTitle: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = if (largeTitle) {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    } else {
        null
    }
    val scaffoldModifier = if (scrollBehavior != null) {
        modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    } else {
        modifier
    }
    Scaffold(
        modifier = scaffoldModifier,
        containerColor = surfaceColorOf(SurfaceLevel.Page),
        topBar = {
            if (largeTitle && scrollBehavior != null) {
                val fraction = scrollBehavior.state.collapsedFraction
                MediumTopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = (24f + (17f - 24f) * fraction).sp,
                                lineHeight = (30f + (22f - 30f) * fraction).sp,
                                fontWeight = FontWeight(600),
                            ),
                        )
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                            }
                        }
                    },
                    actions = actions,
                    colors = TopAppBarDefaults.mediumTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = surfaceColorOf(SurfaceLevel.Page).copy(alpha = 0.94f),
                    ),
                    scrollBehavior = scrollBehavior,
                )
            } else {
                TopAppBar(
                    title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                            }
                        }
                    },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                )
            }
        },
    ) { padding ->
        content(padding)
    }
}

/** 列表页通用状态渲染：Loading / Failed / Empty / 内容，切换时淡入淡出。 */
@Composable
fun <T> StateContent(
    state: UiState<T>,
    modifier: Modifier = Modifier,
    emptyIcon: ImageVector? = null,
    retryText: String = "重试",
    onRetry: () -> Unit = {},
    ready: @Composable (T) -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = {
                fadeIn(PrismMotion.tween(PrismMotion.Quick)) togetherWith
                    fadeOut(PrismMotion.tween(PrismMotion.Fast))
            },
            label = "state-content",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            when (current) {
                is UiState.Loading -> PrismSkeletonList(
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                    rows = 4,
                )
                is UiState.Failed -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            current.message,
                            style = subheadlineStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        PrismButton(filled = false, onClick = onRetry) { Text(retryText) }
                        if (current.isAuth) {
                            Text(
                                "可到「我的 → 凭证中心」重新登录",
                                style = footnoteStyle(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                is UiState.Empty -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    NullStatus(
                        icon = emptyIcon ?: Icons.Rounded.Inbox,
                        title = "暂无数据",
                        subtitle = current.hint,
                    )
                }
                is UiState.Ready -> ready(current.data)
            }
        }
    }
}