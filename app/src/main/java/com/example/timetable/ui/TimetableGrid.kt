package com.example.timetable.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timetable.data.CardStyle
import com.example.timetable.data.Course
import com.example.timetable.data.SectionTime
import com.example.timetable.ui.theme.courseColor
import com.example.timetable.ui.theme.isLightColor
import com.example.timetable.ui.theme.pressBounce

/**
 * 课表网格的布局常量。
 *
 * 数值对齐 WakeUp 课程表的真实资源（aapt2 从 APK 中提取）：
 * - `dimen/weekItemHeight = 56dp`  每节课的行高
 * - `dimen/weekItemMarTop  = 2dp`  课程卡片相对格子的上边距
 * - `dimen/weekItemMarLeft = 0dp`  课程卡片相对格子的左边距
 *
 * 统一行高后整个课表更紧凑，下午 9、10 节不用滚动太多就能看到。
 */
object GridSpec {
    const val DAYS = 7

    /** 兜底节次数（作息表未加载时使用） */
    const val DEFAULT_SECTIONS = 12

    val headerHeight = 44.dp

    /** 每节课的行高（WakeUp `weekItemHeight`） */
    val sectionHeight = 56.dp

    /** 课程卡片相对格子的上边距（WakeUp `weekItemMarTop`） */
    val weekItemMarTop = 2.dp

    /** 课程卡片相对格子的左右边距（WakeUp `weekItemMarLeft`） */
    val weekItemMarLeft = 0.dp

    /**
     * 左侧节次栏宽度。
     *
     * 之前是 64dp，但内容靠右对齐，导致左侧留出一大片空白（用户反馈的
     * 「最左侧一列竖着的白条」）。这里收窄到 48dp 并改为居中对齐，
     * 节次数字与时间紧贴课程区，视觉上更接近 WakeUp。
     */
    val timeColumnWidth = 48.dp

    /** 课程卡片四周视觉留白（在格子内再内缩，保证卡片不粘连） */
    val cellGap = 1.dp

    /**
     * 午休空档。
     *
     * 上午第 4 节 12:00 下课，下午第 5 节 14:00 才上课，中间空出两小时。
     * 数据上第 4、5 节是**相邻行**，若不额外留白，视觉上会以为
     * 「上完第 4 节紧接着就上第 5 节」，容易看错时间。
     *
     * 做法：在第 [LUNCH_AFTER_SECTION] 节与下一节之间插入一段空隙，
     * 高度不按真实时长换算（真实 2 小时会撑得过高），只做视觉分隔。
     */
    const val LUNCH_AFTER_SECTION = 4

    /** 午休空档的高度 */
    val lunchGapHeight = 34.dp

    /**
     * 第 [section]（1 起）节的**内容行高**（不含午休空档），恒为 [sectionHeight]。
     *
     * 所有节次号、课程卡片的尺寸都基于这个值，
     * 保证「一行就是一节」，不会因为午休空档而变形。
     */
    fun heightOf(@Suppress("UNUSED_PARAMETER") section: Int) = sectionHeight

    /**
     * 第 [section] 节顶部的偏移量（含此前插入的午休空档）。
     *
     * 布局顺序为：`第1节 | 第2节 | 第3节 | 第4节 | 午休空档 | 第5节 | …`
     * 因此第 5 节及之后的每一节，都要把 [lunchGapHeight] 计入。
     */
    fun topOf(section: Int): androidx.compose.ui.unit.Dp {
        var acc = 0.dp
        for (s in 1 until section) acc += heightOf(s)
        // 跨过午休：从第 5 节起整体下移一个空档
        if (section > LUNCH_AFTER_SECTION) acc += lunchGapHeight
        return acc
    }

    /** 整个网格的总高度（节次行 + 午休空档） */
    fun totalHeight(sections: Int): androidx.compose.ui.unit.Dp =
        spanHeight(1, sections) + lunchGapHeight

