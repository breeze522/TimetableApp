package com.example.timetable.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.timetable.data.AppDatabase
import com.example.timetable.data.Course
import com.example.timetable.data.SectionTime
import com.example.timetable.data.SettingsRepository
import com.example.timetable.data.WeekCalculator
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 上课提醒调度器。
 *
 * 思路：不预先排满整学期的闹钟，而是每次「只排下一节课」。
 * 当某个闹钟触发时，[ReminderReceiver] 会展示通知并调用 [scheduleNext] 排下一节，
 * 形成自驱动的链式调度 —— 既省电，又不必担心系统重启后大量闹钟失效。
 */
object ReminderScheduler {

    const val ACTION_REMIND = "com.example.timetable.action.REMIND"
    private const val TAG = "ReminderScheduler"

    /** 请求码基数，配合课程 id 生成唯一 PendingIntent */
    private const val REQUEST_CODE = 1001

    /**
     * 计算并安排「下一节需要提醒的课」。
     *
     * 会先取消已有闹钟，再根据当前设置重新安排。
     * 提醒关闭或没有课程时，只做取消。
     */
    suspend fun reschedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancel(context, alarmManager)

        val settings = SettingsRepository(context)
        val enabled = settings.reminderEnabled.first()
        if (!enabled) {
            Log.d(TAG, "提醒已关闭，不安排闹钟")
            return
        }

        val leadMinutes = settings.reminderLeadMinutes.first()
        val sectionTimes = settings.sectionTimes.first()
        val termStart = settings.termStart.first() ?: return
        val totalWeeks = settings.totalWeeks.first()

        val courses = AppDatabase.get(context).courseDao().getAllOnce()
        if (courses.isEmpty()) return

        val next = findNextReminder(
            courses = courses,
            termStart = termStart,
            totalWeeks = totalWeeks,
            sectionTimes = sectionTimes,
            leadMinutes = leadMinutes,
            now = LocalDateTime.now(),
        ) ?: run {
            Log.d(TAG, "未来 7 天内没有可提醒的课")
            return
        }

