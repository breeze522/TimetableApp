package com.example.timetable.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.School
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 底部导航栏的四个一级入口。
 *
 * 顺序即底部栏的排列顺序；[label] 是显示在图标下方的文字。
 * 视觉上采用「胶囊式浮岛」：整条栏有圆角背景与描边，
 * 选中项图标 + 文字变成主题色，并在其下方/周围铺一层淡淡的胶囊底。
 */
enum class MainTab(
    val label: String,
    val icon: ImageVector,
) {
    /** 课表主页（周 / 日视图） */
    TIMETABLE("课表", Icons.Default.CalendarMonth),

    /** 成绩查询 */
    GRADES("成绩", Icons.Default.School),

    /** 校园生活 */
    CAMPUS("校园生活", Icons.Default.Face),

    /** 登录教务系统 */
    LOGIN("登录", Icons.Default.AccountCircle),
    ;

    companion object {
        /**
         * 把外部传入的入口名换算成枚举。
         *
         * 用于桌面小组件等外部入口指定「打开后落到哪一页」；
         * 名字对不上（含 null）时一律回落到课表页。
         */
        fun fromName(name: String?): MainTab =
            values().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: TIMETABLE
    }
}
