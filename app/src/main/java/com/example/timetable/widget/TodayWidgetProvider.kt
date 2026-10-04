package com.example.timetable.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.timetable.MainActivity
import com.example.timetable.R
import com.example.timetable.data.AppDatabase
import com.example.timetable.data.Course
import com.example.timetable.data.SectionTime
import com.example.timetable.data.SettingsRepository
import com.example.timetable.data.WeekCalculator
import com.example.timetable.ui.theme.courseColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 「今日课表」桌面小组件。
 *
 * 每次更新时读取一次数据库，算出今天所在周次、当天的课程，
 * 用 RemoteViews 逐条渲染（最多展示 5 条，避免超出高度）。
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { id ->
                    val views = buildViews(context)
                    appWidgetManager.updateAppWidget(id, views)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {

        private const val MAX_ITEMS = 5
        private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

        /** 主动刷新全部小组件实例 */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, TodayWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            val intent = Intent(context, TodayWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }

        private suspend fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today)

            val settings = SettingsRepository(context)
            val termStart = settings.termStart.first()
            val totalWeeks = settings.totalWeeks.first()
            val sectionTimes = settings.sectionTimes.first().ifEmpty { SectionTime.defaults() }

            val today = LocalDate.now()
            val week = termStart?.let { WeekCalculator.weekOf(it, today) } ?: 1
            val dayOfWeek = today.dayOfWeek.value // 1=周一 … 7=周日

            val all = AppDatabase.get(context).courseDao().getAllOnce()
            val todays = all
                .filter { it.dayOfWeek == dayOfWeek && it.isActiveInWeek(week) }
                .sortedBy { it.startSection }

            // 标题：日期 + 星期
            views.setTextViewText(
                R.id.widget_title,
                "${today.monthValue}月${today.dayOfMonth}日 ${dayLabel(dayOfWeek)}",
            )
            views.setTextViewText(
                R.id.widget_subtitle,
                if (termStart != null) "第 $week 周" else "",
            )

            // 点击整块 → 打开 App 并落到「课表」页
            // （组件展示的就是今日课表，点进去自然应该看课表，
            //   而不是上次停留的页面）
            val launch = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_TIMETABLE)
            }
            val pi = PendingIntent.getActivity(
                context, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_title, pi)
            views.setOnClickPendingIntent(R.id.widget_list, pi)

            views.removeAllViews(R.id.widget_list)

            if (todays.isEmpty()) {
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                views.setViewVisibility(R.id.widget_list, View.GONE)
                return views
            }

            views.setViewVisibility(R.id.widget_empty, View.GONE)
            views.setViewVisibility(R.id.widget_list, View.VISIBLE)

            todays.take(MAX_ITEMS).forEach { course ->
                val item = RemoteViews(context.packageName, R.layout.widget_today_item)

                item.setTextViewText(R.id.item_name, course.name)

                val roomLine = buildString {
                    if (course.room.isNotBlank()) append(course.room)
                    if (course.teacher.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(course.teacher)
                    }
                }
                item.setTextViewText(R.id.item_room, roomLine)

                // 时间段：第 N 节 08:00
                val st = sectionTimes.getOrNull(course.startSection - 1)
                val timeText = if (st != null) {
                    "第${course.startSection}节 ${st.start}"
                } else {
                    "第${course.startSection}节"
                }
                item.setTextViewText(R.id.item_time, timeText)

                // 左侧色条与主色一致
                item.setInt(
                    R.id.item_bar,
                    "setBackgroundColor",
                    courseColor(course.colorIndex).toArgb(),
                )

                views.addView(R.id.widget_list, item)
            }

            // 超出 5 条时给个提示（复用 subtitle 位置不合适，这里忽略，保持简洁）
            return views
        }

        private fun dayLabel(day: Int): String =
            listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
                .getOrElse(day - 1) { "" }

        private fun androidx.compose.ui.graphics.Color.toArgb(): Int =
            android.graphics.Color.argb(
                (alpha * 255).toInt(),
                (red * 255).toInt(),
                (green * 255).toInt(),
                (blue * 255).toInt(),
            )
    }
}
