package com.catatan.sekolah

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object Scheduler {
    private const val REMIND_HOUR = 18 // pengingat jam 18:00 pada H-1
    private const val LESSON_CODE = 1

    private fun alarmMgr(c: Context) = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun taskCode(id: Long) = (id % 1_000_000_000L).toInt() + 10

    private fun pending(c: Context, code: Int, type: String, id: Long): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java)
            .putExtra("type", type)
            .putExtra("id", id)
        return PendingIntent.getBroadcast(
            c, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setAlarm(c: Context, at: Long, pi: PendingIntent) {
        val am = alarmMgr(c)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun scheduleTask(c: Context, t: Task) {
        val pi = pending(c, taskCode(t.id), "task", t.id)
        val cal = Calendar.getInstance().apply {
            timeInMillis = t.dueDate
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, REMIND_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (t.done || cal.timeInMillis <= System.currentTimeMillis()) {
            alarmMgr(c).cancel(pi)
        } else {
            setAlarm(c, cal.timeInMillis, pi)
        }
    }

    fun cancelTask(c: Context, t: Task) {
        alarmMgr(c).cancel(pending(c, taskCode(t.id), "task", t.id))
    }

    /** Alarm harian jam 18:00: memberi tahu jadwal pelajaran esok hari. */
    fun scheduleDailyLesson(c: Context) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, REMIND_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis() + 60_000) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        setAlarm(c, cal.timeInMillis, pending(c, LESSON_CODE, "lesson", 0))
    }

    fun scheduleAll(c: Context) {
        Store.tasks(c).forEach { scheduleTask(c, it) }
        scheduleDailyLesson(c)
    }
}
