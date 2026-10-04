package com.example.timetable.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM schedules ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<Schedule>>

    @Query("SELECT * FROM schedules ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllOnce(): List<Schedule>

    @Query("SELECT * FROM schedules WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): Schedule?

    @Query("SELECT COUNT(*) FROM schedules")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: Schedule): Long

    @Update
    suspend fun update(schedule: Schedule)

    @Delete
    suspend fun delete(schedule: Schedule)

    /** 删除某份课表下的全部课程 */
    @Query("DELETE FROM courses WHERE scheduleId = :scheduleId")
    suspend fun clearCourses(scheduleId: Long)

    /** 把某份课表的课程整体迁移到另一份课表 */
    @Query("UPDATE courses SET scheduleId = :toId WHERE scheduleId = :fromId")
    suspend fun moveCourses(fromId: Long, toId: Long)
}
