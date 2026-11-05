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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.union.campus.MainActivityHolder
import kotlinx.coroutines.flow.first
import com.union.campus.di.ServiceLocator
import com.union.campus.domain.CourseCell
import com.union.campus.navigation.Screen
import com.union.campus.ui.prism.surfaceColorOf
import com.union.campus.ui.prism.SurfaceLevel
import com.union.campus.ui.screens.life.ClassroomDetailScreen
import com.union.campus.ui.screens.life.DeviceDetailScreen
import com.union.campus.ui.screens.life.ElectricScreen
import com.union.campus.ui.screens.life.ElectricTopUpScreen
import com.union.campus.ui.screens.life.FreeClassroomsScreen
import com.union.campus.ui.screens.life.LifeScreen
import com.union.campus.ui.screens.life.ShowerScreen
import com.union.campus.ui.screens.life.ShowerSessionScreen
import com.union.campus.ui.screens.life.UsageDetailScreen
import com.union.campus.ui.screens.life.WalletScreen
import com.union.campus.ui.screens.me.CredentialsCenterScreen
import com.union.campus.ui.screens.me.PrivacyScreen
import com.union.campus.ui.screens.me.ProfileScreen
import com.union.campus.ui.screens.me.RemindersCenterScreen
import com.union.campus.ui.screens.onboarding.BindDoneScreen
import com.union.campus.ui.screens.onboarding.BindElectricScreen
import com.union.campus.ui.screens.onboarding.LoginAcademicScreen
import com.union.campus.ui.screens.onboarding.LoginShowerScreen
import com.union.campus.ui.screens.onboarding.WelcomeScreen
import com.union.campus.ui.screens.study.AcademicAffairsScreen
import com.union.campus.ui.screens.study.ConfirmSelectionScreen
import com.union.campus.ui.screens.study.CourseDetailScreen
import com.union.campus.ui.screens.study.CourseSelectScreen
import com.union.campus.ui.screens.study.EvaluationScreen
import com.union.campus.ui.screens.study.ExamDetailScreen
import com.union.campus.ui.screens.study.ExamsRoute
import com.union.campus.ui.screens.study.GradeDetailScreen
import com.union.campus.ui.screens.study.GradesScreen
import com.union.campus.ui.screens.study.NoticeDetailScreen
import com.union.campus.ui.screens.study.NoticesScreen
import com.union.campus.ui.screens.study.StudentRecordScreen
import com.union.campus.ui.screens.study.TextbooksScreen
import com.union.campus.ui.screens.study.TimetableRoute
import com.union.campus.ui.screens.today.TodayScreen

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
    val showerRepo = ServiceLocator.showerRepository
    val activeSession = showerRepo.activeSession.value

    // 已完成引导/绑定的用户冷启动直接进今天页
    val startRoute = produceState(initialValue = "", producer = {
        value = if (ServiceLocator.settings.boundDone.first()) Screen.Today.route else Screen.Welcome.route
    })

    Scaffold(
        modifier = modifier,
        containerColor = surfaceColorOf(SurfaceLevel.Page),
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
        if (startRoute.value.isEmpty()) {
            Box(Modifier.fillMaxSize()) // 启动判定帧
            return@Scaffold
        }
        NavHost(
            navController = navController,
            startDestination = startRoute.value,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            enterTransition = { slideInHorizontally(PushOffsetSpec) { it } },
            exitTransition = { slideOutHorizontally(PushOffsetSpec) { -it / 3 } + fadeOut(TabFade) },
            popEnterTransition = { slideInHorizontally(PushOffsetSpec) { -it / 3 } + fadeIn(TabFade) },
            popExitTransition = { slideOutHorizontally(PushOffsetSpec) { it } },
        ) {
            // ---------- 绑定流程 ----------
            composable(Screen.Welcome.route) {
                WelcomeScreen(
                    onStartBinding = { navController.navigate(Screen.LoginAcademic.route) },
                    onExplore = {
                        navController.navigate(Screen.Today.route) {
                            popUpTo(Screen.Welcome.route) { inclusive = true }
                        }
                    },
                )
            }
            composable(Screen.LoginAcademic.route) {
                LoginAcademicScreen(
                    onBack = { navController.popBackStack() },
                    onNext = { navController.navigate(Screen.LoginShower.route) },
                )
            }
            composable(Screen.LoginShower.route) {
                LoginShowerScreen(
                    onBack = { navController.popBackStack() },
                    onNext = { navController.navigate(Screen.BindElectric.route) },
                )
            }
            composable(Screen.BindElectric.route) {
                BindElectricScreen(
                    onBack = { navController.popBackStack() },
                    onNext = { navController.navigate(Screen.BindDone.route) },
                )
            }
            composable(Screen.BindDone.route) {
                BindDoneScreen(
                    onEnter = {
                        navController.navigate(Screen.Today.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }

            // ---------- 今天（tab） ----------
            tabRoute(Screen.Today.route) {
                TodayScreen(
                    onOpenTimetable = { navController.navigate(Screen.Timetable.route) },
                    onOpenCourse = { navController.navigate(Screen.Timetable.route) },
                    onOpenEvaluation = { navController.navigate(Screen.Evaluation.route) },
                    onOpenExams = { navController.navigate(Screen.Exams.route) },
                    onOpenElectric = { navController.navigate(Screen.Electric.route) },
                    onOpenShower = { navController.navigate(Screen.Shower.route) },
                    onOpenLife = { navController.navigate(Screen.Life.route) },
                    onOpenReminders = { navController.navigate(Screen.Reminders.route) },
                )
            }

            // ---------- 学习（tab） ----------
            tabRoute(Screen.Timetable.route) {
                TimetableRoute(
                    onOpenCourse = { cell ->
                        AppState.selectedCourse = cell
                        navController.navigate(Screen.CourseDetail.route)
                    },
                    onOpenGrades = { navController.navigate(Screen.Grades.route) },
                    onOpenFreeClassrooms = { navController.navigate(Screen.FreeClassrooms.route) },
                )
            }

            // ---------- 生活（tab） ----------
            tabRoute(Screen.Life.route) {
                LifeScreen(
                    onOpenElectric = { navController.navigate(Screen.Electric.route) },
                    onOpenShower = { navController.navigate(Screen.Shower.route) },
                    onOpenFreeClassrooms = { navController.navigate(Screen.FreeClassrooms.route) },
                    onOpenPrivacy = { navController.navigate(Screen.Privacy.route) },
                )
            }

            // ---------- 我的（tab） ----------
            tabRoute(Screen.Profile.route) {
                ProfileScreen(
                    onOpenCredentials = { navController.navigate(Screen.Credentials.route) },
                    onOpenAcademicAffairs = { navController.navigate(Screen.AcademicAffairs.route) },
                    onOpenReminders = { navController.navigate(Screen.Reminders.route) },
                    onOpenPrivacy = { navController.navigate(Screen.Privacy.route) },
                    onOpenSettings = { navController.navigate(Screen.Privacy.route) },
                )
            }

            // ---------- 学习详情 ----------
            composable(Screen.CourseDetail.route) {
                val cell = AppState.selectedCourse
                    ?: CourseCell("0", "未选择课程", "", "", 1, "1-2节", 1, 2, "")
                CourseDetailScreen(
                    cell = cell,
                    onBack = { navController.popBackStack() },
                    onOpenTimetable = { navController.navigate(Screen.Timetable.route) { launchSingleTop = true } },
                )
            }
            composable(Screen.Grades.route) {
                GradesScreen(
                    onOpenGrade = { g ->
                        AppState.selectedGrade = g
                        navController.navigate(Screen.GradeDetail.route)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Screen.GradeDetail.route) {
                val g = AppState.selectedGrade
                if (g == null) {
                    navController.popBackStack()
                } else {
                    GradeDetailScreen(grade = g, onBack = { navController.popBackStack() })
                }
            }
            composable(Screen.Exams.route) {
                ExamsRoute(
                    onOpenExam = { e ->
                        AppState.selectedExam = e
                        navController.navigate(Screen.ExamDetail.route)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Screen.ExamDetail.route) {
                val e = AppState.selectedExam
                if (e == null) {
                    navController.popBackStack()
                } else {
                    ExamDetailScreen(exam = e, onBack = { navController.popBackStack() })
                }
            }
            composable(Screen.CourseSelect.route) {
                CourseSelectScreen(
                    onBack = { navController.popBackStack() },
                    onConfirm = { c ->
                        AppState.confirmCourse = c
                        navController.navigate(Screen.ConfirmSelection.route)
                    },
                )
            }
            composable(Screen.ConfirmSelection.route) {
                val c = AppState.confirmCourse
                if (c == null) {
                    navController.popBackStack()
                } else {
                    ConfirmSelectionScreen(
                        course = c,
                        onBack = { navController.popBackStack() },
                        onSubmitted = { navController.popBackStack() },
                    )
                }
            }
            composable(Screen.Evaluation.route) {
                EvaluationScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Textbooks.route) {
                TextbooksScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.AcademicAffairs.route) {
                AcademicAffairsScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { link ->
                        when (link) {
                            "record" -> navController.navigate(Screen.StudentRecord.route)
                            "select" -> navController.navigate(Screen.CourseSelect.route)
                            "textbooks" -> navController.navigate(Screen.Textbooks.route)
                            "evaluation" -> navController.navigate(Screen.Evaluation.route)
                            "notices", "change", "exemption" -> navController.navigate(Screen.Notices.route)
                            "freeClassrooms" -> navController.navigate(Screen.FreeClassrooms.route)
                            else -> navController.navigate(Screen.Notices.route)
                        }
                    },
                )
            }
            composable(Screen.StudentRecord.route) {
                StudentRecordScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Notices.route) {
                NoticesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenNotice = { n ->
                        AppState.selectedNotice = n
                        navController.navigate(Screen.NoticeDetail.route)
                    },
                )
            }
            composable(Screen.NoticeDetail.route) {
                val n = AppState.selectedNotice
                if (n == null) {
                    navController.popBackStack()
                } else {
                    NoticeDetailScreen(
                        notice = n,
                        onBack = { navController.popBackStack() },
                        onOpenExams = { navController.navigate(Screen.Exams.route) },
                    )
                }
            }

            // ---------- 生活详情 ----------
            composable(Screen.Shower.route) {
                ShowerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenWallet = { navController.navigate(Screen.Wallet.route) },
                    onOpenBind = { navController.navigate(Screen.LoginShower.route) },
                    onOpenDevice = { d, area ->
                        AppState.selectedDevice = d
                        AppState.selectedAreaName = area?.name ?: ""
                        navController.navigate(Screen.DeviceDetail.route)
                    },
                    onOpenSession = { navController.navigate(Screen.ShowerSession.route) },
                )
            }
            composable(Screen.DeviceDetail.route) {
                val d = AppState.selectedDevice
                if (d == null) {
                    navController.popBackStack()
                } else {
                    DeviceDetailScreen(
                        device = d,
                        areaName = AppState.selectedAreaName,
                        onBack = { navController.popBackStack() },
                        onOpenSession = { navController.navigate(Screen.ShowerSession.route) },
                    )
                }
            }
            composable(Screen.ShowerSession.route) {
                val session = showerRepo.activeSession.value
                if (session == null) {
                    navController.popBackStack()
                } else {
                    ShowerSessionScreen(
                        session = session,
                        onBack = { navController.popBackStack() },
                        onOpenWallet = { navController.navigate(Screen.Wallet.route) },
                    )
                }
            }
            composable(Screen.Wallet.route) {
                WalletScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Electric.route) {
                ElectricScreen(
                    onBack = { navController.popBackStack() },
                    onOpenUsage = { navController.navigate(Screen.UsageDetail.route) },
                    onOpenTopUp = { navController.navigate(Screen.ElectricTopUp.route) },
                    onOpenBind = { navController.navigate(Screen.BindElectric.route) },
                )
            }
            composable(Screen.UsageDetail.route) {
                UsageDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.ElectricTopUp.route) {
                ElectricTopUpScreen(
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() },
                )
            }
            composable(Screen.FreeClassrooms.route) {
                FreeClassroomsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.ClassroomDetail.route) {
                ClassroomDetailScreen(
                    room = AppState.selectedAreaName.ifBlank { "教室" },
                    onBack = { navController.popBackStack() },
                )
            }

            // ---------- 我的详情 ----------
            composable(Screen.Credentials.route) {
                CredentialsCenterScreen(
                    onBack = { navController.popBackStack() },
                    onLogoutAll = {
                        navController.navigate(Screen.Welcome.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
            composable(Screen.Reminders.route) {
                RemindersCenterScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Privacy.route) {
                PrivacyScreen(
                    onBack = { navController.popBackStack() },
                    onWipeAll = {
                        navController.navigate(Screen.Welcome.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    requestBiometric = { MainActivityHolder.requestBiometric() },
                )
            }
        }
    }
}

/** 标签页路由：淡入淡出。 */
private fun NavGraphBuilder.tabRoute(
    route: String,
    content: @Composable () -> Unit,
) {
    composable(
        route = route,
        enterTransition = { fadeIn(TabFade) },
        exitTransition = { fadeOut(TabFade) },
        popEnterTransition = { fadeIn(TabFade) },
        popExitTransition = { fadeOut(TabFade) },
    ) { content() }
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
        containerColor = surfaceColorOf(SurfaceLevel.Page),
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
