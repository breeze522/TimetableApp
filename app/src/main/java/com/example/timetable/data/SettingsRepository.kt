package com.example.timetable.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * 学期设置：开学日期 + 总周数 + 作息时间表。
 *
 * 开学日期以 epochDay 存储，避免时区序列化问题。
 */
class SettingsRepository(private val context: Context) {

    private val keyTermStart = longPreferencesKey("term_start_epoch_day")
    private val keyTotalWeeks = intPreferencesKey("total_weeks")
    private val keySectionTimes = stringPreferencesKey("section_times")
    private val keyReminderEnabled = intPreferencesKey("reminder_enabled")
    private val keyReminderLeadMinutes = intPreferencesKey("reminder_lead_minutes")

    // ---- 显示设置（对齐 WakeUp 的「课表设置」）----
    private val keyShowWeekend = intPreferencesKey("show_weekend")
    private val keyShowNotCurrentWeek = intPreferencesKey("show_not_current_week")
    private val keyShowTeacher = intPreferencesKey("show_teacher")
    private val keyShowRoom = intPreferencesKey("show_room")
    private val keyShowSectionTime = intPreferencesKey("show_section_time")
    private val keyCardStyle = intPreferencesKey("card_style")
    private val keyDefaultViewMode = intPreferencesKey("default_view_mode")
    private val keyWeekStartDay = intPreferencesKey("week_start_day")
    private val keyCurrentScheduleId = longPreferencesKey("current_schedule_id")

    /** 课表背景方案名（见 ui.theme.BackgroundStyle），默认极简 */
    private val keyBackgroundStyle = stringPreferencesKey("background_style")

    /**
     * 主题模式名（见 ui.theme.ThemeMode：SYSTEM / LIGHT / DARK）。
     *
     * 存在这里（而不是 ViewModel 的内存态）是因为**主题要在 Activity 最外层
     * 就确定** —— MainActivity 直接读这条流来包 `TimetableTheme`，
     * ViewModel 还没创建时就得能拿到值。
     */
    private val keyThemeMode = stringPreferencesKey("theme_mode")

    // ---- 成绩缓存 ----
    /** 上次成功抓取到的成绩原始载荷（EAMS_RESULT: 之后的 JSON 文本） */
    private val keyGradeCache = stringPreferencesKey("grade_cache_payload")

    /** 上次成功抓取成绩的时间（epoch millis），用于「上次更新」显示 */
    private val keyGradeCacheTime = longPreferencesKey("grade_cache_time")

    /**
     * 作息表版本号。
     *
     * 默认作息调整后（下午改为 14:00 开始、上午连排），
     * 老用户本地还存着旧的一份，需要在首次启动时覆盖成新默认值。
     * 当前期望版本见 [SECTION_TIMES_VERSION]，存的值小于它就重置。
     */
    private val keySectionTimesVersion = intPreferencesKey("section_times_version")

    /** 是否显示周六周日 */
    val showWeekend: Flow<Boolean> = context.dataStore.data.map { (it[keyShowWeekend] ?: 1) == 1 }

    /**
     * 是否显示非本周的课程。
     *
     * 默认关闭：只在当前周次里显示「这一周真会上」的课，画面干净。
     * 打开后整学期的课都会出现，不属于当前周的淡显。
     */
    val showNotCurrentWeek: Flow<Boolean> =
        context.dataStore.data.map { (it[keyShowNotCurrentWeek] ?: 0) == 1 }

    /** 卡片上是否显示教师 */
    val showTeacher: Flow<Boolean> = context.dataStore.data.map { (it[keyShowTeacher] ?: 1) == 1 }

    /** 卡片上是否显示教室 */
    val showRoom: Flow<Boolean> = context.dataStore.data.map { (it[keyShowRoom] ?: 1) == 1 }

    /** 节次栏是否显示起止时间（关闭则只显示节次编号） */
    val showSectionTime: Flow<Boolean> =
        context.dataStore.data.map { (it[keyShowSectionTime] ?: 1) == 1 }

    /** 卡片样式：0 = 实色，1 = 淡色描边 */
    val cardStyle: Flow<CardStyle> = context.dataStore.data.map { prefs ->
        when (prefs[keyCardStyle]) {
            CardStyle.LIGHT.ordinal -> CardStyle.LIGHT
            else -> CardStyle.SOLID
        }
    }

