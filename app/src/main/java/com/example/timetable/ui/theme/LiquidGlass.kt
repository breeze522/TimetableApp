package com.example.timetable.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 轻量「拟玻璃」（glassmorphism）工具集。
 *
 * 为什么不用 Haze 全量糊一遍？
 * - Haze 的真模糊依赖 RenderEffect，每次采样都要 GPU 离屏渲染；
 *   如果给网格里每一张课程卡片都挂一个 hazeEffect（一屏可能有 30+ 张），
 *   反而会把帧率拖垮。
 * - 所以这里的分工是：
 *   · **底栏 / 顶栏** 这类「大面积、少量、需要透出内容」的区域 → 用 Haze 真模糊；
 *   · **课程卡片 / 列表项 / 分组** 这类「小面积、大量」的元素 → 用本文件的
 *     「高光渐变 + 细描边 + 半透明底」三件套模拟玻璃质感，几乎零额外 GPU 开销。
 */

private const val HIGHLIGHT_LIGHT = 0.55f
private const val HIGHLIGHT_DARK = 0.10f

/**
 * 玻璃高光画笔：自顶向下的四段渐变。
 *
 * 比单纯的两端线性渐变更接近真实玻璃的边缘聚光 ——
 * 顶部最亮（受光面）、中部几乎透明（本体）、底部微亮（反光）。
 *
 * 注意：这里刻意写成**非 Composable** 的纯函数，
 * 这样它可以被 [Modifier.liquidGlass] 直接调用（Modifier 扩展里不能调 @Composable）。
 */
fun glassHighlightBrush(
    isDark: Boolean,
    intensity: Float? = null,
): Brush {
    val top = intensity ?: if (isDark) HIGHLIGHT_DARK else HIGHLIGHT_LIGHT
    return Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = top),
            Color.White.copy(alpha = top * 0.12f),
            Color.White.copy(alpha = 0f),
            Color.White.copy(alpha = top * 0.22f),
        ),
    )
}

/** Composable 版本：自动读取当前主题明暗 */
@Composable
fun rememberGlassHighlightBrush(intensity: Float? = null): Brush =
    glassHighlightBrush(isSystemInDarkTheme(), intensity)

/**
 * 液态玻璃表面修饰符（拟玻璃版）。
 *
 * 依次叠加：
 * 1. 半透明底色（[tint]）；
 * 2. 自左上到右下的白色高光渐变 —— 这是「玻璃」最关键的视觉线索；
 * 3. 一圈极细的描边，勾出玻璃的厚度。
 *
 * 示例：
 * ```
 * Box(Modifier.liquidGlass(
 *     tint = MaterialTheme.colorScheme.surface,
 *     isDark = isSystemInDarkTheme(),
 * ))
 * ```
 *
 * @param tint 玻璃底色，通常传主题 surface 或课程主题色
 * @param isDark 当前是否深色主题（决定高光强度与描边色）
 * @param alpha 底色不透明度，越小越「透」
 * @param shape 圆角形状
 * @param borderWidth 描边宽度
 * @param highlightIntensity 顶部高光强度（0~1），null 时按主题自动取值
 */
fun Modifier.liquidGlass(
    tint: Color,
    isDark: Boolean,
    alpha: Float = 0.55f,
    shape: Shape = RoundedCornerShape(18.dp),
    borderWidth: Dp = 0.8.dp,
    highlightIntensity: Float? = null,
): Modifier = this
    .clip(shape)
    .background(tint.copy(alpha = alpha))
    .background(glassHighlightBrush(isDark, highlightIntensity))
    .border(borderWidth, glassOutlineColor(isDark), shape)

/** 玻璃描边色：深色下用淡白线（模拟玻璃断面），浅色下用白线 */
fun glassOutlineColor(isDark: Boolean): Color =
    if (isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.55f)

