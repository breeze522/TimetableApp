package com.example.timetable.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.example.timetable.ui.theme.secondaryGlassBar
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timetable.data.CardStyle
import com.example.timetable.data.SectionTime
import com.example.timetable.data.SettingsRepository
import com.example.timetable.data.ViewMode
import com.example.timetable.data.WeekCalculator
import com.example.timetable.ui.theme.BackgroundStyle
import com.example.timetable.ui.theme.backgroundBrush
import com.example.timetable.ui.theme.painterBackground
import java.time.LocalDate

/**
 * 课表设置页（全屏）。
 *
 * 结构对齐 WakeUp 的 ScheduleSettingsActivity：
 * 分「课表显示」「课程显示」「上课提醒」「作息时间」「学期」几组，
 * 每组之间用分隔线隔开，行尾统一右对齐开关或当前值。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSettingsScreen(
    state: TimetableUiState,
    onBack: () -> Unit,
    onOpenSectionTimes: () -> Unit,
    onOpenTermSettings: () -> Unit,
    onOpenScheduleManage: () -> Unit,
    onOpenImport: () -> Unit,
    onSetShowWeekend: (Boolean) -> Unit,
    onSetShowNotCurrentWeek: (Boolean) -> Unit,
    onSetShowTeacher: (Boolean) -> Unit,
    onSetShowRoom: (Boolean) -> Unit,
    onSetShowSectionTime: (Boolean) -> Unit,
    onSetCardStyle: (CardStyle) -> Unit,
    onSetDefaultViewMode: (ViewMode) -> Unit,
    onSetWeekStartDay: (Int) -> Unit,
    onSetBackgroundStyle: (BackgroundStyle) -> Unit,
    onPickBackgroundImage: () -> Unit,
    onSetReminderEnabled: (Boolean) -> Unit,
    onSetReminderLeadMinutes: (Int) -> Unit,
    onClearAllCourses: () -> Unit = {},
    hazeState: dev.chrisbanes.haze.HazeState? = null,
) {
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(
        // 透明容器：让全局背景层（渐变 / 自定义图片）透上来，
        // 二级页面与主页共用同一个背景，切换时才不会「换了一个世界」。
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "课表设置",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
                modifier = Modifier.secondaryGlassBar(hazeState),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(bottom = 28.dp),
        ) {

            // ================= 课表显示 =================
            GroupTitle("课表显示")

            SettingSwitchRow(
                title = "显示周末",
                subtitle = "关闭后只显示周一至周五",
                checked = state.showWeekend,
                onCheckedChange = onSetShowWeekend,
            )

            SettingSwitchRow(
                title = "显示非本周课程",
                subtitle = "查看其它周次时，淡显不属于该周的课",
                checked = state.showNotCurrentWeek,
                onCheckedChange = onSetShowNotCurrentWeek,
            )

            SettingChoiceRow(
                title = "每周起始日",
                subtitle = "决定课表最左侧是哪一天",
                options = listOf(
                    1 to "一", 2 to "二", 3 to "三", 4 to "四",
                    5 to "五", 6 to "六", 7 to "日",
                ),
                selected = state.weekStartDay,
                onSelect = onSetWeekStartDay,
            )

            SettingChoiceRow(
                title = "默认视图",
                subtitle = "打开应用时先看到哪种排布",
                options = listOf(
                    ViewMode.WEEK.ordinal to "周视图",
                    ViewMode.DAY.ordinal to "日视图",
                ),
                selected = state.defaultViewMode.ordinal,
                onSelect = { onSetDefaultViewMode(ViewMode.entries[it]) },
            )

            SettingChoiceRow(
                title = "卡片样式",
                subtitle = "实色醒目，淡色柔和",
                options = listOf(
                    CardStyle.SOLID.ordinal to "实色",
                    CardStyle.LIGHT.ordinal to "淡色",
                ),
                selected = state.cardStyle.ordinal,
                onSelect = { onSetCardStyle(CardStyle.entries[it]) },
            )

            BackgroundPickerRow(
                selected = state.backgroundStyle,
                onSelect = onSetBackgroundStyle,
                onPickImage = onPickBackgroundImage,
            )

            GroupDivider()

            // ================= 课程显示 =================
            GroupTitle("课程显示")

            SettingSwitchRow(
                title = "显示教师",
                subtitle = null,
                checked = state.showTeacher,
                onCheckedChange = onSetShowTeacher,
            )

            SettingSwitchRow(
                title = "显示教室",
                subtitle = null,
                checked = state.showRoom,
                onCheckedChange = onSetShowRoom,
            )

            SettingSwitchRow(
                title = "节次栏显示时间",
                subtitle = "关闭后左侧只显示节次编号",
                checked = state.showSectionTime,
                onCheckedChange = onSetShowSectionTime,
            )

            GroupDivider()

            // ================= 上课提醒 =================
            GroupTitle("上课提醒")

            SettingSwitchRow(
                title = "开启提醒",
                subtitle = if (state.reminderEnabled) {
                    "上课前 ${state.reminderLeadMinutes} 分钟通知"
                } else {
                    "已关闭"
                },
                checked = state.reminderEnabled,
                onCheckedChange = onSetReminderEnabled,
            )

            if (state.reminderEnabled) {
                SettingChoiceRow(
                    title = "提前时间",
                    subtitle = null,
                    options = listOf(5 to "5 分", 10 to "10 分", 15 to "15 分", 30 to "30 分"),
                    selected = state.reminderLeadMinutes,
                    onSelect = onSetReminderLeadMinutes,
                )
            }

            GroupDivider()

            // ================= 作息与学期 =================
            GroupTitle("作息与学期")

            SettingNavRow(
                title = "课表管理",
                subtitle = "当前：${state.currentScheduleName} · 共 ${state.schedules.size} 份",
                onClick = onOpenScheduleManage,
            )

            SettingNavRow(
                title = "作息时间",
                subtitle = "共 ${state.sectionTimes.size} 节，点击自定义上下课时间",
                onClick = onOpenSectionTimes,
            )

            SettingNavRow(
                title = "学期设置",
                subtitle = termSummary(state.termStart, state.totalWeeks),
                onClick = onOpenTermSettings,
            )

            GroupDivider()

            // ================= 数据 =================
            GroupTitle("数据")

            SettingNavRow(
                title = "从教务系统导入",
                subtitle = "支持树维（Supwisdom）教务系统",
                leading = {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = onOpenImport,
            )

            SettingNavRow(
                title = "清空全部课程",
                subtitle = "删除本地保存的所有课程数据",
                titleColor = MaterialTheme.colorScheme.error,
                leading = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = { showResetConfirm = true },
            )
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("清空全部课程？") },
            text = {
                Text(
                    "会删除「${state.currentScheduleName}」下的全部课程，且无法撤销。" +
                        "其它课表不受影响。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    onClearAllCourses()
                }) {
                    Text("确认清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
            },
        )
    }
}

/** 学期信息一句话概述 */
private fun termSummary(termStart: LocalDate?, totalWeeks: Int): String {
    if (termStart == null) return "共 $totalWeeks 周，尚未设置开学日期"
    val current = WeekCalculator.weekOf(termStart, LocalDate.now())
    return "开学 ${termStart.monthValue}/${termStart.dayOfMonth}  共 $totalWeeks 周  当前第 $current 周"
}

