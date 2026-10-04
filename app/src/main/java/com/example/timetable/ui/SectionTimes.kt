package com.example.timetable.ui

import com.example.timetable.data.SectionTime

/**
 * 节次时间表（UI 侧的辅助工具）。
 *
 * 数据来源是 [SectionTime] 列表（可在设置里自定义），
 * 这里只提供「取第 N 节的起止时间」等便捷方法。
 */
object SectionTimes {

    /** 兜底默认值，仅在数据尚未加载时使用 */
    val defaults: List<SectionTime> = SectionTime.defaults()

    /** 给定作息表，取第 [section] 节（1 起）的开始时间 */
    fun startOf(times: List<SectionTime>, section: Int): String =
        times.getOrNull(section - 1)?.start ?: ""

    /** 给定作息表，取第 [section] 节（1 起）的结束时间 */
    fun endOf(times: List<SectionTime>, section: Int): String =
        times.getOrNull(section - 1)?.end ?: ""
}
