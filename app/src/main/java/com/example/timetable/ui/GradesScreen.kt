package com.example.timetable.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.timetable.data.EamsImporter
import com.example.timetable.data.GradeCache
import com.example.timetable.data.GradeItem
import com.example.timetable.data.SemesterGrades
import com.example.timetable.ui.theme.isLightColor
import com.example.timetable.ui.theme.liquidGlass
import com.example.timetable.ui.theme.pressBounce
import com.example.timetable.ui.theme.secondaryGlassBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * 「成绩」页。
 *
 * 数据来源：西安建筑科技大学教务系统成绩单页面。
 *
 * **缓存策略**：进入页面时优先读本地缓存（[cache]），不主动联网；
 * 只有用户点标题栏的刷新按钮、且缓存为空或用户明确要更新时，才用隐藏
 * WebView 去教务系统重新抓取，成功后通过 [onSaveCache] 落盘。
 *
 * 布局：
 * 1. 标题栏：学生成绩 + 学期下拉（可切换查看某个学期）+ 刷新；
 * 2. 各学期总评：入学至今累计（总学分 / 加权绩点 / 门数）+ 逐学期汇总；
 * 3. 本学期成绩：当前所选学期的逐门明细。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GradesScreen(
    cache: GradeCache?,
    onSaveCache: (String) -> Unit,
    modifier: Modifier = Modifier,
    onGoLogin: () -> Unit = {},
    hazeState: dev.chrisbanes.haze.HazeState? = null,
) {
    var semesters by remember { mutableStateOf<List<SemesterGrades>?>(null) }
    // 是否正在「主动刷新」
    var refreshing by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var needLogin by remember { mutableStateOf(false) }
    var showWebFallback by remember { mutableStateOf(false) }

    // 当前查看的学期；null 表示「全部学期（总评）」
    var selectedSemesterId by remember { mutableStateOf<String?>(null) }

    // 每次主动刷新 +1，用于重建隐藏 WebView 并重新注入脚本
    var attempt by remember { mutableStateOf(0) }
    val resultFlow = remember { MutableStateFlow<String?>(null) }

    // 缓存里的「上次更新时间」
    val cacheTime = cache?.timestamp ?: 0L

    /** 把一份原始载荷解析并填到界面上 */
    fun applyPayload(payload: String) {
        val result = EamsImporter.parseGrades(EamsImporter.RESULT_PREFIX + payload)
        if (result.success) {
            semesters = result.semesters
            if (semesters?.none { it.semesterId == selectedSemesterId } != false) {
                selectedSemesterId = result.semesters.firstOrNull()?.semesterId
            }
            errorText = null
            needLogin = false
        } else {
            needLogin = result.needLogin
            errorText = result.error ?: "读取失败"
        }
    }

    // 首次进入：优先用缓存；缓存为空则自动抓一次
    LaunchedEffect(cacheTime) {
        if (semesters != null) return@LaunchedEffect
        val cached = cache
        if (cached != null) {
            applyPayload(cached.payload)
        } else {
            refreshing = true
            attempt++
        }
    }

    val bridge = remember {
        object {
            @JavascriptInterface
            fun onResult(value: String) {
                resultFlow.value = value
            }
        }
    }

    // 主动刷新时等待脚本回传
    LaunchedEffect(attempt) {
        if (attempt == 0 || showWebFallback) return@LaunchedEffect
        refreshing = true
        errorText = null
        needLogin = false
        val raw = withTimeoutOrNull(30_000) { resultFlow.filterNotNull().first() }
        resultFlow.value = null
        refreshing = false
        if (raw == null) {
            // 超时时：若已有内容就不打扰用户，否则提示
            if (semesters == null) errorText = "读取超时，请稍后重试。"
            return@LaunchedEffect
        }
        val result = EamsImporter.parseGrades(raw)
        if (result.success) {
            semesters = result.semesters
            if (semesters?.none { it.semesterId == selectedSemesterId } != false) {
                selectedSemesterId = result.semesters.firstOrNull()?.semesterId
            }
            // 抓取成功 → 落盘缓存
            onSaveCache(raw.substringAfter(EamsImporter.RESULT_PREFIX, "").trim())
        } else {
            needLogin = result.needLogin
            if (semesters == null) errorText = result.error ?: "读取失败"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize(),
    ) {
        // ---- 标题栏：学生成绩 + 学期选择 ----
        //
        // 也做成液态玻璃：与主页顶栏 / 底栏 / 校园生活页顶栏保持同一种材质。
        // 成绩列表向上滚动时，卡片会从这条标题栏下面模糊穿过 ——
        // 这是「内容从玻璃下面穿过」的空间感，纯实心标题栏给不了。
        GradesTopBar(
            semesters = semesters.orEmpty(),
            selectedSemesterId = selectedSemesterId,
            onSelectSemester = { selectedSemesterId = it },
            onRefresh = {
                showWebFallback = false
                attempt++
            },
            refreshing = refreshing,
            enabled = semesters != null,
            updatedAt = cacheTime,
            hazeState = hazeState,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            val list = semesters
            when {
                list != null -> GradeContent(
                    semesters = list,
                    selectedSemesterId = selectedSemesterId,
                )

                showWebFallback -> WebFallback(
                    onRetry = {
                        showWebFallback = false
                        errorText = null
                        attempt++
                    },
                    onGoLogin = onGoLogin,
                )

                else -> {
                    if (refreshing) {
                        GradeFetcherWebView(
                            key = attempt,
                            bridge = bridge,
                            modifier = Modifier.size(1.dp),
                        )
                        LoadingHint("正在从教务系统读取成绩…")
                    } else {
                        ErrorHint(
                            message = errorText ?: "暂无成绩数据",
                            needLogin = needLogin,
                            onRetry = { attempt++ },
                            onGoLogin = onGoLogin,
                            onOpenWeb = { showWebFallback = true },
                        )
                    }
                }
            }
        }
    }
}

/** 标题栏：学生成绩 + 学期下拉。做成液态玻璃，与全站顶栏同一材质。 */
@Composable
private fun GradesTopBar(
    semesters: List<SemesterGrades>,
    selectedSemesterId: String?,
    onSelectSemester: (String?) -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean,
    enabled: Boolean,
    updatedAt: Long,
    hazeState: dev.chrisbanes.haze.HazeState?,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentName = semesters
        .firstOrNull { it.semesterId == selectedSemesterId }
        ?.semesterName
        ?: "全部学期"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 玻璃挂在最外层：padding 放在玻璃**里面**，
            // 这样玻璃区域会一直铺到屏幕左右边缘和状态栏下方，
            // 不会出现「玻璃只包住文字、两边露出背景」的割裂感。
            .then(Modifier.secondaryGlassBar(hazeState))
            // 状态栏避让放在玻璃**之后** → 玻璃背景延伸到状态栏区域
            // （状态栏里的时间/电量就浮在毛玻璃上），内容被推下来。
            // 若放在玻璃之前，玻璃会被挤到状态栏下方，顶部露一条背景缝隙。
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "学生成绩",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.weight(1f))

            // 学期选择器
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(enabled = enabled) { expanded = true }
                        .padding(start = 14.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (enabled) currentName else "…",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "选择学期",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("全部学期（总评）") },
                        onClick = {
                            onSelectSemester(null)
                            expanded = false
                        },
                    )
                    semesters.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.semesterName) },
                            onClick = {
                                onSelectSemester(s.semesterId)
                                expanded = false
                            },
                        )
                    }
                }
            }

            // 刷新（抓取中显示转圈）
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(enabled = enabled && !refreshing, onClick = onRefresh),
                contentAlignment = Alignment.Center,
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "刷新",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 上次更新时间
        if (updatedAt > 0L) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "更新于 " + formatTime(updatedAt),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 把时间戳格式化为「M月d日 HH:mm」 */
