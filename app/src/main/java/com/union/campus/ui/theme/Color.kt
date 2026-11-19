package com.union.campus.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Teal 备用配色（Android 12+ 优先使用壁纸动态取色）
// 草图指定的角色值原样引用，未指定的角色按 M3 关系推补。

// ---------- 浅色 ----------
private val TealPrimaryLight = Color(0xFF0B4F52)
private val TealOnPrimaryLight = Color(0xFFFDFFFF)
private val TealPrimaryContainerLight = Color(0xFF9FF0F5)
private val TealOnPrimaryContainerLight = Color(0xFF063639)

private val TealSecondaryLight = Color(0xFF354A4C)
private val TealSecondaryContainerLight = Color(0xFFCFE7E8)
private val TealOnSecondaryContainerLight = Color(0xFF1F3435)

private val TealTertiaryLight = Color(0xFF475F78)
private val TealTertiaryContainerLight = Color(0xFFD3E4FA)
private val TealOnTertiaryContainerLight = Color(0xFF213243)

private val TealSurfaceLight = Color(0xFFF2FBFB)
private val TealOnSurfaceLight = Color(0xFF161D1D)
private val TealOnSurfaceVariantLight = Color(0xFF2E3E3F)
private val TealSurfaceContainerLowLight = Color(0xFFEDF5F6)
private val TealSurfaceContainerLight = Color(0xFFE7EFF0)
private val TealSurfaceContainerHighLight = Color(0xFFE1EAEA)
private val TealSurfaceContainerHighestLight = Color(0xFFDCE4E4)

private val TealOutlineLight = Color(0xFF506263)
private val TealOutlineVariantLight = Color(0xFF8EA1A2)

private val TealInverseSurfaceLight = Color(0xFF2B3232)
private val TealInverseOnSurfaceLight = Color(0xFFEAF2F3)
private val TealInversePrimaryLight = Color(0xFF83D4D8)

private val TealErrorLight = Color(0xFFB3261E)
private val TealOnErrorLight = Color(0xFFFFFFFF)
private val TealErrorContainerLight = Color(0xFFF9DEDC)
private val TealOnErrorContainerLight = Color(0xFF410E0B)

// ---------- 深色 ----------
private val TealPrimaryDark = Color(0xFF91E2E7)
private val TealOnPrimaryDark = Color(0xFF063639)
private val TealPrimaryContainerDark = Color(0xFF0B4F52)
private val TealOnPrimaryContainerDark = Color(0xFFBFFCFF)

private val TealSecondaryDark = Color(0xFFC1D9DA)
private val TealSecondaryContainerDark = Color(0xFF354A4C)
private val TealOnSecondaryContainerDark = Color(0xFFDDF5F7)

private val TealTertiaryDark = Color(0xFFB1C9E8)
private val TealTertiaryContainerDark = Color(0xFF38485A)
private val TealOnTertiaryContainerDark = Color(0xFFE7F2FF)

private val TealSurfaceDark = Color(0xFF0D1515)
// 深色 Page 底色：比 surfaceContainer(#1A2121) 暗到 1.20:1 以上，
// 满足 PrismSurface 的 Page/Card 可辨阈值（原 #0D1515 仅 1.13:1）。
private val TealSurfaceDimDark = Color(0xFF060C0C)
private val TealOnSurfaceDark = Color(0xFFDCE4E4)
private val TealOnSurfaceVariantDark = Color(0xFFC4D8D9)
private val TealSurfaceContainerLowDark = Color(0xFF161D1D)
private val TealSurfaceContainerDark = Color(0xFF1A2121)
private val TealSurfaceContainerHighDark = Color(0xFF252B2B)
private val TealSurfaceContainerHighestDark = Color(0xFF2F3636)

private val TealOutlineDark = Color(0xFF9CAFB0)
private val TealOutlineVariantDark = Color(0xFF687A7B)

