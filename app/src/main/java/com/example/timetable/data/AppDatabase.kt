package com.example.timetable.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Course::class, Schedule::class, Adjustment::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun courseDao(): CourseDao

    abstract fun scheduleDao(): ScheduleDao

    abstract fun adjustmentDao(): AdjustmentDao

    companion object {

        /**
         * v1 → v2：引入「多课表」。
         *
         * 新增 `schedules` 表，并给 `courses` 加 `scheduleId` 列。
         * 已存在的课程统一归到 scheduleId = 0（默认课表），
         * 同时插入一条 id=0 的默认课表记录，界面上就能直接看到并重命名它。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `schedules` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `name` TEXT NOT NULL,
                        `termStartEpochDay` INTEGER,
                        `totalWeeks` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "ALTER TABLE `courses` ADD COLUMN `scheduleId` INTEGER NOT NULL DEFAULT 0",
                )
                // 默认课表：显式指定 id = 0，与旧数据的 scheduleId 对齐
                db.execSQL(
                    """
                    INSERT INTO `schedules` (`id`, `name`, `termStartEpochDay`, `totalWeeks`, `sortOrder`)
                    VALUES (0, '我的课表', NULL, 20, 0)
                    """.trimIndent(),
                )
            }
        }

        /**
         * v2 → v3：引入「单周调课」记录表。
         *
         * 注意：`Adjustment` 实体没有声明 `@Entity(indices = ...)`，
         * Room 在迁移后会比对表结构，多出来的索引同样会判定为「迁移不正确」，
         * 所以这里**不能**建索引。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `adjustments`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `adjustments` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `scheduleId` INTEGER NOT NULL,
                        `courseId` INTEGER NOT NULL,
                        `week` INTEGER NOT NULL,
                        `type` INTEGER NOT NULL,
                        `targetDayOfWeek` INTEGER NOT NULL,
                        `targetStartSection` INTEGER NOT NULL,
                        `targetSectionCount` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * v3 → v4：作息调整，**节次整体前移 2 节**。
         *
         * 学校作息改成「4 个大节 × 2 小节」，下午从第 5 节（14:00）开始。
         * 之前排在第 7、8、9、10 节的课需要整体上移两个节次：
         *
         * - 原第 7 节 → 第 5 节
         * - 原第 8 节 → 第 6 节
         * - 原第 9 节 → 第 7 节
         * - 原第 10 节 → 第 8 节
         *
         * 即：凡是 `startSection >= 7` 的课程，起始节次 −2，
         * 持续节数不变（跨节课程整体平移）。
         * 第 1~6 节保持原位；第 11 节（晚上）一并前移到第 9 节。
         *
         * 调课记录里的 `targetStartSection` 也要同步平移，
         * 否则「第 N 周的调课」会指向错误的节次。
         *
         * `sectionCount` 不变，因此只需更新起始节次。
         *
         * 注：同样的规则也写进了导入器（[EamsImporter.shiftForwardSections]），
         * 保证「已存在的旧数据」和「以后重新导入的数据」结果一致。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 课程：起始节次 >= 7 的整体上移 2 节
                db.execSQL(
                    """
                    UPDATE `courses`
                    SET `startSection` = `startSection` - 2
                    WHERE `startSection` >= 7
                    """.trimIndent(),
                )
                // 调课记录：目标节次同步平移（CANCEL 记录 targetStartSection 为 0，不动）
                db.execSQL(
                    """
                    UPDATE `adjustments`
                    SET `targetStartSection` = `targetStartSection` - 2
                    WHERE `targetStartSection` >= 7
                    """.trimIndent(),
                )
            }
        }



        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "timetable.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
