package com.union.campus.ui.prism

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 语义状态色：只用于状态点、进度、图表和轻提示，不做大面积底色。
 * 亮/暗两套，保证在动态取色下仍有稳定的“空闲/将过期/信息”含义。
 */
object PrismStatus {
    private val SuccessLight = Color(0xFF2E7D4F)
    private val SuccessContainerLight = Color(0xFFDDF3E4)
    private val OnSuccessContainerLight = Color(0xFF0F3D27)

    private val WarningLight = Color(0xFF9A6700)
    private val WarningContainerLight = Color(0xFFFFF1D6)
    private val OnWarningContainerLight = Color(0xFF4A3000)

    private val InfoLight = Color(0xFF2F5D8A)
    private val InfoContainerLight = Color(0xFFDDEBFA)
    private val OnInfoContainerLight = Color(0xFF17324A)

    private val SuccessDark = Color(0xFF7FD8A4)
    private val SuccessContainerDark = Color(0xFF173D2A)
    private val OnSuccessContainerDark = Color(0xFFC9F2DC)

    private val WarningDark = Color(0xFFF2C14E)
    private val WarningContainerDark = Color(0xFF3D2E10)
    private val OnWarningContainerDark = Color(0xFFFFE7B0)

    private val InfoDark = Color(0xFF9FC6EA)
    private val InfoContainerDark = Color(0xFF1B3348)
    private val OnInfoContainerDark = Color(0xFFD6E8FA)

    @Composable
    fun success(): Color = if (LocalPrismDark.current) SuccessDark else SuccessLight

    @Composable
    fun successContainer(): Color = if (LocalPrismDark.current) SuccessContainerDark else SuccessContainerLight

    @Composable
    fun onSuccessContainer(): Color = if (LocalPrismDark.current) OnSuccessContainerDark else OnSuccessContainerLight

    @Composable
    fun warning(): Color = if (LocalPrismDark.current) WarningDark else WarningLight

    @Composable
    fun warningContainer(): Color = if (LocalPrismDark.current) WarningContainerDark else WarningContainerLight

    @Composable
    fun onWarningContainer(): Color = if (LocalPrismDark.current) OnWarningContainerDark else OnWarningContainerLight

    @Composable
    fun info(): Color = if (LocalPrismDark.current) InfoDark else InfoLight

    @Composable
    fun infoContainer(): Color = if (LocalPrismDark.current) InfoContainerDark else InfoContainerLight

    @Composable
    fun onInfoContainer(): Color = if (LocalPrismDark.current) OnInfoContainerDark else OnInfoContainerLight
}