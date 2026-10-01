package dev.gatsaeng.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dev.gatsaeng.app.MainActivity
import dev.gatsaeng.app.R
import dev.gatsaeng.app.model.Routine
import java.time.LocalDate
import java.time.ZonedDateTime

class AlarmScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun hasExactPermission(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    fun notificationsAllowed(): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)
    }

    fun createChannel() {
        val channel = NotificationChannel(CHANNEL, "루틴 리마인더", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "선택한 요일과 시간에 루틴을 알려드려요."
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun alarmIntent(id: String): Intent = Intent(context, RoutineAlarmReceiver::class.java).apply {
        data = "gatsaeng://reminder/$id".toUri()
        putExtra(EXTRA_ID, id)
    }

    fun cancel(id: String) {
        val pending = PendingIntent.getBroadcast(context, 0, alarmIntent(id),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) {
            alarms.cancel(pending)
            pending.cancel()
        }
    }

    fun clearNotification(id: String) = NotificationManagerCompat.from(context).cancel(id, 0)

    fun schedule(routine: Routine) {
        cancel(routine.id)
        if (!notificationsAllowed()) return
        val next = routine.nextReminder(ZonedDateTime.now()) ?: return
        val intent = alarmIntent(routine.id).apply {
            putExtra(EXTRA_DATE, next.toLocalDate().toString())
            putExtra(EXTRA_TRIGGER, next.toInstant().toEpochMilli())
        }
        val pending = PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val trigger = next.toInstant().toEpochMilli()
        try {
            if (hasExactPermission()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        } catch (_: SecurityException) {
            // Exact-alarm access can be revoked between checking and scheduling.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }

    fun scheduleAll(routines: List<Routine>) {
        createChannel()
        routines.forEach(::schedule)
    }

    fun notify(routine: Routine, date: LocalDate): Boolean {
        createChannel()
        if (!notificationsAllowed()) return false
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val open = PendingIntent.getActivity(context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val completeIntent = Intent(context, CompleteRoutineReceiver::class.java).apply {
            data = "gatsaeng://complete/${routine.id}/$date".toUri()
            putExtra(EXTRA_ID, routine.id)
            putExtra(EXTRA_DATE, date.toString())
        }
        val complete = PendingIntent.getBroadcast(context, 0, completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(routine.title)
            .setContentText(routine.note.ifBlank { "작은 실천 하나, 오늘의 갓생을 시작해요." })
            .setStyle(NotificationCompat.BigTextStyle().bigText(routine.note.ifBlank { "루틴을 실천하고 완료를 체크해주세요." }))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(R.drawable.ic_notification, "완료했어요", complete)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(routine.id, 0, notification)
            true
        } catch (_: SecurityException) { false }
    }

    companion object {
        const val CHANNEL = "routine_reminders"
        const val EXTRA_ID = "routine_id"
        const val EXTRA_DATE = "routine_date"
        const val EXTRA_TRIGGER = "routine_trigger"
    }
}
