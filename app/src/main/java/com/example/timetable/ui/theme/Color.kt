package com.example.timetable.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 课程卡片调色板。
 *
 * 选取偏低饱和、明度适中的色相，浅色主题下配白色/淡底卡片柔和耐看，
 * 深色主题下作为强调色也有足够辨识度。
 */
val CoursePalette: List<Color> = listOf(
    Color(0xFF7C83F5), // 靛蓝
    Color(0xFF4FA8E8), // 天蓝
    Color(0xFF3DB89A), // 青碧
    Color(0xFFE8A33D), // 琥珀
    Color(0xFFE06E6E), // 珊瑚
    Color(0xFFA77BE8), // 紫罗兰
    Color(0xFFE070A8), // 玫红
    Color(0xFF52B7C9), // 湖蓝
    Color(0xFF8F7FE8), // 紫
    Color(0xFF7E8CA0), // 石板灰
)

fun courseColor(index: Int): Color = CoursePalette[index.mod(CoursePalette.size)]

/** 课程卡片的背景色（低饱和淡底，柔和不刺眼） */
fun courseSurface(index: Int): Color = courseColor(index).copy(alpha = 0.16f)

/** 课程卡片的描边 / 强调色 */
fun courseAccent(index: Int): Color = courseColor(index).copy(alpha = 0.42f)

/** 课程卡片左侧的竖向色条 */
fun courseBar(index: Int): Color = courseColor(index)

/**
 * 设计令牌：圆角、间距、阴影。
 *
 * 统一收敛在主题层，避免各界面各写各的魔数。
 */
object Shapes {
    val xs = 6
    val sm = 8
    val md = 12
    val lg = 16
    val xl = 20
    val pill = 999
}

object Elev {
    /** 课程卡片的柔和投影高度 */
    val card = 1
    val raised = 3
    val dialog = 6
}
