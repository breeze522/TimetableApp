package com.example.timetable.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AdjustmentDao {

    @Query(
        """
        SELECT * FROM adjustments
        WHERE scheduleId = :scheduleId
        ORDER BY week ASC, courseId ASC
        """,
    )
    fun observeBySchedule(scheduleId: Long): Flow<List<Adjustment>>

    @Query("SELECT * FROM adjustments WHERE scheduleId = :scheduleId")
    suspend fun getByScheduleOnce(scheduleId: Long): List<Adjustment>

    /** 同一门课在同一周的调整记录只保留一条 */
    @Query(
        """
        SELECT * FROM adjustments
        WHERE scheduleId = :scheduleId AND courseId = :courseId AND week = :week
        LIMIT 1
        """,
    )
    suspend fun find(scheduleId: Long, courseId: Long, week: Int): Adjustment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(adjustment: Adjustment): Long

    @Delete
    suspend fun delete(adjustment: Adjustment)

    @Query("DELETE FROM adjustments WHERE scheduleId = :scheduleId")
    suspend fun clearBySchedule(scheduleId: Long)

    /** 取消/删除某门课的全部调课记录（课程被删时调用） */
    @Query("DELETE FROM adjustments WHERE courseId = :courseId")
    suspend fun deleteByCourse(courseId: Long)
}
