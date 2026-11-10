package com.union.campus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.union.campus.ui.prism.LocalPrismDark
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.LocalPressLook
import com.union.campus.ui.prism.LocalSurfaceLevel
import com.union.campus.ui.prism.PressLook
import com.union.campus.ui.prism.PrismMessenger
import com.union.campus.ui.prism.PrismPressIndication
import com.union.campus.ui.prism.SurfaceLevel

/**
 * Union 主题：M3 动态取色 + Teal 备用；交互/形状/色阶复刻 FlClash prism_ui：
 * 全局无 ripple、iOS 式按压反馈、SurfaceLevel 体系、expressive 动效。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UnionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val messenger = remember { PrismMessenger() }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    CompositionLocalProvider(
        LocalIndication provides PrismPressIndication,
        LocalRippleConfiguration provides null,
        LocalPrismDark provides darkTheme,
        LocalSurfaceLevel provides SurfaceLevel.Page,
        LocalPressLook provides PressLook.Ink,
        LocalPrismMessenger provides messenger,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = UnionTypography,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}
