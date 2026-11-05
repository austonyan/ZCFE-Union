package com.union.campus.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.union.campus.navigation.Screen
import com.union.campus.ui.screens.onboarding.WelcomeScreen
import com.union.campus.ui.screens.placeholder.ScreenPlaceholder

/**
 * 页面转场（FlClash page_transition 复刻）：iOS 27 push——
 * 新页 300ms 临界阻尼弹簧从右滑入；被覆盖页回退 30% 并淡出。
 */
private val PushEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val PushSpec = tween<Float>(300, easing = PushEasing)
private val PushOffsetSpec = tween<IntOffset>(300, easing = PushEasing)
private val TabFade = tween<Float>(220, easing = PushEasing)

@Composable
fun UnionApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentScreen = Screen.fromRoute(backStackEntry?.destination?.route)

    Scaffold(
        modifier = modifier,
        containerColor = com.union.campus.ui.prism.surfaceColorOf(com.union.campus.ui.prism.SurfaceLevel.Page),
        bottomBar = {
            AnimatedVisibility(
                visible = currentScreen in Screen.Tabs,
                enter = slideInVertically(PushOffsetSpec) { it } + fadeIn(TabFade),
                exit = slideOutVertically(PushOffsetSpec) { it } + fadeOut(TabFade),
            ) {
                UnionNavigationBar(
                    current = currentScreen,
                    onSelect = { tab -> navigateToTab(navController, tab) },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Welcome.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            // push：从右整幅滑入；被覆盖页向左回退 30%
            enterTransition = { slideInHorizontally(PushOffsetSpec) { it } },
            exitTransition = { slideOutHorizontally(PushOffsetSpec) { -it / 3 } + fadeOut(TabFade) },
            popEnterTransition = { slideInHorizontally(PushOffsetSpec) { -it / 3 } + fadeIn(TabFade) },
            popExitTransition = { slideOutHorizontally(PushOffsetSpec) { it } },
        ) {
            Screen.entries.forEach { screen ->
                when (screen) {
                    Screen.Welcome -> composable(Screen.Welcome.route) {
                        WelcomeScreen(
                            onStartBinding = { navController.navigate(Screen.LoginAcademic.route) },
                            onExplore = {
                                navController.navigate(Screen.Today.route) {
                                    popUpTo(Screen.Welcome.route) { inclusive = true }
                                }
                            },
                        )
                    }

                    in Screen.Tabs -> composable(
                        route = screen.route,
                        enterTransition = { fadeIn(TabFade) },
                        exitTransition = { fadeOut(TabFade) },
                        popEnterTransition = { fadeIn(TabFade) },
                        popExitTransition = { fadeOut(TabFade) },
                    ) {
                        ScreenPlaceholder(screen = screen, showBack = false)
                    }

                    else -> composable(screen.route) {
                        ScreenPlaceholder(
                            screen = screen,
                            showBack = true,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

private fun navigateToTab(navController: NavHostController, tab: Screen) {
    navController.navigate(tab.route) {
        popUpTo(Screen.Today.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun UnionNavigationBar(current: Screen?, onSelect: (Screen) -> Unit) {
    NavigationBar(
        containerColor = com.union.campus.ui.prism.surfaceColorOf(com.union.campus.ui.prism.SurfaceLevel.Page),
    ) {
        Screen.Tabs.forEach { tab ->
            NavigationBarItem(
                selected = current == tab,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        imageVector = tab.tabIcon ?: Icons.Rounded.Home,
                        contentDescription = tab.title,
                    )
                },
                label = { Text(tab.title) },
            )
        }
    }
}
