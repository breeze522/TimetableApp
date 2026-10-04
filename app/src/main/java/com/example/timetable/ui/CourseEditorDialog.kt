package com.example.timetable.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.example.timetable.data.Adjustment
import com.example.timetable.data.Course
import com.example.timetable.ui.theme.CoursePalette
import com.example.timetable.ui.theme.courseAccent
import com.example.timetable.ui.theme.courseSurface

/**
 * 课程编辑 / 新建对话框。
 *
 * [original] 为 null 时表示编辑已有课程。
 */
@Composable
fun CourseEditorDialog(
    course: Course,
    original: Course?,
    maxWeeks: Int,
    /** 当前查看的周次，用于「本周调整」区块 */
    currentWeek: Int = 1,
    /** 这门课在 currentWeek 的调课记录（如果有） */
    adjustment: Adjustment? = null,
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit,
    onDelete: (Course) -> Unit,
    /** 把课挪到新的星期/节次（仅对 currentWeek 生效） */
    onMoveForWeek: (dayOfWeek: Int, startSection: Int) -> Unit = { _, _ -> },
    /** 让这门课在 currentWeek 停上一次 */
    onCancelForWeek: () -> Unit = {},
    /** 撤销这门课在 currentWeek 的调整 */
    onClearAdjustment: () -> Unit = {},
) {
    var name by remember { mutableStateOf(course.name) }
    var teacher by remember { mutableStateOf(course.teacher) }
    var room by remember { mutableStateOf(course.room) }
    var dayOfWeek by remember { mutableStateOf(course.dayOfWeek) }
    var startSection by remember { mutableStateOf(course.startSection.toString()) }
    var sectionCount by remember { mutableStateOf(course.sectionCount.toString()) }
    var startWeek by remember { mutableStateOf(course.startWeek.toString()) }
    var endWeek by remember { mutableStateOf(course.endWeek.toString()) }
    var parity by remember { mutableStateOf(course.weekParity) }
    var colorIndex by remember { mutableStateOf(course.colorIndex) }

    // 「本周调整」子面板
    var showAdjust by remember { mutableStateOf(false) }

    val isEditing = original != null && original.id != 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "编辑课程" else "添加课程") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课程名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("教师（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("教室（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                FieldLabel("星期")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (d in 1..7) {
                        SelectableChip(
                            text = dayLabelShortPublic(d),
                            selected = dayOfWeek == d,
                            modifier = Modifier.weight(1f),
                            onClick = { dayOfWeek = d },
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = startSection,
                        onValueChange = { startSection = it.filter { c -> c.isDigit() } },
                        label = { Text("开始节次") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = sectionCount,
                        onValueChange = { sectionCount = it.filter { c -> c.isDigit() } },
                        label = { Text("持续节数") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = startWeek,
                        onValueChange = { startWeek = it.filter { c -> c.isDigit() } },
                        label = { Text("起始周") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endWeek,
                        onValueChange = { endWeek = it.filter { c -> c.isDigit() } },
                        label = { Text("结束周") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                FieldLabel("单双周")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SelectableChip(
                        text = "每周",
                        selected = parity == Course.PARITY_ALL,
                        modifier = Modifier.weight(1f),
                        onClick = { parity = Course.PARITY_ALL },
                    )
                    SelectableChip(
                        text = "单周",
                        selected = parity == Course.PARITY_ODD,
                        modifier = Modifier.weight(1f),
                        onClick = { parity = Course.PARITY_ODD },
                    )
                    SelectableChip(
                        text = "双周",
                        selected = parity == Course.PARITY_EVEN,
                        modifier = Modifier.weight(1f),
                        onClick = { parity = Course.PARITY_EVEN },
                    )
                }

                FieldLabel("颜色")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CoursePalette.forEachIndexed { index, color ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (colorIndex == index) 3.dp else 0.dp,
                                    color = if (colorIndex == index) {
                                        MaterialTheme.colorScheme.onBackground
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = CircleShape,
                                )
                                .clickable { colorIndex = index },
                        )
                    }
                }

                // ---- 本周调整（单周调课）----
                if (isEditing) {
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showAdjust = !showAdjust }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "第 $currentWeek 周调整",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = adjustmentSummary(adjustment),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = if (showAdjust) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (showAdjust) {
                        AdjustPanel(
                            currentWeek = currentWeek,
                            course = course,
                            adjustment = adjustment,
                            onMoveForWeek = onMoveForWeek,
                            onCancelForWeek = onCancelForWeek,
                            onClearAdjustment = onClearAdjustment,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        course.copy(
                            name = name.trim(),
                            teacher = teacher.trim(),
                            room = room.trim(),
                            dayOfWeek = dayOfWeek,
                            startSection = startSection.toIntOrNull()
                                ?.coerceIn(1, GridSpec.DEFAULT_SECTIONS) ?: 1,
                            sectionCount = sectionCount.toIntOrNull()?.coerceIn(1, 6) ?: 1,
                            startWeek = startWeek.toIntOrNull()?.coerceIn(1, maxWeeks) ?: 1,
                            endWeek = endWeek.toIntOrNull()?.coerceIn(1, maxWeeks) ?: maxWeeks,
                            weekParity = parity,
                            colorIndex = colorIndex,
                        ).let {
                            // 保证 startWeek <= endWeek
                            if (it.startWeek > it.endWeek) it.copy(endWeek = it.startWeek) else it
                        },
                    )
                },
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (isEditing) {
                    IconButton(onClick = { onDelete(course) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** 调课记录的一句话描述 */
private fun adjustmentSummary(adj: Adjustment?): String = when {
    adj == null -> "本周按原时间上课"
    adj.type == Adjustment.ADJUST_CANCEL -> "本周已停上一次"
    else -> "本周改到 ${dayLabelShortPublic(adj.targetDayOfWeek)} 第 ${adj.targetStartSection} 节"
}

/**
 * 单周调课面板。
 *
 * 选择新的星期与起始节次后点「应用到本周」，或直接「本周停上一次」；
 * 若已存在调整，可一键撤销。
 */
@Composable
private fun AdjustPanel(
    currentWeek: Int,
    course: Course,
    adjustment: Adjustment?,
    onMoveForWeek: (dayOfWeek: Int, startSection: Int) -> Unit,
    onCancelForWeek: () -> Unit,
    onClearAdjustment: () -> Unit,
) {
    // 预填：已有调整就用调整后的值，否则用原课程的位置
    var targetDay by remember(adjustment) {
        mutableStateOf(
            if (adjustment != null && adjustment.type == Adjustment.ADJUST_MOVE) {
                adjustment.targetDayOfWeek
            } else {
                course.dayOfWeek
            },
        )
    }
    var targetSection by remember(adjustment) {
        mutableStateOf(
            if (adjustment != null && adjustment.type == Adjustment.ADJUST_MOVE) {
                adjustment.targetStartSection.toString()
            } else {
                course.startSection.toString()
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "只影响第 $currentWeek 周，不改动其余周次",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FieldLabel("改到星期")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (d in 1..7) {
                SelectableChip(
                    text = dayLabelShortPublic(d),
                    selected = targetDay == d,
                    modifier = Modifier.weight(1f),
                    onClick = { targetDay = d },
                )
            }
        }

        OutlinedTextField(
            value = targetSection,
            onValueChange = { targetSection = it.filter { c -> c.isDigit() }.take(2) },
            label = { Text("第几节开始") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        val sec = targetSection.toIntOrNull()
                            ?.coerceIn(1, GridSpec.DEFAULT_SECTIONS) ?: course.startSection
                        onMoveForWeek(targetDay, sec)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "应用到本周",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onCancelForWeek),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "本周停上一次",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (adjustment != null) {
            Text(
                text = "撤销本周调整",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onClearAdjustment)
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SelectableChip(
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
            textAlign = TextAlign.Center,
        )
    }
}

/** 供对话框使用的短名 */
internal fun dayLabelShortPublic(day: Int): String =
    listOf("一", "二", "三", "四", "五", "六", "日").getOrElse(day - 1) { "" }
