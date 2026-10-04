package com.example.timetable.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.timetable.MainActivity
import com.example.timetable.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 上课提醒的广播接收器。
 *
 * 收到闹钟后：
 * 1. 弹出通知（课程名 / 教室 / 剩余分钟）；
 * 2. 立刻排下一节课的提醒，形成链式调度。
 *
 * ## 关于「灵动岛」（Android 16 Live Updates）
 *
 * Android 16 起，系统对「正在进行的、有时效价值的活动」提供了官方展示位：
 * 状态栏胶囊（灵动岛区域）+ 锁屏大卡片。要拿到这个展示位需要满足：
 *
 * 1. 声明 `POST_PROMOTED_NOTIFICATIONS` 权限（普通权限，安装即授予）；
 * 2. 必须使用**平台** `Notification.Builder`（`NotificationCompat` 不支持提升）；
 * 3. 必须 `.setOngoing(true)` + `.setRequestPromotedOngoing(true)`；
 * 4. 必须有 `setContentTitle`；
 * 5. 样式只能是 Standard / BigText / Call / Progress / Metric 之一；
 * 6. 不能 `setCustomContentView`、不能 `setColorized(true)`；
 * 7. 渠道重要性不能是 `IMPORTANCE_MIN`。
 *
 * 另外官方规范要求：Live Update 只应在「事件开始前不久」展示（约 30 分钟内），
 * 而不是提前很久就挂上去 —— 本应用的提醒提前量默认 10/15 分钟，
 * 天然落在这个窗口内。
 *
 * 不满足条件时（老系统 / 未获提升资格），自动退回到原来的 `NotificationCompat` 实现。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val courseName = intent.getStringExtra(ReminderScheduler.EXTRA_COURSE_NAME).orEmpty()
        val room = intent.getStringExtra(ReminderScheduler.EXTRA_ROOM).orEmpty()
        val teacher = intent.getStringExtra(ReminderScheduler.EXTRA_TEACHER).orEmpty()
        val sectionStart = intent.getIntExtra(ReminderScheduler.EXTRA_SECTION_START, 0)
        val leadMinutes = intent.getIntExtra(ReminderScheduler.EXTRA_LEAD_MINUTES, 10)
        val classAtMillis = intent.getLongExtra(ReminderScheduler.EXTRA_CLASS_AT, 0L)

        if (courseName.isNotBlank()) {
            showNotification(
                context = context,
                courseName = courseName,
                room = room,
                teacher = teacher,
                sectionStart = sectionStart,
                leadMinutes = leadMinutes,
                classAtMillis = classAtMillis,
            )
        }

        // 链式安排下一节
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.reschedule(context)
            } finally {
                pendingResult.finish()
            }
        }

        // 驱动胶囊里的倒计时文字。
        //
        // 背景：实测 HyperOS 3 的状态栏胶囊**不渲染**系统的 chronometer
        // （`setUsesChronometer`/`setChronometerCountDown` 即使正确写入 extras
        // 也不会显示），只渲染「文字」。所以这里改成自己算倒计时文本、
        // 每秒重发一次通知，让胶囊里的 `分:秒` 真的走起来。
        startCountdownTicker(
            context = context,
            courseName = courseName,
            room = room,
            teacher = teacher,
            sectionStart = sectionStart,
            classAtMillis = classAtMillis,
        )
    }

    /**
     * 每秒刷新一次通知，让胶囊里的倒计时文字自走。
     *
     * 只在「距上课 ≤ 60 分钟」时启动，到点自动停止，避免无谓唤醒。
     * 用 [goAsync] 拿到的额外执行窗口不足以跑一分钟，所以这里直接起
     * 一段脱离 receiver 生命周期的协程 —— 通知更新本身不需要 receiver 存活。
     */
    private fun startCountdownTicker(
        context: Context,
        courseName: String,
        room: String,
        teacher: String,
        sectionStart: Int,
        classAtMillis: Long,
    ) {
        if (classAtMillis <= 0L) return
        val remaining = classAtMillis - System.currentTimeMillis()
        if (remaining <= 0L || remaining > 60 * 60 * 1000L) return

        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                val left = classAtMillis - System.currentTimeMillis()
                if (left <= 0L) break
                // 每秒重画一次胶囊文本
                runCatching {
                    showNotification(
                        context = appContext,
                        courseName = courseName,
                        room = room,
                        teacher = teacher,
                        sectionStart = sectionStart,
                        leadMinutes = (left / 60_000L).toInt(),
                        classAtMillis = classAtMillis,
                    )
                }
                delay(1000L)
            }
            // 上课时刻到：撤下实时活动
            runCatching {
                NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
            }
        }
    }

    private fun showNotification(
        context: Context,
        courseName: String,
        room: String,
        teacher: String,
        sectionStart: Int,
        leadMinutes: Int,
        classAtMillis: Long,
    ) {
        ensureChannel(context)

        // Android 13+ 需要运行时通知权限，未授权时静默跳过
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = if (leadMinutes > 0) {
            "$courseName 还有 $leadMinutes 分钟上课"
        } else {
            "$courseName 现在开始上课"
        }

        val detail = buildString {
            if (sectionStart > 0) append("第 $sectionStart 节")
            if (room.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(room)
            }
            if (teacher.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(teacher)
            }
        }.ifBlank { "点击查看课表" }

        val notification = if (canUseLiveUpdate(context)) {
            buildLiveUpdateNotification(
                context = context,
                title = title,
                detail = detail,
                courseName = courseName,
                room = room,
                classAtMillis = classAtMillis,
                contentPi = contentPi,
            )
        } else {
            buildLegacyNotification(context, title, detail, contentPi)
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    /**
     * 判断当前是否具备使用 Live Update 的条件。
     *
     * - 系统需为 Android 16（API 36）及以上；
     * - 且系统认为本应用「可以发布被提升的 ongoing 通知」
     *   （取决于权限授予 + 渠道配置 + 用户是否关闭了提升开关）。
     */
    private fun canUseLiveUpdate(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.canPostPromotedNotifications()
    }

    /**
     * 计算胶囊里显示的倒计时文本，格式 `分:秒`（如 `7:30`）。
     *
     * - 剩 ≥ 1 小时：`1:05:20`
     * - 剩 < 1 小时：`7:30`
     * - 已到点：返回 null（调用方会撤下通知）
     */
    private fun countdownText(classAtMillis: Long): String? {
        if (classAtMillis <= 0L) return null
        val left = classAtMillis - System.currentTimeMillis()
        if (left <= 0L) return null
        val totalSec = left / 1000L
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            "%d:%02d:%02d".format(h, m, s)
        } else {
            "%d:%02d".format(m, s)
        }
    }

    /**
     * 胶囊里显示的短文本 —— **只放教室房间号**。
     *
     * 为什么只放教室：
     * - 胶囊只有**一个**文本槽位（见 [buildLiveUpdateNotification] 的说明），
     *   课程名 + 教室硬拼在一起会互相挤占，两头都读不全；
     * - 房间号通常只有 5 个字符（如 `9-212`），**远低于官方「7 字符完整显示」
     *   的阈值**，能完整、清晰地显示出来；
     * - 「马上上什么课」在下拉卡片里写得清清楚楚（课程名在 subText，
     *   还有实时倒计时），胶囊只承担「去哪儿」这一个最紧急的指引。
     *
     * 例：`草堂9-212` → `9-212`；`雁塔 8-104` → `8-104`。
     */
    private fun buildCapsuleText(room: String): String? {
        val roomShort = compactRoom(room)
        return roomShort.ifBlank { null }
    }

    /**
     * Android 16 灵动岛版本（小米「焦点通知」实时活动样式）。
     *
     * 目标效果 —— 状态栏胶囊：
     * ```
     * [图标] 9-212
     * ```
     * **只显示教室房间号**，干净、完整、不截断。
     * 课程名与实时倒计时在下拉卡片里展示。
     *
     * ## 实现要点与踩坑
     *
     * 1. 用平台 `Notification.Builder`（不是 Compat）；
     * 2. `setOngoing(true)` + 请求提升的 extra 才「申请提升」；
     * 3. **坑一**：HyperOS 3 的胶囊不渲染系统 chronometer。即使
     *    `setUsesChronometer(true)` / `setChronometerCountDown(true)` 都正确写入
     *    extras，胶囊仍只显示纯文字。倒计时因此改为由**应用自己**每秒重算并重发
     *    通知（见 [startCountdownTicker]），呈现在**下拉卡片**里。
     * 4. **坑二**：胶囊只有**一个**文本槽位，`setShortCriticalText` 与标题是
     *    「谁短谁上」的关系，**没有左右两个独立文本框**，也无法多行、无法改字号。
     *    胶囊上限 96dp、少于 7 个字符才完整显示 —— 这正好适合只放房间号。
     *
     * ## 关于提升开关的兼容处理
     *
     * `Notification.Builder.setRequestPromotedOngoing(boolean)` 直到 **API 37** 才成为
     * 公开方法，而本项目的 compileSdk 是 36 —— 直接调用编译不过。
     * 但 API 36 已经具备完整的提升能力（有 `canPostPromotedNotifications()`、
     * 也有 `FLAG_PROMOTED_ONGOING`），只是开关要通过 **extra** 传递。
     * 所以这里走「按名字查 extra key」的兼容路径：
     * 反射拿到 37 上的常量，拿不到就退回 AOSP 里稳定不变的字符串字面量。
     * 这样在 API 36 真机上同样能触发灵动岛，且不依赖编译期符号。
     */
    private fun buildLiveUpdateNotification(
        context: Context,
        title: String,
        detail: String,
        courseName: String,
        room: String,
        classAtMillis: Long,
        contentPi: PendingIntent,
    ): Notification {
        // 教室压缩成短文本，胶囊里用它
        val roomShort = compactRoom(room)
        val capsuleTitle = roomShort.ifBlank { courseName.take(7) }

        // 下拉卡片正文里带实时倒计时（`7:30`）。胶囊放不下它，但卡片可以，
        // 所以倒计时信息不丢，只是从胶囊移到了下拉卡片。
        val cd = countdownText(classAtMillis)
        val cardText = if (cd != null) "$detail · 还有 $cd" else detail

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify_class)
            // 胶囊主文字：教室（满足 Live Update 必须有 contentTitle 的硬性要求）
            .setContentTitle(capsuleTitle)
            .setContentText(cardText)
            .setStyle(Notification.BigTextStyle().bigText("$courseName $cardText"))
            .setContentIntent(contentPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // 允许系统在锁屏展示（提升为 Live Update 的前提之一）
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            // 展开卡片里补充课程名
            .setSubText(courseName.ifBlank { null })

        // 胶囊里只显示教室房间号（如 `9-212`）。
        // 必须用 shortCriticalText —— HyperOS 只认它，不认 chronometer。
        val capsuleText = buildCapsuleText(room)
        if (capsuleText != null) {
            builder.setShortCriticalText(capsuleText)
        }

        // 同时把 when/chronometer 也设上：在不挑食的 AOSP 设备上，
        // 系统能渲染出更精确的原生倒计时（HyperOS 上会被忽略，无副作用）。
        if (classAtMillis > 0L) {
            builder.setWhen(classAtMillis)
            builder.setShowWhen(true)
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(true)
        } else {
            builder.setShowWhen(false)
        }

        // 申请提升为 Live Update（API 37 用公开方法，API 36 用 extra）
        applyPromotedOngoingRequest(builder)

        return builder.build()
    }

    /**
     * 把教室名压缩成适合放进状态栏胶囊的短文本。
     *
     * 「草堂9-212」→「9-212」；「草堂 8-104」→「8-104」。
     * 规则：优先取「数字开头的楼栋-房间」片段；没有则直接截断。
     */
    private fun compactRoom(room: String): String {
        if (room.isBlank()) return ""
        // 匹配形如 9-212 / 8-104 / 12-305 的楼栋-房间编号
        val m = Regex("""(\d{1,2}\s*[-－]\s*\d{1,4})""").find(room)
        if (m != null) return m.value.replace(" ", "").replace("－", "-")
        // 没有编号就退回原文本，去掉常见后缀噪音
        return room
            .removePrefix("草堂")
            .removePrefix("雁塔")
            .trim()
            .take(7)
    }

    /**
     * 向系统申请把这条 ongoing 通知提升到灵动岛 / 状态栏胶囊。
     *
     * 优先用 API 37 起的公开构建器方法；不可用时退回 extra 方式
     * （`Notification.EXTRA_REQUEST_PROMOTED_ONGOING`，即
     * `"android.requestPromotedOngoing"`）。
     *
     * 用 `getExtras()` 拿到 builder 当前的 Bundle 再写入 ——
     * 这个方法是公开 API，比反射私有字段稳。任何一步失败都只是
     * 「拿不到提升」，不会影响通知本身的展示。
     */
    private fun applyPromotedOngoingRequest(builder: Notification.Builder) {
        // 路径 1：API 37+ 的公开方法 setRequestPromotedOngoing(true)
        val viaMethod = runCatching {
            val m = Notification.Builder::class.java
                .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
            m.invoke(builder, true)
            true
        }.getOrDefault(false)
        if (viaMethod) {
            return
        }

        // 路径 2：通过 extra 传递
        runCatching {
            // 常量在 API 37 才公开，拿不到就用 AOSP 里稳定不变的字面量
            val key = runCatching {
                Notification::class.java
                    .getField("EXTRA_REQUEST_PROMOTED_ONGOING")
                    .get(null) as? String
            }.getOrNull() ?: "android.requestPromotedOngoing"

            val extras = builder.extras ?: android.os.Bundle()
            extras.putBoolean(key, true)
            builder.setExtras(extras)
        }
    }

    /** 兼容旧系统 / 未获提升资格的普通高优先级通知 */
    private fun buildLegacyNotification(
        context: Context,
        title: String,
        detail: String,
        contentPi: PendingIntent,
    ): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify_class)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .build()

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "上课提醒",
            // 注意：Live Update 要求渠道重要性不能是 IMPORTANCE_MIN
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "在上课前提醒你即将开始的课程"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "class_reminder"
        const val NOTIFICATION_ID = 2001
    }
}
