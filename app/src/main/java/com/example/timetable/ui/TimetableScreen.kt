package com.example.timetable.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.timetable.data.Adjustment
import com.example.timetable.data.Course
import com.example.timetable.data.SectionTime
import com.example.timetable.data.ViewMode
import com.example.timetable.data.WeekCalculator
import com.example.timetable.ui.theme.isLightColor
import com.example.timetable.ui.theme.painterBackground
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel = viewModel(),
    /** 启动来源指定的一级入口名（见 [com.example.timetable.MainActivity.EXTRA_OPEN_TAB]） */
    initialTabName: String? = null,
    /** Debug-only：启动后直接弹出学期设置（见 [com.example.timetable.MainActivity.EXTRA_DEBUG_SETTINGS]） */
    openSettingsOnStart: Boolean = false,
) {
    val state by viewModel.uiState.collectAsState()
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    // 每次重新选图 +1，用来强制刷新背景 Painter 的缓存 key
    var backgroundImageVersion by remember { mutableStateOf(0) }
    val editing by viewModel.editing.collectAsState()
    val showTermSettings by viewModel.showTermSettings.collectAsState()
    val showSectionTimes by viewModel.showSectionTimes.collectAsState()
    val showImport by viewModel.showImport.collectAsState()
    val showScheduleSettings by viewModel.showScheduleSettings.collectAsState()
    val showScheduleManage by viewModel.showScheduleManage.collectAsState()
    val manualViewMode by viewModel.viewMode.collectAsState()

    // Debug：让 adb 能直接落到学期设置弹窗（华为设备无法注入点击，只能这样验证）
    LaunchedEffect(openSettingsOnStart) {
        if (openSettingsOnStart) viewModel.openTermSettings()
    }

    // 视图模式：用户手动切过就用用户的，否则跟随设置里的「默认视图」
    val viewMode = manualViewMode ?: state.defaultViewMode

    // 导入完成后的确认弹窗数据
    var pendingImport by remember { mutableStateOf<List<Course>?>(null) }
    // 导入时携带的「当前教学周」，用于反推开学日期
    var pendingWeek by remember { mutableStateOf<Int?>(null) }

    // 登录后自动导入：置位时跳过确认弹窗，直接以「覆盖」方式导入
    var autoImportMode by remember { mutableStateOf(false) }

    // 课程搜索
    var showSearch by remember { mutableStateOf(false) }

    // ---- 选取自定义背景图 ----
    // 用系统的「打开文档」选择器（GetContent）、限定 image/*：
    // 不需要读相册权限，也不用引入 ActivityResult 以外的依赖，返回一个可读的 Uri。
    val bgPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            val ok = com.example.timetable.data.BackgroundImageStore.save(appContext, uri)
            if (ok) {
                viewModel.setBackgroundStyle(
                    com.example.timetable.ui.theme.BackgroundStyle.CUSTOM,
                )
                // 通知 Compose 重新读图（Uri 未变，但文件内容变了）
                backgroundImageVersion++
            }
        }
    }

    // 底部导航当前选中的一级入口（课表 / 成绩 / 校园生活 / 登录）
    var selectedTab by remember { mutableStateOf(MainTab.fromName(initialTabName)) }

    // ---- 周视图 Pager ----
    // initialPage 固定为 0：真实「本周」要等数据加载完才知道，
    // 就绪后再由 LaunchedEffect 一次性滚过去，避免初值取错导致标题/内容错位。
    val weekPagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { state.totalWeeks },
    )

    // ---- 日视图 Pager（7 天）----
    val dayPagerState = rememberPagerState(
        initialPage = todayColumnIndex(state),
        pageCount = { GridSpec.dayCount },
    )

    // 每次进入应用时重排一次提醒（开机后由 BootReceiver 兜底）
    LaunchedEffect(Unit) {
        viewModel.refreshReminder()
    }

    // ---- 登录后自动导入课表 ----
    // 登录成功 → ViewModel 置位 autoImportRequested → 这里切到导入页并进入「自动模式」，
    // 抓取完成后不再弹确认框，直接以「覆盖」方式落库。
    val autoImportRequested by viewModel.autoImportRequested.collectAsState()
    LaunchedEffect(autoImportRequested) {
        if (autoImportRequested) {
            viewModel.consumeAutoImportRequest()
            autoImportMode = true
            viewModel.openImport()
        }
    }

    // 依据「每周起始日 / 显示周末」刷新列序，必须在绘制之前完成
    LaunchedEffect(state.weekStartDay, state.showWeekend) {
        GridSpec.applyWeekStart(state.weekStartDay, state.showWeekend)
    }

    // 数据尚未加载完成时，Pager 处于占位状态（realWeek 默认为 1），
    // 这段期间不要让 Pager 反向写回 ViewModel，否则会把 manualWeek 设成 1
    // 并覆盖掉真实的「本周」，造成标题与内容不一致。
    var pagerReady by remember { mutableStateOf(false) }

    // 数据就绪后，把 Pager 定位到「本周」并解锁双向绑定
    LaunchedEffect(state.loading, state.realWeek, state.totalWeeks) {
        if (!state.loading) {
            val target = (state.realWeek - 1).coerceIn(0, state.totalWeeks - 1)
            if (weekPagerState.currentPage != target) {
                weekPagerState.scrollToPage(target)
            }
            pagerReady = true
        }
    }

    // 以 Pager 为唯一周次来源：滑动结束后写回 ViewModel
    LaunchedEffect(weekPagerState, state.totalWeeks, pagerReady) {
        if (!pagerReady) return@LaunchedEffect
        snapshotFlow { weekPagerState.settledPage }.collect { page ->
            val week = (page + 1).coerceIn(1, state.totalWeeks)
            viewModel.goToWeek(week)
        }
    }

    // 「回到本周」把 manualWeek 置空后，currentWeek 跟随 realWeek，此时滚到该页
    LaunchedEffect(state.currentWeek, state.totalWeeks, pagerReady) {
        if (!pagerReady) return@LaunchedEffect
        val target = (state.currentWeek - 1).coerceIn(0, state.totalWeeks - 1)
        if (weekPagerState.currentPage != target && !weekPagerState.isScrollInProgress) {
            weekPagerState.scrollToPage(target)
        }
    }

    // 切到日视图时，把日视图 Pager 定位到今天。
    // 列数会随「显示周末」变化，因此这里同步做一次越界纠正。
    LaunchedEffect(
        viewMode,
        state.currentWeek,
        state.weekStartDay,
        state.showWeekend,
        GridSpec.visibleColumns,
    ) {
        if (viewMode == ViewMode.DAY) {
            val count = GridSpec.dayCount
            if (count <= 0) return@LaunchedEffect
            val idx = todayColumnIndex(state).coerceIn(0, count - 1)
            if (dayPagerState.currentPage != idx) {
                dayPagerState.scrollToPage(idx)
            }
        }
    }

    // ---- 系统返回键（含手势返回）----
    // 本应用是单 Activity + Compose 状态切换，没有引入 Navigation 组件，
    // 因此二级页面（设置 / 搜索 / 导入 / 课表管理 / 学期设置 / 作息设置 / 课程编辑）
    // 默认不会被系统返回键识别为「一个可返回的目的地」，
    // 按返回会直接退出到桌面。这里按「由内到外」的顺序拦截：
    // 关闭当前最内层的弹窗/页面，全部关完后再交给系统（退出应用）。
    val settingsOrigin by viewModel.settingsOrigin.collectAsState()
    val manageOrigin by viewModel.manageOrigin.collectAsState()

    val hasOverlay = editing != null ||
        showTermSettings ||
        showSectionTimes ||
        pendingImport != null ||
        showImport ||
        showSearch ||
        showScheduleSettings ||
        showScheduleManage

    BackHandler(enabled = hasOverlay) {
        when {
            // 最内层：课程编辑弹窗
            editing != null -> viewModel.cancelEdit()
            // 学期设置：回到进入它的那一层
            showTermSettings -> {
                viewModel.closeTermSettings()
                if (settingsOrigin == TimetableViewModel.SettingsOrigin.SCHEDULE_SETTINGS) {
                    viewModel.openScheduleSettings()
                }
            }
            // 作息设置：同上
            showSectionTimes -> {
                viewModel.closeSectionTimes()
                if (settingsOrigin == TimetableViewModel.SettingsOrigin.SCHEDULE_SETTINGS) {
                    viewModel.openScheduleSettings()
                }
            }
            // 导入确认弹窗
            pendingImport != null -> {
                pendingImport = null
                pendingWeek = null
            }
            // 导入页
            showImport -> {
                viewModel.closeImport()
                autoImportMode = false
            }
            // 课程搜索
            showSearch -> showSearch = false
            // 课表设置
            showScheduleSettings -> viewModel.closeScheduleSettings()
            // 课表管理（多课表）：回到进入它的那一层
            showScheduleManage -> {
                viewModel.closeScheduleManage()
                if (manageOrigin == TimetableViewModel.SettingsOrigin.SCHEDULE_SETTINGS) {
                    viewModel.openScheduleSettings()
                }
            }
        }
    }

    // ---- 玻璃模糊共享状态（提升到路由层）----
    //
    // 主页顶栏 / 底栏、以及所有二级页面的顶栏，都从这里取同一个 HazeState。
    // 放在这一层而不是 MainTimetableContent 内部，是为了让二级页面（设置 /
    // 搜索 / 课表管理）也能用上同款毛玻璃 —— 否则切过去时顶栏会从玻璃变实心。
    val hazeState = remember { dev.chrisbanes.haze.HazeState() }

    // 校园生活 → 点击某条通知后打开的详情链接；null = 未打开。
    //
    // 放在路由层（而不是 MainTimetableContent 内部）是为了让底栏能感知它：
    // 详情页是整屏网页，底栏若还浮在上面会割裂，需要一起隐藏。
    var openedNoticeUrl by remember { mutableStateOf<String?>(null) }

    // 上面那个链接是否要持久化登录会话。
    // 校园服务（图书馆预约）需要，通知公告不需要 —— 两者共用同一个
    // WebView 页面，靠这个标记区分。
    var openedLinkPersist by remember { mutableStateOf(false) }

    // ---- 全屏页切换（带滑动过渡）----
    //
    // 用 AnimatedContent 按「当前页面」做过渡：
    // 进二级页面从右侧滑入、返回时向右滑出，形成自然的层级感。
    // 页面深度 depth 用于判断方向：depth 变大 = 进入更深一层 → 从右往左推入。
    val screenKey: String = when {
        showScheduleManage -> "schedule_manage"
        showScheduleSettings -> "schedule_settings"
        showSearch -> "search"
        showImport -> "import"
        else -> "main"
    }
    val screenDepth: Int = when (screenKey) {
        "main" -> 0
        // 课表管理可以从课表设置再进一层，深度给 2，保证过渡方向自然
        "schedule_manage" -> 2
        else -> 1
    }

    // ---- 全局背景层 ----
    //
    // 提升到路由层之后，**所有页面**（主页 + 二级页 + 搜索页）共用同一张背景，
    // 切页时背景纹丝不动 —— 这是消除「割裂感」最根本的一条。
    // 背景铺在最底、覆盖全屏（含状态栏 / 导航栏），且不挂 hazeSource：
    // 它只是被采样的对象，本身不该被再模糊一次。
    val bgBrush = com.example.timetable.ui.theme.rememberBackgroundBrush(state.backgroundStyle)
    val bgColor = com.example.timetable.ui.theme.plainBackgroundColor()
    val customBgUri = remember(state.backgroundStyle, backgroundImageVersion) {
        if (state.backgroundStyle == com.example.timetable.ui.theme.BackgroundStyle.CUSTOM) {
            com.example.timetable.data.BackgroundImageStore.uri(appContext)
        } else {
            null
        }
    }
    val customBgPainter = remember(customBgUri, backgroundImageVersion) {
        customBgUri?.let { uri ->
            runCatching {
                android.graphics.BitmapFactory.decodeStream(
                    appContext.contentResolver.openInputStream(uri),
                )?.let { androidx.compose.ui.graphics.painter.BitmapPainter(it.asImageBitmap()) }
            }.getOrNull()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景层（最底，全屏，不挂 hazeSource）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    when {
                        customBgPainter != null -> Modifier.painterBackground(customBgPainter)
                        bgBrush != null -> Modifier.background(bgBrush)
                        else -> Modifier.background(bgColor)
                    },
                ),
        )

        // 内容层：挂 hazeSource，铺满全屏（含底栏所在区域），
        // 这样顶栏 / 底栏 / 二级页顶栏都能采样到背后的内容。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState),
        ) {

    AnimatedContent(
        targetState = screenKey to screenDepth,
        transitionSpec = {
            val (fromKey, fromDepth) = initialState
            val (toKey, toDepth) = targetState
            // 一级页面（课表 / 成绩 / 校园生活 / 登录）之间是**平级切换**：
            // 左右滑动会让人以为是「前进/后退」，心理模型不对；
            // 而且四个 tab 视觉差异很大，硬滑过去很生硬。
            // 所以平级切换改成**慢速交叉淡入** —— 旧页缓缓退场、新页渐渐显现，
            // 像同一块玻璃底下换了张底片，优雅且不抢注意力。
            val isSameLevel = fromDepth == 0 && toDepth == 0
            val ease = androidx.compose.animation.core.FastOutSlowInEasing

            if (isSameLevel) {
                // 交叉淡入：两边同时进行，时长拉长到 420ms 才「看得出」是渐变。
                // 入场稍微快一点点收尾（360 vs 420），让新页先稳住，
                // 避免两页等权叠加时出现「半透明鬼影」的中段。
                fadeIn(tween(360, easing = ease)) togetherWith
                    fadeOut(tween(420, easing = ease))
            } else {
                // 二级页过渡：入场页从右侧整体推入，出场页同步向左轻移并**缩小后退**，
                // 形成「新页面盖上来、旧页面向后退」的纵深层次。
                // 时长从 260 放到 340，配合更柔的缓动，整体更「缓」。
                val forward = toDepth >= fromDepth
                val enterDur = 340
                val exitDur = 340
                if (forward) {
                    (
                        slideInHorizontally(tween(enterDur, easing = ease)) { it } +
                            fadeIn(tween(enterDur)) +
                            scaleIn(
                                initialScale = 0.96f,
                                animationSpec = tween(enterDur, easing = ease),
                            )
                        ) togetherWith (
                        slideOutHorizontally(tween(exitDur, easing = ease)) { -it / 5 } +
                            fadeOut(tween(exitDur)) +
                            scaleOut(
                                targetScale = 0.94f,
                                animationSpec = tween(exitDur, easing = ease),
                            )
                        )
                } else {
                    (
                        slideInHorizontally(tween(enterDur, easing = ease)) { -it / 5 } +
                            fadeIn(tween(enterDur)) +
                            scaleIn(
                                initialScale = 0.94f,
                                animationSpec = tween(enterDur, easing = ease),
                            )
                        ) togetherWith (
                        slideOutHorizontally(tween(exitDur, easing = ease)) { it } +
                            fadeOut(tween(exitDur)) +
                            scaleOut(
                                targetScale = 0.96f,
                                animationSpec = tween(exitDur, easing = ease),
                            )
                        )
                }.using(SizeTransform(clip = true))
            }
        },
        label = "screen",
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
    ) { (key, _) ->
        when (key) {
            "schedule_manage" -> ScheduleManageScreen(
                schedules = state.schedules,
                currentScheduleId = state.currentScheduleId,
                courseCountOf = { id -> state.courseCounts[id] ?: 0 },
                onBack = {
                    viewModel.closeScheduleManage()
                    if (manageOrigin == TimetableViewModel.SettingsOrigin.SCHEDULE_SETTINGS) {
                        viewModel.openScheduleSettings()
                    }
                },
                onSwitch = { schedule ->
                    viewModel.switchSchedule(schedule)
                    viewModel.closeScheduleManage()
                },
                onCreate = viewModel::createSchedule,
                onRename = viewModel::renameSchedule,
                onDelete = viewModel::deleteSchedule,
                hazeState = hazeState,
            )

            "schedule_settings" -> ScheduleSettingsScreen(
                state = state,
                onBack = viewModel::closeScheduleSettings,
                onOpenSectionTimes = {
                    viewModel.closeScheduleSettings()
                    viewModel.openSectionTimes(
                        TimetableViewModel.SettingsOrigin.SCHEDULE_SETTINGS,
                    )
                },
                onOpenTermSettings = {
                    viewModel.closeScheduleSettings()
                    viewModel.openTermSettings(
                        TimetableViewModel.SettingsOrigin.SCHEDULE_SETTINGS,
                    )
                },
                onOpenScheduleManage = {
                    viewModel.closeScheduleSettings()
                    viewModel.openScheduleManage(
                        TimetableViewModel.SettingsOrigin.SCHEDULE_SETTINGS,
                    )
                },
                onOpenImport = {
                    viewModel.closeScheduleSettings()
                    viewModel.openImport()
                },
                onSetShowWeekend = viewModel::setShowWeekend,
                onSetShowNotCurrentWeek = viewModel::setShowNotCurrentWeek,
                onSetShowTeacher = viewModel::setShowTeacher,
                onSetShowRoom = viewModel::setShowRoom,
                onSetShowSectionTime = viewModel::setShowSectionTime,
                onSetCardStyle = viewModel::setCardStyle,
                onSetDefaultViewMode = viewModel::setDefaultViewMode,
                onSetWeekStartDay = viewModel::setWeekStartDay,
                onSetBackgroundStyle = viewModel::setBackgroundStyle,
                onPickBackgroundImage = { bgPicker.launch("image/*") },
                onSetReminderEnabled = viewModel::setReminderEnabled,
                onSetReminderLeadMinutes = viewModel::setReminderLeadMinutes,
                onClearAllCourses = viewModel::clearAllCourses,
                hazeState = hazeState,
            )

            "search" -> CourseSearchScreen(
                courses = state.courses,
                sectionTimes = state.sectionTimes,
                currentWeek = state.currentWeek,
                onCourseClick = { course ->
                    showSearch = false
                    viewModel.startEdit(course)
                },
                onDismiss = { showSearch = false },
                hazeState = hazeState,
            )

            "import" -> ImportScreen(
                autoMode = autoImportMode,
                onCancel = {
                    viewModel.closeImport()
                    autoImportMode = false
                },
                onFinish = { courses, week ->
                    viewModel.closeImport()
                    if (autoImportMode) {
                        // 自动模式：跳过确认框，直接以「覆盖」方式导入
                        autoImportMode = false
                        viewModel.importCourses(courses, replace = true, currentWeek = week)
                    } else {
                        pendingImport = courses
                        pendingWeek = week
                    }
                },
            )

            else -> {
                // ---- 主界面（周/日视图）----
                MainTimetableContent(
                    state = state,
                    pagerReady = pagerReady,
                    weekPagerState = weekPagerState,
                    dayPagerState = dayPagerState,
                    viewMode = viewMode,
                    viewedWeekProducer = { viewedWeekOf(pagerReady, weekPagerState, state) },
                    viewModel = viewModel,
                    showSearch = { showSearch = true },
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    backgroundImageVersion = backgroundImageVersion,
                    hazeState = hazeState,
                    onOpenNotice = { link, persist ->
                        openedNoticeUrl = link
                        openedLinkPersist = persist
                    },
                )
            }
        }
        }
    }

        // ---- 底部导航浮层（在路由层，所有页面共用）----
        //
        // 放在这里而不是 MainTimetableContent 里面，有两个原因：
        // 1. 位置/尺寸恒定，Tab 切换的整屏动画不会牵动它 → 玻璃模糊只采样一次，不卡；
        // 2. 它能感知 screenKey，进入二级页面时整条下滑退场 ——
        //    否则「半屏是设置页、底栏还挂在下面」会非常割裂。
        AnimatedVisibility(
            visible = screenKey == "main" && openedNoticeUrl == null,
            enter = slideInVertically(
                animationSpec = tween(300, easing = FastOutSlowInEasing),
            ) { full -> full } + fadeIn(tween(220)),
            exit = slideOutVertically(
                animationSpec = tween(220, easing = FastOutSlowInEasing),
            ) { full -> full } + fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            MainBottomBar(
                selected = selectedTab,
                onSelect = { selectedTab = it },
                hazeState = hazeState,
            )
        }

        // ---- 内嵌网页（通知公告详情 / 校园服务），全屏，盖在底栏之上 ----
        openedNoticeUrl?.let { link ->
            NoticeWebScreen(
                url = link,
                onBack = { openedNoticeUrl = null },
                hazeState = hazeState,
                // 校园服务（图书馆预约等）保持登录态，公告详情页不需要
                persistSession = openedLinkPersist,
            )
        }
    }

    // 弹窗（课程编辑 / 学期设置 / 作息设置 / 导入确认）需要知道「当前查看的周次」，
    // 用于按周调整的读写，因此这里单独算一次。
    val viewedWeek = viewedWeekOf(pagerReady, weekPagerState, state)

    // 编辑 / 新建课程
    editing?.let { beingEdited ->
        val original = if (beingEdited.id != 0L) beingEdited else null
        CourseEditorDialog(
            course = beingEdited,
            original = original,
            maxWeeks = state.totalWeeks,
            currentWeek = viewedWeek,
            adjustment = original?.let { state.adjustmentOf(it.id, viewedWeek) },
            onDismiss = viewModel::cancelEdit,
            onSave = { viewModel.saveCourse(it, original) },
            onDelete = viewModel::deleteCourse,
            onMoveForWeek = { day, section ->
                original?.let { viewModel.moveCourseForWeek(it, viewedWeek, day, section) }
            },
            onCancelForWeek = {
                original?.let { viewModel.cancelCourseForWeek(it, viewedWeek) }
            },
            onClearAdjustment = {
                original?.let { viewModel.clearAdjustmentForWeek(it, viewedWeek) }
            },
        )
    }

    // 学期设置
    if (showTermSettings) {
        SettingsDialog(
            termStart = state.termStart,
            totalWeeks = state.totalWeeks,
            reminderEnabled = state.reminderEnabled,
            reminderLeadMinutes = state.reminderLeadMinutes,
            weekStartDay = state.weekStartDay,
            themeMode = state.themeMode,
            onSetThemeMode = viewModel::setThemeMode,
            onDismiss = viewModel::closeTermSettings,
            onSetTermStart = viewModel::setTermStart,
            onSetTotalWeeks = viewModel::setTotalWeeks,
            onSetReminderEnabled = viewModel::setReminderEnabled,
            onSetReminderLeadMinutes = viewModel::setReminderLeadMinutes,
            onOpenSectionTimes = {
                viewModel.closeTermSettings()
                // 保留来源：若学期设置是从课表设置进来的，作息设置回去也要落在课表设置
                viewModel.openSectionTimes(settingsOrigin)
            },
        )
    }

    // 作息时间设置
    if (showSectionTimes) {
        SectionTimesDialog(
            sectionTimes = state.sectionTimes,
            onDismiss = viewModel::closeSectionTimes,
            onSave = viewModel::setSectionTimes,
        )
    }

    // 导入确认
    pendingImport?.let { courses ->
        ImportConfirmDialog(
            courses = courses,
            hasExisting = state.courses.isNotEmpty(),
            onConfirm = { replace ->
                viewModel.importCourses(courses, replace, pendingWeek)
                pendingImport = null
                pendingWeek = null
            },
            onDismiss = {
                pendingImport = null
                pendingWeek = null
            },
        )
    }
}