    /** 跨越 [start] 起共 [count] 节的课程卡片高度（不含午休空档） */
    fun courseHeightOf(start: Int, count: Int): androidx.compose.ui.unit.Dp {
        var acc = 0.dp
        for (s in start until start + count) acc += heightOf(s)
        return acc
    }

    /** 跨越 [start] 起共 [count] 节的总高度 */
    fun spanHeight(start: Int, count: Int): androidx.compose.ui.unit.Dp {
        var acc = 0.dp
        for (s in start until start + count) acc += heightOf(s)
        return acc
    }

    /**
     * 列的显示顺序：数组下标即列索引 0..6，值是对应的 dayOfWeek（1 = 周一 … 7 = 周日）。
     *
     * 默认周日起始 `[7, 1, 2, 3, 4, 5, 6]`，可通过设置里的「每周起始日」改变。
     * 这里保留为可变属性，由 `TimetableScreen` 在组合前按设置写入。
     */
    var columnOrder: List<Int> = listOf(7, 1, 2, 3, 4, 5, 6)

    /** 当前视图实际展示的列（按 [showWeekend] 决定是否裁掉周六周日） */
    var visibleColumns: List<Int> = columnOrder

    /** 是否显示周六周日 */
    var showWeekend: Boolean = true

    /** 当前生效的「每周起始日」（1 = 周一 … 7 = 周日），供日期换算复用 */
    var weekStartOfCurrent: Int = com.example.timetable.data.WeekCalculator.DEFAULT_START_DAY

    /** 依据设置刷新列顺序与可见列 */
    fun applyWeekStart(weekStartDay: Int, weekendVisible: Boolean) {
        weekStartOfCurrent = weekStartDay.coerceIn(1, 7)
        columnOrder = com.example.timetable.data.WeekCalculator.columnOrder(weekStartDay)
        showWeekend = weekendVisible
        visibleColumns = if (weekendVisible) columnOrder else columnOrder.filter { it <= 5 }
    }

    /** 当前展示的列数 */
    val dayCount: Int get() = visibleColumns.size

    /** dayOfWeek 在当前视图中的列下标；不可见时返回 -1 */
    fun columnIndexOf(dayOfWeek: Int): Int = visibleColumns.indexOf(dayOfWeek)
}

/**
 * 课表网格。
 *
 * 列顺序为 日 一 二 三 四 五 六（周日起始）。
 * - 表头带日期，今天高亮；
 * - 节次栏显示「节次 + 起止时间」；
 * - 课程为实色卡片，白字。
 *
 * @param dates 与列顺序一一对应的日期文字（长度 7）
 * @param todayIndex 今天所在的列索引（0..6），-1 表示不显示今天
 */
