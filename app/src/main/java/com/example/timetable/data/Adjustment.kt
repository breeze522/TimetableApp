package com.example.timetable.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 单周调课记录。
 *
 * 对齐 WakeUp 的「调课工具」：不修改原始课程，而是记一条
 * 「第 N 周的某节课被挪走了 / 暂停了」的例外。
 *
 * [type] = MOVE 时，[targetDayOfWeek] / [targetStartSection] 是新的位置；
 * [type] = CANCEL 时表示这一周这节课停上。
 */
@Entity(tableName = "adjustments")
data class Adjustment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 所属课表 */
    val scheduleId: Long = 0,

    /** 被调整的课程 id */
    val courseId: Long,

    /** 生效的周次 */
    val week: Int,

    /** ADJUST_MOVE / ADJUST_CANCEL */
    val type: Int = ADJUST_MOVE,

    /** 调整后的星期（1 = 周一 … 7 = 周日）；CANCEL 时忽略 */
    val targetDayOfWeek: Int = 0,

    /** 调整后的起始节次；CANCEL 时忽略 */
    val targetStartSection: Int = 0,

    /** 调整后的持续节数；0 表示沿用原课程 */
    val targetSectionCount: Int = 0,
) {
    companion object {
        const val ADJUST_MOVE = 0
        const val ADJUST_CANCEL = 1
    }
}
