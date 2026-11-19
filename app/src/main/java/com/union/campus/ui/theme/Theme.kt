package com.union.campus.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.union.campus.ui.prism.LocalPrismDark
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.LocalPressLook
import com.union.campus.ui.prism.LocalSurfaceLevel
import com.union.campus.ui.prism.PressLook
import com.union.campus.ui.prism.PrismMessenger
import com.union.campus.ui.prism.PrismPressIndication
import com.union.campus.ui.prism.PrismMaterialShapes
import com.union.campus.ui.prism.SurfaceLevel

/**
 * Union 主题：M3 动态取色 + Teal 备用；交互/形状/色阶复刻 FlClash prism_ui：
 * Prism 按压反馈 + M3 三态叠加、SurfaceLevel 体系、expressive 动效。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UnionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 默认走品牌预设：动态取色在不同壁纸下会生成发灰/发绿的 surface，校园 App 的识别度
    // 比"跟随壁纸"更重要；需要个性化时由设置页显式开启。
    dynamicColor: Boolean = false,
    presetColor: String = "teal",
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val messenger = remember { PrismMessenger() }
    // 字号缩放限制在 0.8–1.4：1.3x 下单行页头、Dock 与课表行仍完整。
    val density = LocalDensity.current
    val clampedDensity = remember(density) {
        Density(density.density, density.fontScale.coerceIn(0.8f, 1.4f))
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> PresetPalettes.scheme(presetColor, darkTheme)
            ?: if (darkTheme) DarkColors else LightColors
    }

    // 边到边：状态栏/导航栏图标明暗跟随主题，避免浅色主题下白图标不可见
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(darkTheme) {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
            onDispose {}
        }
    }

    CompositionLocalProvider(
        LocalDensity provides clampedDensity,
        // Prism 按压反馈作为默认 indication；不屏蔽 ripple，保证键盘/指针状态下有三态叠加
        LocalIndication provides PrismPressIndication,
        LocalPrismDark provides darkTheme,
        LocalSurfaceLevel provides SurfaceLevel.Page,
        LocalPressLook provides PressLook.Ink,
        LocalPrismMessenger provides messenger,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = UnionTypography,
            shapes = PrismMaterialShapes,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}
