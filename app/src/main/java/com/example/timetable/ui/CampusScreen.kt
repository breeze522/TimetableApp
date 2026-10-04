package com.example.timetable.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timetable.R
import com.example.timetable.data.Announcement
import com.example.timetable.ui.theme.isLightColor
import com.example.timetable.ui.theme.liquidGlass
import com.example.timetable.ui.theme.pressBounce
import com.example.timetable.ui.theme.secondaryGlassBar

/**
 * 校园生活页。
 *
 * 目前承载「教务处通知公告」分区 —— 数据来自西安建筑科技大学教务处
 * 的通知列表页（见 [com.example.timetable.data.AnnouncementRepository]）。
 *
 * 视觉上沿用全局液态玻璃语言：
 * - 页面本身透明，透出全局渐变 / 自定义背景；
 * - 顶部一条毛玻璃标题栏（[secondaryGlassBar]），与主页 / 二级页完全一致；
 * - 公告卡片用半透明白玻璃 + 高光描边，和课表卡片同一套观感。
 *
 * @param announcements 已抓到的公告；空且 [loading] 为 true 时显示加载态
 * @param loading 是否正在加载
 * @param error 错误信息；非空时展示错误态与重试按钮
 * @param onRefresh 点击「刷新」时触发
 * @param onOpenLink 打开内嵌网页。
 *   参数一 = 绝对地址；参数二 = 是否保持登录会话
 *   （校园服务 true，通知公告 false —— 公告页是静态的，不需要留 Cookie）。
 */
