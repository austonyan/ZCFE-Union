package com.union.campus.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
import com.union.campus.ui.prism.LocalPrismMessenger
import com.union.campus.ui.prism.PrismDock
import com.union.campus.ui.prism.PrismMotion
import com.union.campus.ui.prism.PrismSnackbarHost
import com.union.campus.ui.prism.PrismSpec
import com.union.campus.ui.screens.life.ClassroomDetailScreen
import com.union.campus.ui.screens.life.DeviceDetailScreen
import com.union.campus.ui.screens.life.ElectricScreen
import com.union.campus.ui.screens.life.FreeClassroomsScreen
import com.union.campus.ui.screens.life.LifeScreen
import com.union.campus.ui.screens.life.ShowerOrdersScreen
import com.union.campus.ui.screens.life.ShowerScreen
import com.union.campus.ui.screens.life.ShowerSessionScreen
import com.union.campus.ui.screens.life.UsageDetailScreen
import com.union.campus.ui.screens.life.WalletScreen
import com.union.campus.ui.screens.me.CredentialsCenterScreen
import com.union.campus.ui.screens.me.AboutScreen
import com.union.campus.ui.screens.me.PrivacyScreen
import com.union.campus.ui.screens.me.ProfileScreen
import com.union.campus.ui.screens.me.RemindersCenterScreen
import com.union.campus.ui.screens.me.SettingsScreen
import com.union.campus.ui.screens.onboarding.BindDoneScreen
import com.union.campus.ui.screens.onboarding.BindElectricScreen
import com.union.campus.ui.screens.onboarding.LoginAcademicScreen
import com.union.campus.ui.screens.onboarding.LoginShowerScreen
import com.union.campus.ui.screens.onboarding.WelcomeScreen
import com.union.campus.ui.screens.study.AcademicAffairsScreen
import com.union.campus.ui.screens.study.AcademicsScreen
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
import com.union.campus.ui.screens.study.TimetableSettingsScreen
import com.union.campus.ui.screens.today.TodayScreen

/**
 * 页面转场：详情页走 24dp 短位移 push/pop（[PrismSpec]），
 * 五个 tab 走 fade-through 微缩放，底部 dock 仍用纵向滑动。
 */
private val PushOffsetSpec = PrismMotion.tween<IntOffset>(PrismMotion.Emphasized)
private val TabFade = PrismMotion.tween<Float>(PrismMotion.Standard)

