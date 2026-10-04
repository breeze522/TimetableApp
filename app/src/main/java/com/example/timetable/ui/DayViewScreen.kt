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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timetable.data.Course
import com.example.timetable.data.SectionTime
import com.example.timetable.data.WeekCalculator
import com.example.timetable.ui.theme.courseColor
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 日视图：一次只看一天，按节次顺序列出当天的课。
 *
 * 与周视图是同一份数据的两种排布：周视图看「全局分布」，
 * 日视图看「今天具体几点在哪上什么」，文字空间更充裕。
 *
 * 左右滑动切换星期，页面顺序为 日 一 二 三 四 五 六（周日起始）。
 */
@Composable
fun DayViewScreen(
    pagerState: PagerState,
    courses: List<Course>,
    week: Int,
    realWeek: Int,
    termStart: LocalDate?,
    sectionTimes: List<SectionTime>,
    onCourseClick: (Course) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        DayStripHeader(
            pagerState = pagerState,
            termStart = termStart,
            week = week,
            realWeek = realWeek,
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { it },
        ) { page ->
            val dow = GridSpec.columnOrder[page]
            DayCourseList(
                dayOfWeek = dow,
                courses = remember(courses, week, dow) {
                    courses
                        .filter { it.dayOfWeek == dow && it.isActiveInWeek(week) }
                        .sortedBy { it.startSection }
                },
                sectionTimes = sectionTimes,
                onCourseClick = onCourseClick,
            )
        }
    }
}

/**
 * 顶部的星期条：一行 7 个圆点 + 星期字，跟随 Pager 实时联动。
 *
 * 今天用实心主色圆点标记，与周视图表头的今日高亮保持一致。
 */
@Composable
private fun DayStripHeader(
    pagerState: PagerState,
    termStart: LocalDate?,
    week: Int,
    realWeek: Int,
) {
    val current = pagerState.currentPage
    val todayDow = remember(week, realWeek) {
        if (week == realWeek) LocalDate.now().dayOfWeek.value else -1
    }
    val scope = rememberCoroutineScope()
    val columns = GridSpec.visibleColumns

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        columns.forEachIndexed { index, dow ->
            val selected = index == current
            val isToday = dow == todayDow
            val date = remember(termStart, week, dow) {
                termStart?.let {
                    WeekCalculator.dateOf(it, week, dow, GridSpec.weekStartOfCurrent)
                        .format(dayFormatter)
                } ?: ""
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        scope.launch { pagerState.animateScrollToPage(index) }
                    }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = dayLabelByDow(dow),
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        selected -> MaterialTheme.colorScheme.primary
                        isToday -> MaterialTheme.colorScheme.onBackground
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (date.isNotEmpty()) {
                    Text(
                        text = date,
                        fontSize = 10.sp,
                        color = when {
                            selected -> MaterialTheme.colorScheme.primary
                            isToday -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline
                        },
                    )
                }
                Spacer(modifier = Modifier.size(3.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                selected -> MaterialTheme.colorScheme.primary
                                isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                else -> androidx.compose.ui.graphics.Color.Transparent
                            },
                        ),
                )
            }
        }
    }
}

private val dayFormatter: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("M/d")

/**
 * 单天的课程列表。
 *
 * 每一行 = 左侧时间轴 + 右侧课程卡片，相邻课程之间用竖线连接形成时间感。
 * 没有课的日子给一句友好的空态文案。
 */
@Composable
private fun DayCourseList(
    dayOfWeek: Int,
    courses: List<Course>,
    sectionTimes: List<SectionTime>,
    onCourseClick: (Course) -> Unit,
) {
    if (courses.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "今天没有课",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "好好休息一下",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        courses.forEach { course ->
            DayCourseRow(
                course = course,
                sectionTimes = sectionTimes,
                onClick = { onCourseClick(course) },
            )
        }
    }
}

/** 单条课程行：左侧「节次 + 起止时间」，右侧课程信息卡片 */
@Composable
private fun DayCourseRow(
    course: Course,
    sectionTimes: List<SectionTime>,
    onClick: () -> Unit,
) {
    val startTime = sectionTimes.getOrNull(course.startSection - 1)?.start.orEmpty()
    val endTime = sectionTimes.getOrNull(course.startSection + course.sectionCount - 2)?.end
        .orEmpty()
        .ifBlank {
            sectionTimes.getOrNull(course.startSection - 1)?.end.orEmpty()
        }

    Row(modifier = Modifier.fillMaxWidth()) {
        // 时间轴
        Column(
            modifier = Modifier.width(58.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = startTime,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = endTime,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.size(6.dp))
            SectionBadge(
                text = if (course.sectionCount > 1) {
                    "${course.startSection}-${course.startSection + course.sectionCount - 1} 节"
                } else {
                    "第 ${course.startSection} 节"
                },
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // 课程卡片：左侧色条 + 课程信息
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(courseColor(course.colorIndex))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.size(4.dp))
                val sub = buildList {
                    if (course.room.isNotBlank()) add("@${course.room}")
                    if (course.teacher.isNotBlank()) add(course.teacher)
                    if (course.parityLabel.isNotBlank()) add(course.parityLabel)
                }.joinToString("  ")
                if (sub.isNotEmpty()) {
                    Text(
                        text = sub,
                        fontSize = 12.sp,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 节次小徽标 */
@Composable
private fun SectionBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 某一周里第 [dayOfWeek] 天对应的日期文字（M/d），供日视图标题使用 */
fun dayDateLabel(termStart: LocalDate?, week: Int, dayOfWeek: Int): String =
    termStart?.let {
        WeekCalculator.dateOf(it, week, dayOfWeek)
            .format(java.time.format.DateTimeFormatter.ofPattern("M/d"))
    } ?: ""
