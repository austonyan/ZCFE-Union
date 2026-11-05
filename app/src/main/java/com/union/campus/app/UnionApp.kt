package com.union.campus.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.foundation.layout.padding
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

// 详情页：从右侧滑入并带回弹；返回时反向播放（predictive back 由系统手势触发同款效果）
private val SlideSpec = spring<IntOffset>(
    dampingRatio = 0.9f,
    stiffness = Spring.StiffnessMediumLow,
)
private val Fade = tween<Float>(250, easing = FastOutSlowInEasing)

@Composable
fun UnionApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentScreen = Screen.fromRoute(backStackEntry?.destination?.route)

    Scaffold(
        modifier = modifier,
        bottomBar = {
            AnimatedVisibility(
                visible = currentScreen in Screen.Tabs,
                enter = slideInVertically(SlideSpec) { it } + fadeIn(Fade),
                exit = slideOutVertically(SlideSpec) { it } + fadeOut(Fade),
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
            enterTransition = { slideInHorizontally(SlideSpec) { it } + fadeIn(Fade) },
            exitTransition = { slideOutHorizontally(SlideSpec) { -it / 3 } + fadeOut(Fade) },
            popEnterTransition = { slideInHorizontally(SlideSpec) { -it / 3 } + fadeIn(Fade) },
            popExitTransition = { slideOutHorizontally(SlideSpec) { it } + fadeOut(Fade) },
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

                    in Screen.Tabs -> composable( // when 分支的 in 使用 List.contains
                        route = screen.route,
                        enterTransition = { fadeIn(Fade) },
                        exitTransition = { fadeOut(Fade) },
                        popEnterTransition = { fadeIn(Fade) },
                        popExitTransition = { fadeOut(Fade) },
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
    NavigationBar {
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