        scheduleAt(
            context,
            alarmManager,
            next.triggerAt,
            next.course,
            next.leadMinutes,
            next.classAt,
        )
        Log.d(TAG, "已安排提醒：${next.course.name} @ ${next.triggerAt}")
    }

    /** 取消当前所有待触发的提醒闹钟 */
    fun cancel(context: Context, alarmManager: AlarmManager? = null) {
        val am = alarmManager
            ?: context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context, 0, null, 0)
        am.cancel(pi)
    }

    /** 一条即将到期的提醒 */
    data class NextReminder(
        val course: Course,
        /** 提醒触发时刻（= 上课时刻 - 提前量） */
        val triggerAt: LocalDateTime,
        val leadMinutes: Int,
        /** 上课时刻本身，用于灵动岛胶囊的倒计时（setWhen） */
        val classAt: LocalDateTime,
    )

    /**
     * 找出从现在起最近的一节可提醒课程。
     *
     * 逐天向后扫描（最多 7 天）：对每一天筛出当周有效的课程，
     * 按开始时间排序，取第一个「触发时间晚于现在」的。
     */
    fun findNextReminder(
        courses: List<Course>,
        termStart: LocalDate,
        totalWeeks: Int,
        sectionTimes: List<SectionTime>,
        leadMinutes: Int,
        now: LocalDateTime,
    ): NextReminder? {
        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            val week = WeekCalculator.weekOf(termStart, date)
            if (week < 1 || week > totalWeeks) continue

            val dayOfWeek = date.dayOfWeek.value // 1=周一 … 7=周日

            val todays = courses
                .filter { it.dayOfWeek == dayOfWeek && it.isActiveInWeek(week) }
                .mapNotNull { c ->
                    val st = sectionTimes.getOrNull(c.startSection - 1) ?: return@mapNotNull null
                    val start = parseTime(st.start) ?: return@mapNotNull null
                    val classAt = LocalDateTime.of(date, start)
                    val trigger = classAt.minusMinutes(leadMinutes.toLong())
                    ReminderCandidate(c, trigger, leadMinutes, classAt)
                }
                .sortedBy { it.triggerAt }

            for (candidate in todays) {
                if (candidate.triggerAt.isAfter(now)) {
                    return NextReminder(
                        course = candidate.course,
                        triggerAt = candidate.triggerAt,
                        leadMinutes = candidate.leadMinutes,
                        classAt = candidate.classAt,
                    )
                }
            }
        }
        return null
    }

    /** 内部使用的候选提醒（比 [NextReminder] 多用于排序的字段） */
    private data class ReminderCandidate(
        val course: Course,
        val triggerAt: LocalDateTime,
        val leadMinutes: Int,
        val classAt: LocalDateTime,
    )

    /** 把提醒排到系统的精准闹钟上 */
    private fun scheduleAt(
        context: Context,
        alarmManager: AlarmManager,
        triggerAt: LocalDateTime,
        course: Course,
        leadMinutes: Int,
        classAt: LocalDateTime,
    ) {
        val triggerMillis = triggerAt
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val classAtMillis = classAt
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val pi = buildPendingIntent(context, REQUEST_CODE, course, leadMinutes, classAtMillis)

        // Android 12+ 需要 SCHEDULE_EXACT_ALARM；无权限时退化为非精准闹钟
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerMillis, pi,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerMillis, pi,
                )
            }
        } catch (e: SecurityException) {
            // 极少数机型的兜底
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
        }
    }

    private fun buildPendingIntent(
        context: Context,
        requestCode: Int,
        course: Course?,
        leadMinutes: Int,
        classAtMillis: Long = 0L,
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_COURSE_NAME, course?.name.orEmpty())
            putExtra(EXTRA_ROOM, course?.room.orEmpty())
            putExtra(EXTRA_TEACHER, course?.teacher.orEmpty())
            putExtra(EXTRA_SECTION_START, course?.startSection ?: 0)
            putExtra(EXTRA_LEAD_MINUTES, leadMinutes)
            // 上课时刻（epoch millis）。灵动岛胶囊用 setWhen 显示倒计时依赖它。
            putExtra(EXTRA_CLASS_AT, classAtMillis)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    /** 解析 "08:30" 这类时间文本 */
    private fun parseTime(text: String): LocalTime? = runCatching {
        val parts = text.trim().split(":")
        LocalTime.of(parts[0].toInt(), parts[1].toInt())
    }.getOrNull()

    /**
     * **仅用于调试**：立刻发一条「上课提醒」，用来观察灵动岛 / 状态栏胶囊效果。
     *
     * 直接给自己发一条内部广播，走和真实闹钟完全相同的代码路径
     * （[ReminderReceiver.onReceive]），因此看到的样式就是真机上的真实样式。
     *
     * 之所以不用 `adb shell am broadcast`：`ReminderReceiver` 是
     * `exported="false"`（安全考虑，不应该让第三方应用能伪造上课提醒），
     * 外部 shell 广播不会被投递。改从应用进程内部发就绕开了这个限制。
     *
     * @param leadSec 距离「上课时刻」还有多少秒；倒计时会从这里开始走。
     */
    fun triggerPreviewReminder(context: Context, leadSec: Long = 90L) {
        val classAt = System.currentTimeMillis() + leadSec * 1000L
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_COURSE_NAME, "自动控制原理")
            putExtra(EXTRA_ROOM, "草堂9-212")
            putExtra(EXTRA_TEACHER, "张老师")
            putExtra(EXTRA_SECTION_START, 3)
            putExtra(EXTRA_LEAD_MINUTES, (leadSec / 60L).toInt())
            putExtra(EXTRA_CLASS_AT, classAt)
        }
        context.sendBroadcast(intent)
    }

    const val EXTRA_COURSE_NAME = "extra_course_name"
    const val EXTRA_ROOM = "extra_room"
    const val EXTRA_TEACHER = "extra_teacher"
    const val EXTRA_SECTION_START = "extra_section_start"
    const val EXTRA_LEAD_MINUTES = "extra_lead_minutes"

    /** 上课时刻（epoch millis），灵动岛倒计时用 */
    const val EXTRA_CLASS_AT = "extra_class_at"
}