private fun formatTime(millis: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    val mm = cal.get(java.util.Calendar.MONTH) + 1
    val dd = cal.get(java.util.Calendar.DAY_OF_MONTH)
    val hh = cal.get(java.util.Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
    val mi = cal.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')
    return "${mm}月${dd}日 $hh:$mi"
}

/**
 * 内容区。
 *
 * - 选中「全部学期」：显示总评（累计 + 逐学期）；
 * - 选中具体学期：显示该学期的课程明细 + 该学期汇总。
 */
@Composable
private fun GradeContent(
    semesters: List<SemesterGrades>,
    selectedSemesterId: String?,
) {
    val selected = semesters.firstOrNull { it.semesterId == selectedSemesterId }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (selected == null) {
            // ---- 全部学期：总评 ----
            item { SectionTitle("各学期总评") }
            item {
                OverallSummaryCard(semesters)
            }
            items(semesters, key = { "s_" + it.semesterId }) { s ->
                SemesterSummaryRow(s)
            }
        } else {
            // ---- 单学期：明细 ----
            item { SectionTitle(selected.semesterName + " 成绩") }
            item { SemesterStatCard(selected) }
            items(selected.grades, key = { it.courseName + it.scoreText }) { g ->
                GradeRow(g)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
    )
}

/** 入学至今累计总评卡 —— 也做成玻璃牌，与顶栏的材质呼应 */
@Composable
private fun OverallSummaryCard(semesters: List<SemesterGrades>) {
    val allGrades = semesters.flatMap { it.grades }
    val totalCredit = allGrades.mapNotNull { it.credit }.sum()
    val gpaSamples = allGrades.filter { it.credit != null && it.gradePoint != null }
    val gpa = if (gpaSamples.isNotEmpty()) {
        val sum = gpaSamples.sumOf { it.credit!! * it.gradePoint!! }
        val cr = gpaSamples.sumOf { it.credit!! }
        if (cr > 0) sum / cr else null
    } else null

    val isDarkCard = MaterialTheme.colorScheme.background.isLightColor().not()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Q 弹：按下时整张卡缩到 0.96，松手弹回（低阻尼会过冲一下）。
            // 必须放在 liquidGlass **之前** —— pressBounce 里的 graphicsLayer
            // 是外层，才能把内层画好的玻璃整体缩放；反过来只会缩放内容。
            .pressBounce(scale = 0.96f)
            // 拟玻璃三件套（半透明底 + 顶部高光渐变 + 细描边）。
            // 这里**不用 Haze 真模糊**：这张卡在滚动列表里，
            // 背后没有可透的内容（就一张背景色），真模糊纯属浪费 GPU；
            // 而「玻璃感」其实 90% 来自高光渐变和描边，不是来自模糊。
            .liquidGlass(
                tint = MaterialTheme.colorScheme.primary,
                isDark = isDarkCard,
                // 主题色染色稍重一点，保住原来 primaryContainer 的「重音」作用
                alpha = if (isDarkCard) 0.22f else 0.16f,
                shape = RoundedCornerShape(20.dp),
                borderWidth = 1.dp,
                highlightIntensity = if (isDarkCard) 0.14f else 0.68f,
            ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = "入学至今",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "共 ${semesters.size} 个学期 · ${allGrades.size} 门课程",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row {
                StatCell("总学分", formatNum(totalCredit), Modifier.weight(1f), isDarkCard)
                StatCell(
                    "加权绩点",
                    gpa?.let { String.format("%.2f", it) } ?: "—",
                    Modifier.weight(1f),
                    isDarkCard,
                )
                StatCell("课程数", allGrades.size.toString(), Modifier.weight(1f), isDarkCard)
            }
        }
    }
}

/** 单个学期的汇总行（用于总评列表）—— 玻璃 */
@Composable
private fun SemesterSummaryRow(s: SemesterGrades) {
    val isDarkRow = MaterialTheme.colorScheme.background.isLightColor().not()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pressBounce(scale = 0.96f)
            .liquidGlass(
                tint = MaterialTheme.colorScheme.surface,
                isDark = isDarkRow,
                // 列表项数量多，染色比总评卡更淡，避免整屏糊成一片紫
                alpha = if (isDarkRow) 0.10f else 0.28f,
                shape = RoundedCornerShape(16.dp),
                borderWidth = 0.9.dp,
                highlightIntensity = if (isDarkRow) 0.12f else 0.58f,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = s.semesterName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${s.grades.size} 门课程 · ${formatNum(s.totalCredit)} 学分",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = s.weightedGpa?.let { String.format("%.2f", it) } ?: "—",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "加权绩点",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 单学期统计卡（用于单学期页顶部）—— 与总评卡同一种玻璃 */
@Composable
private fun SemesterStatCard(s: SemesterGrades) {
    val isDarkCard = MaterialTheme.colorScheme.background.isLightColor().not()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pressBounce(scale = 0.96f)
            .liquidGlass(
                tint = MaterialTheme.colorScheme.primary,
                isDark = isDarkCard,
                alpha = if (isDarkCard) 0.22f else 0.16f,
                shape = RoundedCornerShape(20.dp),
                borderWidth = 1.dp,
                highlightIntensity = if (isDarkCard) 0.14f else 0.68f,
            ),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatCell("总学分", formatNum(s.totalCredit), Modifier.weight(1f), isDarkCard)
            StatCell(
                "加权绩点",
                s.weightedGpa?.let { String.format("%.2f", it) } ?: "—",
                Modifier.weight(1f),
                isDarkCard,
            )
            StatCell("课程数", s.grades.size.toString(), Modifier.weight(1f), isDarkCard)
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isDark: Boolean = MaterialTheme.colorScheme.background.isLightColor().not(),
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            // 玻璃底上用 onSurface：比 onPrimaryContainer 更清透，
            // 也不会因为底色变半透明而糊成一片
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GradeRow(g: GradeItem) {
    val isDarkRow = MaterialTheme.colorScheme.background.isLightColor().not()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 课程行也带 Q 弹。缩得比总评卡轻一点（0.97），
            // 因为列表里行挨着行，缩太多会让上下两行之间出现明显缝隙闪动。
            .pressBounce(scale = 0.97f)
            // 每一条课程行也是玻璃。列表里同时可能有十几行，
            // 这里**绝不能**用 Haze 真模糊（每行一次离屏渲染会直接掉帧）——
            // 用 liquidGlass 的「半透明底 + 顶部高光 + 细描边」三件套，
            // 零额外 GPU 开销，但视觉上和顶栏是同一族材质。
            .liquidGlass(
                tint = MaterialTheme.colorScheme.surface,
                isDark = isDarkRow,
                alpha = if (isDarkRow) 0.10f else 0.28f,
                shape = RoundedCornerShape(16.dp),
                borderWidth = 0.9.dp,
                highlightIntensity = if (isDarkRow) 0.12f else 0.58f,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = g.courseName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = listOfNotNull(
                        g.courseCode?.takeIf { it.isNotBlank() },
                        g.courseProperty,
                        g.courseType,
                        buildMeta(g).takeIf { it != "—" },
                    ).joinToString("  ·  ").ifBlank { "—" },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            ScoreBadge(g)
        }
    }
}

@Composable
private fun ScoreBadge(g: GradeItem) {
    val isDark = MaterialTheme.colorScheme.background.isLightColor().not()
    val (bg, fg) = scoreColors(g, isDark)
    Box(
        modifier = Modifier
            .size(52.dp)
            // 分数徽标也做成玻璃方块：
            // 绩点色作为**染色**保留（红→紫的语义不能丢），
            // 上面叠「顶部高光 + 细描边」变成一块「有颜色的玻璃」。
            //
            // 注意这里 alpha 给得比列表行高（0.62/0.52）——
            // 徽标是整行唯一的「重音」，太透会让分数看不清、
            // 绩点色也不再构成一眼可辨的色带。
            .liquidGlass(
                tint = bg,
                isDark = isDark,
                alpha = if (isDark) 0.52f else 0.62f,
                shape = RoundedCornerShape(14.dp),
                borderWidth = 0.9.dp,
                highlightIntensity = if (isDark) 0.24f else 0.66f,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = g.scoreText.ifBlank { "—" },
            fontSize = if (g.scoreText.length > 3) 12.sp else 17.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
    }
}

private fun buildMeta(g: GradeItem): String {
    val parts = mutableListOf<String>()
    g.credit?.let { parts += "${formatNum(it)} 学分" }
    g.gradePoint?.let { parts += "绩点 ${String.format("%.1f", it)}" }
    return if (parts.isEmpty()) "—" else parts.joinToString("  ·  ")
}

/**
 * 成绩徽标配色 —— 按**绩点**连续渐变（红 → 紫）。
 *
 * 规则（用户指定）：
 * - 绩点越靠近 **5.0**，颜色越偏**紫**（好）；
 * - 越靠近 **1.0**，颜色越偏**红**（差）；
 * - **低于 1.0** 直接转灰，退出这套渐变 —— 那是「挂科 / 无绩点」的范畴，
 *   和「及格但绩点低」应该一眼区分开。
 *
 * 实现：把绩点线性归一化成 [0,1] 的 t，再在 HSV 色相环上从 8°（红）
 * 插值到 275°（紫）。选 HSV 而不是 RGB 直插，是因为红和紫的 RGB 中点会发浑，
 * HSV 只动色相，饱满度稳定，整条色带才是均匀过渡的。
 *
 * 深色模式下整体降饱和、提明度，避免在深底上糊成一片。
 */
@Composable
private fun scoreColors(g: GradeItem, isDark: Boolean): Pair<Color, Color> {
    val gp = g.gradePoint

    // 无绩点 / 挂科 / 低于 1.0 → 灰色系
    if (gp == null || g.passed == false || gp < GPA_MIN) {
        val bg = if (isDark) Color(0xFF4A4A4E) else Color(0xFFE3E3E6)
        val fg = if (isDark) Color(0xFFB9B9BE) else Color(0xFF6E6E73)
        return bg to fg
    }

    val t = ((gp - GPA_MIN) / (GPA_MAX - GPA_MIN)).coerceIn(0.0, 1.0).toFloat()
    // 色相：8°（红）→ 275°（紫）。t 越大（绩点越高）越紫。
    val hue = HUE_RED + (HUE_PURPLE - HUE_RED) * t
    // 深色底上把饱和度压低、明度抬高，否则红紫会沉进背景
    val saturation = if (isDark) 0.58f else 0.72f
    val value = if (isDark) 0.95f else 0.82f
    val bg = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
    return bg to Color.White
}

/** 绩点映射区间：≤1.0 视为「极差」，5.0 为满分 */
private const val GPA_MIN = 1.0
private const val GPA_MAX = 5.0

/** 色相环角度：低绩点端的红 */
private const val HUE_RED = 8f

/** 色相环角度：高绩点端的紫 */
private const val HUE_PURPLE = 275f

private fun formatNum(v: Double): String =
    if (v == v.roundToInt().toDouble()) v.roundToInt().toString()
    else String.format("%.1f", v)

// ----------------------------------------------------------------------
// 加载 / 失败 / 兜底
// ----------------------------------------------------------------------

@Composable
private fun LoadingHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(14.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorHint(
    message: String,
    needLogin: Boolean,
    onRetry: () -> Unit,
    onGoLogin: () -> Unit,
    onOpenWeb: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (needLogin) "需要先登录" else "暂时没读到成绩",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (needLogin) {
                    "成绩来自教务系统，需要先登录后才能读取。\n点下方「去登录」，登录完成再回到成绩页即可。"
                } else {
                    message
                },
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (needLogin) {
                    PillButton("去登录", filled = true, onClick = onGoLogin)
                    PillButton("打开网页", filled = false, onClick = onOpenWeb)
                } else {
                    PillButton("重试", filled = true, onClick = onRetry)
                    PillButton("打开网页", filled = false, onClick = onOpenWeb)
                }
            }
        }
    }
}

@Composable
private fun PillButton(text: String, filled: Boolean, onClick: () -> Unit) {
    val bg = if (filled) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (filled) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = fg)
    }
}