    /** 默认视图：0 = 周视图，1 = 日视图 */
    val defaultViewMode: Flow<ViewMode> = context.dataStore.data.map { prefs ->
        when (prefs[keyDefaultViewMode]) {
            ViewMode.DAY.ordinal -> ViewMode.DAY
            else -> ViewMode.WEEK
        }
    }

    /** 一周的起始日：1 = 周一 … 7 = 周日，默认周日 */
    val weekStartDay: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[keyWeekStartDay] ?: 7).coerceIn(1, 7)
    }

    /** 上次使用的课表 id，默认 0（默认课表） */
    val currentScheduleId: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[keyCurrentScheduleId] ?: 0L
    }

    /**
     * 课表背景方案。
     *
     * 存的是枚举名（如 `"MIST"`），改名会回退到默认值 ——
     * 这里刻意不做兼容映射：背景是可随时重选的装饰项，不值得为它背历史包袱。
     */
    val backgroundStyle: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[keyBackgroundStyle] ?: "PLAIN"
    }

    suspend fun setBackgroundStyle(name: String) {
        context.dataStore.edit { it[keyBackgroundStyle] = name }
    }

    /**
     * 主题模式名（"SYSTEM" / "LIGHT" / "DARK"）。
     *
     * 返回的是**原始字符串**而不是枚举：data 层不反向依赖 ui.theme，
     * 由调用方（MainActivity / ViewModel）自己转成 [com.example.timetable.ui.theme.ThemeMode]。
     */
    val themeModeName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[keyThemeMode] ?: "SYSTEM"
    }

    suspend fun setThemeModeName(name: String) {
        context.dataStore.edit { it[keyThemeMode] = name }
    }

    suspend fun setCurrentScheduleId(id: Long) {
        context.dataStore.edit { it[keyCurrentScheduleId] = id }
    }

    // ---- 成绩缓存 ----

    /**
     * 缓存的成绩载荷 + 抓取时间。
     *
     * 只在用户**主动刷新**且抓取成功后才写入；
     * 页面打开时优先读它，避免每次进成绩页都去教务系统拉一次。
     */
    val gradeCache: Flow<GradeCache?> = context.dataStore.data.map { prefs ->
        val payload = prefs[keyGradeCache]
        if (payload.isNullOrBlank()) {
            null
        } else {
            GradeCache(payload, prefs[keyGradeCacheTime] ?: 0L)
        }
    }

    suspend fun setGradeCache(payload: String, timestamp: Long) {
        context.dataStore.edit {
            it[keyGradeCache] = payload
            it[keyGradeCacheTime] = timestamp
        }
    }

    suspend fun clearGradeCache() {
        context.dataStore.edit {
            it.remove(keyGradeCache)
            it.remove(keyGradeCacheTime)
        }
    }

    // ---- 登录用户信息 ----
    private val keyUserName = stringPreferencesKey("user_name")
    private val keyUserNo = stringPreferencesKey("user_no")
    private val keyUserClass = stringPreferencesKey("user_class")
    private val keyUserMajor = stringPreferencesKey("user_major")
    private val keyUserDept = stringPreferencesKey("user_dept")
    private val keyUserLoginTime = longPreferencesKey("user_login_time")

    /** 上次成功登录后保存的用户信息；未登录时为 null */
    val userProfile: Flow<UserProfile?> = context.dataStore.data.map { prefs ->
        val name = prefs[keyUserName]
        val no = prefs[keyUserNo]
        val cls = prefs[keyUserClass]
        val dept = prefs[keyUserDept]
        if (name.isNullOrBlank() && no.isNullOrBlank() && cls.isNullOrBlank() && dept.isNullOrBlank()) {
            null
        } else {
            UserProfile(
                name = name ?: "",
                studentNo = no ?: "",
                className = cls ?: "",
                majorName = prefs[keyUserMajor] ?: "",
                departmentName = dept ?: "",
                loginTime = prefs[keyUserLoginTime] ?: 0L,
            )
        }
    }

    suspend fun setUserProfile(profile: UserProfile) {
        context.dataStore.edit {
            it[keyUserName] = profile.name
            it[keyUserNo] = profile.studentNo
            it[keyUserClass] = profile.className
            it[keyUserMajor] = profile.majorName
            it[keyUserDept] = profile.departmentName
            it[keyUserLoginTime] = profile.loginTime
        }
    }

    suspend fun clearUserProfile() {
        context.dataStore.edit {
            it.remove(keyUserName)
            it.remove(keyUserNo)
            it.remove(keyUserClass)
            it.remove(keyUserMajor)
            it.remove(keyUserDept)
            it.remove(keyUserLoginTime)
        }
    }

    // ---- 记住账号密码（自动登录）----
    private val keySavedUsername = stringPreferencesKey("saved_username")
    /** Keystore 加密后的密码；设备不支持时该键不写入 */
    private val keySavedPasswordEnc = stringPreferencesKey("saved_password_enc")
    /** 是否允许自动登录：0 = 只记住不自动登录，1 = 记住并自动登录 */
    private val keyAutoLogin = intPreferencesKey("saved_auto_login")

    /**
     * 已记住的登录凭据；未记住或解密失败时为 null。
     *
     * 密码用 Android Keystore 里的 AES 密钥加密后存储（见 [CredentialCipher]），
     * 拿到 DataStore 文件也无法直接读出明文。
     */
    val savedCredentials: Flow<SavedCredentials?> = context.dataStore.data.map { prefs ->
        val user = prefs[keySavedUsername]
        if (user.isNullOrBlank()) return@map null
        val enc = prefs[keySavedPasswordEnc] ?: return@map null
        val pwd = CredentialCipher.decrypt(enc) ?: return@map null
        SavedCredentials(
            username = user,
            password = pwd,
            autoLogin = (prefs[keyAutoLogin] ?: 1) == 1,
        )
    }

    /**
     * 记住账号密码。
     *
     * 加密失败（例如设备异常）时**只存账号、不存密码** —— 宁可下次要用户
     * 重新输密码，也不落一份明文。
     */
    suspend fun setSavedCredentials(username: String, password: String, autoLogin: Boolean) {
        val enc = CredentialCipher.encrypt(password)
        context.dataStore.edit {
            it[keySavedUsername] = username
            if (enc != null) it[keySavedPasswordEnc] = enc else it.remove(keySavedPasswordEnc)
            it[keyAutoLogin] = if (autoLogin) 1 else 0
        }
    }

    /** 只更新「是否自动登录」，不动已存的账号密码 */
    suspend fun setAutoLogin(enabled: Boolean) {
        context.dataStore.edit { it[keyAutoLogin] = if (enabled) 1 else 0 }
    }

    suspend fun clearSavedCredentials() {
        context.dataStore.edit {
            it.remove(keySavedUsername)
            it.remove(keySavedPasswordEnc)
            it.remove(keyAutoLogin)
        }
    }

    suspend fun setShowWeekend(value: Boolean) = putFlag(keyShowWeekend, value)

    suspend fun setShowNotCurrentWeek(value: Boolean) = putFlag(keyShowNotCurrentWeek, value)

    suspend fun setShowTeacher(value: Boolean) = putFlag(keyShowTeacher, value)

    suspend fun setShowRoom(value: Boolean) = putFlag(keyShowRoom, value)

    suspend fun setShowSectionTime(value: Boolean) = putFlag(keyShowSectionTime, value)

    suspend fun setCardStyle(style: CardStyle) {
        context.dataStore.edit { it[keyCardStyle] = style.ordinal }
    }

    suspend fun setDefaultViewMode(mode: ViewMode) {
        context.dataStore.edit { it[keyDefaultViewMode] = mode.ordinal }
    }

    suspend fun setWeekStartDay(day: Int) {
        context.dataStore.edit { it[keyWeekStartDay] = day.coerceIn(1, 7) }
    }

    private suspend fun putFlag(key: androidx.datastore.preferences.core.Preferences.Key<Int>, value: Boolean) {
        context.dataStore.edit { it[key] = if (value) 1 else 0 }
    }

    /** 开学日期；未设置时返回 null */
    val termStart: Flow<LocalDate?> = context.dataStore.data.map { prefs ->
        prefs[keyTermStart]?.let { LocalDate.ofEpochDay(it) }
    }

    /** 学期总周数，默认 20 周 */
    val totalWeeks: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[keyTotalWeeks] ?: DEFAULT_TOTAL_WEEKS
    }

    /**
     * 作息时间表。
     *
     * 未设置时返回默认值；若本地存的是**旧版本**的作息（版本号低于
     * [SECTION_TIMES_VERSION]），则一次性覆盖为新的默认值，
     * 这样用户不用手动去设置里改一遍。
     *
     * 注意：发现版本落后时挂一个协程异步写回，**不要**在 map 里同步调用
     * suspend 写入 —— 那样会阻塞数据流，且可能在写入完成前被反复触发。
     */
    private val resetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val sectionTimes: Flow<List<SectionTime>> = context.dataStore.data.map { prefs ->
        val storedVersion = prefs[keySectionTimesVersion] ?: 0
        if (storedVersion < SECTION_TIMES_VERSION) {
            val fresh = SectionTime.defaults()
            // 只在确实落后时补一次写回
            resetScope.launch { setSectionTimes(fresh, SECTION_TIMES_VERSION) }
            fresh
        } else {
            prefs[keySectionTimes]?.let { decodeSectionTimes(it) }
                ?: SectionTime.defaults()
        }
    }

    /** 是否开启上课提醒 */
    val reminderEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        (prefs[keyReminderEnabled] ?: 1) == 1
    }

    /** 提前多少分钟提醒，默认 10 分钟 */
    val reminderLeadMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[keyReminderLeadMinutes] ?: 10
    }

    suspend fun setTermStart(date: LocalDate) {
        context.dataStore.edit { it[keyTermStart] = date.toEpochDay() }
    }

    suspend fun setTotalWeeks(weeks: Int) {
        context.dataStore.edit { it[keyTotalWeeks] = weeks.coerceIn(1, 30) }
    }

    suspend fun setSectionTimes(times: List<SectionTime>) {
        setSectionTimes(times, SECTION_TIMES_VERSION)
    }

    /**
     * 写入作息表，并记录版本号。
     *
     * 用户手动在设置里改过之后也会写入当前版本号，
     * 后续升级默认值时不会再把用户的自定义配置冲掉。
     */
    suspend fun setSectionTimes(times: List<SectionTime>, version: Int) {
        context.dataStore.edit {
            it[keySectionTimes] = encodeSectionTimes(times)
            it[keySectionTimesVersion] = version
        }
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { it[keyReminderEnabled] = if (enabled) 1 else 0 }
    }

    suspend fun setReminderLeadMinutes(minutes: Int) {
        context.dataStore.edit {
            it[keyReminderLeadMinutes] = minutes.coerceIn(0, 60)
        }
    }

    companion object {
        const val DEFAULT_TOTAL_WEEKS = 20

        /**
         * 当前作息表默认值版本。
         *
         * 每次调整 [SectionTime.defaults] 就把这个数 +1，
         * 老用户下次启动会自动套用新的默认作息。
         *
         * 1 → 初始版本
         * 2 → 下午改为 14:00 开始，上午 4 节连排
         * 3 → 按「4 个大节 × 2 小节」重排：08:30 / 10:25 / 14:00 / 15:45
         */
        const val SECTION_TIMES_VERSION = 3

        /** 序列化："08:00-08:50,09:00-09:50,..." */
        private fun encodeSectionTimes(times: List<SectionTime>): String =
            times.joinToString(",") { "${it.start}-${it.end}" }

        private fun decodeSectionTimes(raw: String): List<SectionTime> =
            raw.split(",").mapNotNull { seg ->
                val parts = seg.split("-")
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    SectionTime(parts[0].trim(), parts[1].trim())
                } else {
                    null
                }
            }.ifEmpty { SectionTime.defaults() }
    }
}

