package dev.gatsaeng.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.gatsaeng.app.data.RoutineRepository
import java.time.LocalDate

class RoutineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            val id = intent.getStringExtra(AlarmScheduler.EXTRA_ID) ?: return
            val date = LocalDate.parse(intent.getStringExtra(AlarmScheduler.EXTRA_DATE) ?: return)
            val repository = RoutineRepository(context)
            val routines = repository.load()
            val routine = routines.find { it.id == id } ?: return
            val scheduler = AlarmScheduler(context)
            // Delayed alarms from yesterday must not present stale reminders today.
            val valid = date == LocalDate.now() && routine.isDue(date) && routine.reminderEnabled &&
                !routine.isCompleted(date) && routine.lastRemindedDate != date
            val updated = if (valid && scheduler.notify(routine, date)) routine.copy(lastRemindedDate = date) else routine
            if (updated != routine) repository.save(routines.map { if (it.id == id) updated else it })
            scheduler.schedule(updated)
        }.onFailure { Log.e("RoutineAlarm", "Reminder failed", it) }
    }
}

class CompleteRoutineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            val id = intent.getStringExtra(AlarmScheduler.EXTRA_ID) ?: return
            val date = LocalDate.parse(intent.getStringExtra(AlarmScheduler.EXTRA_DATE) ?: return)
            val repository = RoutineRepository(context)
            val routines = repository.load()
            val routine = routines.find { it.id == id } ?: return
            if (date > LocalDate.now() || date < routine.createdOn) return
            val updated = routine.copy(completedDates = routine.completedDates + date)
            repository.save(routines.map { if (it.id == id) updated else it })
            AlarmScheduler(context).apply {
                clearNotification(id)
                schedule(updated)
            }
        }.onFailure { Log.e("RoutineComplete", "Completion failed", it) }
    }
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            )) return
        runCatching {
            AlarmScheduler(context).scheduleAll(RoutineRepository(context).load())
        }.onFailure { Log.e("RoutineReschedule", "Rescheduling failed", it) }
    }
}