/** 兜底：直接内嵌教务系统成绩页 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebFallback(onRetry: () -> Unit, onGoLogin: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    val realUa = settings.userAgentString
                        ?.replace("; wv", "")
                        ?.replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
                    if (!realUa.isNullOrBlank()) settings.userAgentString = realUa
                    runCatching {
                        settings.javaClass
                            .getMethod("setForceDarkAllowed", Boolean::class.javaPrimitiveType)
                            .invoke(settings, false)
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = WebViewClient()
                    loadUrl(EamsImporter.GRADE_URL)
                }
            },
            onRelease = { it.destroy() },
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onGoLogin)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text(
                    "去登录",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "重试抓取",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

/**
 * 隐藏的抓取 WebView。
 *
 * 用 `key` 强制在重试时重建（Compose 会销毁旧的、创建新的），
 * 页面加载完成后注入抓取脚本。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GradeFetcherWebView(
    key: Int,
    bridge: Any,
    modifier: Modifier = Modifier,
) {
    androidx.compose.runtime.key(key) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    val realUa = settings.userAgentString
                        ?.replace("; wv", "")
                        ?.replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
                    if (!realUa.isNullOrBlank()) settings.userAgentString = realUa
                    runCatching {
                        settings.javaClass
                            .getMethod("setForceDarkAllowed", Boolean::class.javaPrimitiveType)
                            .invoke(settings, false)
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    addJavascriptInterface(bridge, "eamsBridge")

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.evaluateJavascript(EamsImporter.GRADE_FETCH_SCRIPT, null)
                        }
                    }
                    loadUrl(EamsImporter.GRADE_URL)
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