/** 单节课的起止时间，如 08:00 - 08:50 */
data class SectionTime(
    val start: String,
    val end: String,
) {
    companion object {
        /**
         * 默认作息（西安建筑科技大学）。
         *
         * 全天按 **4 个大节** 组织，每个大节含 2 小节（45 分钟/节，中间 5 分钟课间）：
         *
         * - 上午第一大节 08:30 – 10:05（第 1、2 节）
         * - 上午第二大节 10:25 – 12:00（第 3、4 节）
         * - 下午第一大节 14:00 – 15:35（第 5、6 节）
         * - 下午第二大节 15:45 – 17:20（第 7、8 节）
         * - 晚上 3 节 19:00 – 21:40（第 9、10、11 节）
         *
         * 合计 11 节。中午整体空出来休息。
         */
        fun defaults(): List<SectionTime> = listOf(
            // 上午第一大节
            SectionTime("08:30", "09:15"),
            SectionTime("09:20", "10:05"),
            // 上午第二大节
            SectionTime("10:25", "11:10"),
            SectionTime("11:15", "12:00"),
            // 下午第一大节（14:00 才开始）
            SectionTime("14:00", "14:45"),
            SectionTime("14:50", "15:35"),
            // 下午第二大节
            SectionTime("15:45", "16:30"),
            SectionTime("16:35", "17:20"),
            // 晚上
            SectionTime("19:00", "19:45"),
            SectionTime("19:50", "20:35"),
            SectionTime("20:40", "21:25"),
        )
    }
}