@Composable
fun CampusScreen(
    announcements: List<Announcement>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onOpenLink: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: dev.chrisbanes.haze.HazeState? = null,
) {
    // 点「查看全部」后置 true，展示完整列表二级页
    var showAllNotices by remember { mutableStateOf(false) }

    if (showAllNotices) {
        NoticeListScreen(
            announcements = announcements,
            loading = loading,
            error = error,
            onRefresh = onRefresh,
            onOpenLink = onOpenLink,
            onBack = { showAllNotices = false },
            hazeState = hazeState,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // ---- 顶栏（毛玻璃，与其它页面统一）----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(Modifier.secondaryGlassBar(hazeState))
                .padding(start = 18.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "校园生活",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            IconButton(onClick = onRefresh, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新",
                    )
                }
            }
        }

        // ---- 内容 ----
        when {
            // 首次加载：整屏 loading
            loading && announcements.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            // 加载失败且没有任何数据：错误态 + 重试
            announcements.isEmpty() && error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 40.dp),
                    ) {
                        Text(
                            text = "通知加载失败",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = error,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "点击重试",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onRefresh)
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // 正常列表
            else -> {
                // 首页只露出前 COLLAPSED_COUNT 条，其余折叠；
                // 想看全量就点「查看全部 N 条」进二级列表页，避免首屏被长列表淹没。
                val collapsed = announcements.size > COLLAPSED_COUNT
                val visible = if (collapsed) announcements.take(COLLAPSED_COUNT) else announcements

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 4.dp,
                        // 底部留白：底栏是浮岛，别让最后一条被压住
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // ---- 校园服务：方形入口宫格 ----
                    item {
                        ServiceGrid(
                            services = listOf(
                                CampusService(
                                    title = "图书馆预约",
                                    drawableRes = R.drawable.ic_campus_library,
                                    url = LIBRARY_BOOKING_URL,
                                ),
                            ),
                            onOpen = { link, persist -> onOpenLink(link, persist) },
                        )
                    }

                    item {
                        SectionHeader(
                            title = "教务处通知",
                            count = announcements.size,
                        )
                    }
                    items(visible, key = { it.url }) { item ->
                        AnnouncementCard(item = item, onClick = { onOpenLink(item.url, false) })
                    }
                    if (collapsed) {
                        item {
                            MoreEntry(
                                total = announcements.size,
                                onClick = { showAllNotices = true },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 首页折叠时露出的公告条数。
 *
 * 校园生活页后面还要继续加别的分区（空教室、校历、失物招领……），
 * 每个分区只留 3 条做「预览」即可，剩下的交给各自的「查看全部」二级页，
 * 这样首屏是一份「卡片目录」，而不是某个分区霸屏。
 */
private const val COLLAPSED_COUNT = 3

/** 图书馆空间预约（西建大 libspace） */
const val LIBRARY_BOOKING_URL = "https://libspace.xauat.edu.cn/h5/index.html#/home"

/**
 * 校园服务入口项。
 *
 * @param title 服务名（只留四五个字，宫格才干净）
 * @param drawableRes 图标（方形，自带圆角底）
 * @param url 点击后用内置 WebView 打开的地址
 * @param persistSession 是否需要保持登录态。
 *   校园服务大多是**需要登录的 SPA**，必须把 Cookie 持久化，
 *   否则每次进来都要重新登录一遍。
 */
private data class CampusService(
    val title: String,
    val drawableRes: Int,
    val url: String,
    val persistSession: Boolean = true,
)

/**
 * 校园服务宫格。
 *
 * 两列等宽排布，每格是一个**竖向的圆角方块**（图标在上、文字在下）。
 *
 * 为什么用 `weight(1f)` 两列而不是流式布局：
 * 服务项数量少，需要的是「宫格」的规整感 —— 等宽两列能让每一格
 * 无论文字长短都保持同样宽度，横平竖直对齐。
 * 单项时右侧留空位（[Spacer] 占 weight），保证单格宽度和满行一致，
 * 不会被拉成整行宽而失衡。
 */
@Composable
private fun ServiceGrid(
    services: List<CampusService>,
    onOpen: (String, Boolean) -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.isLightColor().not()
    val cardBg = if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.58f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.85f)

    val rows = services.chunked(2)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEach { svc ->
                    ServiceTile(
                        service = svc,
                        cardBg = cardBg,
                        borderColor = borderColor,
                        onClick = { onOpen(svc.url, svc.persistSession) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(2 - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 单个服务方块。
 *
 * 竖向布局：图标在上、一行文字在下，整体**靠左对齐**，高度不强制正方形 ——
 * 硬压成正方会让图标和文字之间出现大片空白（之前那版就是这样，格子虚高）。
 * 现在高度由内容撑开（图标 42 + 间距 + 一行字），是舒服的「胶囊方块」比例。
 */
@Composable
private fun ServiceTile(
    service: CampusService,
    cardBg: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            // 与成绩页同一种材质：按压缩放（Q 弹）+ 液态玻璃三件套。
            // pressBounce 必须在 liquidGlass **之前**，让 graphicsLayer
            // 包住内层画好的玻璃，缩放才是整体缩放。
            .pressBounce(scale = 0.95f, onClick = onClick)
            .liquidGlass(
                tint = Color.White,
                isDark = MaterialTheme.colorScheme.background.isLightColor().not(),
                alpha = cardBg.alpha,
                shape = RoundedCornerShape(16.dp),
                borderWidth = 1.dp,
                highlightIntensity = if (MaterialTheme.colorScheme.background
                        .isLightColor()
                ) 0.62f else 0.12f,
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Image(
            painter = painterResource(id = service.drawableRes),
            contentDescription = service.title,
            modifier = Modifier.size(42.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = service.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 分区标题：左侧一道主题色竖条 + 标题 + 数量 */
@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 15.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$count 条",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 单条公告卡片 */
@Composable
private fun AnnouncementCard(item: Announcement, onClick: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background.isLightColor().not()
    val cardBg = if (isDark) {
        Color.White.copy(alpha = 0.06f)
    } else {
        Color.White.copy(alpha = 0.62f)
    }
    val borderColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.85f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressBounce(scale = 0.97f, onClick = onClick)
            .liquidGlass(
                tint = Color.White,
                isDark = isDark,
                alpha = cardBg.alpha,
                shape = RoundedCornerShape(14.dp),
                borderWidth = 0.9.dp,
                highlightIntensity = if (isDark) 0.12f else 0.58f,
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧小图标，弱化存在感，只做「公告」的语义提示
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Campaign,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.width(11.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (item.date.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = item.date,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * 列表底部的「查看全部」入口。
 *
 * 做成一条和公告卡片同宽的浅色条，右侧带箭头，语义上更像「继续往下看」，
 * 而不是一个按钮 —— 和列表本身的观感更连贯。
 */
@Composable
private fun MoreEntry(total: Int, onClick: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background.isLightColor().not()
    val bg = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.45f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressBounce(scale = 0.97f, onClick = onClick)
            .liquidGlass(
                tint = Color.White,
                isDark = isDark,
                alpha = bg.alpha,
                shape = RoundedCornerShape(14.dp),
                borderWidth = 0.9.dp,
                highlightIntensity = if (isDark) 0.12f else 0.58f,
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "查看全部 $total 条",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * 通知列表二级页（全量）。
 *
 * 从校园生活首页的「查看全部」进入，顶部毛玻璃工具栏带返回箭头，
 * 列表本体与首页完全一致 —— 复用 [AnnouncementCard]，只是不做折叠。
 */
@Composable
private fun NoticeListScreen(
    announcements: List<Announcement>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onOpenLink: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    hazeState: dev.chrisbanes.haze.HazeState? = null,
) {
    androidx.activity.compose.BackHandler(enabled = true, onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // ---- 顶栏 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(Modifier.secondaryGlassBar(hazeState))
                .padding(start = 4.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "全部通知",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "共 ${announcements.size} 条",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRefresh, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新",
                    )
                }
            }
        }

        // ---- 内容 ----
        when {
            announcements.isEmpty() && loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            announcements.isEmpty() && error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = error,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp,
                        bottom = 40.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(announcements, key = { it.url }) { item ->
                        AnnouncementCard(item = item, onClick = { onOpenLink(item.url, false) })
                    }
                }
            }
        }
    }
}

/**
 * 通知详情页（内置 WebView）。
 *
 * 为什么用内置 WebView 而不是跳到系统浏览器？
 * 通知正文是教务处的网页，内含大量表格与图片；用系统浏览器会跳出 App，
 * 用户看完要手动切回来，体验割裂。内置一个带毛玻璃工具栏的 WebView，
 * 返回即可回到列表，路径更短。
 *
 * ## 会话持久化（[persistSession]）
 *
 * 校园服务（图书馆预约等）是**需要登录的 SPA**，用户登录一次之后
 * 不应该每次进来都重新登录。默认的 WebView 虽然会把 Cookie 存在
 * 应用私有目录里，但**必须显式打开 Cookie 接收开关**才会写入 ——
 * 而且写入是异步的，进程随时可能被系统回收，所以关键节点（页面加载完、
 * 页面即将退出）都要 `flush()` 一次，把内存里的 Cookie 落盘。
 *
 * 打开后：
 * - 首次进入 → 正常显示登录页，登录一次；
 * - 再进入 / 杀进程重开 → Cookie 还在，直接是登录态，跳过登录。
 *
 * @param url 详情页地址
 * @param onBack 点返回箭头时触发
 * @param persistSession true = 持久化 Cookie 与 WebStorage（校园服务类页面）
 */
@androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.O)
@Composable
fun NoticeWebScreen(
    url: String,
    onBack: () -> Unit,
    hazeState: dev.chrisbanes.haze.HazeState? = null,
    persistSession: Boolean = false,
) {
    // 返回键也能退出详情页
    androidx.activity.compose.BackHandler(enabled = true, onBack = onBack)

    // 离开页面（返回 / 切 Tab）时把 Cookie 落盘。
    // 用户很可能在页面里登录完就直接返回，此时若只依赖 onPageFinished，
    // 最后一次跳转产生的 Cookie 可能还没 flush。
    androidx.compose.runtime.DisposableEffect(persistSession) {
        onDispose { if (persistSession) flushWebSession() }
    }

    var progress by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("通知详情") }
    var webRef by remember { mutableStateOf<android.webkit.WebView?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ---- 顶部工具栏（毛玻璃，与全站统一）----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(Modifier.secondaryGlassBar(hazeState))
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        // 有历史就回退，没有才退出详情页 —— 站内链接跳转也能后退
                        val wv = webRef
                        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
                    },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                    )
                }
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // 刷新
                IconButton(onClick = { webRef?.reload() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新",
                    )
                }
            }
            // 加载进度条（细线，加载完自动消失）
            if (progress in 1..99) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Butt,
                )
            }
        }

        // ---- WebView ----
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    // ---- 权限 / 存储 ----
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true      // SPA 靠 localStorage/sessionStorage
                    settings.databaseEnabled = true

                    // ---- 会话持久化：校园服务（图书馆预约等）需要保持登录态 ----
                    //
                    // 1. Cookie 默认「不接收」，必须显式打开，否则登录后服务端下发的
                    //    Set-Cookie 根本不会写进 WebView 存储 → 每次都要重新登录；
                    // 2. 写入是异步的，进程退出前不 flush 会丢，所以在页面加载完成、
                    //    以及页面销毁时各 flush 一次；
                    // 3. 第三方 Cookie 也一并允许，部分统一认证会用到。
                    run {
                        val cm = android.webkit.CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        cm.setAcceptThirdPartyCookies(this, true)
                    }

                    // 现代网页几乎都是 https，但教务处偶有 http 图片/iframe
                    settings.mixedContentMode =
                        android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    // 站内跳转（同源）保留在 WebView 里
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.setSupportMultipleWindows(false)

                    // ---- 布局 / 缩放 ----
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    // 页面自带的 viewport meta 优先，别被 WebView 覆盖
                    settings.layoutAlgorithm = android.webkit.WebSettings.LayoutAlgorithm.NORMAL

                    // ---- 视觉：避免深色模式把白底网页反色 ----
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        settings.forceDark = android.webkit.WebSettings.FORCE_DARK_OFF
                    }
                    // API 33+ 才有 setAlgorithmicDarkeningAllowed，这里用反射兜底
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        runCatching {
                            android.webkit.WebSettings::class.java
                                .getMethod("setAlgorithmicDarkeningAllowed", Boolean::class.javaPrimitiveType)
                                .invoke(settings, false)
                        }
                    }

                    // ---- UA：去掉 "; wv" 标记，避免部分站点识别为「内置浏览器」而拒绝渲染 ----
                    val defaultUa = settings.userAgentString ?: ""
                    settings.userAgentString = defaultUa
                        .replace("; wv)", ")")
                        .replace(" Version/4.0", "")

                    // ---- 关键修复：部分国产 ROM 的 WebView 里 CSS vh 单位失效（100vh 算出 0）----
                    // 后果：整站 height:100vh / 100% 的高度链坍缩成内容自身高度，
                    //      外层 overflow:hidden 把主体内容整个裁掉 → 页面大片空白。
                    // 这里在页面加载前注入一段补丁，把 --vh 变量和 html/body 高度按真实视口写死。
                    addJavascriptInterface(VhPatchBridge(this), "AndroidVhPatch")

                    // 允许网页弹窗 / 控制台输出到 logcat，便于排查
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: android.webkit.WebView?, newProgress: Int) {
                            progress = newProgress
                        }

                        override fun onConsoleMessage(
                            msg: android.webkit.ConsoleMessage?,
                        ): Boolean {
                            android.util.Log.d(
                                "WebViewConsole",
                                "${msg?.message()} @${msg?.lineNumber()} ${msg?.sourceId()}",
                            )
                            return true
                        }
                    }

                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageStarted(
                            view: android.webkit.WebView?,
                            url0: String?,
                            favicon: android.graphics.Bitmap?,
                        ) {
                            title = "加载中…"
                        }

                        override fun onPageCommitVisible(view: android.webkit.WebView?, url0: String?) {
                            // DOM 已就绪、页面脚本即将执行，此时打补丁最有效
                            view?.evaluateJavascript(VH_PATCH_JS, null)
                        }

                        override fun onPageFinished(view: android.webkit.WebView?, url0: String?) {
                            // 终局再补一次：SPA 首屏渲染可能晚于 commit
                            view?.evaluateJavascript(VH_PATCH_JS, null)
                            view?.title?.takeIf { it.isNotBlank() }?.let { title = it }
                            // 页面加载完往往正是登录成功、Set-Cookie 刚下发完的时刻，
                            // 立刻落盘，避免进程被杀导致 Cookie 丢失。
                            if (persistSession) flushWebSession()
                        }

                        override fun onReceivedError(
                            view: android.webkit.WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?,
                        ) {
                            // 只报主文档的错误，子资源失败不打扰用户
                            if (request?.isForMainFrame == true) {
                                android.util.Log.e(
                                    "WebViewConsole",
                                    "main frame error: ${error?.errorCode} ${error?.description} ${request.url}",
                                )
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: android.webkit.WebView?,
                            request: android.webkit.WebResourceRequest?,
                        ): Boolean {
                            // 交给 WebView 自己处理，保留站内跳转历史（返回键可回退）
                            return false
                        }
                    }

                    // 页面背景色跟随主题，减少加载瞬间的白闪
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)

                    // 允许 chrome://inspect 远程调试（debug 包才有意义，release 下无副作用）
                    android.webkit.WebView.setWebContentsDebuggingEnabled(true)

                    webRef = this
                    loadUrl(url)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                // 底色跟随主题，避免加载瞬间白闪
                .background(MaterialTheme.colorScheme.background),
        )
    }
}