/**
 * 「按压 Q 弹」修饰符 —— 给玻璃卡片一个跳一下的手感。
 *
 * **为什么不用 `Modifier.clickable` 自带的 press 指示**：
 * Compose 的 ripple / 默认 press 反馈是「变暗」或「水波纹」，
 * 和液态玻璃这种「软材质」的气质完全不搭 —— 玻璃被按应该**形变**，不是变色。
 *
 * 【实现要点：为什么不能用 collectIsPressedAsState】
 * 最初写法是 `val pressed by interaction.collectIsPressedAsState()`，
 * 再用 `LaunchedEffect(pressed) { if (pressed) ... else ... }`。
 * **快速点击（手指停 60~120ms）时它是坏的** —— 这是实测出来的：
 *   - `collectIsPressedAsState` 是**状态**而不是**事件**；
 *   - 快速点击时 Press 和 Release 可能落在同一帧，状态直接从
 *     false → true → false 走完，Compose 只看到最终值 `false`；
 *   - 于是 `LaunchedEffect` 里 pressed==true 的分支**从未执行**，
 *     那个「snap 到可见形变」的脉冲根本没发出来，回弹自然也没有；
 *   - 表现就是「长按有反应、点一下没反应」。
 *
 * 所以这里改成**收集事件流**（`interaction.interactions.collect`），
 * 逐个处理 `PressInteraction.Press` / `.Release` / `.Cancel` ——
 * 事件不会像状态那样被"压扁"，哪怕同帧到达也两个都会被处理。
 *
 * 另外加了 [MIN_PULSE_MS]：快速点击时脉冲刚起来就要回弹，
 * 动画会被自己打断、看起来只是"抖了一下"。所以松手后**至少**等
 * 脉冲跑完这段时间再回弹，保证那一下「弹出去再收回来」看得完整。
 *
 * @param scale 按下时的目标缩放（1.0 = 不放大）。默认 0.94，即**按下去缩小**，
 *   模拟「手指把它按扁了」；松手弹回 1.0 并过冲一点。
 * @param enabled 传 false 时完全不做任何事（用于不可点的卡片）
 * @param onClick 点击回调。**必须在这里传**，不能用 `.pressBounce().clickable{}` ——
 *   pressBounce 内部已经挂了 clickable，外层再挂一个会导致两条手势流，
 *   按压状态和点击时机都会错乱。
 */
