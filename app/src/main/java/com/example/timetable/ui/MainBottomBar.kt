package com.example.timetable.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import com.example.timetable.ui.theme.isLightColor
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 * 底部导航栏：胶囊式浮岛，四个一级入口（课表 / 成绩 / 校园生活 / 登录）。
 *
 * 视觉升级（液态玻璃）：
 * - 整条栏是一个大圆角「胶囊」，四周留出外边距，浮在内容之上；
 * - 背景改用 **Haze 真实背景模糊**（[HazeState] 由调用方与内容层共享），
 *   滑动课表时能透过底栏看到被模糊的课程卡片，形成真正的「毛玻璃」观感；
 * - 叠加一层极淡的高光渐变 + 细描边，勾出玻璃的厚度；
 * - 选中项：图标与文字变主题色，背后铺一层浅色胶囊底，用一个会「弹一下」的
 *   指示位移动画做过渡；
 * - 未选中项：图标与文字用次要色，点击切换。
 *
 * @param hazeState 与内容层共享的 Haze 状态；内容层需挂 [Modifier.hazeSource]。
 */
@Composable
fun MainBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant
    // 圆角取到高度的近 60%（62/2=31 → 31dp 即标准胶囊）。
    // 之前 28dp 配 52dp 高度，视觉上还是「圆角矩形」；拉满才是胶囊。
    val barHeight = 62.dp
    val shape = RoundedCornerShape(barHeight / 2)

    val isDark = MaterialTheme.colorScheme.background.isLightColor().not()

    // ---- 按压状态（全栏共享）--------------------------------------------------
    //
    // 一个「按下去多少」的标量，0 = 没按，1 = 按满。它同时驱动两件事：
    //   1. 液珠放大 —— 手指按住时液珠被「压扁又鼓起」，按住越久越大；
    //   2. 整条玻璃栏下沉 —— 手指压力透过玻璃传下去，栏体微微下沉 + 变宽。
    //
    // 用它统一「点击 Q 弹」和「长按变大」：
    //   - 短按：按下瞬间就有一个**可见的初始形变**，松手时用低阻尼 spring
    //     过冲回弹，那一下「弹出去再收回来」就是 Q 弹；
    //   - 长按：从那个初始形变继续从容涨到 1，液珠明显鼓起。
    // 两者共用一条曲线，不需要判断「这是点击还是长按」，手感天然连贯。
    val pressAmount = remember { Animatable(0f) }
    // 是否正处于按下状态。用 var 而不是 derivedStateOf，因为要驱动
    // LaunchedEffect 的 key 变化（进入/离开按压态时各跑一次动画）。
    var isPressed by remember { mutableStateOf(false) }
    // 按下的是第几个 tab（-1 = 没按）。液珠会朝这个方向微微「被吸过去」，
    // 模拟手指把液滴往自己这边拉。切换页面的时机不变（仍在 onClick），
    // 所以按住不会误切页，只是液珠提前给出「我注意到你了」的反馈。
    var pressedIndex by remember { mutableStateOf(-1) }

    // 【为什么要有 PULSE】之前的实现是按下时从 0 慢慢 spring 到 1。
    // 快速点击（手指只停 60~120ms）时，pressAmount 才涨到 0.05 左右就被松手打断，
    // 回弹幅度小到肉眼看不见 —— 用户反馈的「点击没有 Q 弹」就是这个原因。
    // 现在改成：按下瞬间**先 snap 到一个可见的初始形变**（0.5），
    // 再往下继续涨。这样短按一按下去就有明显形变，松手时弹回去，
    // Q 弹立刻可见；长按则从这个起点继续鼓到 1。
    val pulse = 0.5f

    LaunchedEffect(isPressed) {
        if (isPressed) {
            // 按下：瞬间跳到可见形变（snapTo，没有动画过程，
            // 手指一落下就有反馈，这是「跟手」的关键）
            pressAmount.snapTo(pulse)
            // 然后从容地继续涨，按住越久越鼓
            pressAmount.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.55f,
                    stiffness = 260f,
                ),
            )
        } else {
            // 松开：低阻尼 + 高刚度 → 快速回 0 并且**冲过头**再弹回来，
            // 这一下过冲就是「Q 弹」。
            //
            // dampingRatio 从 0.28 压到 **0.22**：过冲更狠、能明显弹两下，
            // 这是「Q 弹」最容易被感知的部分。刚度过高（>900）会让过冲
            // 还没成形就结束，所以 stiffness 保持 620 不变。
            pressAmount.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.22f,
                    stiffness = 620f,
                ),
            )
        }
    }

    // 按压时整条栏的下沉量 / 膨胀量（都是 pressAmount 的线性映射，
    // 放在 graphicsLayer 里做，不参与布局、不引起重排）。
    val press = pressAmount.value
    // 玻璃参数统一从 theme/LiquidGlass.kt 的「参数中心」取，保证顶栏 / 底栏 /
    // 二级页顶栏是**同一种材质**，不会出现浓淡不一致的割裂感。
    val glassBase = com.example.timetable.ui.theme.glassBaseColor(isDark)
    // 描边：浅色模式用白的高光边（玻璃厚度感），深色模式用极淡白线
    val borderColor = com.example.timetable.ui.theme.glassBarBorderColor(isDark)

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 底部留白：手势条高度 + 额外抬升，让浮岛和屏幕底边之间透出一点内容，
            // 「浮」的层次感才出来；贴边会显得像一条系统导航栏。
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            // 「整条栏跟着动」：按住时整块浮岛往下沉一点、纵向略压扁，
            // 松开时靠 pressAmount 的过冲自然弹回。放在外层容器上做，
            // 内层的 Haze 模糊层不参与缩放 —— 否则模糊半径会被拉伸失真。
            //
            // 位移用 translationY 而不是 padding：不触发重新布局，
            // 每帧只更新一次变换矩阵，按压过程不掉帧。
            //
            // 幅度刻意做小：底栏是个「安静」的元素，动幅一大就变成
            // 整块板子在晃，反而廉价。现在的量级是「几乎察觉不到，
            // 但一松开能感到它回落了一下」。
            .graphicsLayer {
                translationY = press * 1.6f * density
                // 纵向轻微压扁、横向极轻微鼓起，像一块被手指压着的软玻璃
                scaleY = 1f - press * 0.028f
                scaleX = 1f + press * 0.006f
                // 缩放锚点放在底部中央：压扁时从上沿往下塌，
                // 底边不动，才像「被按下去」而不是「整体缩水」。
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        // 这一层只负责「定尺寸 + 让栏体和内容层精确重合」。
        // 栏体与内容层是**兄弟**关系，不是父子 —— 这样液珠放大时
        // 可以自由越出玻璃栏的边界（用户要的「超出底栏」），
        // 而不会被玻璃栏自己的圆角裁剪吃掉。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight),
        ) {
            // ---------- A. 玻璃栏体（只负责材质，不含任何内容）----------
            Box(
                modifier = Modifier
                    .matchParentSize()
                    // 独立渲染层：把整条玻璃栏隔离成自己的 layer，
                    // 父级内容动画时尽量复用本层纹理、减少连带重绘。
                    //
                    // 用默认的 Auto 合成策略：由系统自行判断是否需要离屏，
                    // 既拿到层级隔离的收益，又不会因为强制离屏多付一次拷贝。
                    .graphicsLayer {
                        clip = true
                    }
                    // Haze 真模糊：把底栏背后的内容做高斯模糊后透出来。
                    //
                    // 三个参数决定「像不像玻璃」：
                    // - backgroundColor 用**低 alpha**，否则模糊被盖死；
                    // - blurRadius 要够大（酷安的观感约 20~30dp）才看得出糊感；
                    // - tint 给玻璃一点色彩倾向，纯透明会显得「脏」。
                    .clip(shape)
                    .hazeEffect(
                        state = hazeState,
                        style = HazeDefaults.style(
                            backgroundColor = glassBase,
                            tint = HazeDefaults.tint(
                                com.example.timetable.ui.theme.glassTintColor(),
                            ),
                            // 与顶栏 / 二级页顶栏统一取值
                            blurRadius = com.example.timetable.ui.theme.GLASS_BLUR,
                            noiseFactor = com.example.timetable.ui.theme.GLASS_NOISE,
                        ),
                    ) {
                        // 模糊层保持矩形边界（圆角由后面的 .clip(shape) 统一裁切）
                        blurredEdgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Rectangle
                    }
                    .background(
                        // 玻璃高光：顶部一道明显亮边 + 底部暗边，做出「一块有厚度的玻璃」
                        // 与顶栏共用同一支画笔，保证是同一种材质
                        brush = com.example.timetable.ui.theme.glassBarHighlightBrush(isDark),
                    )
                    .border(1.2.dp, borderColor, shape),
            )

            // ---------- B. 内容层（液珠 + 图标）----------
            // 与栏体同尺寸重合，但**不裁剪** —— 液珠长按放大时可以顶出去。
            // 内边距放在这里（而不是玻璃栏体上），因为液珠需要按「内容区」
            // 而不是「整条栏」来定位；这样移动端改栏高时两边自动同步。
            BoxWithConstraints(
                modifier = Modifier
                    .matchParentSize()
                    .padding(vertical = 6.dp, horizontal = 6.dp),
            ) {
                val tabCount = MainTab.entries.size
                // 每个 tab 等分宽度（与下面 Row 的 weight(1f) 一致）
                val slotWidth = maxWidth / tabCount
                // 指示器胶囊尺寸：略大于图标，做成「药丸」压住背景。
                // 高度留出 10dp 余量（62 - 2×6 padding - 10 ≈ 40），和栏同心地悬浮。
                val pillWidth = 58.dp
                val pillHeight = 40.dp

                val selectedIndex = MainTab.entries.indexOf(selected)
                // 目标中心 x = 第 index 个槽位的中心
                val targetCenterX = slotWidth * selectedIndex + slotWidth / 2
                // 用 spring 滑动，带一点回弹，手感跟手。
                // 阻尼调低、刚度调小 → 珠体滑得更从容，拉伸过程看得见；
                // 原来 0.78/620 太快，视觉上基本是「瞬移」，液体感出不来。
                val indicatorCenterX by animateDpAsState(
                    targetValue = targetCenterX,
                    animationSpec = spring(
                        dampingRatio = 0.62f,
                        stiffness = 300f,
                    ),
                    label = "indicatorX",
                )

                // ---- 指示器：一颗「附着在玻璃上的液珠」----
                //
                // 【为什么第一版像塑料】
                // 上一版是「大面积白色纵向渐变 + 均匀白描边 + 一大块白色高光椭圆」，
                // 这三样恰好是塑料制品的视觉签名：
                //   - 塑料是**不透明**的 → 大面积白渐变把珠体填实了；
                //   - 塑料的**边缘亮度均匀** → 一圈等厚等亮的白边；
                //   - 塑料的**高光是「涂」上去的一块** → 高光占比 46%×30%，太大。
                //
                // 【真实液珠长什么样】
                // 水珠 90% 的面积是**完全透明**的，你看到的「珠」其实是光：
                //   1. 一圈极细的**边缘亮环**，而且**亮度极不均匀** ——
                //      光从左上打来，所以左上最亮、右下几乎消失（这里是关键：
                //      均匀的环=塑料，不均匀的环=玻璃/水）；
                //   2. 一个**极小的镜面点**（不是一片）—— 只占 20% 宽左右，
                //      位置偏上、略偏左，像一粒针尖大的光源反射；
                //   3. 底部一道**内壁反光窄弧** —— 光穿过水体打到下内壁再折回来。
                //      这是「厚度感」的唯一来源，塑料完全没有这一层；
                //   4. 体色只是**极淡的一层染色**（10% 上下），负责告诉你
                //      「这滴水浸在主题色里」，不该有存在感。
                //
                // 【拉伸】
                // 移动速度越快，珠体横向拉长、纵向压扁（真实液滴被拖成椭圆），
                // 且**拉伸时边缘亮环会被自动拉细**（因为环是按下前的比例画的），
                // 停止后回弹成圆角胶囊。刚性的圆角矩形无论加多少高光都只会像塑料。
                val velocity = kotlin.math.abs(targetCenterX.value - indicatorCenterX.value)
                val stretch = (velocity / slotWidth.value).coerceIn(0f, 1f)

                // 按压时液珠的「鼓起量」。长按越久 press 越接近 1，珠体越明显，
                // 松开后由 pressAmount 的低阻尼 spring 带回一个过冲 —— 就是 Q 弹。
                //
                // 放大比例：横向 +8%、纵向 +30%。
                //
                // 上一版试过横向 +20%，按住时珠体被拉成一个「宽扁的浅紫大饼」——
                // 因为静止态的胶囊本来就 58dp 宽，再横向撑开就盖住了整个槽位，
                // 边缘那圈细腻的亮环被稀释掉，又回到了「塑料片」的观感。
                // 现在让**纵向主导**：珠子是被「顶高」的，横向几乎不变，
                // 于是它始终是一颗「立起来的液珠」而不是「摊开的色块」。
                val pressScale = press * 1f
                val pressScaleX = 1f + pressScale * 0.08f
                val pressScaleY = 1f + pressScale * 0.30f

                // 液珠朝按下的 tab 方向微微偏移（最多 22% 个槽宽），
                // 做出「手指把液滴吸过去」的牵引感。松手后自动回位。
                val pullTowardPressed = if (pressedIndex >= 0) {
                    val pressedCenterX = slotWidth * pressedIndex + slotWidth / 2
                    (pressedCenterX - indicatorCenterX) * 0.22f * press
                } else {
                    0.dp
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        // 从中心锚点偏移：左边缘 = 中心 - 胶囊半宽
                        .offset(x = indicatorCenterX - pillWidth / 2 + pullTowardPressed)
                        .size(pillWidth, pillHeight)
                        // 横向拉伸：随速度拉长，停下来回到 1.0
                        .graphicsLayer {
                            // 滑动拉伸与按压鼓起相乘：两个效果可以叠加，
                            // 「边滑边被按住」时珠体又长又鼓，是最液体的状态。
                            scaleX = (1f + stretch * 0.42f) * pressScaleX
                            scaleY = (1f - stretch * 0.14f) * pressScaleY
                            // 纵向放大的锚点压在**底部**：珠子往上「涌」起来，
                            // 底边始终贴着玻璃不动 —— 这是液体的行为
                            // （水受挤压会往上鼓包，但接触面不会离开桌面）。
                            // 若锚点在中心，效果会是「上下同时涨开」，
                            // 那更像一个气球被吹大，不是液体。
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                                0.5f,
                                1f - pressScale * 0.5f, // 从底边（1.0）渐移到中心（0.5）
                            )
                        }
                        // 不在这里 clip：下面的绘制自己带圆角，且缩放在本层内完成。
                        // 若加 clip，边缘亮环（正好压在边界上）会被裁掉一半，
                        // 那圈「水边」就断了 —— 这是上一版显得死板的原因之一。
                        .drawWithContent {
                            drawContent()

                            val w = size.width
                            val h = size.height
                            val corner = h / 2f

                            // 边缘亮环（表面张力）：Compose 的 Border 是单色，
                            // 做不出「一边亮一边灭」，所以用 sweepGradient 手工画环。
                            val ringBrush = Brush.sweepGradient(
                                colorStops = arrayOf(
                                    0.00f to Color.White.copy(alpha = 0.02f),
                                    0.10f to Color.White.copy(alpha = 0.20f),
                                    0.28f to Color.White.copy(alpha = 0.62f), // 左上最亮
                                    0.46f to Color.White.copy(alpha = 0.16f),
                                    0.62f to Color.White.copy(alpha = 0.03f),
                                    0.86f to Color.White.copy(alpha = 0.10f),
                                    1.00f to Color.White.copy(alpha = 0.02f),
                                ),
                                center = Offset(w * 0.46f, h * 0.46f),
                            )
                            // 环宽约 1.3dp；因为亮度不均，观感比同宽的均匀白边更细。
                            val ringWidth = 1.3f * density
                            val innerTopLeft = Offset(ringWidth, ringWidth)
                            val innerSize = Size(
                                w - ringWidth * 2f,
                                h - ringWidth * 2f,
                            )
                            val innerCorner = CornerRadius((h - ringWidth * 2f) / 2f)

                            // 外圈：亮环本体（实心胶囊，随后被内圈覆盖成环）
                            drawRoundRect(
                                brush = ringBrush,
                                topLeft = Offset.Zero,
                                size = Size(w, h),
                                cornerRadius = CornerRadius(corner),
                                alpha = if (isDark) 0.85f else 1f,
                            )
                            // 体色：环内部的主题色染色。用**径向渐变**而不是纯色 ——
                            // 液珠是一个凸透镜，中心的水层最厚、吸色最浓，越靠边越薄。
                            //
                            // alpha 压到 0.16：上一版试过 0.30，结果整颗珠子变成一团
                            // 「紫雾」，因为纯色铺满会让珠体比周围栏背景还亮 ——
                            // 而玻璃上的水珠其实是**透明的**，透出来的是背后的暗色，
                            // 只在边缘和底部把光聚起来。体色一旦过重，透亮感立刻消失。
                            val bodyAlpha = if (isDark) 0.16f else 0.13f
                            drawRoundRect(
                                brush = Brush.radialGradient(
                                    colorStops = arrayOf(
                                        // 中心几乎不上色，把「透」的位置留出来
                                        0.0f to accent.copy(alpha = bodyAlpha * 0.45f),
                                        0.52f to accent.copy(alpha = bodyAlpha * 0.85f),
                                        1.0f to accent.copy(alpha = bodyAlpha),
                                    ),
                                    center = Offset(w * 0.5f, h * 0.5f),
                                    radius = kotlin.math.max(w, h) * 0.62f,
                                ),
                                topLeft = innerTopLeft,
                                size = innerSize,
                                cornerRadius = innerCorner,
                            )

                            // 顶部一层极窄柔光（水膜感）。alpha 只有 0.10 ——
                            // 上一版这里是 0.30 的整片白，正是「塑料」的主因。
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.10f),
                                        Color.White.copy(alpha = 0f),
                                    ),
                                    startY = 0f,
                                    endY = h * 0.55f,
                                ),
                                topLeft = innerTopLeft,
                                size = innerSize,
                                cornerRadius = innerCorner,
                            )

                            // 暗侧收边：珠体右下沿一圈「向内收进去」的暗线。
                            // 为什么必须有：只画亮环的珠子看起来是「浮」在玻璃上的
                            // 一片亮膜；真实的液珠因为自身体积，背光侧的内壁会把
                            // 透过去的光再挡一道，于是右下沿出现一条比背景更暗的收边。
                            // 有了这条暗边，珠子才有「一颗实体压在上面」的重量。
                            drawRoundRect(
                                brush = Brush.sweepGradient(
                                    colorStops = arrayOf(
                                        0.00f to Color.Transparent,
                                        0.16f to Color.Transparent,
                                        0.30f to Color.Black.copy(alpha = 0.16f),
                                        0.42f to Color.Black.copy(alpha = 0.28f), // 右下最暗
                                        0.56f to Color.Black.copy(alpha = 0.14f),
                                        0.72f to Color.Transparent,
                                        1.00f to Color.Transparent,
                                    ),
                                    center = Offset(w * 0.5f, h * 0.5f),
                                ),
                                topLeft = innerTopLeft,
                                size = innerSize,
                                cornerRadius = innerCorner,
                            )

                            // 底部内壁反光窄弧 —— 「厚度感」的关键层。塑料没有这一层。
                            val arcLeft = w * 0.24f
                            val arcRight = w * 0.76f
                            drawArc(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0f),
                                        Color.White.copy(alpha = if (isDark) 0.34f else 0.55f),
                                        Color.White.copy(alpha = 0f),
                                    ),
                                    startX = arcLeft,
                                    endX = arcRight,
                                ),
                                startAngle = 18f,
                                sweepAngle = 144f,
                                useCenter = false,
                                topLeft = Offset(arcLeft, h * 0.42f),
                                size = Size(arcRight - arcLeft, h * 0.52f),
                                style = Stroke(width = 1.5f * density, cap = StrokeCap.Round),
                            )

                            // 镜面高光点：**小**才是关键。
                            // 22% 宽 × 14% 高、偏上偏左，alpha 高但面积很小 ——
                            // 看起来是「一粒光」而不是「一块白颜料」。
                            drawOval(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = if (isDark) 0.78f else 0.92f),
                                        Color.White.copy(alpha = 0f),
                                    ),
                                ),
                                topLeft = Offset(w * 0.36f, h * 0.17f),
                                size = Size(w * 0.22f, h * 0.14f),
                            )

                            // 次级微光点：真实水珠的高光会分成大小递减的两三点，
                            // 只有一个光点反而像贴图。
                            drawOval(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = if (isDark) 0.30f else 0.42f),
                                        Color.White.copy(alpha = 0f),
                                    ),
                                ),
                                topLeft = Offset(w * 0.60f, h * 0.26f),
                                size = Size(w * 0.10f, h * 0.08f),
                            )
                        },
                )

                // ---- 图标行（铺在指示器之上）----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    MainTab.entries.forEachIndexed { index, tab ->
                        BottomBarItem(
                            tab = tab,
                            selected = tab == selected,
                            accent = accent,
                            inactive = inactive,
                            onClick = { onSelect(tab) },
                            // 按压反馈：把「哪个 tab 被按住了」上报给上层，
                            // 由上层统一驱动液珠鼓起 + 整条栏下沉。
                            // 放在这里而不是每个 item 各自做，是为了让整条栏
                            // 共享同一份按压状态 —— 四个图标才能一起「呼吸」。
                            onPressStateChanged = { pressed ->
                                isPressed = pressed
                                pressedIndex = if (pressed) index else -1
                            },
                            pressAmount = { press },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    tab: MainTab,
    selected: Boolean,
    accent: Color,
    inactive: Color,
    onClick: () -> Unit,
    onPressStateChanged: (Boolean) -> Unit,
    pressAmount: () -> Float,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) accent else inactive,
        animationSpec = tween(180),
        label = "tabTint",
    )
    // 选中时图标微微放大，形成「弹一下」的反馈。
    // 注意：那个浅色胶囊底**不在这里画** —— 它由 MainBottomBar 里
    // 一根共享的滑动指示器统一承担，这样切换时是「滑过去」而不是「闪一下」。
    val selectedScale by animateFloatAsState(
        targetValue = if (selected) 1.10f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "tabIconScale",
    )

    val interaction = remember { MutableInteractionSource() }
    // 收集「手指是否正在本项上按住」。
    // 用 collectIsPressedAsState 而不是手写 pointerInput：它会正确处理
    // 移出手指范围、被父级抢走事件、手势取消等边界情况。
    val pressed by interaction.collectIsPressedAsState()

    // 把按压状态上报给上层（驱动整条栏 + 液珠）。
    // 用 LaunchedEffect(pressed) 而不是在组合期直接调回调 ——
    // 组合函数里写外部状态会导致重组期间修改状态，Compose 会报警告。
    LaunchedEffect(pressed) {
        onPressStateChanged(pressed)
    }
    // 组件销毁（比如页面重建）时兜底复位，避免按压状态卡在 true。
    DisposableEffect(Unit) {
        onDispose { onPressStateChanged(false) }
    }

    // 按住的这一项：图标也跟着被「压」一下。
    // 液珠在鼓起来，图标如果一动不动，珠子会像贴在图标外面 ——
    // 图标同步微缩、下沉一点点，才像整块玻璃被按下去。
    val press = pressAmount()
    val iconScale = if (pressed) selectedScale * (1f - press * 0.10f) else selectedScale
    val iconSink = if (pressed) press * 1.2f else 0f

    // 纯图标底栏（对齐酷安）：不放文字标签，
    // 靠「滑动的浅色胶囊底 + 图标放大」表达当前页，视觉更干净、占高更小。
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    translationY = iconSink * density
                }
                .then(
                    Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val w = (placeable.width * iconScale).toInt()
                        val h = (placeable.height * iconScale).toInt()
                        layout(w, h) {
                            placeable.place(-(w - placeable.width) / 2, -(h - placeable.height) / 2)
                        }
                    },
                ),
        )
    }
}
