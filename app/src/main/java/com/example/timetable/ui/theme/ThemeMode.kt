package com.example.timetable.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

/**
 * 主题模式（深色 / 浅色 / 跟随系统）。
 *
 * 存到 DataStore 时用 [name]（枚举名），改名会导致回退到 [SYSTEM]。
 * 这里刻意不做兼容映射 —— 主题是可随时重选的外观项，不值得为它背历史包袱。
 *
 * @param label 设置项上显示的中文名
 */
enum class ThemeMode(val label: String) {
    /** 跟随系统深色模式开关（默认） */
    SYSTEM("跟随系统"),

    /** 强制浅色 */
    LIGHT("浅色"),

    /** 强制深色 */
    DARK("深色");

    /**
     * 结合系统当前状态，算出最终该用不用深色。
     *
     * [SYSTEM] 是唯一需要读系统状态的模式；其余两种是用户的显式选择，
     * 优先级高于系统 —— 这正是「自带开关」的意义所在。
     */
    @Composable
    fun resolveIsDark(): Boolean = when (this) {
        SYSTEM -> isSystemInDarkTheme()
        LIGHT -> false
        DARK -> true
    }

    companion object {
        /** 默认模式 */
        val DEFAULT = SYSTEM

        /** 从 DataStore 存的字符串还原，非法值回退到 [DEFAULT] */
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
