package com.example.timetable.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timetable.data.Adjustment
import com.example.timetable.data.AdjustmentDao
import com.example.timetable.data.AppDatabase
import com.example.timetable.data.CardStyle
import com.example.timetable.data.Course
import com.example.timetable.data.CourseDao
import com.example.timetable.data.GradeCache
import com.example.timetable.data.Schedule
import com.example.timetable.data.ScheduleDao
import com.example.timetable.data.SavedCredentials
import com.example.timetable.data.SectionTime
import com.example.timetable.data.SettingsRepository
import com.example.timetable.data.UserProfile
import com.example.timetable.data.ViewMode
import com.example.timetable.data.WeekCalculator
import com.example.timetable.reminder.ReminderScheduler
import com.example.timetable.widget.TodayWidgetProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)

/** 课表页面的完整状态 */
data class TimetableUiState(    val courses: List<Course> = emptyList(),
    /** 当前正在查看的周次 */
    val currentWeek: Int = 1,
    /** 真实「本周」的周次（用于回到本周按钮与高亮） */
    val realWeek: Int = 1,
    val termStart: LocalDate? = null,
    val totalWeeks: Int = SettingsRepository.DEFAULT_TOTAL_WEEKS,
    /** 作息时间（节次起止），可配置 */
    val sectionTimes: List<SectionTime> = SectionTime.defaults(),
    /** 是否开启上课提醒 */
    val reminderEnabled: Boolean = true,
    /** 提前多少分钟提醒 */
    val reminderLeadMinutes: Int = 10,

    // ---- 显示设置 ----
    val showWeekend: Boolean = true,
    val showNotCurrentWeek: Boolean = false,
    val showTeacher: Boolean = true,
    val showRoom: Boolean = true,
    val showSectionTime: Boolean = true,
    val cardStyle: CardStyle = CardStyle.SOLID,
    val defaultViewMode: ViewMode = ViewMode.WEEK,
    val weekStartDay: Int = WeekCalculator.DEFAULT_START_DAY,

    /** 课表背景方案（见 ui.theme.BackgroundStyle） */
    val backgroundStyle: com.example.timetable.ui.theme.BackgroundStyle =
        com.example.timetable.ui.theme.BackgroundStyle.PLAIN,

    /** 主题模式（深色 / 浅色 / 跟随系统），见 ui.theme.ThemeMode */
    val themeMode: com.example.timetable.ui.theme.ThemeMode =
        com.example.timetable.ui.theme.ThemeMode.DEFAULT,

    // ---- 多课表 ----
    /** 全部课表 */
    val schedules: List<Schedule> = emptyList(),
    /** 当前使用的课表 id */
    val currentScheduleId: Long = 0,
    /** 当前课表名称，用于顶栏副标题 */
    val currentScheduleName: String = Schedule.DEFAULT_NAME,
    /** 各课表的课程数（课表 id → 数量） */
    val courseCounts: Map<Long, Int> = emptyMap(),

    /** 当前课表的调课记录 */
    val adjustments: List<Adjustment> = emptyList(),

    val loading: Boolean = true,
) {
    /** 当前周需要展示的课程（已按单双周与周次区间过滤） */
    val visibleCourses: List<Course>
        get() = courses.filter { it.isActiveInWeek(currentWeek) }

    /**
     * 把某周的基础课程套上调课记录，得到该周真实要显示的课程。
     *
     * - 原始课程被「停上」→ 从结果里移除
     * - 原始课程被「挪走」→ 移除原位，并在新位置插入一条同名课程（保留 id 以便点击编辑）
     */
    fun coursesForWeek(week: Int): List<Course> {
        val base = courses.filter { it.isActiveInWeek(week) }
        if (adjustments.isEmpty()) return base

        // 本周生效的调整，按 courseId 索引
        val forWeek = adjustments.filter { it.week == week }.associateBy { it.courseId }
        if (forWeek.isEmpty()) return base

        val result = mutableListOf<Course>()
        base.forEach { c ->
            when (val adj = forWeek[c.id]) {
                null -> result += c
                else -> when (adj.type) {
                    Adjustment.ADJUST_CANCEL -> Unit // 停上，丢弃
                    else -> result += c.copy(
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
        return result
    }

    /** 某门课在第 [week] 周是否有调课记录 */
    fun adjustmentOf(courseId: Long, week: Int): Adjustment? =
        adjustments.firstOrNull { it.courseId == courseId && it.week == week }
}

class TimetableViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val dao: CourseDao = db.courseDao()
    private val scheduleDao: ScheduleDao = db.scheduleDao()
    private val adjustmentDao: AdjustmentDao = db.adjustmentDao()
    private val settings = SettingsRepository(app)

    /** 用户手动切换周次；null 表示跟随「本周」 */
    private val manualWeek = MutableStateFlow<Int?>(null)

    /** 当前使用的课表 id */
    private val currentScheduleId = MutableStateFlow(0L)

    // ---- 校园通知公告 ----
    /** 已抓到的教务处通知（学生相关） */
    private val _announcements = MutableStateFlow<List<com.example.timetable.data.Announcement>>(emptyList())
    val announcements: StateFlow<List<com.example.timetable.data.Announcement>> = _announcements

    /** 是否正在抓取 */
    private val _announcementsLoading = MutableStateFlow(false)
    val announcementsLoading: StateFlow<Boolean> = _announcementsLoading

    /** 抓取失败信息；成功时为 null */
    private val _announcementsError = MutableStateFlow<String?>(null)
    val announcementsError: StateFlow<String?> = _announcementsError

    /**
     * 是否已经抓取过。
     *
     * 校园生活页每次切回来都会重组，但不能每次都重新发请求（浪费流量、
     * 也会闪一下 loading）。用这个标记做「首次进入才自动抓」，
     * 之后由用户手动点刷新。
     */
    private var announcementsFetched = false

    /**
     * 加载教务处通知公告。
     *
     * @param force true = 忽略「已抓过」标记，强制重新请求（下拉/点刷新）
     */
    fun loadAnnouncements(force: Boolean = false) {
        if (_announcementsLoading.value) return
        if (!force && announcementsFetched && _announcements.value.isNotEmpty()) return

        viewModelScope.launch {
            _announcementsLoading.value = true
            _announcementsError.value = null
            runCatching {
                com.example.timetable.data.AnnouncementRepository.fetch()
            }.onSuccess { list ->
                if (list.isNotEmpty()) {
                    _announcements.value = list
                    announcementsFetched = true
                } else {
                    _announcementsError.value = "页面结构可能已变化，未解析到内容"
                }
            }.onFailure { e ->
                _announcementsError.value = e.message ?: "网络连接失败"
            }
            _announcementsLoading.value = false
        }
    }

    // ---- 成绩缓存 ----
    /** 缓存的成绩载荷（含抓取时间），页面打开时优先使用 */
    val gradeCache: StateFlow<GradeCache?> =
        settings.gradeCache.stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.Eagerly,
            null,
        )
    /** 抓取成功后写入缓存 */
    fun saveGradeCache(payload: String) {
        viewModelScope.launch {
            settings.setGradeCache(payload, System.currentTimeMillis())
        }
    }

    // ---- 登录用户信息 ----
    /** 已登录用户信息；未登录为 null */
    val userProfile: StateFlow<UserProfile?> =
        settings.userProfile.stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.Eagerly,
            null,
        )

    /**
     * 登录成功后保存用户信息。
     *
     * 同时置位 [autoImportRequested]，让界面在登录完成后自动抓取课表
     * 并以「覆盖」方式导入（用户要求：登录后课表自动导入、自动覆盖）。
     */
    fun saveUserProfile(profile: UserProfile) {
        viewModelScope.launch { settings.setUserProfile(profile) }
        _autoImportRequested.value = true
    }

    /** 「登录后自动导入课表」的待处理请求 */
    private val _autoImportRequested = MutableStateFlow(false)
    val autoImportRequested: StateFlow<Boolean> = _autoImportRequested.asStateFlow()

    /** 界面已消费该请求（例如已切到导入页） */
    fun consumeAutoImportRequest() {
        _autoImportRequested.value = false
    }

    // ---- 记住账号密码（自动登录）----
    /** 已记住的账号密码；未记住为 null */
    val savedCredentials: StateFlow<SavedCredentials?> =
        settings.savedCredentials.stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.Eagerly,
            null,
        )

    /** 记住账号密码（加密存储） */
    fun saveCredentials(username: String, password: String, autoLogin: Boolean) {
        viewModelScope.launch {
            settings.setSavedCredentials(username, password, autoLogin)
        }
    }

    /** 只改「是否自动登录」开关 */
    fun setAutoLogin(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoLogin(enabled) }
    }

    /** 忘掉已记住的账号密码 */
    fun forgetCredentials() {
        viewModelScope.launch { settings.clearSavedCredentials() }
    }

    /** 退出登录：清除用户信息与成绩缓存（换账号后成绩应重新拉取） */
    fun logout() {
        viewModelScope.launch {
            settings.clearUserProfile()
            settings.clearGradeCache()
        }
    }

    /** 首次启动时确保至少存在一份课表 */
    init {
        viewModelScope.launch {
            if (scheduleDao.count() == 0) {
                scheduleDao.insert(Schedule(id = 0, name = Schedule.DEFAULT_NAME))
            }
            // 读取上次使用的课表
            val saved = settings.currentScheduleId.first()
            val exists = scheduleDao.findById(saved) != null
            currentScheduleId.value = if (exists || saved == 0L) saved else 0L
        }
        viewModelScope.launch {
            // 当前课表被删除时回落到第一份
            scheduleDao.observeAll().collect { list ->
                schedules.value = list
                if (list.isNotEmpty() && list.none { it.id == currentScheduleId.value }) {
                    currentScheduleId.value = list.first().id
                }
            }
        }
    }

    /** 课表管理页是否可见 */
    private val _showScheduleManage = MutableStateFlow(false)
    val showScheduleManage: StateFlow<Boolean> = _showScheduleManage.asStateFlow()

    /**
     * 课表管理页的来源：从课表设置进入时，返回应回到课表设置而不是主页。
     *
     * 复用 [SettingsOrigin] 表示「上一级是谁」，语义足够。
     */
    private val _manageOrigin = MutableStateFlow(SettingsOrigin.HOME)
    val manageOrigin: StateFlow<SettingsOrigin> = _manageOrigin.asStateFlow()

    fun openScheduleManage(from: SettingsOrigin = SettingsOrigin.HOME) {
        _manageOrigin.value = from
        _showScheduleManage.value = true
    }

    fun closeScheduleManage() {
        _showScheduleManage.value = false
    }

    /** 供 UI 读取「用户是否手动切过周」 */
    val isManualWeek: Boolean
        get() = manualWeek.value != null

    /** 用于编辑的课程（null = 关闭编辑面板） */
    private val _editing = MutableStateFlow<Course?>(null)
    val editing: StateFlow<Course?> = _editing.asStateFlow()

    /** 新增时的预填位置（星期 + 节次） */
    private val _draftSlot = MutableStateFlow<Pair<Int, Int>?>(null)
    val draftSlot: StateFlow<Pair<Int, Int>?> = _draftSlot.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    /** 是否展示「学期设置」对话框（从课表设置页进入） */
    private val _showTermSettings = MutableStateFlow(false)
    val showTermSettings: StateFlow<Boolean> = _showTermSettings.asStateFlow()

    /**
     * 「学期设置 / 作息设置」关闭后应回到哪里。
     *
     * 这两个弹窗有两个入口：课表设置页、以及主界面的设置齿轮。
     * 按返回键或点关闭后，应当回到**进入它时的那个页面**，
     * 而不是一律回主页，否则会丢掉「刚才是从课表设置进来的」这一层。
     */
    enum class SettingsOrigin { HOME, SCHEDULE_SETTINGS }

    private val _settingsOrigin = MutableStateFlow(SettingsOrigin.HOME)
    val settingsOrigin: StateFlow<SettingsOrigin> = _settingsOrigin.asStateFlow()

    /** 从课表设置页打开学期设置 */
    fun openTermSettings(from: SettingsOrigin = SettingsOrigin.HOME) {
        _settingsOrigin.value = from
        _showTermSettings.value = true
    }

    fun closeTermSettings() {
        _showTermSettings.value = false
    }

    /** 课表设置页是否可见（全屏页） */
    private val _showScheduleSettings = MutableStateFlow(false)
    val showScheduleSettings: StateFlow<Boolean> = _showScheduleSettings.asStateFlow()

    fun openScheduleSettings() {
        _showScheduleSettings.value = true
    }

    fun closeScheduleSettings() {
        _showScheduleSettings.value = false
    }

    /** 作息时间设置面板是否可见 */
    private val _showSectionTimes = MutableStateFlow(false)
    val showSectionTimes: StateFlow<Boolean> = _showSectionTimes.asStateFlow()

    fun openSectionTimes(from: SettingsOrigin = SettingsOrigin.HOME) {
        _settingsOrigin.value = from
        _showSectionTimes.value = true
    }

    fun closeSectionTimes() {
        _showSectionTimes.value = false
    }

    fun setSectionTimes(times: List<SectionTime>) {
        viewModelScope.launch {
            settings.setSectionTimes(times)
            ReminderScheduler.reschedule(getApplication())
            TodayWidgetProvider.refresh(getApplication())
        }
    }

    /** 开关上课提醒 */
    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setReminderEnabled(enabled)
            ReminderScheduler.reschedule(getApplication())
        }
    }

    /** 设置提前提醒的分钟数 */
    fun setReminderLeadMinutes(minutes: Int) {
        viewModelScope.launch {
            settings.setReminderLeadMinutes(minutes)
            ReminderScheduler.reschedule(getApplication())
        }
    }

    /** 应用启动时重排提醒 */
    fun refreshReminder() {
        viewModelScope.launch {
            ReminderScheduler.reschedule(getApplication())
        }
    }

    /** 全部课表（供管理页与顶栏使用） */
    private val schedules = MutableStateFlow<List<Schedule>>(emptyList())

    /** 各课表的课程数 */
    private val courseCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())

    /** 当前课表的调课记录 */
    private val adjustments = MutableStateFlow<List<Adjustment>>(emptyList())

    init {
        viewModelScope.launch {
            dao.observeCountsBySchedule().collect { rows ->
                courseCounts.value = rows.associate { it.scheduleId to it.cnt }
            }
        }
        viewModelScope.launch {
            // 调课记录跟随当前课表切换
            currentScheduleId.collect { id ->
                adjustmentDao.observeBySchedule(id).collect { adjustments.value = it }
            }
        }
    }

    val uiState: StateFlow<TimetableUiState> =
        combine(
            // 当前课表的课程随 currentScheduleId 切换
            currentScheduleId.flatMapLatest { id -> dao.observeBySchedule(id) },
            settings.termStart,
            settings.totalWeeks,
            manualWeek,
            settings.sectionTimes,
            settings.reminderEnabled,
            settings.reminderLeadMinutes,
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val courses = values[0] as List<Course>
            val termStart = values[1] as LocalDate?
            val totalWeeks = values[2] as Int
            val manual = values[3] as Int?
            val sectionTimes = values[4] as List<SectionTime>
            val reminderEnabled = values[5] as Boolean
            val reminderLead = values[6] as Int

            val realWeek = termStart?.let {
                WeekCalculator.weekOf(it, LocalDate.now(), weekStartDay.value)
            } ?: 1
            TimetableUiState(
                courses = courses,
                currentWeek = (manual ?: realWeek).coerceIn(1, totalWeeks),
                realWeek = realWeek.coerceIn(1, totalWeeks),
                termStart = termStart,
                totalWeeks = totalWeeks,
                sectionTimes = sectionTimes,
                reminderEnabled = reminderEnabled,
                reminderLeadMinutes = reminderLead,
                showWeekend = showWeekend.value,
                showNotCurrentWeek = showNotCurrentWeek.value,
                showTeacher = showTeacher.value,
                showRoom = showRoom.value,
                showSectionTime = showSectionTime.value,
                cardStyle = cardStyle.value,
                defaultViewMode = defaultViewMode.value,
                weekStartDay = weekStartDay.value,
                backgroundStyle = backgroundStyle.value,
                themeMode = themeMode.value,
                schedules = schedules.value,
                currentScheduleId = currentScheduleId.value,
                currentScheduleName = schedules.value
                    .firstOrNull { it.id == currentScheduleId.value }
                    ?.name ?: Schedule.DEFAULT_NAME,
                courseCounts = courseCounts.value,
                adjustments = adjustments.value,
                loading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TimetableUiState(),
        )

    // ---- 多课表管理 ----

    /** 切换当前使用的课表 */
    fun switchSchedule(schedule: Schedule) {
        currentScheduleId.value = schedule.id
        manualWeek.value = null
        // 各课表的学期设置各自独立，切过去时把它的开学日期也应用上
        viewModelScope.launch {
            settings.setCurrentScheduleId(schedule.id)
            schedule.termStartEpochDay?.let { settings.setTermStart(LocalDate.ofEpochDay(it)) }
            settings.setTotalWeeks(schedule.totalWeeks)
            ReminderScheduler.reschedule(getApplication())
            TodayWidgetProvider.refresh(getApplication())
        }
    }

    /** 新建一份课表并切换过去 */
    fun createSchedule(name: String) {
        viewModelScope.launch {
            val nextOrder = (schedules.value.maxOfOrNull { it.sortOrder } ?: 0) + 1
            val id = scheduleDao.insert(
                Schedule(
                    name = name.ifBlank { Schedule.DEFAULT_NAME },
                    sortOrder = nextOrder,
                ),
            )
            currentScheduleId.value = id
            settings.setCurrentScheduleId(id)
            manualWeek.value = null
            ReminderScheduler.reschedule(getApplication())
            TodayWidgetProvider.refresh(getApplication())
        }
    }

    /** 重命名课表 */
    fun renameSchedule(schedule: Schedule, name: String) {
        viewModelScope.launch {
            scheduleDao.update(schedule.copy(name = name.ifBlank { Schedule.DEFAULT_NAME }))
        }
    }

    /** 删除课表（连同其下课程） */
    fun deleteSchedule(schedule: Schedule) {
        viewModelScope.launch {
            scheduleDao.clearCourses(schedule.id)
            scheduleDao.delete(schedule)
            if (currentScheduleId.value == schedule.id) {
                val remain = scheduleDao.getAllOnce().firstOrNull()
                if (remain != null) {
                    currentScheduleId.value = remain.id
                    settings.setCurrentScheduleId(remain.id)
                }
            }
            ReminderScheduler.reschedule(getApplication())
            TodayWidgetProvider.refresh(getApplication())
        }
    }

    // ---- 显示设置 ----

    /** 显示设置先落到本地 StateFlow（UI 立即响应），再异步持久化。 */
    private val showWeekend = MutableStateFlow(true)
    private val showNotCurrentWeek = MutableStateFlow(false)
    private val showTeacher = MutableStateFlow(true)
    private val showRoom = MutableStateFlow(true)
    private val showSectionTime = MutableStateFlow(true)
    private val cardStyle = MutableStateFlow(CardStyle.SOLID)
    private val defaultViewMode = MutableStateFlow(ViewMode.WEEK)

    /** 每周起始日（1 = 周一 … 7 = 周日） */
    private val weekStartDay = MutableStateFlow(WeekCalculator.DEFAULT_START_DAY)

    /** 课表背景方案 */
    private val backgroundStyle = MutableStateFlow(
        com.example.timetable.ui.theme.BackgroundStyle.PLAIN,
    )

    /** 主题模式 */
    private val themeMode = MutableStateFlow(com.example.timetable.ui.theme.ThemeMode.DEFAULT)

    init {
        // 首次启动时把持久化的显示设置读进内存
        viewModelScope.launch {
            settings.showWeekend.collect { showWeekend.value = it }
        }
        viewModelScope.launch {
            settings.showNotCurrentWeek.collect { showNotCurrentWeek.value = it }
        }
        viewModelScope.launch {
            settings.showTeacher.collect { showTeacher.value = it }
        }
        viewModelScope.launch {
            settings.showRoom.collect { showRoom.value = it }
        }
        viewModelScope.launch {
            settings.showSectionTime.collect { showSectionTime.value = it }
        }
        viewModelScope.launch {
            settings.cardStyle.collect { cardStyle.value = it }
        }
        viewModelScope.launch {
            settings.defaultViewMode.collect { defaultViewMode.value = it }
        }
        viewModelScope.launch {
            settings.weekStartDay.collect { weekStartDay.value = it }
        }
        viewModelScope.launch {
            settings.backgroundStyle.collect {
                backgroundStyle.value = com.example.timetable.ui.theme.BackgroundStyle.fromName(it)
            }
        }
        viewModelScope.launch {
            settings.themeModeName.collect {
                themeMode.value = com.example.timetable.ui.theme.ThemeMode.fromName(it)
            }
        }
    }

    /** 切换主题模式（深色 / 浅色 / 跟随系统） */
    fun setThemeMode(mode: com.example.timetable.ui.theme.ThemeMode) {
        themeMode.value = mode
        viewModelScope.launch { settings.setThemeModeName(mode.name) }
    }

    /** 切换课表背景 */
    fun setBackgroundStyle(style: com.example.timetable.ui.theme.BackgroundStyle) {
        backgroundStyle.value = style
        viewModelScope.launch { settings.setBackgroundStyle(style.name) }
    }

    fun setShowWeekend(value: Boolean) {
        showWeekend.value = value
        viewModelScope.launch { settings.setShowWeekend(value) }
    }

    fun setShowNotCurrentWeek(value: Boolean) {
        showNotCurrentWeek.value = value
        viewModelScope.launch { settings.setShowNotCurrentWeek(value) }
    }

    fun setShowTeacher(value: Boolean) {
        showTeacher.value = value
        viewModelScope.launch { settings.setShowTeacher(value) }
    }

    fun setShowRoom(value: Boolean) {
        showRoom.value = value
        viewModelScope.launch { settings.setShowRoom(value) }
    }

    fun setShowSectionTime(value: Boolean) {
        showSectionTime.value = value
        viewModelScope.launch { settings.setShowSectionTime(value) }
    }

    fun setCardStyle(style: CardStyle) {
        cardStyle.value = style
        viewModelScope.launch { settings.setCardStyle(style) }
    }

    fun setDefaultViewMode(mode: ViewMode) {
        defaultViewMode.value = mode
        viewModelScope.launch { settings.setDefaultViewMode(mode) }
    }

    fun setWeekStartDay(day: Int) {
        weekStartDay.value = day.coerceIn(1, 7)
        viewModelScope.launch { settings.setWeekStartDay(day) }
    }

    // ---- 视图模式 ----

    /** 周视图 / 日视图切换，是否已手动切过 */
    private val _viewMode = MutableStateFlow<ViewMode?>(null)
    val viewMode: StateFlow<ViewMode?> = _viewMode.asStateFlow()

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
    }

    /** 清空当前课表的全部课程 */
    fun clearAllCourses() {
        viewModelScope.launch {
            dao.clearBySchedule(currentScheduleId.value)
            ReminderScheduler.reschedule(getApplication())
            TodayWidgetProvider.refresh(getApplication())
        }
    }

    // ---- 周次切换 ----

    fun goToWeek(week: Int) {
        val s = uiState.value
        val total = s.totalWeeks
        val target = week.coerceIn(1, total)
        // 滑到「本周」时视为未手动切换，便于「回到本周」高亮状态自然复位
        manualWeek.value = if (target == s.realWeek) null else target
    }

    fun nextWeek() = goToWeek(uiState.value.currentWeek + 1)

    fun prevWeek() = goToWeek(uiState.value.currentWeek - 1)

    /** 回到真实本周 */
    fun backToToday() {
        manualWeek.value = null
    }

    // ---- 课程增删改 ----

    /** 点击空白格：新建一门课，预填星期与节次 */
    fun startCreate(dayOfWeek: Int, section: Int) {
        _editing.value = Course(
            name = "",
            dayOfWeek = dayOfWeek,
            startSection = section,
            startWeek = 1,
            endWeek = uiState.value.totalWeeks,
            scheduleId = currentScheduleId.value,
        )
        _draftSlot.value = dayOfWeek to section
    }

    /** 点击已有课程卡片：编辑 */
    fun startEdit(course: Course) {
        _editing.value = course
        _draftSlot.value = null
    }

    fun cancelEdit() {
        _editing.value = null
        _draftSlot.value = null
    }

    /**
     * 保存课程。[original] 为 null 表示新增，否则为被编辑的原课程。
     */
    fun saveCourse(course: Course, original: Course?) {
        if (course.name.isBlank()) return
        viewModelScope.launch {
            // 新增的课归属当前课表
            val toSave = course.copy(scheduleId = currentScheduleId.value)
            if (original == null || original.id == 0L) {
                dao.insert(toSave.copy(id = 0))
            } else {
                dao.update(toSave)
            }
            cancelEdit()
            ReminderScheduler.reschedule(getApplication())
            TodayWidgetProvider.refresh(getApplication())
        }
    }

    fun deleteCourse(course: Course) {
        viewModelScope.launch {
            dao.delete(course)
            // 顺带清掉这门课的调课记录，避免留下悬挂引用
            adjustmentDao.deleteByCourse(course.id)
            cancelEdit()
            ReminderScheduler.reschedule(getApplication())
            TodayWidgetProvider.refresh(getApplication())
        }
    }

    // ---- 单周调课 ----

    /**
     * 把某门课在第 [week] 周挪到新的「星期 + 节次」。
     *
     * 只记录例外，不改动原始课程。
     */
    fun moveCourseForWeek(
        course: Course,
        week: Int,
        targetDayOfWeek: Int,
        targetStartSection: Int,
        targetSectionCount: Int = course.sectionCount,
    ) {
        viewModelScope.launch {
            val sid = currentScheduleId.value
            val existing = adjustmentDao.find(sid, course.id, week)
            val record = (existing ?: Adjustment(
                scheduleId = sid,
                courseId = course.id,
                week = week,
            )).copy(
                type = Adjustment.ADJUST_MOVE,
                targetDayOfWeek = targetDayOfWeek,
                targetStartSection = targetStartSection,
                targetSectionCount = targetSectionCount,
            )
            adjustmentDao.insert(record)
            cancelEdit()
            ReminderScheduler.reschedule(getApplication())
            TodayWidgetProvider.refresh(getApplication())
        }
    }

    /** 让某门课在第 [week] 周停上一次 */
    fun cancelCourseForWeek(course: Course, week: Int) {
        viewModelScope.launch {
            val sid = currentScheduleId.value
            val existing = adjustmentDao.find(sid, course.id, week)
            val record = (existing ?: Adjustment(
                scheduleId = sid,
                courseId = course.id,
                week = week,
            )).copy(type = Adjustment.ADJUST_CANCEL)
            adjustmentDao.insert(record)
            cancelEdit()
            ReminderScheduler.reschedule(getApplication())
            TodayWidgetProvider.refresh(getApplication())
        }
    }

    /** 撤销某门课在第 [week] 周的调课记录 */
    fun clearAdjustmentForWeek(course: Course, week: Int) {
        viewModelScope.launch {
            adjustmentDao.find(currentScheduleId.value, course.id, week)?.let {
                adjustmentDao.delete(it)
            }
            cancelEdit()
            ReminderScheduler.reschedule(getApplication())
            TodayWidgetProvider.refresh(getApplication())
        }
    }

    // ---- 设置 ----

    fun openSettings() {
        _showSettings.value = true
    }

    fun closeSettings() {
        _showSettings.value = false
    }

    fun setTermStart(date: LocalDate) {
        viewModelScope.launch {
            settings.setTermStart(date)
            manualWeek.value = null
            ReminderScheduler.reschedule(getApplication())
        }
    }

    fun setTotalWeeks(weeks: Int) {
        viewModelScope.launch { settings.setTotalWeeks(weeks) }
    }

    // ---- 从教务系统导入 ----

    private val _showImport = MutableStateFlow(false)
    val showImport: StateFlow<Boolean> = _showImport.asStateFlow()

    fun openImport() {
        _showImport.value = true
    }

    fun closeImport() {
        _showImport.value = false
    }

    /**
     * 批量导入课程。
     *
     * @param courses 解析得到的课程列表
     * @param replace true = 先清空现有课程；false = 追加
     * @param currentWeek 教务系统给出的当前教学周；用于反推开学日期
     */
    fun importCourses(
        courses: List<Course>,
        replace: Boolean,
        currentWeek: Int? = null,
    ) {
        viewModelScope.launch {
            val sid = currentScheduleId.value
            if (replace) dao.clearBySchedule(sid)
            courses.forEach { dao.insert(it.copy(id = 0, scheduleId = sid)) }

            // 用教务系统的「当前教学周」反推开学日期：
            // 本周起始日向前推 (当前周 - 1) 周，即开学那一周的起始日。
            if (currentWeek != null && currentWeek > 0) {
                val start = WeekCalculator.weekStartOf(LocalDate.now(), weekStartDay.value)
                val termStart = start.minusWeeks((currentWeek - 1).toLong())
                settings.setTermStart(termStart)
                manualWeek.value = null
                // 同步写回当前课表，切走再切回来时学期设置仍在
                scheduleDao.findById(sid)?.let {
                    scheduleDao.update(it.copy(termStartEpochDay = termStart.toEpochDay()))
                }
            }
            _showImport.value = false
            ReminderScheduler.reschedule(getApplication())
            TodayWidgetProvider.refresh(getApplication())
        }
    }
}