/** 分组标题 */
@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
    )
}

/** 分组之间的留白 + 分隔线 */
@Composable
private fun GroupDivider() {
    Spacer(modifier = Modifier.height(14.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** 带开关的设置行 */
@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 带一组互斥选项的设置行。
 *
 * [options] 为「值 to 显示文字」，[selected] 为当前值。
 */
@Composable
private fun <T> SettingChoiceRow(
    title: String,
    subtitle: String?,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Text(text = title, fontSize = 15.sp)
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .clickable { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 课表背景选择行。
 *
 * 与 [SettingChoiceRow] 的区别：背景是**视觉项**，光看名字选不出来，
 * 所以每个选项渲染成一小块**真实的渐变缩略图**，所见即所得。
 * 选中的用主题色描一圈 + 打一个 ✓。
 */
@Composable
private fun BackgroundPickerRow(
    selected: BackgroundStyle,
    onSelect: (BackgroundStyle) -> Unit,
    onPickImage: () -> Unit,
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val context = LocalContext.current
    // 「自定义」缩略图：有图就显示真实图片，没有就显示一个「+」
    val customUri = remember(selected) {
        com.example.timetable.data.BackgroundImageStore.uri(context)
    }
    val customPainter = remember(customUri) {
        customUri?.let { uri ->
            runCatching {
                android.graphics.BitmapFactory.decodeStream(
                    context.contentResolver.openInputStream(uri),
                )?.let { androidx.compose.ui.graphics.painter.BitmapPainter(it.asImageBitmap()) }
            }.getOrNull()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Text(text = "课表背景", fontSize = 15.sp)
        Text(
            text = "液态玻璃底栏会模糊它，选个有颜色的更好看",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BackgroundStyle.entries.forEach { style ->
                val isSelected = style == selected
                val brush = backgroundBrush(style, isDark)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            // 「自定义」：没有图就先让用户选图，已有图则直接应用
                            if (style == BackgroundStyle.CUSTOM) {
                                onPickImage()
                            } else {
                                onSelect(style)
                            }
                        }
                        .padding(3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .then(
                                when {
                                    style == BackgroundStyle.CUSTOM && customPainter != null ->
                                        Modifier.painterBackground(customPainter)
                                    brush != null -> Modifier.background(brush)
                                    else -> Modifier.background(MaterialTheme.colorScheme.background)
                                },
                            )
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(9.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            isSelected -> Text(
                                text = "✓",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (customPainter != null) Color.White else MaterialTheme.colorScheme.primary,
                            )
                            style == BackgroundStyle.CUSTOM && customPainter == null -> Text(
                                text = "+",
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            else -> Unit
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = style.label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 可点击跳转的设置行 */
@Composable
private fun SettingNavRow(
    title: String,
    subtitle: String?,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, color = titleColor)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Text(
            text = "›",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 兜底：设置页在数据未加载时使用的作息 */
internal fun fallbackSectionTimes(times: List<SectionTime>): List<SectionTime> =
    if (times.isNotEmpty()) times else SectionTime.defaults()

/** 供设置页复用的默认周数常量 */
internal val defaultTotalWeeks: Int = SettingsRepository.DEFAULT_TOTAL_WEEKS
