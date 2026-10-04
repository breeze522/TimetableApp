package com.example.timetable.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.example.timetable.data.Course
import com.example.timetable.data.SectionTime
import com.example.timetable.ui.theme.courseColor
import com.example.timetable.ui.theme.secondaryGlassBar

/**
 * 课程搜索页（全屏）。
 *
 * 支持按课程名 / 教师 / 教室模糊匹配，结果按「星期 + 节次」排序，
 * 点击任意一条直接打开该课程的编辑面板。
 */
@Composable
fun CourseSearchScreen(
    courses: List<Course>,
    sectionTimes: List<SectionTime>,
    currentWeek: Int,
    onCourseClick: (Course) -> Unit,
    onDismiss: () -> Unit,
    hazeState: dev.chrisbanes.haze.HazeState? = null,
) {
    var query by remember { mutableStateOf("") }

    val results: List<Course> = remember(courses, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            emptyList()
        } else {
            courses.filter { c ->
                c.name.contains(q, ignoreCase = true) ||
                    c.teacher.contains(q, ignoreCase = true) ||
                    c.room.contains(q, ignoreCase = true)
            }.sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // 透明背景：与主页共用全局背景层，切页观感统一
            .background(Color.Transparent)
            // 全屏页在 enableEdgeToEdge 下会画到状态栏后面，
            // 顶部补出状态栏高度，避免搜索栏与时钟重叠。
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // ---- 搜索栏（毛玻璃顶条，与主页顶栏 / 二级页顶栏同款）----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(Modifier.secondaryGlassBar(hazeState))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索课程、教师或教室", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        when {
            query.trim().isEmpty() -> HintText("输入关键词开始搜索")
            results.isEmpty() -> HintText("没有找到匹配的课程")
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = "共 ${results.size} 条结果",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
                results.forEach { course ->
                    SearchResultRow(
                        course = course,
                        sectionTimes = sectionTimes,
                        currentWeek = currentWeek,
                        onClick = { onCourseClick(course) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun HintText(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 单条搜索结果 */
@Composable
private fun SearchResultRow(
    course: Course,
    sectionTimes: List<SectionTime>,
    currentWeek: Int,
    onClick: () -> Unit,
) {
    val startTime = sectionTimes.getOrNull(course.startSection - 1)?.start.orEmpty()
    val endTime = sectionTimes
        .getOrNull(course.startSection + course.sectionCount - 2)?.end
        .orEmpty()
        .ifBlank { sectionTimes.getOrNull(course.startSection - 1)?.end.orEmpty() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧色条（与课表卡片同色，便于快速对应）
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(courseColor(course.colorIndex)),
        )
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = course.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (course.isActiveInWeek(currentWeek)) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "本周",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = buildString {
                    append(dayLabelFull(course.dayOfWeek))
                    append("  第 ${course.startSection} 节")
                    if (startTime.isNotEmpty()) append("  $startTime-$endTime")
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val extra = buildList {
                if (course.room.isNotBlank()) add("@${course.room}")
                if (course.teacher.isNotBlank()) add(course.teacher)
                add(weekRangeLabel(course))
            }.joinToString("  ")
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = extra,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 「第 1-16 周」/「第 1-16 周 单周」形式的周次描述 */
fun weekRangeLabel(course: Course): String {
    val base = if (course.startWeek == course.endWeek) {
        "第 ${course.startWeek} 周"
    } else {
        "第 ${course.startWeek}-${course.endWeek} 周"
    }
    return if (course.parityLabel.isBlank()) base else "$base ${course.parityLabel}"
}
