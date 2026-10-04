package com.example.timetable.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一份课表（一个学期 / 一个身份）。
 *
 * WakeUp 支持同时保存多个课表并在其间快速切换，
 * 例如「本学期」「下学期」「辅修」。
 *
 * 每份课表各自持有独立的学期信息（开学日期 / 总周数），
 * 课程通过 [Course.scheduleId] 归属于某一份课表。
 */
@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 课表名称，如「2026 秋」「大四上」 */
    val name: String,

    /** 开学日期（epochDay），可为空表示尚未设置 */
    val termStartEpochDay: Long? = null,

    /** 学期总周数 */
    val totalWeeks: Int = 20,

    /** 排序权重，越小越靠前 */
    val sortOrder: Int = 0,
) {
    companion object {
        const val DEFAULT_NAME = "我的课表"
    }
}