@Composable
fun TimetableGrid(
    courses: List<Course>,
    modifier: Modifier = Modifier,
    dates: List<String> = emptyList(),
    todayIndex: Int = -1,
    sectionTimes: List<SectionTime> = emptyList(),
    /** 是否绘制顶部「星期 + 日期」表头（周次页面共用时置 false） */
    showHeader: Boolean = true,
    /** 是否绘制左侧节次时间栏（周次页面共用时置 false） */
    showTimeColumn: Boolean = true,
    /** 卡片样式（实色 / 淡色） */
    cardStyle: CardStyle = CardStyle.SOLID,
    /** 卡片上是否显示教室 / 教师 */
    showRoom: Boolean = true,
    showTeacher: Boolean = true,
    /** 节次栏是否显示起止时间 */
    showSectionTime: Boolean = true,
    /**
     * 当前查看的周次；用于「显示非本周课程」时把不属于该周的课淡显。
     * 传 0 表示不做淡显区分。
     */
    highlightWeek: Int = 0,
    /** 本周有调课记录的课程 id，卡片右上角会打一个「调」角标 */
    adjustedIds: Set<Long> = emptySet(),
    onCourseClick: (Course) -> Unit,
    onEmptyCellClick: (dayOfWeek: Int, section: Int) -> Unit,
) {
    val times = if (sectionTimes.isNotEmpty()) sectionTimes else SectionTime.defaults()
    val sections = times.size
    val columns = GridSpec.visibleColumns
    val dayCount = columns.size.coerceAtLeast(1)

    val courseGrid: Map<Pair<Int, Int>, Course> = remember(courses) {
        buildMap {
            courses.forEach { c ->
                for (s in c.startSection until c.startSection + c.sectionCount) {
                    put(c.dayOfWeek to s, c)
                }
            }
        }
    }

    // 计算每门课在「同一天、时间重叠」的课程组中的横向位置与总列数，
    // 避免同一时段多门课互相遮挡。
    //
    // 关键：只把「属于当前查看周次」的课程纳入分组计算。
    // 否则淡显模式下整学期的课会全挤在同一个格子里，
    // 每门课都被判定为互相重叠，列宽被压成一条线。
    val layout: Map<Long, Pair<Int, Int>> = remember(courses, highlightWeek) {
        val result = mutableMapOf<Long, Pair<Int, Int>>()
        // 参与「占位竞争」的课程：无高亮周次时就是全部
        val competing = if (highlightWeek > 0) {
            courses.filter { it.isActiveInWeek(highlightWeek) }
        } else {
            courses
        }
        competing.groupBy { it.dayOfWeek }.forEach { (_, dayCourses) ->
            val sorted = dayCourses.sortedBy { it.startSection }
            val groups = mutableListOf<MutableList<Course>>()
            for (c in sorted) {
                val g = groups.lastOrNull()
                val overlaps = g != null && g.any { other ->
                    c.startSection < other.startSection + other.sectionCount &&
                        other.startSection < c.startSection + c.sectionCount
                }
                if (overlaps) g!!.add(c) else groups.add(mutableListOf(c))
            }
            groups.forEach { g ->
                g.forEachIndexed { idx, c -> result[c.id] = idx to g.size }
            }
        }
        result
    }

    Column(modifier = modifier) {

        val gridLineColor = MaterialTheme.colorScheme.outlineVariant

        // ---- 顶部：星期 + 日期 ----
        if (showHeader) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(GridSpec.timeColumnWidth))
                columns.forEachIndexed { colIndex, dow ->
                    val isToday = colIndex == todayIndex
                    val isWeekend = dow == 7 || dow == 6
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
                        val dateText = dates.getOrNull(colIndex).orEmpty()
                        if (dateText.isNotEmpty()) {
                            Text(
                                text = dateText,
                                fontSize = 10.sp,
                                fontWeight = if (isToday) FontWeight.SemiBold
                                else FontWeight.Normal,
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
            HorizontalDivider(thickness = 1.dp, color = gridLineColor)
        }

        // ---- 主体：节次栏 + 单元格 ----
        Row(modifier = Modifier.fillMaxWidth()) {
            // 左侧节次编号 + 时间
            if (showTimeColumn) {
                Column(modifier = Modifier.width(GridSpec.timeColumnWidth)) {
                    for (section in 1..sections) {
                        Column(
                            modifier = Modifier
                                .height(GridSpec.heightOf(section))
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = "$section",
                                    fontSize = 12.sp,
                                    lineHeight = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (showSectionTime) {
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = times.getOrNull(section - 1)?.start.orEmpty(),
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
                                    text = times.getOrNull(section - 1)?.end.orEmpty(),
                                    fontSize = 9.sp,
                                    lineHeight = 10.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                )
                            }
                        }
                        // 第 4 节后补出午休空档，与右侧课程区严格对齐
                        if (section == GridSpec.LUNCH_AFTER_SECTION && section < sections) {
                            Box(
                                modifier = Modifier
                                    .height(GridSpec.lunchGapHeight)
                                    .fillMaxWidth(),
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
                    }
                }
            }

            // 课程区域
            Box(modifier = Modifier.weight(1f)) {

                val gridHeight = remember(sections) {
                    GridSpec.totalHeight(sections)
                }
                // 各节顶部相对课程区顶部的像素偏移（下标 = 节次）。
                // 注意：从第 5 节起要多算一个午休空档，因此必须逐节累加，
                // 不能简单地用 `(s - 1) * sectionHeight`。
                val density = LocalDensity.current
                val sectionTopsPx = remember(sections, density) {
                    IntArray(sections + 2).also { arr ->
                        val rowPx = with(density) { GridSpec.sectionHeight.toPx() }
                        val lunchPx = with(density) { GridSpec.lunchGapHeight.toPx() }
                        // 语义：arr[s] = 第 s 节内容区**顶部**（含此前插入的午休空档）。
                        //
                        // 关键：午休空档是**一次性**的常量偏移，不是逐节累加！
                        // 曾经写成在循环内 `if (s > LUNCH_AFTER_SECTION) arr[s] += gapPx`，
                        // 结果第 5 节 +1 个 gap、第 6 节 +2 个、第 7 节 +3 个……
                        // 越往后偏移越夸张（第 12 节会多叠 8 个 gap），
                        // 用户看到的就是「下午 3、4 节离下午 1、2 节特别远」。
                        //
                        // 现在改为：先按纯行高铺满，再对「第 5 节及之后」统一加一个 gap。
                        val gapPx = lunchPx.toInt()
                        arr[1] = 0
                        for (s in 2..(sections + 1)) {
                            arr[s] = arr[s - 1] + rowPx.toInt()
                        }
                        // 午休之后的所有节次整体下移一次（不含第 1~4 节）
                        for (s in (GridSpec.LUNCH_AFTER_SECTION + 1)..(sections + 1)) {
                            arr[s] += gapPx
                        }
                    }
                }

                // 手势回调与占位表通过 State 读取，避免成为 pointerInput 的 key
                // （否则课程一变就重建手势检测器，滑动时会造成输入延迟）。
                val emptyCellHandler by rememberUpdatedState(onEmptyCellClick)
                val occupiedCells by rememberUpdatedState(courseGrid)

                // 底层：网格线 + 点击热区，全部在一个 pointerInput 里命中，
                // 不再为每格创建 Composable 节点（原来 7×12=84 个 Box）。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gridHeight)
                        .drawBehind {
                            val colW = size.width / dayCount
                            val rowPx = GridSpec.sectionHeight.toPx()
                            for (i in 1 until dayCount) {
                                val x = colW * i
                                drawLine(
                                    color = gridLineColor,
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 1f,
                                )
                            }
                            for (r in 1 until sections) {
                                // 跳过午休空档：那一处不画横线，保持「断开」的观感
                                if (r == GridSpec.LUNCH_AFTER_SECTION) continue
                                val y = sectionTopsPx[r + 1].toFloat()
                                // 大节分界（每 2 小节一组）画粗一点的线，
                                // 让「上午第一大节 / 第二大节 / 下午…」的结构一眼可见
                                val isBigSectionBreak = r % 2 == 0
                                drawLine(
                                    color = if (isBigSectionBreak) {
                                        gridLineColor
                                    } else {
                                        gridLineColor.copy(alpha = 0.5f)
                                    },
                                    start = Offset(0f, y),
                                    end = Offset(colW * dayCount, y),
                                    strokeWidth = if (isBigSectionBreak) 1.5f else 1f,
                                )
                            }

                            // 午休空档：整条横向留白 + 中间一条淡淡的虚线，
                            // 明确表达「12:00–14:00 没有课」
                            if (sections > GridSpec.LUNCH_AFTER_SECTION) {
                                // 空档顶部 = 第 4 节底部 = 第 4 节顶部 + 一行高
                                // （注意：不能用 arr[5]，那是第 5 节顶部、已经含空档）
                                val gapTop =
                                    sectionTopsPx[GridSpec.LUNCH_AFTER_SECTION] + rowPx
                                val gapBottom = gapTop + GridSpec.lunchGapHeight.toPx()
                                val midY = (gapTop + gapBottom) / 2f
                                val dash = 8.dp.toPx()
                                var x = 0f
                                while (x < size.width) {
                                    drawLine(
                                        color = gridLineColor.copy(alpha = 0.35f),
                                        start = Offset(x, midY),
                                        end = Offset((x + dash).coerceAtMost(size.width), midY),
                                        strokeWidth = 1f,
                                    )
                                    x += dash * 2
                                }
                            }
                        }
                        .pointerInput(sections, dayCount) {
                            detectTapGestures { offset ->
                                val colW = size.width / dayCount
                                val col = (offset.x / colW).toInt().coerceIn(0, dayCount - 1)
                                val dow = columns[col]
                                // 找到点击落在哪一节
                                var section = -1
                                for (s in 1..sections) {
                                    if (offset.y >= sectionTopsPx[s] &&
                                        offset.y < sectionTopsPx[s + 1]
                                    ) {
                                        section = s
                                        break
                                    }
                                }
                                if (section > 0 && !occupiedCells.containsKey(dow to section)) {
                                    emptyCellHandler(dow, section)
                                }
                            }
                        },
                )

                // 课程卡片：绝对定位。列宽在整个课程区里统一算一次，
                // 避免每张卡片各建一个 BoxWithConstraints 造成测量歧义与开销。
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gridHeight),
                ) {
                    val colWidth = maxWidth / dayCount
                    courses.forEach { course ->
                        key(course.id) {
                            val (lane, lanes) = layout[course.id] ?: (0 to 1)
                            CourseBlockAbsolute(
                                course = course,
                                lane = lane,
                                lanes = lanes,
                                colWidth = colWidth,
                                sectionTopsPx = sectionTopsPx,
                                cardStyle = cardStyle,
                                showRoom = showRoom,
                                showTeacher = showTeacher,
                                // 不属于当前查看周次的课程降低不透明度，视觉上退到背景
                                dimmed = highlightWeek > 0 && !course.isActiveInWeek(highlightWeek),
                                adjusted = adjustedIds.contains(course.id),
                                onClick = { onCourseClick(course) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单个课程卡片：实色底 + 白字，**绝对定位**。
 *
 * 用 `offset` 直接摆放，而不是「Spacer + weight」嵌套。
 * 后者每张卡片会产生约 10 个参与权重测量的节点，
 * 滑动时每帧都要重新测量，是卡顿的主因。
 *
 * @param lane 该课程在重叠组中的横向序号（0 起）
 * @param lanes 重叠组的总列数
 * @param colWidth 单列（一天）的宽度，由父级统一计算
 * @param sectionTopsPx 每节顶部相对课程区顶部的像素偏移（下标 = 节次）
 */
@Composable
private fun CourseBlockAbsolute(
    course: Course,
    lane: Int,
    lanes: Int,
    colWidth: androidx.compose.ui.unit.Dp,
    sectionTopsPx: IntArray,
    cardStyle: CardStyle,
    showRoom: Boolean,
    showTeacher: Boolean,
    dimmed: Boolean = false,
    /** 本周有调课记录，右上角打「调」角标 */
    adjusted: Boolean = false,
    onClick: () -> Unit,
) {
    // 卡片圆角：从 6dp 提到 9dp，更贴合「玻璃片」的圆润观感
    val shape = RoundedCornerShape(9.dp)

    // 课程所在列（按当前周起始日决定的显示顺序）
    val colIndex = GridSpec.columnIndexOf(course.dayOfWeek)
    if (colIndex < 0) return

    val density = LocalDensity.current
    val gap = GridSpec.cellGap
    val cardWidth = colWidth / lanes

    val topPx = sectionTopsPx.getOrElse(course.startSection) { 0 }
    // 卡片底边 = 自身顶部 + 行数 × 行高。
    //
    // 关键：**不能**直接用 sectionTopsPx[startSection + sectionCount]，
    // 因为那取到的是「下一节的顶部」，而当跨过第 4 节时会额外包含午休空档，
    // 会让「3~4 节」这类卡片被拉长（历史 bug）。
    // 这里按纯行高现算，保证「跨几节就是几行高」。
    val rowPx = with(density) { GridSpec.sectionHeight.toPx() }.toInt()
    val rawBottomPx = topPx + rowPx * course.sectionCount
    // 若卡片横跨午休（如第 4~5 节），不能让它跨过 12:00–14:00 的空档，
    // 否则视觉上会以为中间那两小时也在上课。这里把底边夹到第 4 节末尾，
    // 卡片保持「一节一格」的高度，午休空档永远是干净的背景。
    val bottomPx = if (
        course.startSection <= GridSpec.LUNCH_AFTER_SECTION &&
        course.startSection + course.sectionCount - 1 > GridSpec.LUNCH_AFTER_SECTION
    ) {
        // 第 4 节末尾 = 第 4 节顶部 + 一行（不含空档）
        sectionTopsPx.getOrElse(GridSpec.LUNCH_AFTER_SECTION) { topPx } + rowPx
    } else {
        rawBottomPx
    }

    val solid = cardStyle == CardStyle.SOLID
    val base = courseColor(course.colorIndex)
    // 非本周课程淡显：整体降低不透明度，文字也跟着变淡
    val dimAlpha = if (dimmed) 0.4f else 1f

    // ---- 单节课方块：颜色做渐变处理 ----
    //
    // 只有 1 节高度的方块在网格里最"孤"，平涂一块纯色会显得像色块贴纸。
    // 用同一色相做**上深下浅**的竖向渐变（亮部在下方，和顶部柔光形成对照），
    // 方块就有了体积感，也和其他多节卡片的液态语言统一。
    //
    // 实色/淡色两种样式都做，只是渐变幅度不同：
    // 实色样式本身不透明，可以用较大的明暗差；淡色样式底子很淡，
    // 只能靠 alpha 的轻微起伏来暗示体积，幅度大了会显脏。
    val singleSection = course.sectionCount == 1

    val containerColor = when {
        !solid -> base.copy(alpha = 0.30f * dimAlpha)
        else -> base.copy(alpha = dimAlpha)
    }
    val containerBrush: Brush? = if (singleSection) {
        if (solid) {
            // 顶部略深、底部略亮，线性插值出体积
            val top = lerpColor(base, Color.Black, 0.16f).copy(alpha = dimAlpha)
            val mid = base.copy(alpha = dimAlpha)
            val bottom = lerpColor(base, Color.White, 0.14f).copy(alpha = dimAlpha)
            Brush.verticalGradient(
                colorStops = arrayOf(0.00f to top, 0.55f to mid, 1.00f to bottom),
            )
        } else {
            // 淡色样式：底色极淡，改成「顶部稍透、底部稍实」的 alpha 起伏
            val aTop = 0.22f * dimAlpha
            val aMid = 0.30f * dimAlpha
            val aBottom = 0.40f * dimAlpha
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to base.copy(alpha = aTop),
                    0.50f to base.copy(alpha = aMid),
                    1.00f to base.copy(alpha = aBottom),
                ),
            )
        }
    } else {
        null
    }
    val titleColor = if (solid) {
        Color.White.copy(alpha = if (dimmed) 0.8f else 1f)
    } else {
        base.copy(alpha = if (dimmed) 0.55f else 1f)
    }
    val subColor = if (solid) {
        Color.White.copy(alpha = if (dimmed) 0.65f else 0.9f)
    } else {
        base.copy(alpha = if (dimmed) 0.5f else 0.85f)
    }

    // 玻璃高光：用静态画笔（非 Composable），避免在网格里引入额外重组。
    // 只在「非淡显」时叠加，淡显卡片保持轻盈。
    val isDark = MaterialTheme.colorScheme.background.isLightColor().not()
    val highlightAlpha = if (dimmed) 0.4f else 1f

    Box(
        modifier = Modifier
            // 按压 Q 弹必须放在链子最前面：
            // 它内部的 graphicsLayer 缩放要罩住**整块卡片**（含 offset/size/clip），
            // 放在 clip 之内会被固定尺寸裁掉，视觉上「按了没反应」。
            .pressBounce(scale = 0.93f, onClick = onClick)
            .offset(
                x = colWidth * colIndex + cardWidth * lane + GridSpec.weekItemMarLeft + gap,
                y = with(density) { topPx.toDp() } + GridSpec.weekItemMarTop,
            )
            .width(cardWidth - gap * 2)
            .height(
                with(density) {
                    (bottomPx - topPx).coerceAtLeast(1).toDp()
                } - GridSpec.weekItemMarTop,
            )
            .clip(shape)
            .then(
                if (containerBrush != null) {
                    Modifier.background(containerBrush)
                } else {
                    Modifier.background(containerColor)
                },
            )
            // 左侧竖色条：淡色样式下靠它区分课程，实色样式下起强调作用
            .drawBehind {
                if (!solid) {
                    val barWidth = 3.dp.toPx()
                    drawRect(
                        color = base.copy(alpha = if (dimmed) 0.4f else 1f),
                        size = androidx.compose.ui.geometry.Size(barWidth, size.height),
                    )
                }
            }
            // ---- 液态玻璃质感（与底栏液珠同一套语言）----
            //
            // 课程方块最要紧的是**颜色可辨识**（10 色靠颜色记课），
            // 所以不能像底栏液珠那样把体色压到极淡 —— 这里保留课程色的浓度，
            // 只把「光」的部分借过来，四层叠加：
            //
            //   ① 顶部一层柔光：受光面，别太重（0.14/0.30），否则整块发白
            //   ② 边缘亮环：**亮度不均匀**（左上亮、右下灭）——
            //      这一圈是「液体」最强的识别信号，塑料边的亮度是均匀的
            //   ③ 底部内壁反光窄弧：液体的「厚度感」来源
            //   ④ 一个小镜面高光点：小才是关键，大了就变成一块白颜料
            //
            // 注意：这里**不缩放**、不做 Haze —— 一个屏幕可能有 30+ 张卡片，
            // 任何逐卡片的离屏渲染都会把帧率拖垮。全部是轻量的矢量绘制。
            .drawWithContent {
                drawContent()

                val w = size.width
                val h = size.height
                val corner = 9.dp.toPx()
                val a = highlightAlpha   // 淡显时整体减弱

                // ① 顶部柔光：受光面。除了整体提亮，
                //    再把亮心偏移到左上角——光有方向才不像「白纱」。
                //    用对角渐变实现「左上亮、右下渐灭」。
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.00f to Color.White.copy(alpha = (if (isDark) 0.15f else 0.28f) * a),
                            0.45f to Color.White.copy(alpha = 0.035f * a),
                            1.00f to Color.Transparent,
                        ),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(w * 0.85f, h),
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner),
                )

                // ② 边缘亮环：光在左上、右下几乎灭。
                //    冷色卡上白环容易发灰，所以强度整体压下来，
                //    只保留左上那一段作为「液体边界」的识别信号。
                val ringW = 1.0.dp.toPx()
                val ringInset = ringW / 2f
                drawRoundRect(
                    brush = Brush.sweepGradient(
                        colorStops = arrayOf(
                            0.00f to Color.White.copy(alpha = 0.00f),
                            0.10f to Color.White.copy(alpha = 0.22f * a),
                            0.28f to Color.White.copy(alpha = 0.42f * a),  // 左上最亮
                            0.46f to Color.White.copy(alpha = 0.08f * a),
                            0.62f to Color.White.copy(alpha = 0.00f),      // 右下全灭
                            0.84f to Color.White.copy(alpha = 0.05f * a),
                            1.00f to Color.White.copy(alpha = 0.00f),
                        ),
                        center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.5f),
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(ringInset, ringInset),
                    size = androidx.compose.ui.geometry.Size(w - ringW, h - ringW),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner - ringInset),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = ringW),
                )

                // ③ 底部内壁反光：一条**很扁、很贴底**的弧。
                //    关键是「扁」——椭圆高度只有卡片高的 22%，
                //    这样它读起来是底壁的一条亮线，而不是一个圆圈。
                if (h > 52.dp.toPx()) {
                    val arcW = w * 0.62f
                    val arcLeft = (w - arcW) / 2f
                    val arcH = h * 0.22f
                    drawArc(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0f),
                                Color.White.copy(alpha = 0.22f * a),
                                Color.White.copy(alpha = 0f),
                            ),
                            startX = arcLeft,
                            endX = arcLeft + arcW,
                        ),
                        startAngle = 28f,
                        sweepAngle = 124f,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            arcLeft,
                            h - arcH * 0.78f,
                        ),
                        size = androidx.compose.ui.geometry.Size(arcW, arcH),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 1.0.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        ),
                    )
                }

                // ④ 镜面高光：一粒**四面渐隐**的柔光，贴左上内缘。
                //    不能用实心椭圆——实心会读成「凹槽/按钮」；
                //    用径向渐变把四周化开，才像液面反光。
                //    尺寸收得比较小（半径 22% 宽），贴左缘、偏上，
                //    这样它读起来是「液面反射的一粒光」而不是「卡顶光斑」。
                val hlCx = w * 0.20f
                val hlCy = h * 0.11f
                val hlR = (w * 0.22f).coerceAtMost(h * 0.48f)
                drawOval(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to Color.White.copy(alpha = (if (isDark) 0.30f else 0.48f) * a),
                            0.40f to Color.White.copy(alpha = (if (isDark) 0.08f else 0.14f) * a),
                            1.00f to Color.Transparent,
                        ),
                        center = androidx.compose.ui.geometry.Offset(hlCx, hlCy),
                        radius = hlR,
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(hlCx - hlR, hlCy - hlR * 0.44f),
                    size = androidx.compose.ui.geometry.Size(hlR * 2f, hlR * 0.88f),
                )
            }
            .padding(
                start = if (solid) 5.dp else 7.dp,
                end = 5.dp,
                top = 4.dp,
                bottom = 4.dp,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = course.name,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                maxLines = if (course.sectionCount >= 2) 4 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (showRoom && course.room.isNotBlank()) {
                Text(
                    text = "@${course.room}",
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    color = subColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showTeacher && course.teacher.isNotBlank() && course.sectionCount >= 2) {
                Text(
                    text = course.teacher,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    color = subColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // 调课角标：右上角一个小「调」字，提示这一节的安排与常规不同
        if (adjusted && !dimmed) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = if (solid) 0.28f else 0.7f))
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            ) {
                Text(
                    text = "调",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (solid) Color.White else base,
                )
            }
        }
    }
}

/**
 * 在两个颜色之间做线性插值（含 alpha）。
 *
 * 用来给单节课方块调出「同色相的深浅两端」——
 * 直接和纯黑/纯白混合会掉饱和度，所以只混很小的比例（≤0.16）。
 */
private fun lerpColor(from: Color, to: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * f,
        green = from.green + (to.green - from.green) * f,
        blue = from.blue + (to.blue - from.blue) * f,
        alpha = from.alpha + (to.alpha - from.alpha) * f,
    )
}

/** dayOfWeek（1 = 周一 … 7 = 周日）对应的单字短名 */
fun dayLabelByDow(dow: Int): String =
    listOf("一", "二", "三", "四", "五", "六", "日").getOrElse(dow - 1) { "" }

/** 完整中文名 */
fun dayLabelFull(day: Int): String =
    listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        .getOrElse(day - 1) { "" }