@Composable
fun Modifier.pressBounce(
    scale: Float = 0.94f,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
): Modifier {
    if (!enabled) return this
    val interaction = remember { MutableInteractionSource() }
    // 0 = 常态，1 = 按满
    val amount = remember { Animatable(0f) }

    // 【踩坑记录：为什么不用 scope.launch】
    // 最初写成 `scope.launch { snapTo(0.55f); animateTo(1f) }`，
    // **快速点击时它是坏的**，logcat 实测：
    //     event=Press   amount=0.0
    //     event=Release amount=0.0
    // 事件都收到了，但 amount 始终为 0 —— 因为 `scope.launch` 是**异步**的，
    // 协程还没被调度执行，紧跟而来的 Release 就 `pulseJob.cancel()` 把它掐了，
    // 协程体里 `snapTo` 那一行**一次都没跑过**。
    //
    // 正解：**在 collect 的同一协程里顺序执行**，不要另起 job。
    // 用「循环 + 内部并行等待」的方式表达「按下要涨 / 松开要弹」：
    // 每个事件到来时 cancelAll 掉上一轮动画，然后原地发起新的动画，
    // 动画本身仍是挂起的，但**snap 一定先于任何 cancel 执行**。
    val animScope = rememberCoroutineScope()
    var animJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(interaction) {
        interaction.interactions.collect { event ->
            when (event) {
                is PressInteraction.Press -> {
                    // 关键：**同步**先 snap，保证接触即有形变。
                    // snapTo 是挂起函数，但用一个 UNDISPATCHED 启动的协程
                    // 可以让它立即在当前线程执行到第一个真正挂起的点。
                    animJob?.cancel()
                    animJob = animScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        amount.snapTo(0.55f)
                        amount.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = 0.55f,
                                stiffness = 380f,
                            ),
                        )
                    }
                }

                is PressInteraction.Release,
                is PressInteraction.Cancel,
                -> {
                    // 先记下松手瞬间的形变量，再取消按下动画。
                    // 不能用 pulseJob.cancel() 后直接 animateTo —
                    // 那样会和 Press 里那个 UNDISPATCHED 协程抢同一个 Animatable。
                    val startFrom = amount.value
                    animJob?.cancel()
                    animJob = animScope.launch {
                        // 给「按下」一点展示时间，否则快速点击只看到一次抖动。
                        // 若松手时形变还没起来（startFrom 很小，说明 snap 刚发生），
                        // 也要等够时间让用户看见。
                        if (startFrom < 0.6f) delay(MIN_PULSE_MS)
                        // 回弹：低阻尼 → 过冲 → 弹两下
                        amount.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = 0.24f,
                                stiffness = 560f,
                            ),
                        )
                    }
                }
            }
        }
    }

    // 组件离开时收尾，避免动画继续跑在已销毁的节点上
    DisposableEffect(Unit) {
        onDispose {
            animJob?.cancel()
        }
    }

    val a = amount.value
    // 由 0→1 的 amount 插值出实际缩放：常态 1.0，按满 scale
    val currentScale = 1f + (scale - 1f) * a

    return this
        .graphicsLayer {
            scaleX = currentScale
            scaleY = currentScale
        }
        .clickable(
            interactionSource = interaction,
            indication = null,   // 不要 ripple —— 形变本身就是反馈
            onClick = { onClick?.invoke() },
        )
}

/**
 * 脉冲最短存活时长。快速点击时，松手事件几乎紧跟着按下事件到来，
 * 若立刻回弹，那个「弹出去」的过程会被掐掉，用户只看到一次轻微抖动。
 * 等这段时间再回弹，Q 弹才「看得见」。
 */
private const val MIN_PULSE_MS = 110L


/**
 * 用 [Painter] 铺满整个区域（类似 CSS 的 `background-size: cover`）。
 *
 * Compose 自带的 `Modifier.background(brush)` 不支持画笔，而 `Image`
 * 又要参与布局测量。这里用 `drawWithContent` 直接在画布上画：
 * 按「保持宽高比、铺满并居中裁切」算出目标矩形，
 * 保证任意比例的图（尤其是竖向的壁纸）都不会被拉变形。
 */
fun Modifier.painterBackground(painter: Painter): Modifier =
    this.drawWithContent {
        drawContent()
        val intrinsic = painter.intrinsicSize
        if (intrinsic.width <= 0f || intrinsic.height <= 0f) {
            with(painter) { draw(size = size) }
        } else {
            val scale = maxOf(size.width / intrinsic.width, size.height / intrinsic.height)
            val w = intrinsic.width * scale
            val h = intrinsic.height * scale
            translate(left = (size.width - w) / 2f, top = (size.height - h) / 2f) {
                with(painter) { draw(size = androidx.compose.ui.geometry.Size(w, h)) }
            }
        }
    }

/** 感知亮度（sRGB 近似），用于判断某个颜色偏亮还是偏暗 */
internal fun Color.isLightColor(): Boolean =
    0.2126f * red + 0.7152f * green + 0.0722f * blue > 0.5f