/**
 * 把 WebView 的 Cookie 从内存刷到磁盘。
 *
 * `CookieManager.flush()` 在 Android 上**不会立刻同步返回** ——
 * 官方文档明确说它把写盘动作丢到后台线程，所以调用完不能假定已落盘。
 * 这里包一层 runCatching，避免个别 ROM 上抛异常影响主流程。
 *
 * 触发时机：页面加载完成、页面即将销毁（返回 / 切 Tab）。
 *
 * 注意：**不要**碰 `WebStorage.deleteAllData()` —— 那会把
 * localStorage / IndexedDB 整个清空，正好和「保持登录态」相反。
 */
private fun flushWebSession() {
    runCatching {
        android.webkit.CookieManager.getInstance().flush()
    }
}

/**
 * 修复部分国产 ROM（华为 EMUI 等）WebView 中 CSS `vh` 单位失效的问题。
 *
 * 现象：`getComputedStyle(el).height` 对 `height:100vh` 返回 `0px`，
 * 而 `window.innerHeight` 是正确的 936。于是所有以 `height:100vh` / `100%`
 * 铺满屏幕的站点（Vuetify、Element Plus 后台、各类 SPA）高度链全部坍缩，
 * 外层 `overflow:hidden` 会把主体内容整块裁掉，表现为**页面大片空白**。
 *
 * 补丁做了三件事：
 * 1. 定义 CSS 变量 `--vh`，并把 `html` / `body` 的高度显式写成真实像素；
 * 2. 注入样式，对常见「整屏容器」选择器兜底设置 min-height；
 * 3. 监听 resize / 旋转，重算一次。
 */
