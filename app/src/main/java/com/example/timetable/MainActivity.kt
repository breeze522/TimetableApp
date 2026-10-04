package com.example.timetable

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.timetable.ui.TimetableScreen
import com.example.timetable.ui.theme.ThemeMode
import com.example.timetable.ui.theme.TimetableTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Android 13+ 通知权限申请 */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 忽略结果 */ }

    /**
     * 桌面小组件携带的「打开哪个一级入口」。
     *
     * 值为 null 时按 App 默认行为（课表页）；点击「今日课表」小组件会传课表页，
     * 保证用户从组件进来看到的就是课表。
     */
    private var requestedTab by mutableStateOf<String?>(null)

    /** Debug-only：启动后直接弹出学期设置弹窗（供 adb 验证主题开关） */
    private var debugOpenSettings by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        requestedTab = readRequestedTab(intent)
        debugOpenSettings = readDebugOpenSettings(intent)
        handleDebugReminder(intent)
        handleDebugTheme(intent)
        setContent {
            // 主题模式要在最外层决定 —— 它决定了整个 App 用浅色还是深色配色，
            // 必须早于任何页面构建。这里直接订阅 DataStore（ViewModel 还没创建），
            // 值一变化整棵树重组，切换即时生效。
            val themeMode by rememberThemeMode()
            TimetableTheme(darkTheme = themeMode.resolveIsDark()) {
                TimetableScreen(
                    initialTabName = requestedTab,
                    openSettingsOnStart = debugOpenSettings,
                )
            }
        }
    }

    /**
     * 仅 Debug 包可用的「手动触发上课提醒」入口。
     *
     * 用来验证灵动岛 / 状态栏胶囊，而不必把 `ReminderReceiver` 暴露成
     * `exported="true"`（那会让任意应用都能向我们发通知）。
     *
     * 用法（倒计时 90 秒后上课）：
     * ```
     * adb shell am start -n com.example.timetable/.MainActivity \
     *   -e debug_reminder 1 -e debug_lead_sec 90
     * ```
     * Release 包里这个方法体不会被任何调用触发（`BuildConfig.DEBUG` 为 false 时
     * 直接短路返回），且 release 构建会由 R8 裁掉。
     */
    private fun handleDebugReminder(intent: android.content.Intent?) {
        if (!BuildConfig.DEBUG) return
        if (intent?.getStringExtra(EXTRA_DEBUG_REMINDER) == null) return
        val leadSec = intent.getStringExtra(EXTRA_DEBUG_LEAD_SEC)?.toLongOrNull() ?: 90L
        com.example.timetable.reminder.ReminderScheduler
            .triggerPreviewReminder(this, leadSec)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readRequestedTab(intent)?.let { requestedTab = it }
        debugOpenSettings = readDebugOpenSettings(intent)
        // 让调试入口在 Activity 已存在时也能重复触发（否则只有冷启动那一次有效）
        handleDebugReminder(intent)
        handleDebugTheme(intent)
    }

    /** Debug-only：是否要求启动即打开学期设置 */
    private fun readDebugOpenSettings(intent: android.content.Intent?): Boolean =
        BuildConfig.DEBUG && intent?.getStringExtra(EXTRA_DEBUG_SETTINGS) != null

    /**
     * 仅 Debug 包可用的「切换主题模式」入口。
     *
     * 存在的理由：本机（华为）adb 无法向 Compose 注入点击/滑动，
     * 走不了「学期设置 → 主题」的 UI 路径。这里直接写 DataStore，
     * 等价于用户在设置里点了一下，用来验证主题链路与持久化。
     *
     * 用法：
     * ```
     * adb shell am start -n com.example.timetable/.MainActivity -e debug_theme DARK
     * ```
     * 取值 [ThemeMode] 的枚举名（SYSTEM / LIGHT / DARK），非法值忽略。
     */
    private fun handleDebugTheme(intent: android.content.Intent?) {
        if (!BuildConfig.DEBUG) return
        val raw = intent?.getStringExtra(EXTRA_DEBUG_THEME) ?: return
        val mode = ThemeMode.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: return
        // 落盘后 DataStore 流会推新值，MainActivity 顶层订阅随即重组
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            com.example.timetable.data.SettingsRepository(applicationContext)
                .setThemeModeName(mode.name)
        }
    }

    private fun readRequestedTab(intent: android.content.Intent?): String? =
        intent?.getStringExtra(EXTRA_OPEN_TAB)

    /**
     * 订阅持久化的主题模式。
     *
     * 为什么不走 ViewModel？因为主题要在 `setContent` 的第一层就定下来，
     * 而 ViewModel 是 `TimetableScreen` 内部创建的 —— 等它可用时主题已经包好了。
     * 这里直接读 DataStore 的流，初值用 [ThemeMode.DEFAULT]，
     * 读到持久化值后自动重组一次。
     */
    @androidx.compose.runtime.Composable
    private fun rememberThemeMode(): androidx.compose.runtime.State<ThemeMode> {
        val repo = androidx.compose.runtime.remember {
            com.example.timetable.data.SettingsRepository(applicationContext)
        }
        val modeName by repo.themeModeName
            .collectAsState(initial = ThemeMode.DEFAULT.name)
        // 直接由字符串派生 State：DataStore 一推新值，这里就地重组，
        // 不需要再包一层 remember(mutableStateOf)。
        return androidx.compose.runtime.remember(modeName) {
            androidx.compose.runtime.mutableStateOf(ThemeMode.fromName(modeName))
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        /** Intent extra：指定启动后落到哪个一级入口 */
        const val EXTRA_OPEN_TAB = "open_tab"

        /** [EXTRA_OPEN_TAB] 的取值之一：课表页 */
        const val TAB_TIMETABLE = "timetable"

        /** Debug-only：手动触发一次上课提醒，用于验证灵动岛 */
        const val EXTRA_DEBUG_REMINDER = "debug_reminder"

        /** Debug-only：倒计时提前秒数（默认 90 秒后上课） */
        const val EXTRA_DEBUG_LEAD_SEC = "debug_lead_sec"

        /** Debug-only：切换主题模式（SYSTEM / LIGHT / DARK），用于 adb 验证配色 */
        const val EXTRA_DEBUG_THEME = "debug_theme"

        /** Debug-only：启动后直接打开学期设置弹窗（任意非空值即可） */
        const val EXTRA_DEBUG_SETTINGS = "debug_settings"
    }
}
