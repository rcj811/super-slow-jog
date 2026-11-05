package com.rcj811.slowjog

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Locale

/**
 * 每日運動提醒：每天在設定的時間跳出通知。
 * 用 AlarmManager（不需精準鬧鐘權限，可能晚幾分鐘），開機、改時間後自動重新排程。
 */
object DailyReminder {
    private const val CHANNEL = "daily"
    private const val NOTIF_ID = 77
    const val ACTION_FIRE = "slowjog.DAILY_REMINDER"

    /** 下一次提醒時間（今天的時間已過就排明天） */
    fun nextTrigger(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
        var t = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!t.isAfter(now)) t = t.plusDays(1)
        return t
    }

    fun timeText(hour: Int, minute: Int): String {
        val period = when (hour) {
            in 0..4 -> "凌晨"
            in 5..10 -> "早上"
            in 11..12 -> "中午"
            in 13..17 -> "下午"
            else -> "晚上"
        }
        return String.format(Locale.US, "%s %02d:%02d", period, hour, minute)
    }

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 7,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** 依目前設定排程（或取消） */
    fun schedule(context: Context) {
        val ctx = context.applicationContext
        Prefs.load(ctx)
        val s = Prefs.settings.value
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(ctx)
        am.cancel(pi)
        if (!s.dailyReminderOn) return
        val at = nextTrigger(ZonedDateTime.now(), s.dailyHour, s.dailyMinute).toInstant().toEpochMilli()
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
            .onFailure { runCatching { am.set(AlarmManager.RTC_WAKEUP, at, pi) } }
    }

    /** 跳出提醒通知；今天已達成目標就改成鼓勵，不再催促 */
    fun notify(context: Context) {
        val ctx = context.applicationContext
        Prefs.load(ctx)
        SessionStore.load(ctx)
        val s = Prefs.settings.value
        if (!s.dailyReminderOn) return
        if (RunRepository.state.value.isActive) return // 正在跑就不打擾
        val today = Totals.of(Stats.onDay(SessionStore.sessions.value, LocalDate.now()))
        if (today.minutes >= s.durationMin) return

        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "每日運動提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "每天在設定的時間提醒你超慢跑"
            }
        )
        val open = PendingIntent.getActivity(
            ctx, 8, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val left = s.durationMin - today.minutes
        val text = if (today.minutes > 0) "今天已跑 ${today.minutes} 分鐘，再 $left 分鐘就達標！${s.cadence} 步/分"
        else "今天的目標：${s.durationMin} 分鐘・${s.cadence} 步/分，${s.mascot.label}在等你喔！"
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_run)
            .setContentTitle("超慢跑時間到了 🏃")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setColor(0xFFFF7A45.toInt())
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching { nm.notify(NOTIF_ID, n) }
    }
}

/** 收到每日鬧鐘：發通知並排下一次 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DailyReminder.ACTION_FIRE) {
            runCatching { DailyReminder.notify(context) }
        }
        DailyReminder.schedule(context)
    }
}

/** 開機、更新 App、調整時間或時區後重新排程 */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DailyReminder.schedule(context)
    }
}
