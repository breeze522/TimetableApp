package com.example.timetable.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一门课程。
 *
 * 时间位置用「星期 + 起始节次 + 持续节次」描述，恰好可以直接映射到
 * 课表网格中的矩形区域。
 *
 * 周次用 [startWeek] ~ [endWeek] 的闭区间表示，配合 [weekParity]
 * 处理单双周课程（如「第 1-16 周 单周」）。
 */
@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 课程名，如「高等数学」 */
    val name: String,

    /** 教师，可为空 */
    val teacher: String = "",

    /** 教室，可为空 */
    val room: String = "",

    /** 星期几，1 = 周一 … 7 = 周日 */
    val dayOfWeek: Int,

    /** 起始节次，从 1 开始（第 1 节） */
    val startSection: Int,

    /** 持续节数，至少 1 */
    val sectionCount: Int = 1,

    /** 起始周，1 表示第 1 周 */
    val startWeek: Int = 1,

    /** 结束周，闭区间 */
    val endWeek: Int = 16,

    /** 单双周：0 = 每周，1 = 仅单周，2 = 仅双周 */
    val weekParity: Int = PARITY_ALL,

    /** 颜色索引，用于从调色板取色 */
    val colorIndex: Int = 0,

    /**
     * 所属课表 id。
     *
     * 0 表示「默认课表」——用于兼容旧数据：升级前所有课程都没有归属，
     * 迁移时统一落到 0，界面层把 0 视为当前默认课表。
     */
    val scheduleId: Long = 0,
) {
    companion object {
        const val PARITY_ALL = 0
        const val PARITY_ODD = 1
        const val PARITY_EVEN = 2
    }

    /**
     * 判断本课程在第 [week] 周是否需要上课。
     *
     * 同时校验周次区间与单双周限制。
     */
    fun isActiveInWeek(week: Int): Boolean {
        if (week < startWeek || week > endWeek) return false
        return when (weekParity) {
            PARITY_ODD -> week % 2 == 1
            PARITY_EVEN -> week % 2 == 0
            else -> true
        }
    }

    /** 单双周的可读描述，用于卡片副标题 */
    val parityLabel: String
        get() = when (weekParity) {
            PARITY_ODD -> "单周"
            PARITY_EVEN -> "双周"
            else -> ""
        }
}