private val VH_PATCH_JS = """
(function () {
  if (window.__vhPatched) { window.__applyVh && window.__applyVh(); return; }
  window.__vhPatched = true;

  var FULL_VAR = '--vh-full';

  function viewportH() {
    return window.innerHeight || document.documentElement.clientHeight || 0;
  }

  // 把样式表里所有 "100vh" 替换成 var(--vh-full)，
  // 让整站的 height:calc(100vh - Npx) 之类写法重新可用。
  function rewriteSheets() {
    var sheets = document.styleSheets;
    for (var i = 0; i < sheets.length; i++) {
      var sheet = sheets[i];
      var rules = null;
      try { rules = sheet.cssRules; } catch (e) { continue; }   // 跨域样式表跳过
      if (!rules) continue;
      for (var j = rules.length - 1; j >= 0; j--) {
        var rule = rules[j];
        if (rule.cssText && rule.cssText.indexOf('100vh') !== -1) {
          try {
            var next = rule.cssText.replace(/(\d*\.?\d+)vh/g, function (m, n) {
              return (parseFloat(n) / 100) + ' * var(' + FULL_VAR + ')';
            });
            // 先删旧规则再插入，保持层叠顺序
            sheet.deleteRule(j);
            sheet.insertRule(next, j);
          } catch (e) {}
        }
        // 递归处理 @media / @supports 里的嵌套规则
        if (rule.cssRules) {
          try {
            for (var k = rule.cssRules.length - 1; k >= 0; k--) {
              var inner = rule.cssRules[k];
              if (inner.cssText && inner.cssText.indexOf('100vh') !== -1) {
                var txt = inner.cssText.replace(/(\d*\.?\d+)vh/g, function (m, n) {
                  return (parseFloat(n) / 100) + ' * var(' + FULL_VAR + ')';
                });
                rule.deleteRule(k);
                rule.insertRule(txt, k);
              }
            }
          } catch (e) {}
        }
      }
    }
  }

  // 内联 style 里的 calc(...100vh...) 也要改，否则优先级更高会盖掉样式表
  function rewriteInlineStyles() {
    var all = document.querySelectorAll('[style]');
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      var s = el.getAttribute('style');
      if (s && s.indexOf('vh') !== -1) {
        el.setAttribute('style', s.replace(/(\d*\.?\d+)vh/g, function (m, n) {
          return 'calc(' + (parseFloat(n) / 100) + ' * var(' + FULL_VAR + '))';
        }));
      }
    }
  }

  window.__applyVh = function () {
    var h = viewportH();
    if (!h || h < 1) return;
    var root = document.documentElement;
    // 关键变量：一个「整屏高度」，以及 1vh 的等价像素
    root.style.setProperty(FULL_VAR, h + 'px');
    root.style.setProperty('--vh', (h * 0.01) + 'px');

    // 如果本机 vh 正常（>0）就完全不干预，避免破坏正常网页
    var probe = document.createElement('div');
    probe.style.cssText = 'position:fixed;left:-9999px;top:0;width:1px;height:100vh';
    root.appendChild(probe);
    var nativeVh = parseFloat(getComputedStyle(probe).height) || 0;
    root.removeChild(probe);

    if (nativeVh < 1) {
      rewriteSheets();
      rewriteInlineStyles();
      // html/body 高度兜底
      root.style.height = h + 'px';
      if (document.body) {
        document.body.style.height = h + 'px';
        document.body.style.minHeight = h + 'px';
      }
    }
  };

  window.__applyVh();
  document.addEventListener('DOMContentLoaded', window.__applyVh);
  window.addEventListener('load', window.__applyVh);
  window.addEventListener('resize', function () { setTimeout(window.__applyVh, 60); });
  window.addEventListener('orientationchange', function () { setTimeout(window.__applyVh, 200); });
  setTimeout(window.__applyVh, 300);
  setTimeout(window.__applyVh, 1200);
  setTimeout(window.__applyVh, 2500);
})();
""".trimIndent()

/** 给网页一个占位 JS 接口，防止某些页面探测 Android 桥时抛错。 */
private class VhPatchBridge(private val webView: android.webkit.WebView) {
    @android.webkit.JavascriptInterface
    fun viewportHeight(): Int = webView.height
}