/** 今天在当前列序下的列下标；日视图里即「今天」那一页 */
private fun todayColumnIndex(state: TimetableUiState): Int {
    val dow = LocalDate.now().dayOfWeek.value
    val idx = GridSpec.columnIndexOf(dow)
    return if (idx >= 0) idx else 0
}

/**
 * 顶栏标题里的「正在查看的周次」。
 *
 * 以 Pager 为准，保证标题与表头 / 内容严格一致；
 * 数据尚未就绪前退回 ViewModel 的 currentWeek，避免闪一下第 1 周。
 */
private fun viewedWeekOf(
    pagerReady: Boolean,
    pagerState: PagerState,
    state: TimetableUiState,
): Int = if (pagerReady) {
    (pagerState.currentPage + 1).coerceIn(1, state.totalWeeks)
} else {
    state.currentWeek
}

/**
 * 主界面：顶栏（日期 / 周次 / 操作按钮）+ 课程表本体（周视图或日视图）。
 *
 * 之所以单独拆出来，是为了让它能作为 [AnimatedContent] 的一个「页面」，
 * 与课表设置 / 搜索 / 导入等二级页面做滑动过渡。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTimetableContent(
    state: TimetableUiState,
    pagerReady: Boolean,
    weekPagerState: PagerState,
    dayPagerState: PagerState,
    viewMode: ViewMode,
    viewedWeekProducer: () -> Int,
    viewModel: TimetableViewModel,
    showSearch: () -> Unit,
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    backgroundImageVersion: Int = 0,
    hazeState: dev.chrisbanes.haze.HazeState = remember { dev.chrisbanes.haze.HazeState() },
    onOpenNotice: (String?, Boolean) -> Unit = { _, _ -> },
) {
    val viewedWeek = viewedWeekProducer()
    val gradeCache by viewModel.gradeCache.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val savedCredentials by viewModel.savedCredentials.collectAsState()

    // ---- 校园生活：教务处通知公告 ----
    val campusAnnouncements by viewModel.announcements.collectAsState()
    val campusLoading by viewModel.announcementsLoading.collectAsState()
    val campusError by viewModel.announcementsError.collectAsState()

    // 切到「校园生活」页时自动抓一次（只在首次，之后靠手动刷新）
    LaunchedEffect(selectedTab) {
        if (selectedTab == MainTab.CAMPUS) {
            viewModel.loadAnnouncements()
        }
    }

    // 只有「课表」页需要顶栏；成绩 / 校园生活 / 登录 用各自的标题栏（或全屏网页）。
    val showTopBar = selectedTab == MainTab.TIMETABLE

    val context = androidx.compose.ui.platform.LocalContext.current

    // 背景画笔：null 表示「极简」/「自定义」方案（前者用主题纯色，后者用图片）
    val bgBrush = com.example.timetable.ui.theme.rememberBackgroundBrush(state.backgroundStyle)
    val bgColor = com.example.timetable.ui.theme.plainBackgroundColor()

    // 自定义背景图（若有）。用 Uri 作为 key 缓存 painter，
    // 避免每帧重新解码这张图。
    val customBgUri = remember(state.backgroundStyle, backgroundImageVersion) {
        if (state.backgroundStyle == com.example.timetable.ui.theme.BackgroundStyle.CUSTOM) {
            com.example.timetable.data.BackgroundImageStore.uri(context)
        } else {
            null
        }
    }
    val customBgPainter = remember(customBgUri, backgroundImageVersion) {
        customBgUri?.let { uri ->
            runCatching {
                android.graphics.BitmapFactory.decodeStream(
                    context.contentResolver.openInputStream(uri),
                )?.let { androidx.compose.ui.graphics.painter.BitmapPainter(it.asImageBitmap()) }
            }.getOrNull()
        }
    }

    Scaffold(
            // 让 Scaffold 自身透明，露出更外层的背景层
            containerColor = Color.Transparent,
            // 注意：底栏**不放在 Scaffold.bottomBar 槽位**里。
            //
            // 原因：bottomBar 槽位的高度会参与内容区的测量。Tab 切换时
            // AnimatedContent 里两个页面同时在动画（尺寸变化），Scaffold 会
            // 跟着重新测量 → 底栏每帧重排 + hazeEffect 每帧重新采样整块模糊，
            // 实机上表现为「切页时底栏一顿」。改成独立浮层后底栏尺寸/位置恒定，
            // 模糊只需在切换时采样一次，切换立即变顺。
            topBar = {
                if (!showTopBar) return@Scaffold
                MainTopBar(
                    state = state,
                    viewedWeek = viewedWeek,
                    viewMode = viewMode,
                    viewModel = viewModel,
                    showSearch = showSearch,
                    hazeState = hazeState,
                )
            },
        ) { padding ->

            // 外层：**铺满到屏幕最底**（只吃顶栏/系统栏 padding，不留底栏空间）。
            // 它负责挂 hazeSource，范围必须盖住底栏所在的区域，
            // 否则底栏浮到 Scaffold 外面后就采不到背后内容了。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .hazeSource(state = hazeState),
            ) {
            // 内层：给内容留出底部空间，避免被浮动的玻璃底栏压住。
            // 68dp = 底栏自身内容高（8 外边距×2 + 6 内边距×2 + 40 图标胶囊），
            // 再叠加手势条 / 虚拟导航栏高度。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        // 底部留白 = 底栏自身内容高（8 外边距×2 + 6 内边距×2 + 40 图标胶囊 = 68dp）
                        // + 手势条 / 虚拟导航栏高度。
                        // 这里直接读 navigationBars 的内边距，避免依赖 Scaffold 的 bottomBar 槽位。
                        bottom = 68.dp + with(LocalDensity.current) {
                            WindowInsets.navigationBars.getBottom(this).toDp()
                        },
                    ),
            ) {
            // 底部 Tab 切换：整屏横推 + 轻微缩放 + 交叉淡入。
            //
            // 位移用整屏宽度（不是 1/3），这样「换了一整屏」的方向感非常明确；
            // 同时给离场页一点缩小、入场页从略小放大，制造前后纵深感；
            // 曲线用带减速的 tween，起步干脆、收尾稳。
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val forward = targetState.ordinal >= initialState.ordinal
                    val dir = if (forward) 1 else -1
                    val spec = (
                        slideInHorizontally(
                            animationSpec = tween(360, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                        ) { full -> dir * full } +
                            fadeIn(tween(300)) +
                            scaleIn(
                                initialScale = 0.92f,
                                animationSpec = tween(360, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            )
                        ) togetherWith (
                        slideOutHorizontally(
                            animationSpec = tween(360, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                        ) { full -> -dir * full / 4 } +
                            fadeOut(tween(220)) +
                            scaleOut(targetScale = 0.94f, animationSpec = tween(360))
                        )
                    spec.using(SizeTransform(clip = true))
                },
                label = "tab",
                // clipToBounds：两个页面横推时超出边界的部分直接裁掉，
                // 不再向外溢出重绘（溢出会连带底栏一起重绘，是卡顿的另一来源）。
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
            ) { tab ->
                when (tab) {
                    MainTab.TIMETABLE -> {
                        if (state.loading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            // 周 / 日视图切换：纵向上滑 + 缩放 + 交叉淡入。
                            //
                            // 日视图是「钻进某一天」的语义，用**向上浮起**比纯淡入更传神：
                            // 新视图从下方略微上移放大的位置落定，旧视图向上淡出。
                            AnimatedContent(
                                targetState = viewMode,
                                transitionSpec = {
                                    val spec = (
                                        fadeIn(tween(280)) +
                                            scaleIn(
                                                initialScale = 0.88f,
                                                animationSpec = tween(360, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                            ) +
                                            slideInVertically(
                                                animationSpec = tween(360, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                            ) { h -> h / 12 }
                                        ) togetherWith (
                                        fadeOut(tween(200)) +
                                            scaleOut(targetScale = 1.06f, animationSpec = tween(300)) +
                                            slideOutVertically(tween(300)) { h -> -h / 16 }
                                        )
                                    spec.using(SizeTransform(clip = true))
                                },
                                label = "viewMode",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clipToBounds(),
                            ) { mode ->
                                when (mode) {
                                    ViewMode.WEEK -> TimetableBoard(
                                        state = state,
                                        pagerState = weekPagerState,
                                        viewModel = viewModel,
                                    )

                                    ViewMode.DAY -> DayViewScreen(
                                        pagerState = dayPagerState,
                                        courses = state.coursesForWeek(state.currentWeek),
                                        week = state.currentWeek,
                                        realWeek = state.realWeek,
                                        termStart = state.termStart,
                                        sectionTimes = state.sectionTimes,
                                        onCourseClick = viewModel::startEdit,
                                    )
                                }
                            }
                        }
                    }

                    MainTab.GRADES -> GradesScreen(
                        cache = gradeCache,
                        onSaveCache = viewModel::saveGradeCache,
                        modifier = Modifier.fillMaxSize(),
                        onGoLogin = { onSelectTab(MainTab.LOGIN) },
                        // 让成绩页标题栏能用上同一套液态玻璃（与其它页统一材质）
                        hazeState = hazeState,
                    )

                    MainTab.CAMPUS -> CampusScreen(
                        announcements = campusAnnouncements,
                        loading = campusLoading,
                        error = campusError,
                        onRefresh = { viewModel.loadAnnouncements(force = true) },
                        onOpenLink = { link, persist -> onOpenNotice(link, persist) },
                        modifier = Modifier.fillMaxSize(),
                        hazeState = hazeState,
                    )

                    MainTab.LOGIN -> LoginScreen(
                        profile = userProfile,
                        saved = savedCredentials,
                        onLoginSuccess = viewModel::saveUserProfile,
                        onSaveCredentials = viewModel::saveCredentials,
                        onSetAutoLogin = viewModel::setAutoLogin,
                        onForgetCredentials = viewModel::forgetCredentials,
                        onLogout = viewModel::logout,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        }
    }
}

/**
 * 课表页顶栏：日期 / 周次 / 操作按钮。
 * 抽成独立组件，是为了在切到其它 tab 时可以整条隐藏。
 * 顶栏同样使用 Haze 真模糊：内容向上滚动时，标题栏背后透出被模糊的课表，
 * 让「内容从玻璃下面穿过」的空间感更强。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    state: TimetableUiState,
    viewedWeek: Int,
    viewMode: ViewMode,
    viewModel: TimetableViewModel,
    showSearch: () -> Unit,
    hazeState: dev.chrisbanes.haze.HazeState,
) {
    val isDarkTopBar = MaterialTheme.colorScheme.background.isLightColor().not()

    TopAppBar(
        title = {
            Column {
                val viewingToday = viewedWeek == state.realWeek
                val anchorDate: LocalDate = remember(viewedWeek, state.termStart) {
                    if (viewingToday) {
                        LocalDate.now()
                    } else {
                        state.termStart
                            ?.let {
                                WeekCalculator.weekStartOfWeek(
                                    it, viewedWeek, state.weekStartDay,
                                )
                            }
                            ?: LocalDate.now()
                    }
                }
                Text(
                    text = anchorDate.format(ymdFormatter),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (viewingToday) {
                        "第 $viewedWeek 周  ${
                            dayLabelFull(LocalDate.now().dayOfWeek.value)
                        }  ·  ${state.currentScheduleName}"
                    } else {
                        "第 $viewedWeek 周  ·  ${state.currentScheduleName}"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = {
            ViewModeToggle(mode = viewMode, onChange = viewModel::setViewMode)
            IconButton(onClick = showSearch) {
                Icon(Icons.Default.Search, contentDescription = "搜索课程")
            }
            IconButton(onClick = viewModel::backToToday) {
                Icon(
                    imageVector = Icons.Default.Today,
                    contentDescription = "回到本周",
                    tint = if (viewedWeek == state.realWeek) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            IconButton(onClick = viewModel::openScheduleSettings) {
                Icon(Icons.Default.Settings, contentDescription = "课表设置")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            // 顶栏容器必须**半透明**，否则它会盖住下面透上来的背景色与模糊
            containerColor = Color.Transparent,
        ),
        modifier = Modifier.hazeEffect(
            state = hazeState,
            style = dev.chrisbanes.haze.HazeDefaults.style(
                backgroundColor = com.example.timetable.ui.theme.glassBaseColor(isDarkTopBar),
                tint = dev.chrisbanes.haze.HazeDefaults.tint(
                    com.example.timetable.ui.theme.glassTintColor(),
                ),
                // 与底栏 / 二级页顶栏统一取值（见 LiquidGlass.kt 的参数中心）
                blurRadius = com.example.timetable.ui.theme.GLASS_BLUR,
                noiseFactor = com.example.timetable.ui.theme.GLASS_NOISE,
            ),
        ),
    )
}

/**
 * 顶栏的「周 / 日」分段切换。
 *
 * 用胶囊式的两段控件，比图标按钮更直观地表达「互斥的两种视图」。
 */
