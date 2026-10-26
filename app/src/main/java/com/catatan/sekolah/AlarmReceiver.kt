package com.catatan.sekolah

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getStringExtra("type")) {
            "task" -> {
                val id = intent.getLongExtra("id", -1)
                val t = Store.tasks(context).firstOrNull { it.id == id } ?: return
                if (t.done) return
                val sub = if (t.subject.isNotBlank()) " (${t.subject})" else ""
                Notifier.show(
                    context, (id % 1_000_000L).toInt() + 100,
                    "Besok deadline tugas",
                    "${t.title}$sub" + if (t.notes.isNotBlank()) "\n${t.notes}" else ""
                )
            }
            "lesson" -> {
                val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                    .get(Calendar.DAY_OF_WEEK)
                val list = Store.lessons(context).filter { it.day == tomorrow }.sortedBy { it.start }
                if (list.isNotEmpty()) {
                    val text = list.joinToString("\n") {
                        "${it.start}-${it.end}  ${it.subject}" +
                            if (it.room.isNotBlank()) " (${it.room})" else ""
                    }
                    Notifier.show(context, 1, "Jadwal besok (${dayName(tomorrow)})", text)
                }
                Scheduler.scheduleDailyLesson(context)
            }
        }
    }
}