private val TealInverseSurfaceDark = Color(0xFFDCE4E4)
private val TealInverseOnSurfaceDark = Color(0xFF2B3232)
private val TealInversePrimaryDark = Color(0xFF00696E)

private val TealErrorDark = Color(0xFFF2B8B5)
private val TealOnErrorDark = Color(0xFF601410)
private val TealErrorContainerDark = Color(0xFF8C1D18)
private val TealOnErrorContainerDark = Color(0xFFF9DEDC)

val LightColors = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = TealOnPrimaryLight,
    primaryContainer = TealPrimaryContainerLight,
    onPrimaryContainer = TealOnPrimaryContainerLight,
    secondary = TealSecondaryLight,
    onSecondary = TealOnPrimaryLight,
    secondaryContainer = TealSecondaryContainerLight,
    onSecondaryContainer = TealOnSecondaryContainerLight,
    tertiary = TealTertiaryLight,
    onTertiary = TealOnPrimaryLight,
    tertiaryContainer = TealTertiaryContainerLight,
    onTertiaryContainer = TealOnTertiaryContainerLight,
    error = TealErrorLight,
    onError = TealOnErrorLight,
    errorContainer = TealErrorContainerLight,
    onErrorContainer = TealOnErrorContainerLight,
    background = TealSurfaceLight,
    onBackground = TealOnSurfaceLight,
    surface = TealSurfaceLight,
    onSurface = TealOnSurfaceLight,
    surfaceVariant = TealSurfaceContainerHighLight,
    onSurfaceVariant = TealOnSurfaceVariantLight,
    outline = TealOutlineLight,
    outlineVariant = TealOutlineVariantLight,
    scrim = Color(0xFF000000),
    inverseSurface = TealInverseSurfaceLight,
    inverseOnSurface = TealInverseOnSurfaceLight,
    inversePrimary = TealInversePrimaryLight,
    surfaceDim = Color(0xFFD5E1E1),
    surfaceBright = TealSurfaceLight,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = TealSurfaceContainerLowLight,
    surfaceContainer = TealSurfaceContainerLight,
    surfaceContainerHigh = TealSurfaceContainerHighLight,
    surfaceContainerHighest = TealSurfaceContainerHighestLight,
)

val DarkColors = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = TealOnPrimaryDark,
    primaryContainer = TealPrimaryContainerDark,
    onPrimaryContainer = TealOnPrimaryContainerDark,
    secondary = TealSecondaryDark,
    onSecondary = Color(0xFF1F3435),
    secondaryContainer = TealSecondaryContainerDark,
    onSecondaryContainer = TealOnSecondaryContainerDark,
    tertiary = TealTertiaryDark,
    onTertiary = Color(0xFF1A3141),
    tertiaryContainer = TealTertiaryContainerDark,
    onTertiaryContainer = TealOnTertiaryContainerDark,
    error = TealErrorDark,
    onError = TealOnErrorDark,
    errorContainer = TealErrorContainerDark,
    onErrorContainer = TealOnErrorContainerDark,
    background = TealSurfaceDark,
    onBackground = TealOnSurfaceDark,
    surface = TealSurfaceDark,
    onSurface = TealOnSurfaceDark,
    surfaceVariant = TealSurfaceContainerHighDark,
    onSurfaceVariant = TealOnSurfaceVariantDark,
    outline = TealOutlineDark,
    outlineVariant = TealOutlineVariantDark,
    scrim = Color(0xFF000000),
    inverseSurface = TealInverseSurfaceDark,
    inverseOnSurface = TealInverseOnSurfaceDark,
    inversePrimary = TealInversePrimaryDark,
    surfaceDim = TealSurfaceDimDark,
    surfaceBright = Color(0xFF394141),
    surfaceContainerLowest = Color(0xFF081111),
    surfaceContainerLow = TealSurfaceContainerLowDark,
    surfaceContainer = TealSurfaceContainerDark,
    surfaceContainerHigh = TealSurfaceContainerHighDark,
    surfaceContainerHighest = TealSurfaceContainerHighestDark,
)
