package com.example.timetable.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timetable.data.SettingsRepository
import com.example.timetable.data.WeekCalculator
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 学期设置对话框：设置开学日期与总周数。
 *
 * 开学日期用「年 / 月 / 日」三个数字输入框，避免引入日期选择器依赖。
 */
@Composable
fun SettingsDialog(
    termStart: LocalDate?,
    totalWeeks: Int,
    reminderEnabled: Boolean,
    reminderLeadMinutes: Int,
    weekStartDay: Int = WeekCalculator.DEFAULT_START_DAY,
    themeMode: com.example.timetable.ui.theme.ThemeMode =
        com.example.timetable.ui.theme.ThemeMode.DEFAULT,
    onSetThemeMode: (com.example.timetable.ui.theme.ThemeMode) -> Unit = {},
    onDismiss: () -> Unit,
    onSetTermStart: (LocalDate) -> Unit,
    onSetTotalWeeks: (Int) -> Unit,
    onSetReminderEnabled: (Boolean) -> Unit,
    onSetReminderLeadMinutes: (Int) -> Unit,
    onOpenSectionTimes: () -> Unit = {},
) {
    val today = remember { LocalDate.now() }
    var year by remember { mutableStateOf((termStart ?: today).year.toString()) }
    var month by remember { mutableStateOf((termStart ?: today).monthValue.toString()) }
    var day by remember { mutableStateOf((termStart ?: today).dayOfMonth.toString()) }
    var weeks by remember { mutableStateOf(totalWeeks.toString()) }

    val parsed: LocalDate? = runCatching {
        LocalDate.of(
            year.toInt(),
            month.toInt(),
            day.toInt(),
        )
    }.getOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("学期设置") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "设置开学日期后，应用会自动推算当前是第几周。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "开学日期",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("年") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1.2f),
                    )
                    OutlinedTextField(
                        value = month,
                        onValueChange = { month = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("月") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = day,
                        onValueChange = { day = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("日") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = weeks,
                    onValueChange = { weeks = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("学期总周数") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (parsed != null) {
                    val currentWeek = WeekCalculator.weekOf(parsed, today, weekStartDay)
                    Text(
                        text = "开学日：${parsed.format(formatter)}   今天是第 $currentWeek 周",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        text = "请输入有效的日期",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 作息时间入口
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenSectionTimes)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "作息时间",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "自定义每节课的上下课时间",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 上课提醒开关
                val reminderSummary = if (reminderEnabled) {
                    "上课前 $reminderLeadMinutes 分钟提醒"
                } else {
                    "已关闭"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "上课提醒",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = reminderSummary,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = onSetReminderEnabled,
                    )
                }

                // 提前分钟数（仅在开启时显示）
                if (reminderEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(5, 10, 15, 30).forEach { m ->
                            SelectableChipLite(
                                text = "$m 分钟",
                                selected = reminderLeadMinutes == m,
                                modifier = Modifier.weight(1f),
                                onClick = { onSetReminderLeadMinutes(m) },
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ---- 主题模式 ----
                // 三个互斥选项横排。用「跟随系统 / 浅色 / 深色」而不是一个开关，
                // 因为开关只能表达两态，无法同时兼顾「跟随系统」和「手动指定」。
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        Text(
                            text = "主题",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = com.example.timetable.ui.theme.ThemeMode.entries
                                .firstOrNull { it == themeMode }
                                ?.let { "当前：${it.label}" }
                                ?: "跟随系统深色模式",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        com.example.timetable.ui.theme.ThemeMode.entries.forEach { mode ->
                            SelectableChipLite(
                                text = mode.label,
                                selected = themeMode == mode,
                                modifier = Modifier.weight(1f),
                                onClick = { onSetThemeMode(mode) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    parsed?.let { onSetTermStart(it) }
                    weeks.toIntOrNull()?.let { onSetTotalWeeks(it) }
                    onDismiss()
                },
                enabled = parsed != null,
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日")

/** 轻量选择胶囊（复用课程编辑器里的交互习惯） */
@Composable
private fun SelectableChipLite(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
