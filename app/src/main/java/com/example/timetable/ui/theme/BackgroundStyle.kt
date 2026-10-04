package com.example.timetable.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 课表背景方案。
 *
 * 背景层铺在整个课表内容**之下**（包括底栏 / 顶栏的模糊采样范围内），
 * 这样液态玻璃的底栏才能真正「糊」出色彩 —— 纯白背景上做玻璃是看不出效果的。
 *
 * 每个方案都给出**浅色 / 深色**两套色值，跟随系统主题切换，
 * 避免深色模式下背景过亮刺眼。
 */
enum class BackgroundStyle {
    /** 极简纯色（跟随主题原本的 background，最干净） */
    PLAIN,

    /** 晨曦：暖橙 → 淡粉，轻盈柔和 */
    DAWN,

    /** 雾蓝：冷灰蓝 → 淡青，清冷安静 */
    MIST,

    /** 紫霞：薰衣草 → 淡紫，梦幻 */
    LILAC,

    /** 松林：薄荷绿 → 青绿，清新自然 */
    MINT,

    /** 暮色：深靛 → 暗紫，夜间专用（深色下最耐看） */
    DUSK,

    /** 自定义图片：用户从相册选一张图（存于应用私有目录） */
    CUSTOM,
    ;

    /** 人类可读的名字，用于设置界面 */
    val label: String
        get() = when (this) {
            PLAIN -> "极简"
            DAWN -> "晨曦"
            MIST -> "雾蓝"
            LILAC -> "紫霞"
            MINT -> "松林"
            DUSK -> "暮色"
            CUSTOM -> "自定义"
        }

    companion object {
        fun fromName(name: String?): BackgroundStyle =
            entries.firstOrNull { it.name == name } ?: PLAIN
    }
}

/**
 * 取某个背景方案的画笔。
 *
 * - [BackgroundStyle.PLAIN] 返回 null —— 由调用方直接用主题 background 铺纯色，
 *   这样「极简」方案能完美跟随 Material 主题，不受这里硬编码色值影响；
 * - 其余方案返回三段线性渐变（左上 → 右下），比两端渐变更耐看。
 *
 * @param dark 是否深色主题
 */
fun backgroundBrush(style: BackgroundStyle, dark: Boolean): Brush? {
    // 「极简」用主题纯色；「自定义」由调用方去加载图片，都不走渐变画笔
    if (style == BackgroundStyle.PLAIN || style == BackgroundStyle.CUSTOM) return null
    val colors: List<Color> = when (style) {
        BackgroundStyle.DAWN -> if (dark) {
            listOf(Color(0xFF2A1B24), Color(0xFF1E1620), Color(0xFF121016))
        } else {
            // 暖橙 → 淡粉：要足够饱和，否则玻璃底栏没有颜色可糊
            listOf(Color(0xFFFFD9B8), Color(0xFFFFE4D6), Color(0xFFFFF0EA))
        }

        BackgroundStyle.MIST -> if (dark) {
            listOf(Color(0xFF16202B), Color(0xFF141A22), Color(0xFF0F1319))
        } else {
            listOf(Color(0xFFBFDCF5), Color(0xFFD6E8F7), Color(0xFFEDF4FA))
        }

        BackgroundStyle.LILAC -> if (dark) {
            listOf(Color(0xFF221C33), Color(0xFF1A1626), Color(0xFF121019))
        } else {
            listOf(Color(0xFFDCCBF7), Color(0xFFE7DDF9), Color(0xFFF3EEFC))
        }

        BackgroundStyle.MINT -> if (dark) {
            listOf(Color(0xFF14251F), Color(0xFF131D1A), Color(0xFF0E1512))
        } else {
            listOf(Color(0xFFBFEEDC), Color(0xFFD8F4E9), Color(0xFFF0FAF6))
        }

        BackgroundStyle.DUSK -> if (dark) {
            listOf(Color(0xFF1B1830), Color(0xFF16142A), Color(0xFF0F0E1C))
        } else {
            // 浅色下做成柔和的紫灰蓝，比其它方案更沉一点
            listOf(Color(0xFFC8C6EA), Color(0xFFDAD9F2), Color(0xFFF0F0F9))
        }

        BackgroundStyle.PLAIN -> emptyList()
        BackgroundStyle.CUSTOM -> emptyList()
    }
    return Brush.linearGradient(colors)
}

/** Composable 便捷入口：自动识别当前是否深色主题 */
@Composable
fun rememberBackgroundBrush(style: BackgroundStyle): Brush? =
    backgroundBrush(style, isSystemInDarkTheme())

/** PLAIN 时用于铺底的纯色（跟随 Material 主题） */
@Composable
fun plainBackgroundColor(): Color = MaterialTheme.colorScheme.background