// ---------------------------------------------------------------------------
// 液态玻璃参数中心
//
// 所有「真模糊」玻璃（顶栏 / 底栏 / 二级页顶栏）统一从这里取值，
// 改一处就能整体调浓淡，不会再出现「顶栏和底栏不是一个材质」的割裂。
//
// 调参逻辑（想更浓就往这三个方向走）：
// · [GLASS_BLUR]   —— 模糊半径。太小（<24dp）根本看不出糊，玻璃感就不成立。
// · [GLASS_TINT_ALPHA] —— 染色层不透明度。这是「液态」的关键：
//   模糊只是一个「底板」，真正让人眼觉得是「有厚度的玻璃」的是
//   一层带主题色的半透明染色 + 边缘高光。
// · [GLASS_NOISE] —— 噪点。适量噪点能打散高频细节，让糊感更「像液体」而不是「像失焦照片」。
// ---------------------------------------------------------------------------

/** 统一模糊半径：48dp 在手机上是「一眼可见的糊」，又不会糊成一团纯色 */
val GLASS_BLUR = 48.dp

/** 染色层不透明度：比原来的 0.16f 明显加重，「液态」质感才出得来 */
const val GLASS_TINT_ALPHA = 0.34f

/** 噪点强度：0.05 → 0.12，弱化高频细节、让折射感更自然 */
const val GLASS_NOISE = 0.12f

/**
 * 玻璃底色（铺在模糊之上、染色之下）。
 *
 * 刻意压低 alpha：底色越透，被模糊的内容越能透出来。
 * 浓度主要交给 [GLASS_TINT_ALPHA] 那层染色来做，而不是靠底色堆不透明度 ——
 * 靠底色堆会变成「磨砂塑料」，靠染色才是「玻璃」。
 */
fun glassBaseColor(isDark: Boolean): Color =
    if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.30f)

/** 玻璃染色：带主题色的半透明层，负责「液态」的色彩倾向 */
@Composable
fun glassTintColor(): Color =
    MaterialTheme.colorScheme.surface.copy(alpha = GLASS_TINT_ALPHA)

/** 玻璃描边：浅色用白亮边勾厚度，深色用淡白线 */
fun glassBarBorderColor(isDark: Boolean): Color =
    if (isDark) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.85f)

/**
 * 顶 / 底栏统一的「玻璃高光渐变」。
 *
 * 顶部一道明显亮边（受光面）→ 中段几乎透明（本体）→ 底部一道暗边（厚度阴影），
 * 三段式比两段式更接近真实玻璃的截面光照。
 */
fun glassBarHighlightBrush(isDark: Boolean): Brush =
    if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.20f),
                Color.White.copy(alpha = 0.04f),
                Color.Black.copy(alpha = 0.12f),
            ),
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.78f),
                Color.White.copy(alpha = 0.24f),
                Color.Black.copy(alpha = 0.07f),
            ),
        )
    }

/**
 * 二级页面顶栏 / 底栏的**统一毛玻璃**修饰符。
 *
 * 主页顶栏的玻璃参数是写死在 `TimetableScreen` 里的，二级页面（设置 / 搜索 /
 * 导入 / 课表管理）也需要同款观感，否则从主页切过去时顶栏会「从玻璃变成实心」，
 * 视觉上非常割裂。这里把它抽成一处，所有二级页面共用，保证参数完全一致。
 *
 * @param hazeState 与内容层共享的 Haze 状态。传 null 时退化为**不加模糊**的
 *   半透明玻璃（用于不接 Haze 的场合），观感接近但不采样背景。
 */
@Composable
fun Modifier.secondaryGlassBar(hazeState: dev.chrisbanes.haze.HazeState?): Modifier {
    val isDark = MaterialTheme.colorScheme.background.isLightColor().not()
    if (hazeState == null) {
        return this.background(
            if (isDark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.42f),
        )
    }
    return this.hazeEffect(
        state = hazeState,
        style = dev.chrisbanes.haze.HazeDefaults.style(
            backgroundColor = glassBaseColor(isDark),
            tint = dev.chrisbanes.haze.HazeDefaults.tint(glassTintColor()),
            // 与主页顶栏 / 底栏保持完全一致（统一取值，改一处全站生效）
            blurRadius = GLASS_BLUR,
            noiseFactor = GLASS_NOISE,
        ),
    )
}