@Composable
private fun ViewModeToggle(
    mode: ViewMode,
    onChange: (ViewMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(ViewMode.WEEK to "周", ViewMode.DAY to "日").forEach { (m, label) ->
            val selected = m == mode
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else androidx.compose.ui.graphics.Color.Transparent,
                    )
                    .clickable { onChange(m) }
                    .padding(horizontal = 11.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 课表主面板（周视图）。
 *
 * 把「表头 + 节次栏」提到 Pager 外面：它们在所有周次里完全相同，
 * 放在滑动路径里会导致每帧重建 18 个 Composable，是最主要的滑动开销来源。
 * 现在 Pager 内只剩会随周次变化的课程卡片。
 */
@Composable
private fun TimetableBoard(
    state: TimetableUiState,
    pagerState: PagerState,
    viewModel: TimetableViewModel,
) {
    val times = if (state.sectionTimes.isNotEmpty()) {
        state.sectionTimes
    } else {
        SectionTime.defaults()
    }

    // 一次性把每周的课程算好再交给 Pager。
    //
    // 之前是在 pager 的 page lambda 里调用 state.coursesForWeek(page+1)，
    // 而 Pager 在拖拽过程中会同时重组 3 个页面（当前 + 左右邻居），
    // 于是每帧都要对整份课程表做 3 次 filter/associateBy —— 这就是滑动卡顿的来源。
    // 现在按「课程列表 + 调课记录」缓存，只在数据真正变化时重算一次。
    val weekCourseCache: Map<Int, List<Course>> = remember(
        state.courses,
        state.adjustments,
        state.totalWeeks,
    ) {
        val base = HashMap<Int, List<Course>>(state.totalWeeks * 2)
        for (w in 1..state.totalWeeks) base[w] = state.coursesForWeek(w)
        base
    }

    // 「显示非本周课程」时需要整份课程表（淡显），单独缓存一份
    val allCourses: List<Course> = state.courses

    // 「显示非本周课程」时也要套用本周的调课记录，所以同样按周缓存一份
    val dimmedCourseCache: Map<Int, List<Course>> = remember(
        state.courses,
        state.adjustments,
        state.totalWeeks,
    ) {
        if (state.adjustments.isEmpty()) {
            // 没有调课记录时各周完全一致，共用同一个列表即可
            val shared = state.courses
            HashMap<Int, List<Course>>(state.totalWeeks).also { map ->
                for (w in 1..state.totalWeeks) map[w] = shared
            }
        } else {
            HashMap<Int, List<Course>>(state.totalWeeks * 2).also { map ->
                for (w in 1..state.totalWeeks) {
                    val forWeek = state.adjustments.filter { it.week == w }.associateBy { it.courseId }
                    map[w] = if (forWeek.isEmpty()) {
                        state.courses
                    } else {
                        state.courses.map { c ->
                            val adj = forWeek[c.id] ?: return@map c
                            when (adj.type) {
                                Adjustment.ADJUST_CANCEL -> c
                                else -> c.copy(
                                    dayOfWeek = adj.targetDayOfWeek,
                                    startSection = adj.targetStartSection,
                                    sectionCount = if (adj.targetSectionCount > 0) {
                                        adj.targetSectionCount
                                    } else {
                                        c.sectionCount
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val adjustedIdsByWeek: Map<Int, Set<Long>> = remember(state.adjustments, state.totalWeeks) {
        val result = HashMap<Int, Set<Long>>(state.totalWeeks * 2)
        for (w in 1..state.totalWeeks) {
            result[w] = state.adjustments.filter { it.week == w }.map { it.courseId }.toSet()
        }
        result
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp),
    ) {
        // ---- 表头（星期 + 日期）----
        WeekHeaderBar(
            pagerState = pagerState,
            termStart = state.termStart,
            realWeek = state.realWeek,
            weekStartDay = state.weekStartDay,
        )

        // ---- 主体：固定左侧节次栏 + 可横向滑动的课程区 ----
        Row(modifier = Modifier.fillMaxWidth()) {

            SectionTimeColumn(times = times, showSectionTime = state.showSectionTime)

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                // 只保留当前页与左右相邻页，避免一次性构建全部周次
                beyondViewportPageCount = 1,
                key = { it },
            ) { page ->
                val week = page + 1
                WeekCourses(
                    week = week,
                    // 「显示非本周课程」要整份课表（淡显），否则只看本周
                    allCourses = if (state.showNotCurrentWeek) {
                        dimmedCourseCache[week].orEmpty()
                    } else {
                        weekCourseCache[week].orEmpty()
                    },
                    adjustedIds = adjustedIdsByWeek[week].orEmpty(),
                    sectionTimes = times,
                    cardStyle = state.cardStyle,
                    showRoom = state.showRoom,
                    showTeacher = state.showTeacher,
                    showSectionTime = state.showSectionTime,
                    showNotCurrentWeek = state.showNotCurrentWeek,
                    onCourseClick = viewModel::startEdit,
                    onEmptyCellClick = viewModel::startCreate,
                )
            }
        }
    }
}

/**
 * 表头：星期 + 日期。跟随 Pager 的滑动偏移实时联动，
 * 这样滑到哪一周，表头的日期就跟着变。
 *
 * 注意：这里只依赖 [pagerState.currentPage]，不再额外读 ViewModel 的周次，
 * 避免「滑动中标题已更新、表头还没跟上」的错位。
 */
@Composable
private fun WeekHeaderBar(
    pagerState: PagerState,
    termStart: LocalDate?,
    realWeek: Int,
    weekStartDay: Int,
) {
    val page = pagerState.currentPage
    val week = (page + 1).coerceAtLeast(1)

    val columns = GridSpec.visibleColumns
    val weekDates: List<String> = remember(termStart, week, weekStartDay, columns) {
        termStart?.let {
            columns.map { dow ->
                WeekCalculator.dateOf(it, week, dow, weekStartDay)
                    .format(dayOnlyFormatter)
            }
        } ?: emptyList()
    }
    val todayCol = remember(week, realWeek, columns) {
        if (week == realWeek) columns.indexOf(LocalDate.now().dayOfWeek.value) else -1
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.width(GridSpec.timeColumnWidth))
        columns.forEachIndexed { colIndex, dow ->
            val isToday = colIndex == todayCol
            val isWeekend = dow == 7 || dow == 6
            val dateText = weekDates.getOrNull(colIndex).orEmpty()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(GridSpec.headerHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = dayLabelByDow(dow),
                    fontSize = 12.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        isToday -> MaterialTheme.colorScheme.onBackground
                        isWeekend -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onBackground
                    },
                )
                if (dateText.isNotEmpty()) {
                    Text(
                        text = dateText,
                        fontSize = 10.sp,
                        fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/**
 * Pager 的单页：只有课程卡片层。
 *
 * 日期、表头、节次栏都由外层负责，这里只算「本周有哪些课」并绘制。
 *
 * [showNotCurrentWeek] 打开时，把整学期的课都画出来，
 * 不属于本页周次的课程淡显，便于一眼看出「这周上、那周不上」。
 */
@Composable
private fun WeekCourses(
    week: Int,
    allCourses: List<Course>,
    adjustedIds: Set<Long>,
    sectionTimes: List<SectionTime>,
    cardStyle: com.example.timetable.data.CardStyle,
    showRoom: Boolean,
    showTeacher: Boolean,
    showSectionTime: Boolean,
    showNotCurrentWeek: Boolean,
    onCourseClick: (Course) -> Unit,
    onEmptyCellClick: (Int, Int) -> Unit,
) {
    // 课程列表由 TimetableBoard 预先按周算好（含调课记录），
    // 这里不再重复过滤，避免每帧对整份课表做一次 O(n) 扫描。
    val displayCourses: List<Course> = allCourses

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TimetableGrid(
            courses = displayCourses,
            modifier = Modifier.fillMaxWidth(),
            dates = emptyList(),
            todayIndex = -1,
            sectionTimes = sectionTimes,
            showHeader = false,
            showTimeColumn = false,
            cardStyle = cardStyle,
            showRoom = showRoom,
            showTeacher = showTeacher,
            showSectionTime = showSectionTime,
            highlightWeek = if (showNotCurrentWeek) week else 0,
            adjustedIds = adjustedIds,
            onCourseClick = onCourseClick,
            onEmptyCellClick = onEmptyCellClick,
        )
        Spacer(modifier = Modifier.size(24.dp))
    }
}

/** 左侧节次栏：节次编号 + 起止时间。所有周次共用，只组合一次。 */
@Composable
private fun SectionTimeColumn(
    times: List<SectionTime>,
    showSectionTime: Boolean,
) {
    Column(modifier = Modifier.width(GridSpec.timeColumnWidth)) {
        // 注意：这里**不能**再补一个 headerHeight 的 Spacer。
        // 外层 Column 里这个 Row 已经排在 WeekHeaderBar 下方，
        // 而 Pager 的内容也没有额外的头部占位；
        // 若在此处再加 44dp，节次栏整体会比右侧课程行低 44dp，导致串行。
        for (section in times.indices) {
            val s = section + 1
            Column(
                modifier = Modifier
                    .height(GridSpec.heightOf(s))
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // 节次号 + 开始时间同一行，整体在列内水平居中
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "$s",
                        fontSize = 12.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (showSectionTime) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = times.getOrNull(section)?.start.orEmpty(),
                            fontSize = 9.sp,
                            lineHeight = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                if (showSectionTime) {
                    Text(
                        text = times.getOrNull(section)?.end.orEmpty(),
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                    )
                }
            }
            // 第 4 节之后补出午休空档，与右侧课程区的留白严格对齐，
            // 否则第 5 节起的节次号会整体偏上。
            if (s == GridSpec.LUNCH_AFTER_SECTION && s < times.size) {
                LunchBreakLabel()
            }
        }
    }
}

/**
 * 左侧节次栏里的「午休」占位。
 *
 * 高度与 [GridSpec.lunchGapHeight] 一致，保证与右侧课程区的空档对齐；
 * 文字沿纵向排列，窄栏里也能读。
 */
@Composable
private fun LunchBreakLabel() {
    Box(
        modifier = Modifier
            .height(GridSpec.lunchGapHeight)
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "午休",
            fontSize = 9.sp,
            lineHeight = 10.sp,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
        )
    }
}

private val ymdFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/M/d")
private val dayOnlyFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

/**
 * 导入确认弹窗。
 *
 * 显示抓取到的课程数量与预览，并让用户选择「覆盖」还是「追加」。
 */
@Composable
private fun ImportConfirmDialog(
    courses: List<Course>,
    hasExisting: Boolean,
    onConfirm: (replace: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    // 按课程名去重，用于展示涉及多少门不同的课
    val distinctNames = courses.map { it.name }.distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入课表") },
        text = {
            Column {
                Text(
                    "成功抓取 ${distinctNames.size} 门课程，共 ${courses.size} 个上课时段。",
                    fontSize = 14.sp,
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = distinctNames.take(8).joinToString("、") +
                        if (distinctNames.size > 8) " 等" else "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hasExisting) {
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        "「覆盖」会清空现有课程；「追加」保留现有课程并加入新导入的。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (hasExisting) {
                TextButton(onClick = { onConfirm(true) }) { Text("覆盖") }
            } else {
                TextButton(onClick = { onConfirm(false) }) { Text("导入") }
            }
        },
        dismissButton = {
            if (hasExisting) {
                TextButton(onClick = { onConfirm(false) }) { Text("追加") }
            } else {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
