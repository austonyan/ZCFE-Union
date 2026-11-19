package com.union.campus.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.union.campus.ui.prism.NullStatus
import com.union.campus.ui.prism.PrismIconButton
import com.union.campus.ui.prism.PrismMotion
import com.union.campus.ui.prism.PrismSkeletonList
import com.union.campus.ui.prism.footnoteStyle
import com.union.campus.ui.prism.headlineStyle
import com.union.campus.ui.prism.surfaceColorOf
import com.union.campus.ui.prism.SurfaceLevel

/** 统一页面框架：透明顶栏 + Page 底色；tab 页自带页头，不使用这里的顶栏。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnionScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = surfaceColorOf(SurfaceLevel.Page),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = headlineStyle())
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = footnoteStyle(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        PrismIconButton(
                            icon = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                            onClick = onBack,
                        )
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        // 平板/折叠屏展开时限制正文宽度，避免超长行宽不可读（M3: 840–1040dp 上限）
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .pageWidth(),
            ) {
                content(PaddingValues())
            }
        }
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
                    com.union.campus.ui.prism.PrismErrorState(
                        message = current.message,
                        hint = if (current.isAuth) "可到「我的 → 凭证中心」重新登录" else null,
                        onRetry = onRetry,
                    )
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