@Composable
fun UnionApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    var credentialLoginTarget by remember { mutableStateOf<String?>(null) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentScreen = Screen.fromRoute(backStackEntry?.destination?.route)
    val showerRepo = ServiceLocator.showerRepository
    val activeSession by showerRepo.activeSession.collectAsState(initial = null)
    val messenger = LocalPrismMessenger.current

    // 已完成引导/绑定的用户冷启动直接进今天页
    val startRoute = produceState(initialValue = "", producer = {
        value = if (ServiceLocator.settings.boundDone.first()) Screen.Today.route else Screen.Welcome.route
    })

    Scaffold(
        modifier = modifier,
        containerColor = surfaceColorOf(SurfaceLevel.Page),
        snackbarHost = { PrismSnackbarHost(messenger) },
        bottomBar = {
            AnimatedVisibility(
                visible = currentScreen in Screen.Tabs,
                enter = slideInVertically(PushOffsetSpec) { it } + fadeIn(TabFade),
                exit = slideOutVertically(PushOffsetSpec) { it } + fadeOut(TabFade),
            ) {
                PrismDock(
                    tabs = Screen.Tabs,
                    current = currentScreen,
                    onSelect = { tab -> navigateToTab(navController, tab) },
                    lifeActive = activeSession != null,
                    modifier = Modifier
                        .padding(horizontal = 21.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
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
            enterTransition = { PrismSpec.pushEnter },
            exitTransition = { PrismSpec.pushExit },
            popEnterTransition = { PrismSpec.popEnter },
            popExitTransition = { PrismSpec.popExit },
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
                val credentialMode = credentialLoginTarget == "jw"
                DisposableEffect(Unit) {
                    onDispose {
                        if (credentialLoginTarget == "jw") credentialLoginTarget = null
                    }
                }
                LoginAcademicScreen(
                    onBack = {
                        credentialLoginTarget = null
                        navController.popBackStack()
                    },
                    onNext = {
                        if (credentialMode) {
                            credentialLoginTarget = null
                            navController.popBackStack()
                        } else {
                            navController.navigate(Screen.LoginShower.route)
                        }
                    },
                    showProgress = !credentialMode,
                    skipLabel = if (credentialMode) "取消" else "稍后绑定",
                )
            }
            composable(Screen.LoginShower.route) {
                val credentialMode = credentialLoginTarget == "shower"
                DisposableEffect(Unit) {
                    onDispose {
                        if (credentialLoginTarget == "shower") credentialLoginTarget = null
                    }
                }
                LoginShowerScreen(
                    onBack = {
                        credentialLoginTarget = null
                        navController.popBackStack()
                    },
                    onNext = {
                        if (credentialMode) {
                            credentialLoginTarget = null
                            navController.popBackStack()
                        } else {
                            navController.navigate(Screen.BindElectric.route)
                        }
                    },
                    showProgress = !credentialMode,
                    skipLabel = if (credentialMode) "取消" else "稍后绑定",
                )
            }
            composable(Screen.BindElectric.route) {
                val credentialMode = credentialLoginTarget == "electric"
                DisposableEffect(Unit) {
                    onDispose {
                        if (credentialLoginTarget == "electric") credentialLoginTarget = null
                    }
                }
                BindElectricScreen(
                    onBack = {
                        credentialLoginTarget = null
                        navController.popBackStack()
                    },
                    onNext = {
                        if (credentialMode) {
                            credentialLoginTarget = null
                            navController.popBackStack()
                        } else {
                            navController.navigate(Screen.BindDone.route)
                        }
                    },
                    showProgress = !credentialMode,
                    skipLabel = if (credentialMode) "取消" else "稍后绑定",
                )
            }
            composable(Screen.BindElectricAdd.route) {
                BindElectricScreen(
                    onBack = { navController.popBackStack() },
                    onNext = {
                        // 从电费页进入：加完房间回到电费页并重新加载
                        navController.navigate(Screen.Electric.route) {
                            popUpTo(Screen.Electric.route) { inclusive = true }
                        }
                    },
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
                    onOpenTimetable = { navigateToTab(navController, Screen.Timetable) },
                    onOpenCourse = { navigateToTab(navController, Screen.Timetable) },
                    onOpenEvaluation = { navController.navigate(Screen.Evaluation.route) },
                    onOpenExams = { navController.navigate(Screen.Exams.route) },
                    onOpenElectric = { navController.navigate(Screen.Electric.route) },
                    onOpenShower = { navController.navigate(Screen.Shower.route) },
                    onOpenLife = { navigateToTab(navController, Screen.Life) },
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
                    onOpenTimetableSettings = { navController.navigate(Screen.TimetableSettings.route) },
                )
            }

            // ---------- 教务（tab） ----------
            tabRoute(Screen.Academics.route) {
                AcademicsScreen(
                    onOpen = { link ->
                        when (link) {
                            "timetable" -> navigateToTab(navController, Screen.Timetable)
                            "grades" -> navController.navigate(Screen.Grades.route)
                            "exams" -> navController.navigate(Screen.Exams.route)
                            "select" -> navController.navigate(Screen.CourseSelect.route)
                            "evaluation" -> navController.navigate(Screen.Evaluation.route)
                            "textbooks" -> navController.navigate(Screen.Textbooks.route)
                            "record" -> navController.navigate(Screen.StudentRecord.route)
                            "freeClassrooms" -> navController.navigate(Screen.FreeClassrooms.route)
                            "notices" -> navController.navigate(Screen.Notices.route)
                            "affairs" -> navController.navigate(Screen.AcademicAffairs.route)
                        }
                    },
                    onOpenNotice = { n ->
                        AppState.selectedNotice = n
                        navController.navigate(Screen.NoticeDetail.route)
                    },
                )
            }

            // ---------- 生活（tab） ----------
            tabRoute(Screen.Life.route) {
                LifeScreen(
                    onOpenElectric = { navController.navigate(Screen.Electric.route) },
                    onOpenShower = { navController.navigate(Screen.Shower.route) },
                    onOpenFreeClassrooms = { navController.navigate(Screen.FreeClassrooms.route) },
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                )
            }

            // ---------- 我的（tab） ----------
            tabRoute(Screen.Profile.route) {
                ProfileScreen(
                    onOpenCredentials = { navController.navigate(Screen.Credentials.route) },
                    onOpenReminders = { navController.navigate(Screen.Reminders.route) },
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    onOpenAbout = { navController.navigate(Screen.About.route) },
                )
            }

            // ---------- 学习详情 ----------
            composable(Screen.TimetableSettings.route) {
                TimetableSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.CourseDetail.route) {
                val cell = AppState.selectedCourse
                    ?: CourseCell("0", "未选择课程", "", "", 1, "1-2节", 1, 2, "")
                CourseDetailScreen(
                    cell = cell,
                    onBack = { navController.popBackStack() },
                    onOpenTimetable = { navigateToTab(navController, Screen.Timetable) },
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
                val session = activeSession
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
                WalletScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAllOrders = { navController.navigate(Screen.ShowerOrders.route) },
                )
            }
            composable(Screen.ShowerOrders.route) {
                ShowerOrdersScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Electric.route) {
                ElectricScreen(
                    onBack = { navController.popBackStack() },
                    onOpenUsage = { navController.navigate(Screen.UsageDetail.route) },
                    onOpenBind = { navController.navigate(Screen.BindElectricAdd.route) },
                )
            }
            composable(Screen.UsageDetail.route) {
                UsageDetailScreen(onBack = { navController.popBackStack() })
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
                    onLoginAcademic = {
                        credentialLoginTarget = "jw"
                        navController.navigate(Screen.LoginAcademic.route)
                    },
                    onLoginShower = {
                        credentialLoginTarget = "shower"
                        navController.navigate(Screen.LoginShower.route)
                    },
                    onLoginElectric = {
                        credentialLoginTarget = "electric"
                        navController.navigate(Screen.BindElectric.route)
                    },
                )
            }
            composable(Screen.Reminders.route) {
                RemindersCenterScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTimetableSettings = { navController.navigate(Screen.TimetableSettings.route) },
                    onOpenReminders = { navController.navigate(Screen.Reminders.route) },
                    onOpenPrivacy = { navController.navigate(Screen.Privacy.route) },
                    onOpenAbout = { navController.navigate(Screen.About.route) },
                )
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
            composable(Screen.About.route) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/** 标签页路由：fade-through + 0.98→1 微缩放。 */
private fun NavGraphBuilder.tabRoute(
    route: String,
    content: @Composable () -> Unit,
) {
    composable(
        route = route,
        enterTransition = { PrismSpec.tabEnter },
        exitTransition = { PrismSpec.tabExit },
        popEnterTransition = { PrismSpec.tabEnter },
        popExitTransition = { PrismSpec.tabExit },
    ) { content() }
}

private fun navigateToTab(navController: NavHostController, tab: Screen) {
    if (navController.currentDestination?.route == tab.route) return
    // 如果目标 tab 已在返回栈里，直接回到它，避免从详情/生活入口进入后
    // 直接 navigate 造成的重复实例或无法回到原 tab。
    if (navController.popBackStack(tab.route, inclusive = false)) return
    navController.navigate(tab.route) {
        popUpTo(Screen.Today.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
