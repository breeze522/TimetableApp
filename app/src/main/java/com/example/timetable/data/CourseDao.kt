package com.example.timetable.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** 某份课表的课程数量（用于课表管理页的副标题） */
data class ScheduleCourseCount(
    val scheduleId: Long,
    val cnt: Int,
)

@Dao
interface CourseDao {

    /** 按星期、节次排序返回全部课程，供课表渲染 */
    @Query("SELECT * FROM courses ORDER BY dayOfWeek ASC, startSection ASC")
    fun observeAll(): Flow<List<Course>>

    /** 只观察某一份课表下的课程 */
    @Query(
        """
        SELECT * FROM courses
        WHERE scheduleId = :scheduleId
        ORDER BY dayOfWeek ASC, startSection ASC
        """,
    )
    fun observeBySchedule(scheduleId: Long): Flow<List<Course>>

    /** 一次性读取全部课程（供提醒调度、桌面小组件等后台场景使用） */
    @Query("SELECT * FROM courses ORDER BY dayOfWeek ASC, startSection ASC")
    suspend fun getAllOnce(): List<Course>

    /** 一次性读取某份课表的课程 */
    @Query(
        """
        SELECT * FROM courses
        WHERE scheduleId = :scheduleId
        ORDER BY dayOfWeek ASC, startSection ASC
        """,
    )
    suspend fun getByScheduleOnce(scheduleId: Long): List<Course>

    @Query("SELECT * FROM courses WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): Course?

    /** 各课表的课程数：返回「课表 id → 课程数」 */
    @Query("SELECT scheduleId, COUNT(*) AS cnt FROM courses GROUP BY scheduleId")
    fun observeCountsBySchedule(): Flow<List<ScheduleCourseCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(course: Course): Long

    @Update
    suspend fun update(course: Course)

    @Delete
    suspend fun delete(course: Course)

    @Query("DELETE FROM courses")
    suspend fun clearAll()

    @Query("DELETE FROM courses WHERE scheduleId = :scheduleId")
    suspend fun clearBySchedule(scheduleId: Long)
}