/** 课程卡片样式：实色底白字 / 淡色底深色字 */
enum class CardStyle { SOLID, LIGHT }

/** 默认视图 */
enum class ViewMode { WEEK, DAY }

/** 成绩缓存条目：原始载荷 + 抓取时间戳 */
data class GradeCache(
    val payload: String,
    val timestamp: Long,
)

/**
 * 根据开学日期计算「第几周」。
 *
 * 规则：开学日所在的那一周即第 1 周。
 * 一周的起始日可配置（默认周日，与 WakeUp 一致），
 * 因此这里的工具方法都接受 [weekStartDay] 参数。
 */
object WeekCalculator {

    /** 默认周起始日：周日 */
    const val DEFAULT_START_DAY = 7

    private fun startDow(weekStartDay: Int): DayOfWeek =
        DayOfWeek.of(weekStartDay.coerceIn(1, 7))

    /** 把任意日期归到它所在周的起始日 */
    fun weekStartOf(date: LocalDate, weekStartDay: Int = DEFAULT_START_DAY): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(startDow(weekStartDay)))

    /**
     * 返回 [date] 属于开学第几周。
     * 若早于开学周则返回 1（视为第 1 周），超出总周数时也返回实际值，
     * 由调用方决定是否裁剪。
     */
    fun weekOf(
        termStart: LocalDate,
        date: LocalDate,
        weekStartDay: Int = DEFAULT_START_DAY,
    ): Int {
        val start = weekStartOf(termStart, weekStartDay)
        val target = weekStartOf(date, weekStartDay)
        val diffWeeks = ChronoUnit.WEEKS.between(start, target)
        return (diffWeeks + 1).toInt().coerceAtLeast(1)
    }

    /** 某一周的起始日，用于显示日期 */
    fun weekStartOfWeek(
        termStart: LocalDate,
        week: Int,
        weekStartDay: Int = DEFAULT_START_DAY,
    ): LocalDate = weekStartOf(termStart, weekStartDay).plusWeeks((week - 1).toLong())

    /** 某一周里第 [dayOfWeek] 天（1 = 周一 … 7 = 周日）对应的日期 */
    fun dateOf(
        termStart: LocalDate,
        week: Int,
        dayOfWeek: Int,
        weekStartDay: Int = DEFAULT_START_DAY,
    ): LocalDate {
        val start = weekStartOfWeek(termStart, week, weekStartDay)
        val offset = (dayOfWeek - weekStartDay + 7) % 7
        return start.plusDays(offset.toLong())
    }

    /**
     * 一周里 7 天的 dayOfWeek 排列顺序（1 = 周一 … 7 = 周日）。
     *
     * 例如周日起始 → [7, 1, 2, 3, 4, 5, 6]；周一起始 → [1, 2, 3, 4, 5, 6, 7]。
     */
    fun columnOrder(weekStartDay: Int = DEFAULT_START_DAY): List<Int> {
        val s = weekStartDay.coerceIn(1, 7)
        return (0..6).map { ((s - 1 + it) % 7) + 1 }
    }

    /** 星期几对应的中文短名 */
    fun dayLabel(dayOfWeek: Int): String =
        listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
            .getOrElse(dayOfWeek - 1) { "" }
}
