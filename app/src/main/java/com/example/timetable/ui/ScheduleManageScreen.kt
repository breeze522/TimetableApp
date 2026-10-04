package com.example.timetable.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timetable.data.Schedule
import com.example.timetable.data.WeekCalculator
import com.example.timetable.ui.theme.secondaryGlassBar
import java.time.LocalDate

/**
 * 课表管理页（全屏）。
 *
 * 对齐 WakeUp 的 ScheduleManageActivity：列出全部课表，
 * 可新建、重命名、切换当前课表、删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleManageScreen(
    schedules: List<Schedule>,
    currentScheduleId: Long,
    /** 每份课表的课程数，用于在副标题里展示 */
    courseCountOf: (Long) -> Int,
    onBack: () -> Unit,
    onSwitch: (Schedule) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Schedule, String) -> Unit,
    onDelete: (Schedule) -> Unit,
    hazeState: dev.chrisbanes.haze.HazeState? = null,
) {
    var editing by remember { mutableStateOf<Schedule?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Schedule?>(null) }

    Scaffold(
        // 透明容器：与主页共用同一个全局背景，避免切页时「换了一个世界」
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text("课表管理", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { creating = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新建课表",
                            tint = MaterialTheme.colorScheme.primary,
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = "同时保存多份课表，随时切换。每份课表有独立的课程与学期设置。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            items(schedules, key = { it.id }) { schedule ->
                ScheduleCard(
                    schedule = schedule,
                    isCurrent = schedule.id == currentScheduleId,
                    courseCount = courseCountOf(schedule.id),
                    onClick = { onSwitch(schedule) },
                    onRename = { editing = schedule },
                    onDelete = { pendingDelete = schedule },
                )
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // 新建
    if (creating) {
        NameInputDialog(
            title = "新建课表",
            initial = "",
            placeholder = "例如：2026 秋",
            confirmText = "创建",
            onDismiss = { creating = false },
            onConfirm = { name ->
                onCreate(name)
                creating = false
            },
        )
    }

    // 重命名
    editing?.let { target ->
        NameInputDialog(
            title = "重命名课表",
            initial = target.name,
            placeholder = Schedule.DEFAULT_NAME,
            confirmText = "保存",
            onDismiss = { editing = null },
            onConfirm = { name ->
                onRename(target, name)
                editing = null
            },
        )
    }

    // 删除确认
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除「${target.name}」？") },
            text = {
                Text(
                    "该课表下的 ${courseCountOf(target.id)} 个课程时段会一并删除，且无法撤销。",
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target)
                    pendingDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

/** 单张课表卡片 */
@Composable
private fun ScheduleCard(
    schedule: Schedule,
    isCurrent: Boolean,
    courseCount: Int,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isCurrent) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = schedule.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isCurrent) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "使用中",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = scheduleSummary(schedule, courseCount),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(onClick = onRename) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "重命名",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** 「共 20 周 · 第 5 周 · 12 个时段」 */
private fun scheduleSummary(schedule: Schedule, courseCount: Int): String {
    val parts = mutableListOf<String>()
    parts += "共 ${schedule.totalWeeks} 周"
    schedule.termStartEpochDay?.let { epoch ->
        val start = LocalDate.ofEpochDay(epoch)
        val week = WeekCalculator.weekOf(start, LocalDate.now())
        if (week in 1..schedule.totalWeeks) parts += "第 $week 周"
        parts += "开学 ${start.monthValue}/${start.dayOfMonth}"
    }
    parts += if (courseCount > 0) "$courseCount 个时段" else "暂无课程"
    return parts.joinToString("  ·  ")
}

/** 通用「输入一个名字」的对话框 */
@Composable
private fun NameInputDialog(
    title: String,
    initial: String,
    placeholder: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(SCHEDULE_NAME_MAX_LENGTH) },
                label = { Text("课表名称") },
                placeholder = { Text(placeholder, fontSize = 14.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim().ifBlank { placeholder }) },
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 课表名称的最大长度 */
internal const val SCHEDULE_NAME_MAX_LENGTH = 20
